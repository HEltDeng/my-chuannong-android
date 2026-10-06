package com.mychuannong.app.school

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
        active = session; account = id
        jiaowuTrace("localSessionSaved=true")
    }
    suspend fun identity(): SchoolRead<String> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val session = active ?: SicauJiaowuSessionVault.read()?.let { (id, jar) ->
                account = id; SicauJiaowuSession(jar).also { active = it }
            } ?: return@withContext SchoolRead.Failure(SyncPhase.EXPIRED, JiaowuFailure.SESSION_EXPIRED.userMessage)
            val info = session.fetchPersonalInfo(requireNotNull(account))
            // Persist cookie rotations only after a fresh matched profile. Restored sessions are never trusted by cookie alone.
            try { SicauJiaowuSessionVault.save(session) }
            catch (_: Exception) { throw JiaowuException(JiaowuFailure.LOCAL_STORAGE_FAILED) }
            SchoolRead.Success(info.studentId)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: JiaowuException) {
            jiaowuTrace("failure=${e.reason.name}")
            val invalid = e.reason in setOf(JiaowuFailure.SESSION_EXPIRED, JiaowuFailure.SESSION_COOKIE_MISSING, JiaowuFailure.IDENTITY_MISMATCH)
            if (invalid) clear()
            SchoolRead.Failure(if (invalid) SyncPhase.EXPIRED else SyncPhase.FAILED, e.reason.userMessage)
        } catch (_: Exception) {
            SchoolRead.Failure(SyncPhase.FAILED, JiaowuFailure.NETWORK_UNREACHABLE.userMessage)
        }
    }
    fun clear() { active?.cancel(); active?.jar?.clear(); active = null; account = null; SicauJiaowuSessionVault.clear() }
}
