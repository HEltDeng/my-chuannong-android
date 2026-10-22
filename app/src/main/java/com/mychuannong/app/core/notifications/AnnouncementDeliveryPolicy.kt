package com.mychuannong.app.core.notifications

/**
 * Pure selection rules for announcement delivery. The persistent coordinator
 * owns the side effects; keeping selection here makes the one-time behavior
 * independently testable.
 */
internal data class AnnouncementDeliveryCandidate(
    val id: String,
    val title: String,
    val content: String,
    val publishedAt: Long,
    val notificationEnabled: Boolean,
    val popupEnabled: Boolean,
)

internal fun latestUndeliveredImportantAnnouncement(
    announcements: List<AnnouncementDeliveryCandidate>,
    knownIds: Set<String>,
): AnnouncementDeliveryCandidate? = announcements
    .asSequence()
    .filter { it.id.isNotBlank() && it.id !in knownIds }
    .filter { it.notificationEnabled || it.popupEnabled }
    .sortedByDescending { it.publishedAt }
    .firstOrNull()

/** A response started before logout/account switching must not show a notice in the next session. */
internal fun isCurrentNotificationAudience(requested: String, response: String?, current: String?): Boolean =
    requested.isNotBlank() && requested == response && requested == current
