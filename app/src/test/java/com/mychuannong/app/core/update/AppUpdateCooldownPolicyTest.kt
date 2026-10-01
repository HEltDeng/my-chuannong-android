package com.mychuannong.app.core.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCooldownPolicyTest {
    private val cooldownMillis = 24L * 60L * 60L * 1000L

    @Test
    fun firstPromptForVersionIsAlwaysAllowed() {
        val policy = UpdatePromptCooldownPolicy(cooldownMillis, nowMillis = 1_000L)
        assertTrue(policy.shouldPrompt(-1, 0L, candidateVersionCode = 42))
    }

    @Test
    fun dismissedVersionIsSuppressedInsideCooldown() {
        val dismissedAt = 1_000L
        val now = dismissedAt + cooldownMillis - 1L
        val policy = UpdatePromptCooldownPolicy(cooldownMillis, nowMillis = now)
        assertFalse(policy.shouldPrompt(42, dismissedAt, candidateVersionCode = 42))
    }

    @Test
    fun sameVersionIsAllowedAgainAfterCooldownExpires() {
        val dismissedAt = 1_000L
        val now = dismissedAt + cooldownMillis
        val policy = UpdatePromptCooldownPolicy(cooldownMillis, nowMillis = now)
        assertTrue(policy.shouldPrompt(42, dismissedAt, candidateVersionCode = 42))
    }

    @Test
    fun newerVersionImmediatelyResetsOldCooldown() {
        val dismissedAt = 1_000L
        val now = dismissedAt + 1L
        val policy = UpdatePromptCooldownPolicy(cooldownMillis, nowMillis = now)
        assertTrue(policy.shouldPrompt(42, dismissedAt, candidateVersionCode = 43))
    }
}
