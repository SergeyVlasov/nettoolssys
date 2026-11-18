package net.tools.sys.data

import androidx.room.withTransaction
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.tools.sys.monitoring.AvailabilityRecord
import net.tools.sys.monitoring.AvailabilityStatus
import net.tools.sys.monitoring.AvailabilityTracker
import net.tools.sys.monitoring.StatusNotification

data class RecordedMonitoringResult(
    val saved: Boolean,
    val notification: StatusNotification?,
    val state: ServerMonitoringStateEntity?,
)

class MonitoringRepository(
    private val database: MonitoringDatabase,
    private val legacyStates: suspend () -> Map<String, AvailabilityRecord> = { emptyMap() },
    private val onLegacyImported: suspend () -> Unit = {},
) {
    private val dao = database.monitoringDao()
    private val importLock = Mutex()
    private var legacyImported = false

    suspend fun recordCheck(
        serverId: String,
        source: MonitoringSource = MonitoringSource.LOCAL,
        executionKey: String,
        checkedAt: Long,
        reachable: Boolean,
        responseTimeMs: Long?,
        errorMessage: String?,
    ): RecordedMonitoringResult {
        importLegacyStates()
        return database.withTransaction {
            val insertedId = dao.insertHistory(
                MonitoringHistoryEntity(
                    serverId = serverId,
                    status = if (reachable) MonitoringCheckStatus.ONLINE else MonitoringCheckStatus.OFFLINE,
                    responseTime = if (reachable) responseTimeMs else null,
                    checkedAt = checkedAt,
                    errorMessage = if (reachable) null else errorMessage,
                    source = source,
                    executionKey = executionKey,
                ),
            )
            if (insertedId == -1L) {
                return@withTransaction RecordedMonitoringResult(
                    saved = false,
                    notification = null,
                    state = dao.getState(serverId),
                )
            }

            val previous = dao.getState(serverId)
            val update = resolveMonitoringState(
                previous = previous,
                serverId = serverId,
                checkedAt = checkedAt,
                reachable = reachable,
                responseTimeMs = responseTimeMs,
            )
            if (update.insert) dao.insertState(update.state) else dao.updateState(update.state)
            RecordedMonitoringResult(
                saved = true,
                notification = update.notification,
                state = update.state,
            )
        }
    }

    suspend fun historyForServer(serverId: String): List<MonitoringHistoryEntity> =
        dao.historyForServer(serverId)

    /** Newest checks first, at most [limit] rows, for screens that show a window of history. */
    suspend fun recentHistory(serverId: String, since: Long, limit: Int): List<MonitoringHistoryEntity> =
        dao.historySinceLimited(serverId, since, limit)

    suspend fun historyLast24Hours(serverId: String, now: Long = System.currentTimeMillis()) =
        dao.historyLast24Hours(serverId, now)

    suspend fun historyLast7Days(serverId: String, now: Long = System.currentTimeMillis()) =
        dao.historyLast7Days(serverId, now)

    suspend fun historyLast30Days(serverId: String, now: Long = System.currentTimeMillis()) =
        dao.historyLast30Days(serverId, now)

    suspend fun statistics(
        serverId: String,
        windowStart: Long,
        windowEnd: Long,
        source: MonitoringSource? = MonitoringSource.LOCAL,
    ): MonitoringStatistics {
        val sourceFilter = source?.name ?: ""
        val aggregate = dao.responseAggregate(serverId, windowStart, windowEnd, sourceFilter)
        val duration = dao.availabilityDuration(
            serverId = serverId,
            windowStart = windowStart,
            windowEnd = windowEnd,
            source = sourceFilter,
            maxGapMs = MonitoringWindows.MAX_SAMPLE_GAP_MS,
        )
        val uptimeMs = duration.uptimeMs ?: 0L
        val downtimeMs = duration.downtimeMs ?: 0L
        val covered = uptimeMs + downtimeMs
        return MonitoringStatistics(
            averageResponseTimeMs = aggregate.averageResponseTime,
            minResponseTimeMs = aggregate.minResponseTime,
            maxResponseTimeMs = aggregate.maxResponseTime,
            failureCount = aggregate.failureCount,
            uptimeMs = uptimeMs,
            downtimeMs = downtimeMs,
            availabilityPercent = if (covered > 0L) uptimeMs * 100.0 / covered else null,
        )
    }

    suspend fun statisticsLast24Hours(
        serverId: String,
        now: Long = System.currentTimeMillis(),
        source: MonitoringSource? = MonitoringSource.LOCAL,
    ) = statistics(serverId, now - MonitoringWindows.DAY_MS, now, source)

    suspend fun statisticsLast7Days(
        serverId: String,
        now: Long = System.currentTimeMillis(),
        source: MonitoringSource? = MonitoringSource.LOCAL,
    ) = statistics(serverId, now - MonitoringWindows.DAYS_7_MS, now, source)

    suspend fun statisticsLast30Days(
        serverId: String,
        now: Long = System.currentTimeMillis(),
        source: MonitoringSource? = MonitoringSource.LOCAL,
    ) = statistics(serverId, now - MonitoringWindows.DAYS_30_MS, now, source)

    suspend fun deleteHistoryOlderThan(threshold: Long): Int = dao.deleteHistoryOlderThan(threshold)

    private suspend fun importLegacyStates() {
        if (legacyImported) return
        importLock.withLock {
            if (legacyImported) return
            val records = legacyStates()
            if (records.isNotEmpty()) {
                database.withTransaction {
                    for ((serverId, record) in records) {
                        if (dao.getState(serverId) != null) continue
                        dao.insertState(
                            ServerMonitoringStateEntity(
                                serverId = serverId,
                                lastStatus = record.toStateStatus(),
                                lastCheckTime = 0L,
                                consecutiveFailures = record.consecutiveFailures,
                                lastResponseTime = null,
                            ),
                        )
                    }
                }
                onLegacyImported()
            }
            legacyImported = true
        }
    }

    companion object {
        fun create(context: android.content.Context): MonitoringRepository {
            val settings = MonitoringSettingsRepository(context)
            return MonitoringRepository(
                database = MonitoringDatabase.get(context),
                legacyStates = settings::peekLegacyStatusRecords,
                onLegacyImported = settings::clearLegacyStatusRecords,
            )
        }
    }
}

internal data class StateUpdate(
    val state: ServerMonitoringStateEntity,
    val notification: StatusNotification?,
    val insert: Boolean,
)

internal fun resolveMonitoringState(
    previous: ServerMonitoringStateEntity?,
    serverId: String,
    checkedAt: Long,
    reachable: Boolean,
    responseTimeMs: Long?,
): StateUpdate {
    val decision = AvailabilityTracker.apply(previous.toAvailabilityRecord(), reachable)
    return StateUpdate(
        state = ServerMonitoringStateEntity(
            id = previous?.id ?: 0,
            serverId = serverId,
            lastStatus = decision.record.toStateStatus(),
            lastCheckTime = checkedAt,
            consecutiveFailures = decision.record.consecutiveFailures,
            lastResponseTime = if (reachable) responseTimeMs else previous?.lastResponseTime,
        ),
        notification = decision.notification,
        insert = previous == null,
    )
}

private fun ServerMonitoringStateEntity?.toAvailabilityRecord(): AvailabilityRecord = AvailabilityRecord(
    status = when (this?.lastStatus) {
        MonitoringStateStatus.ONLINE -> AvailabilityStatus.AVAILABLE
        MonitoringStateStatus.OFFLINE -> AvailabilityStatus.UNAVAILABLE
        MonitoringStateStatus.UNKNOWN, null -> null
    },
    consecutiveFailures = this?.consecutiveFailures ?: 0,
)

private fun AvailabilityRecord.toStateStatus(): MonitoringStateStatus = when (status) {
    AvailabilityStatus.AVAILABLE -> MonitoringStateStatus.ONLINE
    AvailabilityStatus.UNAVAILABLE -> MonitoringStateStatus.OFFLINE
    null -> MonitoringStateStatus.UNKNOWN
}
