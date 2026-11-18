package net.tools.sys.monitoring

import net.tools.sys.network.ServerCheckResult

/**
 * A server is available when ping succeeds or any configured port check succeeds.
 * [net.tools.sys.network.ServerConnectivityChecker] already turns timeouts and
 * connection errors into an unreachable result.
 */
fun ServerCheckResult.isAvailable(): Boolean {
    if (ping.reachable) return true
    return ports.any { it.result.reachable }
}

/** Response time of a successful check. Failed checks do not report a time. */
fun ServerCheckResult.monitoringResponseTimeMs(): Long? {
    if (!isAvailable()) return null
    if (ping.reachable) return ping.responseTimeMs
    return ports.asSequence()
        .filter { it.result.reachable }
        .mapNotNull { it.result.responseTimeMs }
        .minOrNull()
}

/**
 * Why a check failed. Timeouts and connection errors inside the checker are
 * reported as a failed ping or port. DNS and other exceptions keep their message.
 */
fun ServerCheckResult.monitoringErrorMessage(): String? {
    if (isAvailable()) return null
    val parts = mutableListOf<String>()
    if (!ping.reachable) {
        val detail = ping.detail?.takeIf { it.isNotBlank() }
        parts += if (detail != null) "Ping failed: $detail" else "Ping failed"
    }
    ports.filter { !it.result.reachable }.forEach { port ->
        val detail = port.result.detail?.takeIf { it.isNotBlank() }
        parts += if (detail != null) {
            "Port ${port.port} ${port.protocol} failed: $detail"
        } else {
            "Port ${port.port} ${port.protocol} failed"
        }
    }
    return parts.joinToString("; ").ifBlank { "Unreachable" }
}
