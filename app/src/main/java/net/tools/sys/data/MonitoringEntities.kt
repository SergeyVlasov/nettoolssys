package net.tools.sys.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

enum class MonitoringStateStatus {
    ONLINE,
    OFFLINE,
    UNKNOWN,
}

enum class MonitoringCheckStatus {
    ONLINE,
    OFFLINE,
}

/**
 * Where a check was performed. [REMOTE] is reserved for a later external probe
 * and is not produced by the app today.
 */
enum class MonitoringSource {
    LOCAL,
    REMOTE,
}

@Entity(
    tableName = "server_monitoring_state",
    indices = [
        Index(value = ["serverId"], unique = true),
    ],
)
data class ServerMonitoringStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String,
    val lastStatus: MonitoringStateStatus,
    val lastCheckTime: Long,
    val consecutiveFailures: Int,
    val lastResponseTime: Long?,
)

@Entity(
    tableName = "monitoring_history",
    indices = [
        Index(value = ["serverId"]),
        Index(value = ["checkedAt"]),
        Index(value = ["serverId", "checkedAt"]),
        Index(value = ["serverId", "source", "executionKey"], unique = true),
    ],
)
data class MonitoringHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String,
    val status: MonitoringCheckStatus,
    val responseTime: Long?,
    val checkedAt: Long,
    val errorMessage: String?,
    val source: MonitoringSource = MonitoringSource.LOCAL,
    /**
     * Identifies one scheduled execution for this server. A repeated run of the
     * same task in the same interval is ignored.
     */
    val executionKey: String,
)

class MonitoringConverters {
    @TypeConverter
    fun stateStatusToString(value: MonitoringStateStatus): String = value.name

    @TypeConverter
    fun stringToStateStatus(value: String): MonitoringStateStatus = MonitoringStateStatus.valueOf(value)

    @TypeConverter
    fun checkStatusToString(value: MonitoringCheckStatus): String = value.name

    @TypeConverter
    fun stringToCheckStatus(value: String): MonitoringCheckStatus = MonitoringCheckStatus.valueOf(value)

    @TypeConverter
    fun sourceToString(value: MonitoringSource): String = value.name

    @TypeConverter
    fun stringToSource(value: String): MonitoringSource = MonitoringSource.valueOf(value)
}
