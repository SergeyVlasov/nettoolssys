package net.tools.sys.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

data class ResponseAggregate(
    @ColumnInfo(name = "averageResponseTime") val averageResponseTime: Double?,
    @ColumnInfo(name = "minResponseTime") val minResponseTime: Long?,
    @ColumnInfo(name = "maxResponseTime") val maxResponseTime: Long?,
    @ColumnInfo(name = "failureCount") val failureCount: Int,
)

data class AvailabilityDuration(
    @ColumnInfo(name = "uptimeMs") val uptimeMs: Long?,
    @ColumnInfo(name = "downtimeMs") val downtimeMs: Long?,
)

@Dao
interface MonitoringDao {

    @Query("SELECT * FROM server_monitoring_state WHERE serverId = :serverId LIMIT 1")
    suspend fun getState(serverId: String): ServerMonitoringStateEntity?

    @Insert
    suspend fun insertState(state: ServerMonitoringStateEntity): Long

    @Update
    suspend fun updateState(state: ServerMonitoringStateEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertHistory(entry: MonitoringHistoryEntity): Long

    @Query(MonitoringSql.HISTORY_FOR_SERVER)
    suspend fun historyForServer(serverId: String): List<MonitoringHistoryEntity>

    @Query(MonitoringSql.HISTORY_SINCE)
    suspend fun historySince(serverId: String, since: Long): List<MonitoringHistoryEntity>

    @Query(MonitoringSql.HISTORY_SINCE_LIMITED)
    suspend fun historySinceLimited(
        serverId: String,
        since: Long,
        limit: Int,
    ): List<MonitoringHistoryEntity>

    suspend fun historyLast24Hours(serverId: String, now: Long): List<MonitoringHistoryEntity> =
        historySince(serverId, now - MonitoringWindows.DAY_MS)

    suspend fun historyLast7Days(serverId: String, now: Long): List<MonitoringHistoryEntity> =
        historySince(serverId, now - MonitoringWindows.DAYS_7_MS)

    suspend fun historyLast30Days(serverId: String, now: Long): List<MonitoringHistoryEntity> =
        historySince(serverId, now - MonitoringWindows.DAYS_30_MS)

    @Query(MonitoringSql.RESPONSE_AGGREGATE)
    suspend fun responseAggregate(
        serverId: String,
        windowStart: Long,
        windowEnd: Long,
        source: String,
    ): ResponseAggregate

    @Query(MonitoringSql.AVAILABILITY_DURATION)
    suspend fun availabilityDuration(
        serverId: String,
        windowStart: Long,
        windowEnd: Long,
        source: String,
        maxGapMs: Long,
    ): AvailabilityDuration

    @Query(MonitoringSql.DELETE_OLD_HISTORY)
    suspend fun deleteHistoryOlderThan(threshold: Long): Int
}
