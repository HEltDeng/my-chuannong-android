package com.mychuannong.app.core.network

import com.mychuannong.app.core.network.campustools.RemoteFeatureFlag
import com.mychuannong.app.core.network.campustools.RemoteFeaturePayload
import com.mychuannong.app.core.network.campustools.resolveFeatureAvailability
import com.mychuannong.app.core.network.campustools.serviceFeatureKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureAvailabilityTest {
    @Test
    fun `coming soon blocks entry and shows planned launch metadata`() {
        val value = resolveFeatureAvailability(RemoteFeatureFlag(
            key = "ielts_toefl", enabled = true,
            payload = RemoteFeaturePayload(status = "COMING_SOON", maintenanceNote = "合成上线提示", expectedRestoreAt = 1900000000000L)))
        assertFalse(value.isOpen)
        assertEquals("即将上线", value.label)
        assertEquals("预计上线", value.timeLabel)
        assertEquals("合成上线提示", value.message)
        assertEquals(1900000000000L, value.expectedRestoreAt)
        val actions = listOf("document", "lost_found", "secondhand", "errand", "electric", "logistics_repair", "driving_school", "ielts_toefl", "civil_postgrad", "gym")
        assertEquals(10, actions.mapNotNull(::serviceFeatureKey).toSet().size)
        assertEquals(null, serviceFeatureKey("schedule"))
    }

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
