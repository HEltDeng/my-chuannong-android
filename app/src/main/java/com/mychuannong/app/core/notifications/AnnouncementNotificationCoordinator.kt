package com.mychuannong.app.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.mychuannong.app.R
import com.mychuannong.app.core.network.campustools.RemoteAnnouncement
import com.mychuannong.app.feature.common.ui.AnnouncementDetailActivity
import com.tencent.mmkv.MMKV
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class AnnouncementPopup(
    val id: String,
    val title: String,
    val content: String,
    val publishedAt: Long,
)

/**
 * Converts public bootstrap announcements into at-most-once local effects.
 * There is deliberately no timer or foreground service here: V1.0.5 has
 * client sync on launch/resume, not a fake server push channel.
 */
object AnnouncementNotificationCoordinator {
    private const val CHANNEL_ID = "announcement_updates_v1"
    private const val STORE_ID = "announcement_delivery_state_v1"
    private const val KEY_BASELINE_INITIALIZED = "baseline_initialized"
    private const val KEY_KNOWN_IDS = "known_ids"
    private const val KEY_NOTIFICATION_ATTEMPTED_IDS = "notification_attempted_ids"
    private const val KEY_POPUP_SHOWN_IDS = "popup_shown_ids"
    private val gson = Gson()
    private val storage by lazy { MMKV.mmkvWithID(STORE_ID) }
    private val processMutex = Mutex()
    private val mutablePopup = MutableStateFlow<AnnouncementPopup?>(null)
    val popup: StateFlow<AnnouncementPopup?> = mutablePopup.asStateFlow()

    suspend fun process(context: Context, announcements: List<RemoteAnnouncement>) {
        val normalized = announcements
            .filter { it.id.isNotBlank() }
            .map {
                AnnouncementDeliveryCandidate(
                    id = it.id,
                    title = it.title,
                    content = it.content,
                    publishedAt = it.publishedAt,
                    notificationEnabled = it.notificationEnabled,
                    popupEnabled = it.popupEnabled,
                )
            }
            .distinctBy { it.id }
            .sortedByDescending { it.publishedAt }
        if (normalized.isEmpty()) return

        processMutex.withLock {
            val knownIds = readIds(KEY_KNOWN_IDS)
            val baselineInitialized = storage.decodeBool(KEY_BASELINE_INITIALIZED, false)
            if (!baselineInitialized) {
                // A fresh install may receive a long history. Establish the
                // baseline first, then surface only the latest important item.
                writeIds(KEY_KNOWN_IDS, knownIds + normalized.map { it.id })
                storage.encode(KEY_BASELINE_INITIALIZED, true)
                latestUndeliveredImportantAnnouncement(normalized, knownIds)
                    ?.let { deliver(context.applicationContext, it) }
                return@withLock
            }

            val fresh = normalized.filterNot { it.id in knownIds }
            if (fresh.isEmpty()) return@withLock
            // Mark the IDs before side effects. This preserves at-most-once
            // behavior even if Android kills the process during delivery.
            writeIds(KEY_KNOWN_IDS, knownIds + fresh.map { it.id })
            latestUndeliveredImportantAnnouncement(fresh, knownIds)
                ?.let { deliver(context.applicationContext, it) }
        }
    }

    fun dismissPopup(id: String) {
        if (mutablePopup.value?.id == id) mutablePopup.value = null
    }

    private fun deliver(context: Context, announcement: AnnouncementDeliveryCandidate) {
        if (announcement.notificationEnabled) {
            val notified = readIds(KEY_NOTIFICATION_ATTEMPTED_IDS)
            if (announcement.id !in notified) {
                writeIds(KEY_NOTIFICATION_ATTEMPTED_IDS, notified + announcement.id)
                postNotification(context, announcement)
            }
        }
        if (announcement.popupEnabled) {
            val shown = readIds(KEY_POPUP_SHOWN_IDS)
            if (announcement.id !in shown) {
                writeIds(KEY_POPUP_SHOWN_IDS, shown + announcement.id)
                mutablePopup.value = AnnouncementPopup(
                    id = announcement.id,
                    title = announcement.title,
                    content = announcement.content,
                    publishedAt = announcement.publishedAt,
                )
            }
        }
    }

    private fun postNotification(
        context: Context,
        announcement: AnnouncementDeliveryCandidate,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel(context)
        val detailIntent = AnnouncementDetailActivity.intent(
            context = context,
            title = announcement.title,
            content = announcement.content,
            publishedAt = announcement.publishedAt,
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            stableNotificationId(announcement.id),
            detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = announcement.content.replace(Regex("\\s+"), " ").trim()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification2)
            .setContentTitle(announcement.title.ifBlank { "重要公告" })
            .setContentText(body.ifBlank { "打开查看公告详情" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(
                stableNotificationId(announcement.id),
                notification,
            )
        }
    }

    private fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "重要公告",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "我的川农重要公告通知"
                },
            )
        }
    }

    private fun stableNotificationId(id: String): Int =
        (id.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

    private fun readIds(key: String): Set<String> = runCatching {
        val encoded = storage.decodeString(key).orEmpty()
        if (encoded.isBlank()) emptySet()
        else gson.fromJson(encoded, Array<String>::class.java).toSet()
    }.getOrDefault(emptySet())

    private fun writeIds(key: String, ids: Set<String>) {
        storage.encode(key, gson.toJson(ids.filter(String::isNotBlank).sorted()))
    }
}
