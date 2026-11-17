package net.tools.sys.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.tools.sys.model.ServerEntry
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.system.measureTimeMillis

data class CheckResult(
    val reachable: Boolean,
    val responseTimeMs: Long?,
    val detail: String?,
)

data class PortCheckResult(
    val port: Int,
    val result: CheckResult,
)

data class ServerCheckResult(
    val ping: CheckResult,
    val ports: List<PortCheckResult>,
)

class ServerConnectivityChecker {

    suspend fun check(server: ServerEntry, timeoutMs: Int = 5_000): ServerCheckResult = withContext(Dispatchers.IO) {
        val ping = runCatching { checkPing(server.host, timeoutMs) }.getOrElse { error ->
            CheckResult(reachable = false, responseTimeMs = null, detail = error.message)
        }
        val portResults = server.resolvedPorts().map { port ->
            PortCheckResult(
                port = port,
                result = runCatching { checkTcp(server.host, port, timeoutMs) }.getOrElse { error ->
                    CheckResult(reachable = false, responseTimeMs = null, detail = error.message)
                },
            )
        }
        ServerCheckResult(ping = ping, ports = portResults)
    }

    private fun checkTcp(host: String, port: Int, timeoutMs: Int): CheckResult {
        var elapsed: Long = 0
        val connected = runCatching {
            elapsed = measureTimeMillis {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(host, port), timeoutMs)
                }
            }
            true
        }.getOrDefault(false)
        return CheckResult(
            reachable = connected,
            responseTimeMs = if (connected) elapsed else null,
            detail = null,
        )
    }

    private fun checkPing(host: String, timeoutMs: Int): CheckResult {
        val pingMs = icmpPingMillis(host, timeoutMs)
        if (pingMs != null) {
            return CheckResult(reachable = true, responseTimeMs = pingMs, detail = null)
        }
        var elapsed: Long = 0
        val reachable = runCatching {
            elapsed = measureTimeMillis {
                val address = InetAddress.getByName(host)
                if (!address.isReachable(timeoutMs)) {
                    error("unreachable")
                }
            }
        }.isSuccess
        return CheckResult(
            reachable = reachable,
            responseTimeMs = if (reachable) elapsed else null,
            detail = null,
        )
    }

    private fun icmpPingMillis(host: String, timeoutMs: Int): Long? {
        val process = runCatching {
            ProcessBuilder("/system/bin/ping", "-c", "1", "-W", (timeoutMs / 1000).coerceAtLeast(1).toString(), host)
                .redirectErrorStream(true)
                .start()
        }.getOrNull() ?: return null

        val output = process.inputStream.bufferedReader().readText()
        process.waitFor()
        if (process.exitValue() != 0) return null

        val timeRegex = Regex("""time=(\d+(?:\.\d+)?)\s*ms""")
        val match = timeRegex.find(output) ?: return null
        return match.groupValues[1].toDoubleOrNull()?.toLong()
    }
}