package net.tools.sys.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import net.tools.sys.data.MonitoringRepository
import net.tools.sys.data.MonitoringSettingsRepository
import net.tools.sys.data.ServerRepository
import net.tools.sys.model.ServerEntry
import net.tools.sys.monitoring.AvailabilityStatus
import net.tools.sys.monitoring.StatusNotification
import net.tools.sys.monitoring.isAvailable
import net.tools.sys.monitoring.monitoringErrorMessage
import net.tools.sys.monitoring.monitoringResponseTimeMs
import net.tools.sys.network.ServerCheckResult
import net.tools.sys.network.ServerConnectivityChecker
import net.tools.sys.notify.ServerStatusNotifier

class ServerMonitorWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!applicationContext.hasValidatedInternet()) return Result.success()

        val settingsRepository = MonitoringSettingsRepository(applicationContext)
        val settings = settingsRepository.currentSettings()
        if (!settings.enabled || settings.selectedIds.isEmpty()) return Result.success()

        val servers = ServerRepository(applicationContext).currentServers()
            .filter { it.id in settings.selectedIds }
        if (servers.isEmpty()) return Result.success()

        val history = MonitoringRepository.create(applicationContext)
        val checker = ServerConnectivityChecker()
        val notifier = ServerStatusNotifier(applicationContext)
        for (server in servers) {
            if (!settingsRepository.currentSettings().enabled) return Result.success()
            val outcome = try {
                readOutcome(checker, server)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                ProbeOutcome(
                    reachable = false,
                    responseTimeMs = null,
                    errorMessage = error.message?.takeIf { it.isNotBlank() } ?: error.javaClass.simpleName,
                )
            }
            val checkedAt = System.currentTimeMillis()
            val recorded = history.recordCheck(
                serverId = server.id,
                executionKey = executionKey(checkedAt, settings.intervalMinutes),
                checkedAt = checkedAt,
                reachable = outcome.reachable,
                responseTimeMs = outcome.responseTimeMs,
                errorMessage = outcome.errorMessage,
            )
            val notification = recorded.notification ?: continue
            if (!settingsRepository.currentSettings().enabled) return Result.success()
            val status = when (notification) {
                StatusNotification.BecameUnavailable -> AvailabilityStatus.UNAVAILABLE
                StatusNotification.Recovered -> AvailabilityStatus.AVAILABLE
            }
            try {
                notifier.showStatusChange(
                    serverId = server.id,
                    serverName = server.notificationName(),
                    status = status,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                // A notification failure must not skip the remaining servers.
            }
        }
        return Result.success()
    }

    private suspend fun readOutcome(
        checker: ServerConnectivityChecker,
        server: ServerEntry,
    ): ProbeOutcome {
        val result = withTimeoutOrNull(CHECK_TIMEOUT_MS) { checker.check(server) }
            ?: return ProbeOutcome(
                reachable = false,
                responseTimeMs = null,
                errorMessage = "Check timed out",
            )
        return result.toProbeOutcome()
    }

    private fun executionKey(checkedAt: Long, intervalMinutes: Int): String {
        val intervalMs = intervalMinutes.coerceAtLeast(1) * 60_000L
        return "$id:${checkedAt / intervalMs}"
    }

    private fun ServerEntry.notificationName(): String = name.trim().ifBlank { host }

    private data class ProbeOutcome(
        val reachable: Boolean,
        val responseTimeMs: Long?,
        val errorMessage: String?,
    )

    private fun ServerCheckResult.toProbeOutcome(): ProbeOutcome {
        val reachable = isAvailable()
        return ProbeOutcome(
            reachable = reachable,
            responseTimeMs = monitoringResponseTimeMs(),
            errorMessage = monitoringErrorMessage(),
        )
    }

    private companion object {
        const val CHECK_TIMEOUT_MS = 60_000L
    }
}
