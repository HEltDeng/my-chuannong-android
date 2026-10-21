package com.mychuannong.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.mychuannong.app.feature.schedule.reminder.CourseReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduleSystemReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                WidgetUpdateCoordinator.updateAll(context)
                CourseReminderScheduler.reschedule(context)
            } finally {
                pending.finish()
            }
        }
    }
}
