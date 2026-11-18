package net.tools.sys.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.tools.sys.monitoring.StatusNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.sql.Connection
import java.sql.DriverManager

class MonitoringHistoryStoreTest {

    @Test
    fun recordsChecks_deduplicates_andSurvivesReopen() {
        val file = File.createTempFile("monitoring-history", ".db")
        file.deleteOnExit()
        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { connection ->
            createSchema(connection)
            val first = record(
                connection,
                executionKey = "run-1",
                checkedAt = 1_000,
                reachable = true,
                responseTimeMs = 42,
                errorMessage = null,
            )!!
            assertEquals(MonitoringStateStatus.ONLINE, first.state.lastStatus)
            assertNull(first.notification)
            assertEquals(42L, first.state.lastResponseTime)
            assertEquals(1, historyCount(connection))

            val duplicate = record(
                connection,
                executionKey = "run-1",
                checkedAt = 1_500,
                reachable = false,
                responseTimeMs = null,
                errorMessage = "Ping failed",
            )
            assertNull(duplicate)
            assertEquals(1, historyCount(connection))
            assertEquals(0, loadState(connection)?.consecutiveFailures)
        }

        DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}").use { reopened ->
            assertEquals(1, historyCount(reopened))
            assertEquals(42L, responseTimeOfOnlyRow(reopened))
            assertEquals(MonitoringStateStatus.ONLINE, loadState(reopened)?.lastStatus)
        }
    }

    @Test
    fun statusChanges_notifyOnlyOnARealTransition() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createSchema(connection)
            val online = record(connection, "up", 1_000, reachable = true, responseTimeMs = 10, errorMessage = null)!!
            val blip = record(connection, "blip", 2_000, reachable = false, responseTimeMs = 99, errorMessage = "Ping failed")!!
            val offline = record(connection, "down-2", 3_000, reachable = false, responseTimeMs = null, errorMessage = "Timed out")!!
            val stillOffline = record(connection, "down-3", 4_000, reachable = false, responseTimeMs = null, errorMessage = "Timed out")!!
            val recovered = record(connection, "up-2", 5_000, reachable = true, responseTimeMs = 15, errorMessage = null)!!

            assertNull(online.notification)
            assertEquals(MonitoringStateStatus.ONLINE, blip.state.lastStatus)
            assertEquals(1, blip.state.consecutiveFailures)
            assertEquals(10L, blip.state.lastResponseTime)
            assertNull(blip.notification)
            assertEquals("Ping failed", errorAt(connection, 2_000))
            assertNull(responseTimeAt(connection, 2_000))
            assertEquals(StatusNotification.BecameUnavailable, offline.notification)
            assertEquals(MonitoringStateStatus.OFFLINE, offline.state.lastStatus)
            assertNull(stillOffline.notification)
            assertEquals(StatusNotification.Recovered, recovered.notification)
            assertEquals(0, recovered.state.consecutiveFailures)
            assertEquals(15L, recovered.state.lastResponseTime)
        }
    }

    @Test
    fun initialFailures_doNotNotify() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createSchema(connection)
            val first = record(connection, "a", 1_000, reachable = false, responseTimeMs = null, errorMessage = "DNS lookup failed")!!
            val second = record(connection, "b", 2_000, reachable = false, responseTimeMs = null, errorMessage = "DNS lookup failed")!!
            assertEquals(MonitoringStateStatus.UNKNOWN, first.state.lastStatus)
            assertNull(first.notification)
            assertEquals(MonitoringStateStatus.OFFLINE, second.state.lastStatus)
            assertNull(second.notification)
        }
    }

    @Test
    fun windowsStatisticsAndCleanup_useTheDaoQueries() {
        DriverManager.getConnection("jdbc:sqlite::memory:").use { connection ->
            createSchema(connection)
            val now = 1_000_000_000_000L
            record(connection, "old", now - MonitoringWindows.DAYS_30_MS - 1, true, 1, null)
            record(connection, "month", now - MonitoringWindows.DAYS_7_MS - 1, false, null, "Ping failed")
            record(connection, "week", now - MonitoringWindows.DAY_MS - 1, true, 100, null)
            record(connection, "day", now - 60_000, true, 300, null)

            assertEquals(4, historySince(connection, 0))
            assertEquals(1, historySince(connection, now - MonitoringWindows.DAY_MS))
            assertEquals(2, historySince(connection, now - MonitoringWindows.DAYS_7_MS))
            assertEquals(3, historySince(connection, now - MonitoringWindows.DAYS_30_MS))
            assertEquals(2, limitedHistoryCount(connection, since = 0, limit = 2))
            assertEquals(4, limitedHistoryCount(connection, since = 0, limit = 200))

            connection.autoCommit = true
            connection.createStatement().executeUpdate("DELETE FROM monitoring_history")
            connection.createStatement().executeUpdate("DELETE FROM server_monitoring_state")

            record(connection, "s1", 1_000_000, true, 100, null)
            record(connection, "s2", 1_010_000, false, null, "Ping failed")
            record(connection, "s3", 1_030_000, true, 300, null)
            val measured = statistics(connection, 1_000_000, 1_040_000)
            assertEquals(200.0, measured.average!!, 0.001)
            assertEquals(100L, measured.min)
            assertEquals(300L, measured.max)
            assertEquals(1, measured.failures)
            assertEquals(20_000L, measured.uptime)
            assertEquals(20_000L, measured.downtime)
            assertEquals(50.0, measured.availability!!, 0.001)

            val removed = deleteOlderThan(connection, 1_020_000)
            assertEquals(2, removed)
            assertEquals(1, historyCount(connection))
            assertEquals(MonitoringStateStatus.ONLINE, loadState(connection)?.lastStatus)
            assertTrue(loadState(connection)?.consecutiveFailures == 0)
        }
    }

    private fun record(
        connection: Connection,
        executionKey: String,
        checkedAt: Long,
        reachable: Boolean,
        responseTimeMs: Long?,
        errorMessage: String?,
    ): StateUpdate? {
        connection.autoCommit = false
        val inserted = connection.prepareStatement(
            """
            INSERT OR IGNORE INTO monitoring_history
                (serverId, status, responseTime, checkedAt, errorMessage, source, executionKey)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, SERVER)
            statement.setString(2, if (reachable) "ONLINE" else "OFFLINE")
            if (reachable && responseTimeMs != null) statement.setLong(3, responseTimeMs) else statement.setNull(3, java.sql.Types.INTEGER)
            statement.setLong(4, checkedAt)
            if (!reachable && errorMessage != null) statement.setString(5, errorMessage) else statement.setNull(5, java.sql.Types.VARCHAR)
            statement.setString(6, "LOCAL")
            statement.setString(7, executionKey)
            statement.executeUpdate()
        }
        if (inserted == 0) {
            connection.rollback()
            return null
        }
        val previous = loadState(connection)
        val update = resolveMonitoringState(previous, SERVER, checkedAt, reachable, responseTimeMs)
        if (update.insert) {
            connection.prepareStatement(
                """
                INSERT INTO server_monitoring_state
                    (serverId, lastStatus, lastCheckTime, consecutiveFailures, lastResponseTime)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, update.state.serverId)
                statement.setString(2, update.state.lastStatus.name)
                statement.setLong(3, update.state.lastCheckTime)
                statement.setInt(4, update.state.consecutiveFailures)
                bindNullableLong(statement, 5, update.state.lastResponseTime)
                statement.executeUpdate()
            }
        } else {
            connection.prepareStatement(
                """
                UPDATE server_monitoring_state
                SET lastStatus = ?, lastCheckTime = ?, consecutiveFailures = ?, lastResponseTime = ?
                WHERE serverId = ?
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, update.state.lastStatus.name)
                statement.setLong(2, update.state.lastCheckTime)
                statement.setInt(3, update.state.consecutiveFailures)
                bindNullableLong(statement, 4, update.state.lastResponseTime)
                statement.setString(5, update.state.serverId)
                statement.executeUpdate()
            }
        }
        connection.commit()
        return update
    }

    private fun statistics(connection: Connection, start: Long, end: Long): Stats {
        val aggregate = queryOne(
            connection,
            MonitoringSql.RESPONSE_AGGREGATE,
            mapOf("serverId" to SERVER, "windowStart" to start, "windowEnd" to end, "source" to "LOCAL"),
        )
        val duration = queryOne(
            connection,
            MonitoringSql.AVAILABILITY_DURATION,
            mapOf(
                "serverId" to SERVER,
                "windowStart" to start,
                "windowEnd" to end,
                "source" to "LOCAL",
                "maxGapMs" to MonitoringWindows.MAX_SAMPLE_GAP_MS,
            ),
        )
        val uptime = longOrNull(duration[0]) ?: 0L
        val downtime = longOrNull(duration[1]) ?: 0L
        val covered = uptime + downtime
        return Stats(
            average = (aggregate[0] as Number?)?.toDouble(),
            min = longOrNull(aggregate[1]),
            max = longOrNull(aggregate[2]),
            failures = (aggregate[3] as Number).toInt(),
            uptime = uptime,
            downtime = downtime,
            availability = if (covered > 0L) uptime * 100.0 / covered else null,
        )
    }

    private fun historySince(connection: Connection, since: Long): Int {
        val rows = queryOne(
            connection,
            "SELECT COUNT(*) FROM (${MonitoringSql.HISTORY_SINCE}) AS windowed",
            mapOf("serverId" to SERVER, "since" to since),
        )
        return (rows[0] as Number).toInt()
    }

    private fun limitedHistoryCount(connection: Connection, since: Long, limit: Long): Int {
        val (jdbc, values) = toJdbc(
            MonitoringSql.HISTORY_SINCE_LIMITED,
            mapOf("serverId" to SERVER, "since" to since, "limit" to limit),
        )
        return connection.prepareStatement(jdbc).use { statement ->
            values.forEachIndexed { index, value ->
                when (value) {
                    is Long -> statement.setLong(index + 1, value)
                    else -> statement.setString(index + 1, value as String)
                }
            }
            statement.executeQuery().use { rows ->
                var count = 0
                while (rows.next()) count++
                count
            }
        }
    }

    private fun deleteOlderThan(connection: Connection, threshold: Long): Int {
        val (jdbc, values) = toJdbc(MonitoringSql.DELETE_OLD_HISTORY, mapOf("threshold" to threshold))
        return connection.prepareStatement(jdbc).use { statement ->
            statement.setLong(1, values.single() as Long)
            statement.executeUpdate()
        }
    }

    private fun createSchema(connection: Connection) {
        val schemaFile = File("schemas/net.tools.sys.data.MonitoringDatabase/1.json")
        val schema = Json { ignoreUnknownKeys = true }.decodeFromString<SchemaFile>(schemaFile.readText())
        connection.createStatement().use { statement ->
            schema.database.entities.forEach { entity ->
                statement.execute(entity.createSql.replace("\${TABLE_NAME}", entity.tableName))
                entity.indices.forEach { index ->
                    statement.execute(index.createSql.replace("\${TABLE_NAME}", entity.tableName))
                }
            }
        }
    }

    private fun loadState(connection: Connection): ServerMonitoringStateEntity? {
        connection.prepareStatement(
            "SELECT id, serverId, lastStatus, lastCheckTime, consecutiveFailures, lastResponseTime FROM server_monitoring_state WHERE serverId = ?",
        ).use { statement ->
            statement.setString(1, SERVER)
            statement.executeQuery().use { rows ->
                if (!rows.next()) return null
                return ServerMonitoringStateEntity(
                    id = rows.getLong(1),
                    serverId = rows.getString(2),
                    lastStatus = MonitoringStateStatus.valueOf(rows.getString(3)),
                    lastCheckTime = rows.getLong(4),
                    consecutiveFailures = rows.getInt(5),
                    lastResponseTime = rows.getLong(6).takeUnless { rows.wasNull() },
                )
            }
        }
    }

    private fun historyCount(connection: Connection): Int =
        connection.createStatement().executeQuery("SELECT COUNT(*) FROM monitoring_history").use { rows ->
            rows.next()
            rows.getInt(1)
        }

    private fun responseTimeOfOnlyRow(connection: Connection): Long =
        connection.createStatement().executeQuery("SELECT responseTime FROM monitoring_history").use { rows ->
            rows.next()
            rows.getLong(1)
        }

    private fun errorAt(connection: Connection, checkedAt: Long): String? =
        connection.prepareStatement(
            "SELECT errorMessage FROM monitoring_history WHERE checkedAt = ?",
        ).use { statement ->
            statement.setLong(1, checkedAt)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getString(1)
            }
        }

    private fun responseTimeAt(connection: Connection, checkedAt: Long): Long? =
        connection.prepareStatement(
            "SELECT responseTime FROM monitoring_history WHERE checkedAt = ?",
        ).use { statement ->
            statement.setLong(1, checkedAt)
            statement.executeQuery().use { rows ->
                check(rows.next())
                rows.getLong(1).takeUnless { rows.wasNull() }
            }
        }

    private fun queryOne(connection: Connection, sql: String, params: Map<String, Any?>): List<Any?> {
        val (jdbc, values) = toJdbc(sql, params)
        connection.prepareStatement(jdbc).use { statement ->
            values.forEachIndexed { index, value ->
                when (value) {
                    is Long -> statement.setLong(index + 1, value)
                    is String -> statement.setString(index + 1, value)
                    else -> statement.setObject(index + 1, value)
                }
            }
            statement.executeQuery().use { rows ->
                assertTrue(rows.next())
                return List(rows.metaData.columnCount) { column ->
                    val value = rows.getObject(column + 1)
                    if (rows.wasNull()) null else value
                }
            }
        }
    }

    private fun toJdbc(sql: String, params: Map<String, Any?> = emptyMap()): Pair<String, List<Any?>> {
        val values = mutableListOf<Any?>()
        val jdbc = Regex(":([A-Za-z][A-Za-z0-9]*)").replace(sql) { match ->
            values += params.getValue(match.groupValues[1])
            "?"
        }
        return jdbc to values
    }

    private fun longOrNull(value: Any?): Long? = (value as Number?)?.toLong()

    private fun bindNullableLong(statement: java.sql.PreparedStatement, index: Int, value: Long?) {
        if (value == null) statement.setNull(index, java.sql.Types.INTEGER) else statement.setLong(index, value)
    }

    @Serializable
    private data class SchemaFile(val database: SchemaDatabase)

    @Serializable
    private data class SchemaDatabase(val entities: List<SchemaEntity>)

    @Serializable
    private data class SchemaEntity(
        val tableName: String,
        val createSql: String,
        val indices: List<SchemaIndex> = emptyList(),
    )

    @Serializable
    private data class SchemaIndex(val createSql: String)

    private data class Stats(
        val average: Double?,
        val min: Long?,
        val max: Long?,
        val failures: Int,
        val uptime: Long,
        val downtime: Long,
        val availability: Double?,
    )

    private companion object {
        const val SERVER = "server-1"
    }
}
