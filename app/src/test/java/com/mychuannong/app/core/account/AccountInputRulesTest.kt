package com.mychuannong.app.core.account

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountInputRulesTest {
    @Test
    fun phoneMustMatchSupportedMobileShape() {
        assertTrue(AccountInputRules.isValidPhone("18812345678"))
        assertFalse(AccountInputRules.isValidPhone("12812345678"))
        assertFalse(AccountInputRules.isValidPhone("1881234567"))
    }

    @Test
    fun registrationRequiresNicknameButLoginDoesNot() {
        assertFalse(
            AccountInputRules.canSubmit("18812345678", "safe-pass", "", registering = true)
        )
        assertTrue(
            AccountInputRules.canSubmit("18812345678", "safe-pass", "", registering = false)
        )
    }

    @Test
    fun limitedAccountMessageUsesRoundedUpRemainingDays() {
        val now = 1_000_000L
        assertEquals(
            "你的账号已被限制访问，请在 7 天后恢复使用",
            accountRestrictionMessage(
                accountStatus = "LIMITED",
                expiresAtMillis = now + 6L * 24L * 60L * 60L * 1000L + 1L,
                nowMillis = now,
            ),
        )
        assertEquals(
            "你的账号已被限制访问，请联系管理员恢复使用",
            accountRestrictionMessage("LIMITED", null, now),
        )
        assertNull(accountRestrictionMessage("ACTIVE", now, now))
    }
}
