package com.mychuannong.app.core.account

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.mychuannong.app.core.network.campustools.*
import org.junit.Assert.*
import org.junit.Test

class PhoneAuthContractTest {
    @Test fun phoneAndIdentityWireNamesSurviveReleaseObfuscation() {
        listOf(PhoneAuthCapabilities::class.java, SmsChallenge::class.java, SmsCodeRequest::class.java,
            ResetPasswordRequest::class.java, VerifyPhoneRequest::class.java, PasswordResetResult::class.java,
            StudentIdentityRequest::class.java, StudentIdentityResult::class.java).forEach { type ->
            type.declaredFields.filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }.forEach {
                assertEquals(it.name, it.getAnnotation(com.google.gson.annotations.SerializedName::class.java)?.value)
            }
        }
        val body = Gson().toJsonTree(StudentIdentityRequest("20260001", "测试学生", "SICAU", "2026-10-23.2", 1792713600000)).asJsonObject
        assertEquals(setOf("studentId", "realName", "schoolCode", "policyVersion", "consentAcceptedAt"), body.keySet())
    }
    @Test fun proofIsSixAsciiDigitsAndConfirmationMustMatch() {
        assertTrue(AccountInputRules.isValidCode("012345"))
        assertFalse(AccountInputRules.isValidCode("１２３４５６"))
        assertFalse(AccountInputRules.isValidCode("12345"))
        assertTrue(AccountInputRules.isConfirmedPassword("abc12345", "abc12345"))
        assertFalse(AccountInputRules.isConfirmedPassword("abc12345", "abc12346"))
        assertFalse(AccountInputRules.isConfirmedPassword("        ", "        "))
    }
    @Test fun smsRequestHasNoPasswordSchoolOrDeviceFields() {
        val request = SmsCodeRequest("13900000000", "verify_phone", "2026-10-23.2", 1792713600000)
        val json = JsonParser.parseString(Gson().toJson(request)).asJsonObject
        assertEquals(setOf("phone","purpose","policyVersion","consentAcceptedAt"), json.keySet())
        val reset = ResetPasswordRequest("13900000000","synthetic-pass","synthetic-pass","synthetic-proof","012345","2026-10-23.2",1792713600000)
        val body = JsonParser.parseString(Gson().toJson(reset)).asJsonObject
        assertEquals("synthetic-proof",body["challengeId"].asString)
        assertEquals("synthetic-pass",body["confirmPassword"].asString)
    }
    @Test fun availabilityAndChallengeContractDecode() {
        val caps = Gson().fromJson("""{"availablePurposes":[],"resetLimit":2,"policyVersion":"2026-10-23.2"}""",PhoneAuthCapabilities::class.java)
        assertTrue(caps.availablePurposes.isEmpty())
        val code = Gson().fromJson("""{"challengeId":"synthetic","expiresIn":300,"retryAfter":60}""",SmsChallenge::class.java)
        assertEquals(300,code.expiresIn)
        assertEquals(60,code.retryAfter)
    }
}
