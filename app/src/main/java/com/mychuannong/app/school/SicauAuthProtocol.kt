package com.mychuannong.app.school

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Public SICAU /wui/assets/index-DwxyaMpm.js, verified 2026-10-02. No CSUST fields. */
object SicauAuthProtocol {
    const val ORIGIN = "https://auth.sicau.edu.cn"
    const val APP_ID = "sicau-cas"
    fun decode(raw: String): JsonObject {
        val envelope = JsonParser.parseString(raw).asJsonObject
        val data = envelope.get("data")
        if (data != null && data.isJsonPrimitive && data.asJsonPrimitive.isString) {
            val bytes = Base64.getDecoder().decode(data.asString)
            require(bytes.size > 16 && bytes.size <= 2_000_000)
            val key = MessageDigest.getInstance("MD5").digest((envelope.get("requestId").asString + ":sicau-cas").toByteArray())
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(bytes.copyOfRange(0,16)))
            envelope.add("data", JsonParser.parseString(String(cipher.doFinal(bytes.copyOfRange(16,bytes.size)), Charsets.UTF_8)))
        }
        return envelope
    }
    fun loginBody(number: String, password: String, code: String, uuid: String) = JsonObject().apply {
        addProperty("appid", APP_ID);addProperty("number",number);addProperty("password",password)
        addProperty("code",code);addProperty("uuid",uuid)
    }
    fun accepted(response: JsonObject): Boolean = runCatching {
        response.get("code").asInt == 200 && response.getAsJsonObject("data").get("st").asString.isNotBlank()
    }.getOrDefault(false)
    /** Only the school-returned matching owner; delegated accounts are not selected. */
    fun confirmedNumber(response: JsonObject, entered: String): String? = runCatching {
        if (!accepted(response)) return null
        val data = response.getAsJsonObject("data")
        val owner = data.get("user")?.takeIf { it.isJsonObject }?.asJsonObject
        val ownerId = owner?.get("id")?.takeUnless { it.isJsonNull }?.asString
        val ownerNumber = owner?.get("number")?.takeUnless { it.isJsonNull }?.asString
        // The official response can contain the authenticated owner and an empty users list.
        // users describes available service identities; it is not the primary owner record.
        if (!ownerNumber.isNullOrBlank()) {
            return ownerNumber.takeIf { it == entered && !ownerId.isNullOrBlank() }
        }
        val users = data.get("users")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { it.takeIf { entry -> entry.isJsonObject }?.asJsonObject }.orEmpty()
        val matching = users.filter { it.get("number")?.asString == entered &&
            (ownerId != null && it.get("id")?.asString == ownerId || ownerId == null && users.size == 1) }
        matching.singleOrNull()?.get("number")?.asString
    }.getOrNull()
    fun safeFailure(response: JsonObject): String {
        val msg = runCatching { response.get("msg")?.asString.orEmpty() }.getOrDefault("")
        return when {
            msg.contains("验证码") -> "学校未接受验证码，请刷新验证码后重试"
            msg.contains("密码") || msg.contains("账号") || msg.contains("用户") -> "学校未接受账号信息，请核对学号和学校密码"
            else -> "学校未完成认证。可能需要激活账号或其他验证；不会将此情况一律当作密码错误"
        }
    }
}
