package com.mychuannong.app.feature.schedule.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mychuannong.app.feature.schedule.CourseScheduleEngine
import com.mychuannong.app.feature.schedule.CourseScheduleRepository
import com.mychuannong.app.feature.schedule.CourseTimePreferences
import com.mychuannong.app.feature.schedule.ScheduleCourse
import java.time.ZonedDateTime

object CourseReminderScheduler {
    const val ACTION_COURSE_REMINDER = "com.mychuannong.app.action.COURSE_REMINDER"
    const val EXTRA_COURSE_ID = "course_id"
    const val EXTRA_COURSE_NAME = "course_name"
    const val EXTRA_ROOM = "course_room"
    const val EXTRA_TEACHER = "course_teacher"
    const val EXTRA_START_MILLIS = "course_start_millis"
    const val EXTRA_END_MILLIS = "course_end_millis"
    const val EXTRA_LEAD_MINUTES = "lead_minutes"

    suspend fun reschedule(context: Context) {
        cancelAll(context)
        if (!CourseReminderPreferences.enabled) return
        val now = ZonedDateTime.now()
        val courses = CourseScheduleRepository.loadCourses(context)
        val occurrences = CourseScheduleEngine.reminderOccurrences(
            courses = courses,
            termStart = CourseScheduleRepository.termStart(com.mychuannong.app.common.cache.CommonInfo.getCurrentTerm()),
            from = now,
            until = now.plusDays(14),
            leadMinutes = CourseReminderPreferences.leadMinutes,
            disabledCourseIds = courses.filter(CourseReminderPreferences::isDisabled)
                .map(ScheduleCourse::id).toSet(),
            periods = CourseTimePreferences.periods,
        )
        val alarm = context.getSystemService(AlarmManager::class.java)
        val requestCodes = linkedSetOf<Int>()
        occurrences.forEach { (occurrence, reminderAt) ->
            val code = reminderCode(occurrence.course.id, occurrence.startsAt.toInstant().toEpochMilli())
            val pending = reminderPendingIntent(
                context = context,
                requestCode = code,
                courseId = occurrence.course.id,
                name = occurrence.course.name,
                room = occurrence.course.room,
                teacher = occurrence.course.teacher,
                starts = occurrence.startsAt.toInstant().toEpochMilli(),
                ends = occurrence.endsAt.toInstant().toEpochMilli(),
                leadMinutes = CourseReminderPreferences.leadMinutes,
            )
            val trigger = reminderAt.toInstant().toEpochMilli()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarm.canScheduleExactAlarms()) {
                alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
            } else {
                alarm.set(AlarmManager.RTC_WAKEUP, trigger, pending)
            }
            requestCodes += code
        }
        CourseReminderPreferences.scheduledRequestCodes = requestCodes
    }

    fun cancelAll(context: Context) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        CourseReminderPreferences.scheduledRequestCodes.forEach { code ->
            val pending = PendingIntent.getBroadcast(
                context,
                code,
                Intent(context, CourseReminderReceiver::class.java).setAction(ACTION_COURSE_REMINDER),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            )
            pending?.let { alarm.cancel(it); it.cancel() }
        }
        CourseReminderPreferences.scheduledRequestCodes = emptySet()
    }

    private fun reminderPendingIntent(
        context: Context,
        requestCode: Int,
        courseId: Int,
        name: String,
        room: String,
        teacher: String,
        starts: Long,
        ends: Long,
        leadMinutes: Int,
    ) = PendingIntent.getBroadcast(
        context,
        requestCode,
        Intent(context, CourseReminderReceiver::class.java).apply {
            action = ACTION_COURSE_REMINDER
            putExtra(EXTRA_COURSE_ID, courseId)
            putExtra(EXTRA_COURSE_NAME, name)
            putExtra(EXTRA_ROOM, room)
            putExtra(EXTRA_TEACHER, teacher)
            putExtra(EXTRA_START_MILLIS, starts)
            putExtra(EXTRA_END_MILLIS, ends)
            putExtra(EXTRA_LEAD_MINUTES, leadMinutes)
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun reminderCode(courseId: Int, startMillis: Long): Int =
        (31 * courseId + (startMillis xor (startMillis ushr 32)).toInt()).and(0x7fffffff)
}
