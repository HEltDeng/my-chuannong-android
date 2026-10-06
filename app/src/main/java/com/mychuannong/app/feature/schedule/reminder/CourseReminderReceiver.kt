package com.mychuannong.app.feature.schedule.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.mychuannong.app.feature.schedule.CourseScheduleEngine
import com.mychuannong.app.feature.schedule.CourseScheduleRepository
import com.mychuannong.app.feature.schedule.CourseTimePreferences
import com.mychuannong.app.feature.timetable.ui.TimeTableActivity
import com.mychuannong.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ReminderSnooze.ACTION_SELECT) {
            ReminderSnooze.schedule(context, intent)
            return
        }
        if (intent.action !in setOf(CourseReminderScheduler.ACTION_COURSE_REMINDER, ReminderSnooze.ACTION_DELIVER)) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == ReminderSnooze.ACTION_DELIVER) ReminderSnooze.remove(intent)
                validateAndNotify(context, intent)
                CourseReminderScheduler.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun validateAndNotify(context: Context, intent: Intent) {
        if (!CourseReminderPreferences.enabled) return
        if (intent.action == ReminderSnooze.ACTION_DELIVER && !CourseReminderPreferences.snoozeEnabled) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val courseId = intent.getIntExtra(CourseReminderScheduler.EXTRA_COURSE_ID, -1)
        val starts = intent.getLongExtra(CourseReminderScheduler.EXTRA_START_MILLIS, -1L)
        if (starts <= 0) return
        val zone = com.mychuannong.app.school.SicauAcademicData.zone
        val startTime = Instant.ofEpochMilli(starts).atZone(zone)
        val matched = CourseScheduleEngine.coursesOnDate(
            courses = CourseScheduleRepository.loadCourses(context),
            termStart = CourseScheduleRepository.termStart(com.mychuannong.app.common.cache.CommonInfo.getCurrentTerm()) ?: return,
            date = startTime.toLocalDate(),
            zoneId = zone,
            periods = CourseTimePreferences.periods,
        ).firstOrNull { it.course.id == courseId && it.startsAt.toInstant().toEpochMilli() == starts } ?: return
        if (CourseReminderPreferences.isDisabled(matched.course)) return
        if (System.currentTimeMillis() >= matched.endsAt.toInstant().toEpochMilli()) return

        val manager = context.getSystemService(NotificationManager::class.java)
        ReminderChannels.ensure(context)
        val name = intent.getStringExtra(CourseReminderScheduler.EXTRA_COURSE_NAME).orEmpty()
        val room = intent.getStringExtra(CourseReminderScheduler.EXTRA_ROOM).orEmpty()
        val teacher = intent.getStringExtra(CourseReminderScheduler.EXTRA_TEACHER).orEmpty()
        val end = Instant.ofEpochMilli(intent.getLongExtra(CourseReminderScheduler.EXTRA_END_MILLIS, starts)).atZone(zone)
        val lead = intent.getIntExtra(CourseReminderScheduler.EXTRA_LEAD_MINUTES, CourseReminderPreferences.leadMinutes)
        val detail = buildString {
            append(name)
            if (CourseReminderPreferences.showDetails && room.isNotBlank()) append(" · $room")
            if (CourseReminderPreferences.showDetails && teacher.isNotBlank()) append(" · $teacher")
        }
        val open = PendingIntent.getActivity(
            context,
            courseId,
            Intent(context, TimeTableActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("highlight_course_id", courseId)
                putExtra("highlight_course_date", startTime.toLocalDate().toString())
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val format = DateTimeFormatter.ofPattern("HH:mm")
        manager.notify(
            notificationId(courseId, starts),
            NotificationCompat.Builder(context, ReminderChannels.currentId())
                .setSmallIcon(R.drawable.notification2)
                .setContentTitle(if (intent.action == ReminderSnooze.ACTION_DELIVER) "课程稍后提醒" else "${lead} 分钟后上课")
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\n${startTime.format(format)}–${end.format(format)}"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setSilent(CourseReminderPreferences.silent)
                .setOnlyAlertOnce(true)
                .apply {
                    if (CourseReminderPreferences.snoozeEnabled) listOf(5, 10).forEach { minutes ->
                        if (System.currentTimeMillis() + minutes * 60000L < matched.endsAt.toInstant().toEpochMilli())
                            addAction(0, "稍后${minutes}分钟", ReminderSnooze.action(context, intent, minutes))
                    }
                }
                .setPriority(if(CourseReminderPreferences.silent) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    private fun notificationId(courseId: Int, startMillis: Long) =
        (31 * courseId + (startMillis xor (startMillis ushr 32)).toInt()).and(0x7fffffff)

    companion object { private const val CHANNEL_ID = "course_reminders_v1" }
}
