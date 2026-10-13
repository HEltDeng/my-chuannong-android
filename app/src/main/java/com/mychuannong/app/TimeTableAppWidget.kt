package com.mychuannong.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import com.mychuannong.app.feature.schedule.CourseOccurrence
import com.mychuannong.app.feature.schedule.CourseScheduleRepository
import com.mychuannong.app.feature.schedule.CourseScheduleSnapshot
import com.mychuannong.app.feature.schedule.CourseScheduleStatus
import com.mychuannong.app.feature.timetable.ui.TimeTableActivity
import com.mychuannong.app.settings.ui.BindingUserActivity
import com.mychuannong.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class TimeTableAppWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgetsAsync(context, manager, ids, WidgetKind.TODAY)
    }
}

class NextCourseAppWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgetsAsync(context, manager, ids, WidgetKind.NEXT)
    }
}

private enum class WidgetKind { NEXT, TODAY }

private fun AppWidgetProvider.updateWidgetsAsync(
    context: Context,
    manager: AppWidgetManager,
    ids: IntArray,
    kind: WidgetKind,
) {
    val pending = goAsync()
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val snapshot = CourseScheduleRepository.snapshot(context)
            ids.forEach { id ->
                val views = when (kind) {
                    WidgetKind.NEXT -> WidgetUpdateCoordinator.nextViews(context, id, snapshot)
                    WidgetKind.TODAY -> WidgetUpdateCoordinator.todayViews(context, id, snapshot)
                }
                manager.updateAppWidget(id, views)
            }
            WidgetUpdateCoordinator.scheduleBoundaryRefresh(context, snapshot)
        } finally {
            pending.finish()
        }
    }
}

object WidgetUpdateCoordinator {
    const val ACTION_REFRESH_SCHEDULE = "com.mychuannong.app.action.REFRESH_SCHEDULE"
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val todayIds = manager.getAppWidgetIds(ComponentName(context, TimeTableAppWidget::class.java))
        val nextIds = manager.getAppWidgetIds(ComponentName(context, NextCourseAppWidget::class.java))
        if (todayIds.isNotEmpty()) {
            context.sendBroadcast(Intent(context, TimeTableAppWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, todayIds)
            })
        }
        if (nextIds.isNotEmpty()) {
            context.sendBroadcast(Intent(context, NextCourseAppWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, nextIds)
            })
        }
    }

    internal fun nextViews(
        context: Context,
        id: Int,
        snapshot: CourseScheduleSnapshot,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.next_course_app_widget).apply {
        setOnClickPendingIntent(R.id.next_widget_root, openScheduleIntent(context, id, snapshot))
        setTextViewText(R.id.next_widget_week, weekLabel(snapshot))
        val occurrence = snapshot.currentCourse ?: snapshot.nextCourse?.takeIf {
            it.date == ZonedDateTime.now(it.startsAt.zone).toLocalDate()
        }
        if (occurrence == null) {
            setTextViewText(R.id.next_widget_state, statusLabel(snapshot.status))
            setTextViewText(R.id.next_widget_course_name, emptyTitle(snapshot.status))
            setTextViewText(R.id.next_widget_room, if (snapshot.status == CourseScheduleStatus.NO_LOCAL_COURSES) "点击同步教务课表" else "轻松安排今天")
            setTextViewText(R.id.next_widget_time, "")
            setTextViewText(R.id.next_widget_countdown, "")
        } else {
            val current = snapshot.currentCourse != null
            setTextViewText(R.id.next_widget_state, if (current) "进行中" else "下一节")
            setTextViewText(R.id.next_widget_course_name, occurrence.course.name)
            setTextViewText(R.id.next_widget_room, occurrence.course.room.ifBlank { "地点待定" })
            setTextViewText(R.id.next_widget_time, "${occurrence.startsAt.format(timeFormatter)}–${occurrence.endsAt.format(timeFormatter)}")
            setTextViewText(R.id.next_widget_countdown, countdown(occurrence, current))
        }
    }

    internal fun todayViews(
        context: Context,
        id: Int,
        snapshot: CourseScheduleSnapshot,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.time_table_app_widget).apply {
        setOnClickPendingIntent(R.id.today_widget_root, openScheduleIntent(context, id + 10_000, snapshot))
        setTextViewText(R.id.today_widget_week, weekLabel(snapshot))
        val courses = snapshot.remainingToday.take(3)
        setViewVisibility(R.id.today_widget_empty, if (courses.isEmpty()) View.VISIBLE else View.GONE)
        setTextViewText(R.id.today_widget_empty, if (courses.isEmpty()) emptyTitle(snapshot.status) else "")
        val rows = listOf(
            Triple(R.id.today_row_1, R.id.today_time_1, Pair(R.id.today_name_1, R.id.today_room_1)),
            Triple(R.id.today_row_2, R.id.today_time_2, Pair(R.id.today_name_2, R.id.today_room_2)),
            Triple(R.id.today_row_3, R.id.today_time_3, Pair(R.id.today_name_3, R.id.today_room_3)),
        )
        rows.forEachIndexed { index, (rowId, timeId, labels) ->
            val item = courses.getOrNull(index)
            setViewVisibility(rowId, if (item == null) View.GONE else View.VISIBLE)
            if (item != null) {
                setInt(rowId, "setBackgroundResource", if (index == 0) R.drawable.widget_row_highlight else R.drawable.widget_row_normal)
                setTextViewText(timeId, item.startsAt.format(timeFormatter))
                setTextViewText(labels.first, item.course.name)
                setTextViewText(labels.second, item.course.room.ifBlank { "地点待定" })
            }
        }
    }

    internal fun scheduleBoundaryRefresh(context: Context, snapshot: CourseScheduleSnapshot) {
        val trigger = snapshot.nextBoundary ?: return
        val now = ZonedDateTime.now(trigger.zone)
        if (!trigger.isAfter(now)) return
        val alarm = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            4_201,
            Intent(context, ScheduleSystemReceiver::class.java).setAction(ACTION_REFRESH_SCHEDULE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), pending)
        } else {
            alarm.set(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), pending)
        }
    }

    private fun openScheduleIntent(context: Context, requestCode: Int, snapshot: CourseScheduleSnapshot): PendingIntent {
        val destination = if (com.mychuannong.app.common.data.local.mmkv.StudentInfoManager.studentId.isBlank() && snapshot.status == CourseScheduleStatus.NO_LOCAL_COURSES) BindingUserActivity::class.java else TimeTableActivity::class.java
        return PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, destination).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("from_timeTable_widget", true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun weekLabel(snapshot: CourseScheduleSnapshot): String {
        val weekday = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[snapshot.weekday.value - 1]
        return snapshot.teachingWeek?.let { "第${it}周 · $weekday" } ?: weekday
    }

    private fun statusLabel(status: CourseScheduleStatus) = when (status) {
        CourseScheduleStatus.NO_LOCAL_COURSES -> "本地课表"
        CourseScheduleStatus.TERM_NOT_STARTED -> "学期未开始"
        CourseScheduleStatus.TERM_ENDED -> "学期已结束"
        else -> "今日课表"
    }

    private fun emptyTitle(status: CourseScheduleStatus) = when (status) {
        CourseScheduleStatus.NO_LOCAL_COURSES -> "点击同步课表"
        CourseScheduleStatus.TERM_NOT_STARTED -> "新学期还没开始"
        CourseScheduleStatus.TERM_ENDED -> "当前在学期外"
        CourseScheduleStatus.TODAY_FINISHED -> "今天课程结束啦"
        CourseScheduleStatus.TERM_DATE_UNKNOWN -> "请先设置学期日期"
        else -> "今天没有课"
    }

    private fun countdown(item: CourseOccurrence, current: Boolean): String {
        if (current) return "结束 ${item.endsAt.format(timeFormatter)}"
        val now = ZonedDateTime.now(item.startsAt.zone)
        if (item.date != now.toLocalDate()) return "${if (item.date == now.toLocalDate().plusDays(1)) "明天" else item.date} ${item.startsAt.format(timeFormatter)}"
        val minutes = Duration.between(now, item.startsAt).toMinutes().coerceAtLeast(0)
        return if (minutes < 60) "还有 ${minutes} 分钟" else "还有 ${minutes / 60} 小时${if (minutes % 60 == 0L) "" else " ${minutes % 60} 分钟"}"
    }
}
