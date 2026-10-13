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
    private val file get() = AtomicFile(File(PlanetApplication.appContext.noBackupFilesDir, "sicau-jiaowu.enc"))
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
        val bytes = (id + "\n" + session.jar.encode()).toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val target = file
        val stream = target.startWrite()
        try { stream.write(cipher.iv + cipher.doFinal(bytes)); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
        finally { bytes.fill(0) }
    }
    @Synchronized fun read(): Pair<String, SicauJiaowuCookieJar>? = runCatching {
        val bytes = file.readFully()
        require(bytes.size > 28)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        val plaintext = cipher.doFinal(bytes.copyOfRange(12, bytes.size))
        try {
            val value = String(plaintext, Charsets.UTF_8)
            val id = value.substringBefore('\n')
            require(Regex("[0-9A-Za-z_-]{4,32}").matches(id) && value.contains('\n'))
            id to SicauJiaowuCookieJar().apply { restore(value.substringAfter('\n')) }
        } finally { plaintext.fill(0) }
    }.getOrNull()
    @Synchronized fun clear() { file.delete() }
}

internal object SicauJiaowuAuth {
    private var active: SicauJiaowuSession? = null
    private var account: String? = null
    suspend fun activate(session: SicauJiaowuSession) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val id = requireNotNull(session.verifiedId)
        try { SicauJiaowuSessionVault.save(session) }
        catch (_: Exception) { throw JiaowuException(JiaowuFailure.LOCAL_STORAGE_FAILED) }
        if(session.jar.route.isWebVpn) WebVpnSessionStore.capture()
        active = session; account = id
        com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.saveVerifiedName(id,session.verifiedName)
        jiaowuTrace("localSessionSaved=true")
    }
    suspend fun identity(): SchoolRead<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            WebVpnSessionStore.restoreOnce()
            val session = active ?: SicauJiaowuSessionVault.read()?.let { (id, jar) ->
                account = id; SicauJiaowuSession(jar).also { active = it }
            } ?: return@withContext SchoolRead.Failure(SyncPhase.EXPIRED, JiaowuFailure.SESSION_EXPIRED.userMessage)
            val info = session.fetchPersonalInfo(requireNotNull(account))
            if (session.jar.route.isWebVpn) SicauWebVpn.move(WebVpnPhase.AVAILABLE)
            // Persist cookie rotations only after a fresh matched profile. Restored sessions are never trusted by cookie alone.
            try { SicauJiaowuSessionVault.save(session) }
            catch (_: Exception) { throw JiaowuException(JiaowuFailure.LOCAL_STORAGE_FAILED) }
            com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.saveVerifiedName(info.studentId,info.name)
            if(session.jar.route.isWebVpn) WebVpnSessionStore.capture()
            SchoolRead.Success(info.studentId)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: JiaowuException) {
            jiaowuTrace("failure=${e.reason.name}")
            if (e.reason == JiaowuFailure.WEBVPN_REQUIRED) {
                SicauWebVpn.move(WebVpnPhase.AUTH_REQUIRED)
                return@withContext SchoolRead.Failure(SyncPhase.FAILED, e.reason.userMessage)
            }
            val invalid = e.reason in setOf(JiaowuFailure.SESSION_EXPIRED, JiaowuFailure.SESSION_COOKIE_MISSING, JiaowuFailure.IDENTITY_MISMATCH)
            if (invalid) clear()
            SchoolRead.Failure(if (invalid) SyncPhase.EXPIRED else SyncPhase.FAILED, e.reason.userMessage)
        } catch (_: Exception) {
            SchoolRead.Failure(SyncPhase.FAILED, JiaowuFailure.NETWORK_UNREACHABLE.userMessage)
        }
    }
    suspend fun <T> read(page:JiaowuPage,parse:(String,String)->T):SchoolRead<T> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val session=active ?: return@withContext SchoolRead.Failure(SyncPhase.EXPIRED,JiaowuFailure.SESSION_EXPIRED.userMessage)
            val id=session.verifiedId ?: return@withContext SchoolRead.Failure(SyncPhase.EXPIRED,JiaowuFailure.SESSION_EXPIRED.userMessage)
            val result=parse(session.fetchBusiness(page),id)
            SicauJiaowuSessionVault.save(session)
            if(session.jar.route.isWebVpn) WebVpnSessionStore.capture()
            SchoolRead.Success(result)
        } catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:JiaowuException){
            if (e.reason == JiaowuFailure.WEBVPN_REQUIRED) {
                SicauWebVpn.move(WebVpnPhase.AUTH_REQUIRED)
                return@withContext SchoolRead.Failure(SyncPhase.FAILED, e.reason.userMessage)
            }
            val expired=e.reason in setOf(JiaowuFailure.SESSION_EXPIRED,JiaowuFailure.SESSION_COOKIE_MISSING)
            if(expired) clear()
            jiaowuTrace("${page.name} failure=${e.reason.name}")
            SchoolRead.Failure(if(expired)SyncPhase.EXPIRED else SyncPhase.FAILED,e.reason.userMessage)
        } catch(_:Exception){SchoolRead.Failure(SyncPhase.FAILED,"教务读取或会话保存失败，保留本机缓存")}
    }
    suspend fun transcript(expected: String): SchoolRead<SchoolTranscript> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val session = active ?: return@withContext SchoolRead.Failure(SyncPhase.EXPIRED, JiaowuFailure.SESSION_EXPIRED.userMessage)
            if(account != expected || session.verifiedId != expected) throw JiaowuException(JiaowuFailure.IDENTITY_MISMATCH)
            val result = session.transcript(expected)
            SicauJiaowuSessionVault.save(session)
            if(session.jar.route.isWebVpn) WebVpnSessionStore.capture()
            SchoolRead.Success(result)
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(e: JiaowuException) {
            val expired = e.reason in setOf(JiaowuFailure.SESSION_EXPIRED, JiaowuFailure.IDENTITY_MISMATCH, JiaowuFailure.SESSION_COOKIE_MISSING)
            if(expired) clear()
            SchoolRead.Failure(when { expired -> SyncPhase.EXPIRED; e.reason == JiaowuFailure.BUSINESS_STRUCTURE -> SyncPhase.PARSER_PENDING; else -> SyncPhase.FAILED },
                if(e.reason == JiaowuFailure.BUSINESS_STRUCTURE) "未找到可确认的个人成绩单结构，请在教务网进入 学生 → 成绩信息 → 成绩查询 → 个人成绩单" else e.reason.userMessage)
        } catch(_: Exception) { SchoolRead.Failure(SyncPhase.FAILED, "成绩读取失败，请检查网络后重试") }
    }
    suspend fun transcriptWebEntry(expected: String): SchoolRead<String> {
        val checked = identity()
        if(checked is SchoolRead.Failure) return checked
        if((checked as SchoolRead.Success).value != expected) return SchoolRead.Failure(SyncPhase.EXPIRED, "教务登录账号已变化，请重新登录")
        val session = active ?: return SchoolRead.Failure(SyncPhase.EXPIRED, "请重新登录教务系统")
        return try {
            val target = session.jar.route.url(JiaowuPage.GRADES.url)
            if(!session.jar.route.isWebVpn) {
                val cookies = session.jar.loadForRequest(JiaowuPage.GRADES.url.toHttpUrl())
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val manager = android.webkit.CookieManager.getInstance()
                    for(cookie in cookies) kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                        manager.setCookie(target, cookie.toString()) { accepted ->
                            if(continuation.isActive) {
                                if(accepted) continuation.resumeWith(Result.success(Unit))
                                else continuation.resumeWith(Result.failure(JiaowuException(JiaowuFailure.LOCAL_STORAGE_FAILED)))
                            }
                        }
                    }
                    manager.flush()
                }
            }
            SchoolRead.Success(target)
        } catch(e: kotlinx.coroutines.CancellationException) { throw e }
        catch(_: Exception) { SchoolRead.Failure(SyncPhase.FAILED, "无法准备教务网页会话，请重试") }
    }
    suspend fun exportExistingToWebView() {
        val owner = com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId
        if(owner.isBlank()) return
        val restored = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SicauJiaowuSessionVault.read() } ?: return
        if(restored.first != owner || restored.second.route.isWebVpn) return
        val target = SicauJiaowuProtocol.PERSONAL_INFO
        val cookies = restored.second.loadForRequest(target.toHttpUrl())
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            if(owner != com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId) return@withContext
            val manager = android.webkit.CookieManager.getInstance()
            // Preserve newer website sessions; only seed a previously unused WebView store.
            if(!manager.getCookie(target).isNullOrBlank()) return@withContext
            for(cookie in cookies) kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
                manager.setCookie(target, cookie.toString()) { if(continuation.isActive) continuation.resumeWith(Result.success(Unit)) }
            }
            manager.flush()
        }
    }
    fun clear() { active?.cancel(); active?.jar?.clear(); active = null; account = null; SicauJiaowuSessionVault.clear() }
}
