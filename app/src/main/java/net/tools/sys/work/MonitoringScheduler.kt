package net.tools.sys.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.tools.sys.monitoring.MonitoringIntervals
import java.util.concurrent.TimeUnit

object MonitoringScheduler {
    const val UNIQUE_WORK_NAME = "server_availability_monitor"
    const val CLEANUP_WORK_NAME = "monitoring_history_cleanup"

    private val activeStates = setOf(
        WorkInfo.State.ENQUEUED,
        WorkInfo.State.RUNNING,
        WorkInfo.State.BLOCKED,
    )

    fun enqueue(context: Context, intervalMinutes: Int) {
        val minutes = MonitoringIntervals.normalize(intervalMinutes)
        val request = PeriodicWorkRequestBuilder<ServerMonitorWorker>(
            minutes.toLong(),
            TimeUnit.MINUTES,
            PeriodicWorkRequest.MIN_PERIODIC_FLEX_MILLIS,
            TimeUnit.MILLISECONDS,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .addTag(intervalTag(minutes))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }

    fun ensureHistoryCleanup(context: Context) {
        val request = PeriodicWorkRequestBuilder<MonitoringHistoryCleanupWorker>(1, TimeUnit.DAYS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            CLEANUP_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Keeps a single periodic job aligned with the saved settings.
     * An existing job with the same interval is left in place.
     */
    suspend fun sync(context: Context, enabled: Boolean, intervalMinutes: Int) {
        if (!enabled) {
            cancel(context)
            return
        }
        val minutes = MonitoringIntervals.normalize(intervalMinutes)
        val aligned = runCatching { hasActiveWork(context, minutes) }.getOrDefault(false)
        if (!aligned) {
            enqueue(context, minutes)
        }
    }

    private suspend fun hasActiveWork(context: Context, intervalMinutes: Int): Boolean {
        val infos = withContext(Dispatchers.IO) {
            WorkManager.getInstance(context).getWorkInfosForUniqueWork(UNIQUE_WORK_NAME).get()
        }
        val tag = intervalTag(intervalMinutes)
        return infos.any { info -> info.state in activeStates && tag in info.tags }
    }

    private fun intervalTag(minutes: Int): String = "monitoring_interval_$minutes"
}
