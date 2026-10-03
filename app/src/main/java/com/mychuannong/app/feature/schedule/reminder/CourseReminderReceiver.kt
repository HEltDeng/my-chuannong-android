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
        if (intent.action != CourseReminderScheduler.ACTION_COURSE_REMINDER) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                validateAndNotify(context, intent)
                CourseReminderScheduler.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun validateAndNotify(context: Context, intent: Intent) {
        if (!CourseReminderPreferences.enabled) return
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val courseId = intent.getIntExtra(CourseReminderScheduler.EXTRA_COURSE_ID, -1)
        val starts = intent.getLongExtra(CourseReminderScheduler.EXTRA_START_MILLIS, -1L)
        if (courseId < 0 || starts <= 0) return
        val zone = ZoneId.of("Asia/Shanghai")
        val startTime = Instant.ofEpochMilli(starts).atZone(zone)
        val matched = CourseScheduleEngine.coursesOnDate(
            courses = CourseScheduleRepository.loadCourses(context),
            termStart = CourseScheduleRepository.termStart(com.mychuannong.app.common.cache.CommonInfo.getCurrentTerm()) ?: return,
            date = startTime.toLocalDate(),
            zoneId = zone,
            periods = CourseTimePreferences.periods,
            maxTeachingWeek = com.mychuannong.app.common.cache.CommonInfo.teachingWeeks(com.mychuannong.app.common.cache.CommonInfo.getCurrentTerm()),
        ).firstOrNull { it.course.id == courseId && it.startsAt.toInstant().toEpochMilli() == starts } ?: return
        if (CourseReminderPreferences.courseKey(matched.course) in CourseReminderPreferences.disabledCourseKeys) return

        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "上课提醒", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "按本地课表提醒即将开始的课程"
            })
        }
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
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.notification2)
                .setContentTitle("${lead} 分钟后上课")
                .setContentText(detail)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$detail\n${startTime.format(format)}–${end.format(format)}"))
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build(),
        )
    }

    private fun notificationId(courseId: Int, startMillis: Long) =
        (31 * courseId + (startMillis xor (startMillis ushr 32)).toInt()).and(0x7fffffff)

    companion object { private const val CHANNEL_ID = "course_reminders_v1" }
}
