package com.mychuannong.app.core.network.campustools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdentityTest {
    @Test
    fun derivesStableAppScopedOpaqueIdentifier() {
        val first = DeviceIdentity.deriveDeviceScopedId("  ABCDEF1234567890  ")
        val second = DeviceIdentity.deriveDeviceScopedId("abcdef1234567890")

        assertEquals(first, second)
        assertTrue(requireNotNull(first).matches(Regex("[0-9a-f]{64}")))
        assertNotEquals("abcdef1234567890", first)
    }

    @Test
    fun rejectsMissingAndKnownInvalidAndroidIdsWithoutInstallationFallback() {
        assertNull(DeviceIdentity.deriveDeviceScopedId(null))
        assertNull(DeviceIdentity.deriveDeviceScopedId(""))
        assertNull(DeviceIdentity.deriveDeviceScopedId("9774d56d682e549c"))
    }
}
