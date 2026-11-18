package com.mychuannong.app.school

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.mychuannong.app.core.PlanetApplication
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Verified unified credentials stay encrypted in a no-backup, device-only file. */
internal class UnifiedCredential(val owner: String, val password: String)

internal object UnifiedCredentialVault {
    private const val ALIAS = "mychuannong_unified_credential_v1"
    private val file get() = AtomicFile(File(PlanetApplication.appContext.noBackupFilesDir, "sicau-unified-credential.enc"))

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }

    @Synchronized fun save(value: UnifiedCredential) {
        require(value.owner.matches(Regex("[0-9A-Za-z_-]{4,32}")) && value.password.isNotBlank())
        val buffer = ByteArrayOutputStream()
        DataOutputStream(buffer).use { it.writeInt(1); it.writeUTF(value.owner); it.writeUTF(value.password) }
        val bytes = buffer.toByteArray()
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
            val stream = file.startWrite()
            try { stream.write(cipher.iv + cipher.doFinal(bytes)); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
        } finally { bytes.fill(0); buffer.reset() }
    }

    @Synchronized fun read(): UnifiedCredential? = runCatching {
        val encrypted = file.readFully()
        require(encrypted.size in 29..70_000)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, encrypted.copyOfRange(0, 12)))
        }
        val bytes = cipher.doFinal(encrypted.copyOfRange(12, encrypted.size))
        try { DataInputStream(ByteArrayInputStream(bytes)).use {
            require(it.readInt() == 1)
            val owner = it.readUTF(); val password = it.readUTF()
            require(owner.matches(Regex("[0-9A-Za-z_-]{4,32}")) && password.isNotBlank())
            UnifiedCredential(owner, password)
        } } finally { bytes.fill(0) }
    }.getOrNull()

    @Synchronized fun clear() { file.delete() }
}
