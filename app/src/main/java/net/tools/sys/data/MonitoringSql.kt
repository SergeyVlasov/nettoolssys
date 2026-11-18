package net.tools.sys.data

internal object MonitoringSql {
    const val HISTORY_FOR_SERVER =
        "SELECT * FROM monitoring_history WHERE serverId = :serverId ORDER BY checkedAt DESC"

    const val HISTORY_SINCE = """
        SELECT * FROM monitoring_history
        WHERE serverId = :serverId AND checkedAt >= :since
        ORDER BY checkedAt DESC
    """

    const val HISTORY_SINCE_LIMITED = """
        SELECT * FROM monitoring_history
        WHERE serverId = :serverId AND checkedAt >= :since
        ORDER BY checkedAt DESC
        LIMIT :limit
    """

    const val RESPONSE_AGGREGATE = """
        SELECT
            AVG(responseTime) AS averageResponseTime,
            MIN(responseTime) AS minResponseTime,
            MAX(responseTime) AS maxResponseTime,
            COUNT(CASE WHEN status = 'OFFLINE' THEN 1 END) AS failureCount
        FROM monitoring_history
        WHERE serverId = :serverId
          AND checkedAt >= :windowStart
          AND checkedAt < :windowEnd
          AND (:source = '' OR source = :source)
    """

    const val AVAILABILITY_DURATION = """
        SELECT
            SUM(
                CASE WHEN status = 'ONLINE' THEN
                    min(
                        COALESCE(
                            (
                                SELECT MIN(next.checkedAt)
                                FROM monitoring_history AS next
                                WHERE next.serverId = h.serverId
                                  AND next.checkedAt > h.checkedAt
                                  AND next.checkedAt < :windowEnd
                                  AND (:source = '' OR next.source = :source)
                            ),
                            :windowEnd
                        ) - h.checkedAt,
                        :maxGapMs
                    )
                ELSE 0 END
            ) AS uptimeMs,
            SUM(
                CASE WHEN status = 'OFFLINE' THEN
                    min(
                        COALESCE(
                            (
                                SELECT MIN(next.checkedAt)
                                FROM monitoring_history AS next
                                WHERE next.serverId = h.serverId
                                  AND next.checkedAt > h.checkedAt
                                  AND next.checkedAt < :windowEnd
                                  AND (:source = '' OR next.source = :source)
                            ),
                            :windowEnd
                        ) - h.checkedAt,
                        :maxGapMs
                    )
                ELSE 0 END
            ) AS downtimeMs
        FROM monitoring_history AS h
        WHERE h.serverId = :serverId
          AND h.checkedAt >= :windowStart
          AND h.checkedAt < :windowEnd
          AND (:source = '' OR h.source = :source)
    """

    const val DELETE_OLD_HISTORY = "DELETE FROM monitoring_history WHERE checkedAt < :threshold"
}
