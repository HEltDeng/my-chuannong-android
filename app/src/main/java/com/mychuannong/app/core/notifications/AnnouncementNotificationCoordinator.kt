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
import com.mychuannong.app.core.network.campustools.AccountSessionState
import com.mychuannong.app.core.network.campustools.CampusToolsSession
import com.mychuannong.app.core.network.campustools.NotificationFollowRequest
import com.mychuannong.app.core.network.campustools.NotificationFollowResult
import com.mychuannong.app.core.network.campustools.authenticatedCall
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
    val module: String,
)

/** Authenticated, opt-in notices synced on launch/resume. OEM push is a separate delivery channel. */
object AnnouncementNotificationCoordinator {
    private const val CHANNEL_ID = "announcement_updates_v1"
    private const val STORE_ID = "announcement_delivery_state_v1"
    private const val KEY_KNOWN_IDS = "known_ids"
    private const val KEY_NOTIFICATION_ATTEMPTED_IDS = "notification_attempted_ids"
    private const val KEY_POPUP_SHOWN_IDS = "popup_shown_ids"
    private val gson = Gson()
    private val processMutex = Mutex()
    private val mutablePopup = MutableStateFlow<AnnouncementPopup?>(null)
    val popup: StateFlow<AnnouncementPopup?> = mutablePopup.asStateFlow()

    private fun activeAccountId(): String? =
        (CampusToolsSession.state.value as? AccountSessionState.SignedIn)?.user?.id

    suspend fun refresh(context: Context) = processMutex.withLock {
        val accountId = activeAccountId() ?: run { clearPopup(); return@withLock }
        val feed = authenticatedCall { it.notificationFeed() }
        if (!isCurrentNotificationAudience(accountId, feed.accountId, activeAccountId())) return@withLock
        // Never fall back to public bootstrap, cached flags, or another account's delivery state.
        val normalized = feed.items.filter { it.id.isNotBlank() && it.module.isNotBlank() }
            .distinctBy { it.id }
        val storage = accountStorage(accountId)
        val knownIds = readIds(storage, KEY_KNOWN_IDS)
        val selected = latestUndeliveredImportantAnnouncement(
            normalized.map {
                AnnouncementDeliveryCandidate(it.id, it.title, it.content, it.publishedAt,
                    it.notificationEnabled, it.popupEnabled)
            }, knownIds,
        ) ?: return@withLock
        if (activeAccountId() != accountId) return@withLock
        // Record before side effects to preserve at-most-once delivery after a process restart.
        writeIds(storage, KEY_KNOWN_IDS, knownIds + normalized.map { it.id })
        val module = normalized.first { it.id == selected.id }.module
        deliver(context.applicationContext, accountId, module, storage, selected)
    }

    // Serialize changes with refresh so an in-flight feed cannot deliver after a successful unfollow.
    suspend fun setFollowing(
        context: Context, accountId: String, module: String, following: Boolean,
    ): NotificationFollowResult = processMutex.withLock {
        check(activeAccountId() == accountId) { "登录账号已变化，请重新打开通知设置" }
        val result = authenticatedCall { it.setNotificationFollow(module, NotificationFollowRequest(following)) }
        check(isCurrentNotificationAudience(accountId, result.accountId, activeAccountId())) {
            "登录账号已变化，请重新打开通知设置"
        }
        if (!result.following) {
            if (mutablePopup.value?.module == module) clearPopup()
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.activeNotifications.filter { it.tag == notificationTag(accountId, module) }
                .forEach { manager.cancel(it.tag, it.id) }
        }
        result
    }

    fun clearPopup() { mutablePopup.value = null }

    fun dismissPopup(id: String) {
        if (mutablePopup.value?.id == id) clearPopup()
    }

    private fun deliver(
        context: Context, accountId: String, module: String, storage: MMKV,
        announcement: AnnouncementDeliveryCandidate,
    ) {
        if (activeAccountId() != accountId) return
        if (announcement.notificationEnabled) {
            val notified = readIds(storage, KEY_NOTIFICATION_ATTEMPTED_IDS)
            if (announcement.id !in notified) {
                writeIds(storage, KEY_NOTIFICATION_ATTEMPTED_IDS, notified + announcement.id)
                postNotification(context, accountId, module, announcement)
            }
        }
        if (announcement.popupEnabled && activeAccountId() == accountId) {
            val shown = readIds(storage, KEY_POPUP_SHOWN_IDS)
            if (announcement.id !in shown) {
                writeIds(storage, KEY_POPUP_SHOWN_IDS, shown + announcement.id)
                mutablePopup.value = AnnouncementPopup(
                    id = announcement.id, title = announcement.title, content = announcement.content,
                    publishedAt = announcement.publishedAt, module = module,
                )
            }
        }
    }

    private fun postNotification(
        context: Context,
        accountId: String,
        module: String,
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
            stableNotificationId(accountId + announcement.id),
            detailIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = announcement.content.replace(Regex("\\s+"), " ").trim()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.notification2)
            .setContentTitle(announcement.title.ifBlank { "关注板块动态" })
            .setContentText(body.ifBlank { "打开查看公告详情" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(
                notificationTag(accountId, module),
                stableNotificationId(accountId + announcement.id),
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
                    "关注板块动态",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "你主动关注的板块的新动态"
                },
            )
        }
    }

    private fun stableNotificationId(id: String): Int =
        (id.hashCode() and Int.MAX_VALUE).coerceAtLeast(1)

    private fun notificationTag(accountId: String, module: String) = "follow:$accountId:$module"

    private fun accountStorage(accountId: String) = MMKV.mmkvWithID("${STORE_ID}_$accountId")

    private fun readIds(storage: MMKV, key: String): Set<String> = runCatching {
        val encoded = storage.decodeString(key).orEmpty()
        if (encoded.isBlank()) emptySet()
        else gson.fromJson(encoded, Array<String>::class.java).toSet()
    }.getOrDefault(emptySet())

    private fun writeIds(storage: MMKV, key: String, ids: Set<String>) {
        storage.encode(key, gson.toJson(ids.filter(String::isNotBlank).sorted()))
    }
}
