package net.tools.sys.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import net.tools.sys.data.MonitoringRepository
import net.tools.sys.data.MonitoringWindows

class MonitoringHistoryCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = MonitoringRepository.create(applicationContext)
        val threshold = System.currentTimeMillis() - MonitoringWindows.RETENTION_MS
        repository.deleteHistoryOlderThan(threshold)
        return Result.success()
    }
}
