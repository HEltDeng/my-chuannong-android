package com.mychuannong.app.core.analytics

import org.junit.Assert.*
import org.junit.Test

class ForegroundActivityPolicyTest {
    @Test fun neverReportsWithoutConsentForegroundAndAccount() {
        val policy = ForegroundActivityPolicy()
        assertFalse(policy.shouldSend("a", "2026-10-22", 0, false, true))
        assertFalse(policy.shouldSend("a", "2026-10-22", 0, true, false))
        assertFalse(policy.shouldSend(null, "2026-10-22", 0, true, true))
    }
    @Test fun activityRecreationAndQuickReturnsDoNotMultiplyRequests() {
        val policy = ForegroundActivityPolicy()
        assertTrue(policy.shouldSend("a", "2026-10-22", 10, true, true))
        policy.accepted("a", "2026-10-22", 10)
        assertFalse(policy.shouldSend("a", "2026-10-22", 100, true, true))
        assertFalse(policy.shouldSend("a", "2026-10-22", 300_009, true, true))
        assertTrue(policy.shouldSend("a", "2026-10-22", 300_010, true, true))
    }
    @Test fun accountSwitchAndMidnightAreNotSuppressed() {
        val policy = ForegroundActivityPolicy()
        policy.accepted("a", "2026-10-22", 100)
        assertTrue(policy.shouldSend("b", "2026-10-22", 101, true, true))
        assertTrue(policy.shouldSend("a", "2026-10-23", 101, true, true))
    }
    @Test fun failedSendDoesNotConsumeTheNextOpportunity() {
        val policy = ForegroundActivityPolicy()
        assertTrue(policy.shouldSend("a", "2026-10-22", 100, true, true))
        assertTrue(policy.shouldSend("a", "2026-10-22", 60_100, true, true))
    }
}
