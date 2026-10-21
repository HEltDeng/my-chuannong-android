package com.mychuannong.app.school

import okhttp3.HttpUrl.Companion.toHttpUrl
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.mychuannong.app.core.PlanetApplication
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Separate key/file from unified auth; atomic encrypted owner + ASP cookies, never a password. */
internal object SicauJiaowuSessionVault {
    private const val ALIAS = "mychuannong_sicau_jiaowu_v1"
    private const val SESSION_VERSION = "binding-v2\n"
    private val file get() = AtomicFile(File(PlanetApplication.appContext.noBackupFilesDir, "sicau-jiaowu.enc"))
    private val bindingFile get() = AtomicFile(File(PlanetApplication.appContext.noBackupFilesDir, "sicau-binding.enc"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(session: SicauJiaowuSession) {
        val id = requireNotNull(session.verifiedId)
        require(session.jar.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
        val bytes = (SESSION_VERSION + id + "\n" + session.jar.encode()).toByteArray(Charsets.UTF_8)
        writeEncrypted(file, bytes)
    }
    private fun writeEncrypted(target: AtomicFile, bytes: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val stream = target.startWrite()
        try { stream.write(cipher.iv + cipher.doFinal(bytes)); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
        finally { bytes.fill(0) }
    }
    private fun readEncrypted(target: AtomicFile): ByteArray {
        val bytes = target.readFully()
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        return cipher.doFinal(bytes.copyOfRange(12, bytes.size))
    }
    @Synchronized fun read(): Pair<String, SicauJiaowuCookieJar>? = runCatching {
        val plaintext = readEncrypted(file)
        try {
            val value = String(plaintext, Charsets.UTF_8).removePrefix(SESSION_VERSION)
            val id = value.substringBefore('\n')
            require(Regex("[0-9A-Za-z_-]{4,32}").matches(id) && value.contains('\n'))
            id to SicauJiaowuCookieJar().apply { restore(value.substringAfter('\n')) }
        } finally { plaintext.fill(0) }
    }.getOrNull()

    /** Written only by explicit activation after the school confirmed this session's owner.
     * Cookie refreshes cannot overwrite a revoked binding. No password is persisted. */
    @Synchronized fun saveBinding(session: SicauJiaowuSession) {
        val id = requireNotNull(session.verifiedId)
        require(session.jar.hasSession(SicauJiaowuProtocol.PERSONAL_INFO))
        writeEncrypted(bindingFile, id.toByteArray(Charsets.UTF_8))
    }

    @Synchronized fun bindingOwner(): String? = runCatching {
        // Upgrade once from an authenticated legacy session, never from the MMKV flag.
        if (!bindingFile.baseFile.exists() && !File(bindingFile.baseFile.path + ".bak").exists()) {
            val legacy = readEncrypted(file)
            try {
                val value = String(legacy, Charsets.UTF_8)
                // New session files require their separate receipt. Deleting a revoked
                // receipt cannot enable migration again, even after a late cookie save.
                if (value.startsWith(SESSION_VERSION)) return@runCatching null
                val id = value.substringBefore('\n')
                require(Regex("[0-9A-Za-z_-]{4,32}").matches(id) && value.contains('\n'))
                writeEncrypted(bindingFile, id.toByteArray(Charsets.UTF_8))
                writeEncrypted(file, (SESSION_VERSION + value).toByteArray(Charsets.UTF_8))
            } finally { legacy.fill(0) }
        }
        val plaintext = readEncrypted(bindingFile)
        try { String(plaintext, Charsets.UTF_8).takeIf { Regex("[0-9A-Za-z_-]{4,32}").matches(it) } }
        finally { plaintext.fill(0) }
    }.getOrNull()

    @Synchronized fun revokeBinding() {
        // A signed tombstone survives process death and prevents legacy-cookie migration.
        try { writeEncrypted(bindingFile, byteArrayOf(0)) }
        catch (_: Exception) {
            // Fail closed even if the device cannot persist the tombstone.
            bindingFile.delete()
            file.delete()
        }
    }
    @Synchronized fun clear() { file.delete() }
}

internal object SicauJiaowuAuth {
    @Volatile private var active: SicauJiaowuSession? = null
    @Volatile private var pending: SicauJiaowuSession? = null
    @Volatile private var generation = 0L
    private fun requireCurrent(epoch: Long) {
        if (epoch != SicauNativeAuth.webRevision.value) throw kotlinx.coroutines.CancellationException("School session changed")
    }
    private val recovery = AcademicRecovery(object : AcademicRecoveryPort {
        override fun revision() = SicauNativeAuth.webRevision.value + AcademicCredentialVault.revision + generation
        override fun discardInvalidSession(epoch: Long) = synchronized(SicauNativeAuth) { if(epoch == revision()) clear() }
        override fun owner() = SicauJiaowuSessionVault.bindingOwner()
        override suspend fun restore(): SicauJiaowuSession? {
            SicauNativeAuth.exportToWebView()
            return active ?: SicauJiaowuSessionVault.read()?.takeIf { it.first == owner() }?.let {
                it.second.awaitRestoredCookies(SicauNativeAuth.webRevision.value)
                SicauJiaowuSession(it.second)
            }
        }
        override suspend fun browser(previous: SicauJiaowuSession?): SicauJiaowuSession? {
            val route = previous?.jar?.route ?: AcademicCredentialVault.read()?.route ?: SicauWebVpn.route() ?: SicauJiaowuRoute.Direct
            if (route.isWebVpn) return SicauJiaowuSession(SicauJiaowuCookieJar(route))
            val target = SicauJiaowuProtocol.PERSONAL_INFO
            val cookies = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                if (!androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.GET_COOKIE_INFO)) emptyList()
                else androidx.webkit.CookieManagerCompat.getCookieInfo(android.webkit.CookieManager.getInstance(), target)
            }
            val jar = SicauJiaowuCookieJar().apply { acceptRawCookies(target.toHttpUrl(), cookies) }
            if (!jar.hasSession(target) || previous?.jar?.loadForRequest(target.toHttpUrl()) == jar.loadForRequest(target.toHttpUrl())) return null
            return SicauJiaowuSession(jar)
        }
        override suspend fun credential() = AcademicCredentialVault.read()
        override suspend fun storeCredential(value: AcademicCredential, epoch: Long) = synchronized(SicauNativeAuth) {
            if (epoch != revision()) throw kotlinx.coroutines.CancellationException()
            if (value.owner != owner()) throw kotlinx.coroutines.CancellationException()
            AcademicCredentialVault.save(value)
        }
        override suspend fun transport(route: SicauJiaowuRoute, force: Boolean) {
            if (force && route.isWebVpn) SicauWebVpn.move(WebVpnPhase.UNKNOWN)
            if (route.isWebVpn && !SicauWebVpn.ensureAvailable()) throw JiaowuException(
                if (SicauWebVpn.phase.value == WebVpnPhase.AUTH_REQUIRED) JiaowuFailure.WEBVPN_REQUIRED
                else JiaowuFailure.NETWORK_UNREACHABLE)
        }
        override fun create(route: SicauJiaowuRoute): SicauJiaowuSession {
            val epoch=revision()
            return SicauJiaowuSession(SicauJiaowuCookieJar(route), authorized={ epoch == revision() }).also { pending=it }
        }
        override suspend fun save(session: SicauJiaowuSession, epoch: Long) {
            synchronized(SicauNativeAuth) {
                if (epoch != revision()) throw kotlinx.coroutines.CancellationException()
                if (session.verifiedId != owner()) throw kotlinx.coroutines.CancellationException()
                SicauJiaowuSessionVault.save(session)
                active = session
            }
            if (session.jar.route.isWebVpn) WebVpnSessionStore.capture()
        }
    })

    suspend fun activate(session: SicauJiaowuSession, expectedRevision: Long = SicauNativeAuth.webRevision.value) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                synchronized(SicauNativeAuth) {
                    requireCurrent(expectedRevision)
                    pending?.cancel(); pending = null
                    generation++
                    session.adoptCurrentRevision()
                    SicauJiaowuSessionVault.save(session)
                    SicauJiaowuSessionVault.saveBinding(session)
                    active = session
                }
                if (session.jar.route.isWebVpn) WebVpnSessionStore.capture()
                requireCurrent(expectedRevision)
                com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.saveVerifiedName(requireNotNull(session.verifiedId), session.verifiedName)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { throw JiaowuException(JiaowuFailure.LOCAL_STORAGE_FAILED) }
        }

    private suspend fun <T> authenticated(block: suspend (SicauJiaowuSession, String) -> T): SchoolRead<T> =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val epoch = SicauNativeAuth.webRevision.value
            try {
                SchoolAuthState.move(SchoolAuthPhase.AUTHENTICATING)
                val result = recovery.read(block)
                requireCurrent(epoch)
                SchoolAuthState.move(SchoolAuthPhase.SESSION_VALID)
                SchoolRead.Success(result)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: JiaowuException) {
                requireCurrent(epoch)
                // Neither transport expiry nor academic expiry revokes the durable binding or the other realm.
                if (e.reason == JiaowuFailure.WEBVPN_REQUIRED) SicauWebVpn.move(WebVpnPhase.AUTH_REQUIRED)
                val needsUser = e.reason.requiresLogin || e.reason.supportsSchoolVerification ||
                    e.reason in setOf(JiaowuFailure.WEBVPN_REQUIRED, JiaowuFailure.CREDENTIAL_REJECTED,
                        JiaowuFailure.AUTO_LOGIN_NOT_ENABLED, JiaowuFailure.AUTO_LOGIN_STOPPED, JiaowuFailure.CAPTCHA_REQUIRED)
                SchoolAuthState.move(if (needsUser) SchoolAuthPhase.SESSION_EXPIRED else SchoolAuthPhase.ERROR)
                SchoolRead.Failure(if (needsUser) SyncPhase.EXPIRED else if (e.reason == JiaowuFailure.BUSINESS_STRUCTURE)
                    SyncPhase.PARSER_PENDING else SyncPhase.FAILED, e.reason.userMessage)
            } catch (_: Exception) {
                requireCurrent(epoch)
                SchoolRead.Failure(SyncPhase.FAILED, "学校连接或本机保存失败，已保留上次数据")
            } finally { pending = null }
        }

    suspend fun identity(): SchoolRead<String> = authenticated { session, owner ->
        com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.saveVerifiedName(owner, session.verifiedName)
        owner
    }
    suspend fun <T> read(page: JiaowuPage, parse: (String, String) -> T): SchoolRead<T> =
        authenticated { session, owner -> parse(session.fetchBusiness(page), owner) }
    suspend fun transcript(expected: String): SchoolRead<SchoolTranscript> = authenticated { session, owner ->
        if (owner != expected) throw JiaowuException(JiaowuFailure.IDENTITY_MISMATCH)
        session.transcript(expected)
    }
    suspend fun transcriptWebEntry(expected: String): SchoolRead<String> = webEntry(JiaowuPage.GRADES.url, expected)

    /** The exact native academic session seeds the web client after server identity verification. */
    suspend fun webEntry(target: String, expected: String): SchoolRead<String> = authenticated { session, owner ->
        if (owner != expected) throw JiaowuException(JiaowuFailure.IDENTITY_MISMATCH)
        val epoch = SicauNativeAuth.webRevision.value
        val entry = session.jar.route.url(target)
        if (!session.jar.route.isWebVpn) exportCookies(session.jar, target, epoch, replace = true)
        requireCurrent(epoch)
        entry
    }
    private suspend fun exportCookies(jar: SicauJiaowuCookieJar, target: String, epoch: Long, replace: Boolean) =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            val manager = android.webkit.CookieManager.getInstance()
            if (!replace && manager.getCookie(target).orEmpty().split(';').any { it.trim().startsWith("ASPSESSIONID", true) }) return@withContext
            for (cookie in jar.rawForRequest(target.toHttpUrl())) {
                requireCurrent(epoch)
                SchoolWebSessionStore.setCookie(manager, target, cookie)
                requireCurrent(epoch)
            }
            manager.flush()
        }
    suspend fun exportExistingToWebView() {
        val epoch = SicauNativeAuth.webRevision.value
        val owner = com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId
        val restored = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SicauJiaowuSessionVault.read() } ?: return
        if (owner.isBlank() || restored.first != owner || restored.second.route.isWebVpn) return
        exportCookies(restored.second, SicauJiaowuProtocol.PERSONAL_INFO, epoch, replace = false)
    }
    /** Online state only. Explicit logout separately destroys opt-in credentials and binding. */
    fun disableAutoLogin() = synchronized(SicauNativeAuth) {
        AcademicCredentialVault.clear()
        if(pending !== active) pending?.cancel()
        pending = null
    }
    fun clear() = synchronized(SicauNativeAuth) {
        generation++
        active?.cancel(); pending?.cancel(); pending = null
        active?.jar?.clear(); active = null; SicauJiaowuSessionVault.clear()
    }
}
