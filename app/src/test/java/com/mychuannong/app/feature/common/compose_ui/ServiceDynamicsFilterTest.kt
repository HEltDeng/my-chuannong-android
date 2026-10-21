package com.mychuannong.app.feature.common.compose_ui

import com.mychuannong.app.core.network.campustools.RemoteServiceUpdate
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceDynamicsFilterTest {
    private fun update(
        id: String,
        type: String,
        versionCode: Int? = null,
    ) = RemoteServiceUpdate(
        id = id,
        type = type,
        title = id,
        versionCode = versionCode,
    )

    @Test
    fun updateOlderOrEqualThanClientIsFilteredOut() {
        val updates = listOf(
            update("u-old", "UPDATE", versionCode = 9),
            update("u-same", "UPDATE", versionCode = 10),
        )
        assertEquals(emptyList<RemoteServiceUpdate>(), filterServiceUpdatesForClient(updates, 10))
    }

    @Test
    fun updateNewerThanClientIsKept() {
        val updates = listOf(
            update("u-new", "UPDATE", versionCode = 11),
            update("u-old", "UPDATE", versionCode = 8),
        )
        val filtered = filterServiceUpdatesForClient(updates, 10)
        assertEquals(listOf("u-new"), filtered.map { it.id })
    }

    @Test
    fun maintenanceClosedAndAnnouncementAreAlwaysKept() {
        val updates = listOf(
            update("m", "MAINTENANCE"),
            update("c", "CLOSED"),
            update("a", "ANNOUNCEMENT"),
        )
        val filtered = filterServiceUpdatesForClient(updates, 999)
        assertEquals(listOf("m", "c", "a"), filtered.map { it.id })
    }

    @Test
    fun virtualPagerPageMapsBackToRealIndexWithModulo() {
        assertEquals(0, virtualPageToRealIndex(0, 3))
        assertEquals(1, virtualPageToRealIndex(10_000 * 3 + 1, 3))
        assertEquals(2, virtualPageToRealIndex(10_000 * 3 + 2, 3))
        assertEquals(0, virtualPageToRealIndex(10_000 * 3 + 3, 3))
        assertEquals(2, virtualPageToRealIndex(-1, 3))
        assertEquals(0, virtualPageToRealIndex(0, 1))
        assertEquals(0, virtualPageToRealIndex(999, 0))
    }
}
