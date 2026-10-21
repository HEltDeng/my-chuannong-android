package com.mychuannong.app.feature.schedule.reminder

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mychuannong.app.core.PlanetApplication
import org.json.JSONObject

/** One persisted snooze per original occurrence. No new course or notification identity. */
object ReminderSnooze {
    const val ACTION_SELECT = "com.mychuannong.app.reminder.SNOOZE"
    const val ACTION_DELIVER = "com.mychuannong.app.reminder.SNOOZE_DELIVER"
    private val prefs get() = PlanetApplication.appContext.getSharedPreferences("reminder_snooze", 0)
    private fun code(intent: Intent): Int {
        val start = intent.getLongExtra(CourseReminderScheduler.EXTRA_START_MILLIS, 0)
        return (31 * intent.getIntExtra(CourseReminderScheduler.EXTRA_COURSE_ID, 0) + (start xor (start ushr 32)).toInt()).and(0x7fffffff)
    }
    fun action(context: Context, original: Intent, minutes: Int): PendingIntent = PendingIntent.getBroadcast(
        context, code(original), Intent(original).setAction(ACTION_SELECT).setData(android.net.Uri.parse("mychuannong-reminder://snooze/$minutes/${code(original)}"))
            .putExtra("snooze_minutes", minutes), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    @Synchronized fun schedule(context: Context, original: Intent) {
        val minutes = original.getIntExtra("snooze_minutes", 0)
        if (minutes !in listOf(5, 10) || !CourseReminderPreferences.enabled || !CourseReminderPreferences.snoozeEnabled) return
        if (prefs.contains(code(original).toString())) return // Duplicate action delivery keeps the existing deadline.
        val at = System.currentTimeMillis() + minutes * 60000L
        if (at >= original.getLongExtra(CourseReminderScheduler.EXTRA_END_MILLIS, 0)) return
        val delivery = Intent(original).setAction(ACTION_DELIVER).setData(null)
        val record = JSONObject().put("at", at).put("intent", delivery.toUri(0))
        prefs.edit().putString(code(original).toString(), record.toString()).commit()
        arm(context, delivery, at)
        context.getSystemService(NotificationManager::class.java).cancel(code(original))
    }
    private fun arm(context: Context, intent: Intent, at: Long) {
        val pending = PendingIntent.getBroadcast(context, code(intent), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val alarm = context.getSystemService(AlarmManager::class.java)
        try {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        } catch (_: SecurityException) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }
    @Synchronized fun remove(intent: Intent) { prefs.edit().remove(code(intent).toString()).commit() }
    @Synchronized fun restore(context: Context) {
        prefs.all.forEach { (key, value) ->
            runCatching {
                val record = JSONObject(value as String)
                val intent = Intent.parseUri(record.getString("intent"), 0).setClass(context, CourseReminderReceiver::class.java)
                if (!CourseReminderPreferences.enabled || !CourseReminderPreferences.snoozeEnabled ||
                    System.currentTimeMillis() >= intent.getLongExtra(CourseReminderScheduler.EXTRA_END_MILLIS, 0)) {
                    val pending = PendingIntent.getBroadcast(context, code(intent), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
                    pending?.let { context.getSystemService(AlarmManager::class.java).cancel(it); it.cancel() }
                    prefs.edit().remove(key).commit()
                } else arm(context, intent, record.getLong("at").coerceAtLeast(System.currentTimeMillis() + 1000))
            }.onFailure { prefs.edit().remove(key).commit() }
        }
    }
}
