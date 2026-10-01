package com.mychuannong.app.core.network

import com.mychuannong.app.core.network.campustools.RemoteFeatureFlag
import com.mychuannong.app.core.network.campustools.RemoteFeaturePayload
import com.mychuannong.app.core.network.campustools.resolveFeatureAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureAvailabilityTest {
    @Test
    fun `open maintenance and closed states resolve consistently`() {
        assertTrue(resolveFeatureAvailability(null).isOpen)
        val maintenance = resolveFeatureAvailability(
            RemoteFeatureFlag(
                key = "sports_reservation",
                enabled = true,
                payload = RemoteFeaturePayload(
                    status = "MAINTENANCE",
                    maintenanceNote = "maintenance",
                    expectedRestoreAt = 42L,
                ),
            ),
        )
        assertFalse(maintenance.isOpen)
        assertEquals("MAINTENANCE", maintenance.status)
        assertEquals(42L, maintenance.expectedRestoreAt)

        val closed = resolveFeatureAvailability(
            RemoteFeatureFlag(
                key = "resource_library",
                enabled = false,
                payload = RemoteFeaturePayload(status = "OPEN"),
            ),
        )
        assertEquals("CLOSED", closed.status)
    }

    @Test
    fun `invalid remote status safely falls back to open`() {
        val availability = resolveFeatureAvailability(
            RemoteFeatureFlag(
                key = "lost_found",
                enabled = true,
                payload = RemoteFeaturePayload(status = "UNKNOWN"),
            ),
        )
        assertTrue(availability.isOpen)
    }
}
