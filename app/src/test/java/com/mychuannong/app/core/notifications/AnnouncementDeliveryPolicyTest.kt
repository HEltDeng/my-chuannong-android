package com.mychuannong.app.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnnouncementDeliveryPolicyTest {
    private fun candidate(
        id: String,
        publishedAt: Long,
        notification: Boolean = true,
        popup: Boolean = true,
    ) = AnnouncementDeliveryCandidate(
        id = id,
        title = id,
        content = id,
        publishedAt = publishedAt,
        notificationEnabled = notification,
        popupEnabled = popup,
    )

    @Test
    fun selects_only_the_latest_new_important_announcement() {
        val selected = latestUndeliveredImportantAnnouncement(
            announcements = listOf(
                candidate("old", 100L),
                candidate("latest", 300L),
                candidate("middle", 200L),
            ),
            knownIds = emptySet(),
        )
        assertEquals("latest", selected?.id)
    }

    @Test
    fun known_id_is_never_selected_again_after_editing() {
        val selected = latestUndeliveredImportantAnnouncement(
            announcements = listOf(candidate("edited", 500L)),
            knownIds = setOf("edited"),
        )
        assertNull(selected)
    }

    @Test
    fun ordinary_announcements_do_not_create_delivery_effects() {
        val selected = latestUndeliveredImportantAnnouncement(
            announcements = listOf(candidate("ordinary", 100L, notification = false, popup = false)),
            knownIds = emptySet(),
        )
        assertNull(selected)
        assertTrue(selected == null)
    }
}
