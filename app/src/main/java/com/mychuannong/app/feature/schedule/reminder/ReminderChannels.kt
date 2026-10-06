package com.mychuannong.app.feature.schedule.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object ReminderChannels {
    const val NORMAL = "course_reminders_v1"
    const val SILENT = "course_reminders_silent_v1"
    fun currentId() = if (CourseReminderPreferences.silent) SILENT else NORMAL
    fun enabled(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26) return true
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = manager.getNotificationChannel(currentId()) ?: return false
        if (channel.importance == NotificationManager.IMPORTANCE_NONE) return false
        return Build.VERSION.SDK_INT < 28 || channel.group == null || manager.getNotificationChannelGroup(channel.group)?.isBlocked != true
    }
    fun ensure(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(NORMAL, "上课提醒", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel(SILENT, "静默上课提醒", NotificationManager.IMPORTANCE_LOW).apply {
            setSound(null, null)
            enableVibration(false)
        })
    }
}
