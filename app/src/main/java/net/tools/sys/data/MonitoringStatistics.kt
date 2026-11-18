package net.tools.sys.data

data class MonitoringStatistics(
    val averageResponseTimeMs: Double?,
    val minResponseTimeMs: Long?,
    val maxResponseTimeMs: Long?,
    val failureCount: Int,
    val uptimeMs: Long,
    val downtimeMs: Long,
    val availabilityPercent: Double?,
)

object MonitoringWindows {
    const val DAY_MS = 24L * 60L * 60L * 1000L
    const val DAYS_7_MS = 7L * DAY_MS
    const val DAYS_30_MS = 30L * DAY_MS
    const val RETENTION_DAYS = 90
    const val RETENTION_MS = 90L * DAY_MS

    /**
     * A sample is not treated as lasting longer than this. Monitoring runs at
     * most every 60 minutes, so a multi-day gap is time the monitor was not running.
     */
    const val MAX_SAMPLE_GAP_MS = 2L * 60L * 60L * 1000L
}
