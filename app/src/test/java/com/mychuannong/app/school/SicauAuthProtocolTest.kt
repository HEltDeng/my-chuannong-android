package com.mychuannong.app.school

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Synthetic fixtures only. These tests make no network requests and do not prove school login. */
class SicauAuthProtocolTest {
    private fun json(s:String)=JsonParser.parseString(s).asJsonObject
    @Test fun normalHttpOrCookieIsNotIdentity() {
        for(raw in listOf("{}", """{"code":200,"data":{}}""", """{"code":200,"data":{"cookie":"test"}}""")) {
            assertFalse(SicauAuthProtocol.accepted(json(raw)))
            assertNull(SicauAuthProtocol.confirmedNumber(json(raw),"SYNTHETIC"))
        }
    }
    @Test fun acceptedSessionAndConfirmedOwnerAreSeparate() {
        val response=json("""{"code":200,"data":{"st":"synthetic-ticket","user":{"id":"owner"},"users":[{"id":"owner","number":"SYNTHETIC"},{"id":"delegate","number":"OTHER"}]}}""")
        assertTrue(SicauAuthProtocol.accepted(response))
        assertEquals("SYNTHETIC",SicauAuthProtocol.confirmedNumber(response,"SYNTHETIC"))
        assertNull(SicauAuthProtocol.confirmedNumber(response,"OTHER"))
        assertNull(SicauAuthProtocol.confirmedNumber(response,"UNKNOWN"))
        response.getAsJsonObject("data").remove("user")
        assertNull(SicauAuthProtocol.confirmedNumber(response,"SYNTHETIC"))
    }
    @Test fun loginPayloadFollowsObservedSchoolSchema() {
        val body=SicauAuthProtocol.loginBody("SYNTHETIC","test-only","abcd","fixture")
        assertEquals(setOf("appid","number","password","code","uuid"),body.keySet())
        assertEquals("sicau-cas",body.get("appid").asString)
        assertEquals("SYNTHETIC",body.get("number").asString)
    }
    @Test fun authenticatedOwnerDoesNotRequireAServiceIdentityList() {
        for (suffix in listOf(",\"users\":[]", ",\"users\":null", "")) {
            val response=json("""{"code":200,"data":{"st":"synthetic-ticket","user":{"id":123,"number":"SYNTHETIC"}$suffix}}""")
            assertEquals("SYNTHETIC",SicauAuthProtocol.confirmedNumber(response,"SYNTHETIC"))
            assertNull(SicauAuthProtocol.confirmedNumber(response,"OTHER"))
        }
    }
    @Test fun explicitOwnerMismatchCannotFallBackToADelegatedIdentity() {
        val response=json("""{"code":200,"data":{"st":"synthetic-ticket","user":{"id":123,"number":"OWNER"},"users":[{"id":123,"number":"SYNTHETIC"}]}}""")
        assertNull(SicauAuthProtocol.confirmedNumber(response,"SYNTHETIC"))
    }
    @Test fun ownerWithoutServerIdentityIsNotConfirmed() {
        val response=json("""{"code":200,"data":{"st":"synthetic-ticket","user":{"number":"SYNTHETIC"},"users":[]}}""")
        assertNull(SicauAuthProtocol.confirmedNumber(response,"SYNTHETIC"))
    }
    @Test fun decodesSchoolEnvelopeWithSyntheticEncryptedData() {
        val requestId="synthetic-request"
        val key=MessageDigest.getInstance("MD5").digest("$requestId:sicau-cas".toByteArray())
        val iv=ByteArray(16) { it.toByte() }
        val cipher=Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),IvParameterSpec(iv))
        val encrypted=iv+cipher.doFinal("\"data:image/png;base64,synthetic\"".toByteArray())
        val envelope=JsonObject().apply { addProperty("code",200);addProperty("requestId",requestId);addProperty("id","fixture");addProperty("data",Base64.getEncoder().encodeToString(encrypted)) }
        val result=SicauAuthProtocol.decode(envelope.toString())
        assertEquals("data:image/png;base64,synthetic",result.get("data").asString)
        assertEquals("fixture",result.get("id").asString)
    }
    @Test fun errorsNeverEchoSchoolResponseOrMislabelNetwork() {
        assertTrue(SicauAuthProtocol.safeFailure(json("""{"msg":"验证码错误 secret-detail"}""")).contains("验证码"))
        val generic=SicauAuthProtocol.safeFailure(json("""{"msg":"private-response-body"}"""))
        assertFalse(generic.contains("private-response-body"))
        assertFalse(generic=="密码错误")
    }
}
