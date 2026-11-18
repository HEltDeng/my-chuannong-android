package com.mychuannong.app.core.account

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.mychuannong.app.core.network.campustools.*
import org.junit.Assert.*
import org.junit.Test

class PhoneAuthContractTest {
    @Test fun passwordRegistrationSendsConfirmationAndConsentWithoutSmsProof() {
        val body = Gson().toJsonTree(RegisterRequest(
            phone = "13900000000", password = "synthetic-pass", nickname = "合成学生",
            installationId = "synthetic-installation", confirmPassword = "synthetic-pass",
            policyVersion = "2026-11-17.1", consentAcceptedAt = 1794873600000,
        )).asJsonObject
        assertEquals(setOf("phone", "password", "nickname", "installationId", "confirmPassword",
            "registrationMethod", "policyVersion", "consentAcceptedAt"), body.keySet())
        assertEquals("password", body["registrationMethod"].asString)
        assertEquals("synthetic-pass", body["confirmPassword"].asString)
        assertFalse(body.has("code"))
        assertFalse(body.has("challengeId"))
        assertFalse(body.has("phoneVerified"))
        RegisterRequest::class.java.declaredFields
            .filterNot { it.isSynthetic || java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .forEach { assertEquals(it.name, it.getAnnotation(SerializedName::class.java)?.value) }
    }

    @Test fun registrationRequiresMatchingNonblankPasswordAndNickname() {
        assertTrue(AccountInputRules.canSubmit("13900000000", "synthetic-pass", "合成学生", true, "synthetic-pass"))
        assertFalse(AccountInputRules.canSubmit("13900000000", "synthetic-pass", "合成学生", true, "different-pass"))
        assertFalse(AccountInputRules.canSubmit("13900000000", "synthetic-pass", "合成学生", true, ""))
        assertFalse(AccountInputRules.canSubmit("13900000000", "        ", "合成学生", true, "        "))
        assertFalse(AccountInputRules.canSubmit("13900000000", "synthetic-pass", " ", true, "synthetic-pass"))
        assertFalse(AccountInputRules.canSubmit("12800000000", "synthetic-pass", "合成学生", true, "synthetic-pass"))
        assertTrue(AccountInputRules.canSubmit("13900000000", "synthetic-pass", "", false))
    }

    @Test fun passwordAccountDoesNotGainPhoneOrStudentVerification() {
        val user = Gson().fromJson("""{"id":"synthetic-user","role":"USER","accountType":"PHONE",
            "nickname":"合成学生","phoneMasked":"139****0000","phoneVerified":false,"identityStatus":"UNBOUND"}""", AuthUser::class.java)
        assertEquals("PHONE", user.accountType)
        assertFalse(user.phoneVerified)
        assertEquals("UNBOUND", user.identityStatus)
        assertNull(user.studentId)
    }
}
