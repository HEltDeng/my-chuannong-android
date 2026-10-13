package com.mychuannong.app.school

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import com.mychuannong.app.core.PlanetApplication
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Device-local key material, never an account password; excluded from backup. */
internal object LearningCacheKeys {
    fun owner(account:String)=MessageDigest.getInstance("SHA-256").digest(account.toByteArray()).joinToString(""){"%02x".format(it)}
    @Synchronized fun secret(name:String):String {
        require(name.matches(Regex("[a-z_]+")))
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        val alias="mychuannong_learning_cache_v1"
        val key=(store.getKey(alias,null) as? SecretKey) ?: KeyGenerator.getInstance("AES","AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
        val file=AtomicFile(File(PlanetApplication.appContext.noBackupFilesDir,"learning-$name.key"))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding")
        if(file.baseFile.exists()) {
            val bytes=file.readFully(); require(bytes.size>28)
            cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)))
            return String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
        }
        val random=java.security.SecureRandom()
        val secret=if(name=="mmkv") (1..16).map { (33+random.nextInt(94)).toChar() }.joinToString("")
            else ByteArray(32).also{random.nextBytes(it)}.joinToString(""){"%02x".format(it)}
        cipher.init(Cipher.ENCRYPT_MODE,key)
        val stream=file.startWrite()
        try {stream.write(cipher.iv+cipher.doFinal(secret.toByteArray()));file.finishWrite(stream)}
        catch(e:Exception){file.failWrite(stream);throw e}
        return secret
    }
    fun store(id:String)=com.tencent.mmkv.MMKV.mmkvWithID(id,com.tencent.mmkv.MMKV.SINGLE_PROCESS_MODE,secret("mmkv").take(16))
}
