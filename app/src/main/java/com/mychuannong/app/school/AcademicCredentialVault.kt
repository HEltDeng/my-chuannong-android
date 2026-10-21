package com.mychuannong.app.school

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.mychuannong.app.core.PlanetApplication
import java.io.*
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Verified academic credentials only. Separate key, authenticated encryption, no cloud/device backup.
 * No school secret is written to preferences, bundles, logs or the product backend. */
internal object AcademicCredentialVault {
    @Volatile var revision: Long = 0
        private set
    @Synchronized fun remember(value: AcademicCredential) {
        revision++
        try { save(value) } catch (e: Exception) { file.delete(); throw e }
    }
    private const val ALIAS = "mychuannong_academic_opt_in_v1"
    private val file get() = AtomicFile(File(PlanetApplication.appContext.noBackupFilesDir, "academic-opt-in.enc"))
    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun save(value: AcademicCredential) {
        require(value.owner.isNotBlank() && value.password.isNotBlank())
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use {
            it.writeInt(1); it.writeUTF(value.owner); it.writeUTF(value.password); it.writeUTF(value.route.prefix)
            it.writeInt(value.failures); it.writeLong(value.retryAfter); it.writeBoolean(value.blocked)
        }
        val bytes = buffer.toByteArray()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val output = file.startWrite()
            try { output.write(cipher.iv + cipher.doFinal(bytes)); file.finishWrite(output) }
            catch (e: Exception) { file.failWrite(output); throw e }
        } finally { bytes.fill(0); buffer.reset() }
    }
    @Synchronized fun read(): AcademicCredential? = runCatching {
        val encrypted = file.readFully()
        require(encrypted.size in 29..70_000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
        }
        val bytes = cipher.doFinal(encrypted.copyOfRange(12, encrypted.size))
        try { DataInputStream(ByteArrayInputStream(bytes)).use {
            require(it.readInt() == 1)
            val owner = it.readUTF(); val password = it.readUTF()
            val route = requireNotNull(SicauJiaowuRoute.restore(it.readUTF()))
            val failures = it.readInt(); val deadline = it.readLong(); val blocked = it.readBoolean()
            require(owner.matches(Regex("[0-9A-Za-z_-]{4,32}")) && password.isNotBlank() && failures in 0..2 && deadline >= 0)
            AcademicCredential(owner, password, route, failures, deadline, blocked)
        } } finally { bytes.fill(0) }
    }.getOrNull()
    @Synchronized fun clear() { revision++; file.delete() }
}
