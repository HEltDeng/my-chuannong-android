package com.mychuannong.app.feature.schedule.reminder

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import java.util.concurrent.TimeUnit

/** Renews the local alarm window even after a vacation with no course alarms firing. */
class ReminderRecoveryWorker(context: Context, parameters: WorkerParameters): CoroutineWorker(context,parameters) {
    override suspend fun doWork(): Result = try {
        CourseReminderScheduler.reschedule(applicationContext)
        Result.success()
    } catch(e:kotlinx.coroutines.CancellationException) { throw e }
      catch(_:Exception) { Result.retry() }
    companion object {
        fun install(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("local_course_reminder_recovery",
                ExistingPeriodicWorkPolicy.KEEP,PeriodicWorkRequestBuilder<ReminderRecoveryWorker>(1,TimeUnit.DAYS).build())
        }
    }
}
