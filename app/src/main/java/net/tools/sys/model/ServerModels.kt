package net.tools.sys.model

import kotlinx.serialization.Serializable
import java.util.UUID

enum class CheckStatus {
    Idle,
    Checking,
    Online,
    Offline,
}

@Serializable
enum class PortProtocol {
    TCP,
    UDP,
}

@Serializable
data class PortTarget(
    val port: Int,
    val protocol: PortProtocol = PortProtocol.TCP,
)

@Serializable
data class ServerEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String,
    val portTargets: List<PortTarget> = emptyList(),
    val ports: List<Int> = emptyList(),
    private val port: Int = 0,
) {
    fun resolvedPortTargets(): List<PortTarget> {
        if (portTargets.isNotEmpty()) return portTargets
        val legacyPorts = ports.ifEmpty { if (port in 1..65535) listOf(port) else emptyList() }
        return legacyPorts.map { PortTarget(port = it, protocol = PortProtocol.TCP) }
    }

    fun forPersistence(): ServerEntry = copy(
        portTargets = resolvedPortTargets(),
        ports = emptyList(),
        port = 0,
    )
}

data class PortCheckStatus(
    val port: Int,
    val protocol: PortProtocol,
    val status: CheckStatus = CheckStatus.Idle,
    val responseTimeMs: Long? = null,
)

data class ServerUiState(
    val entry: ServerEntry,
    val pingStatus: CheckStatus = CheckStatus.Idle,
    val pingResponseTimeMs: Long? = null,
    val portResults: List<PortCheckStatus> = emptyList(),
    val message: String? = null,
) {
    val isChecking: Boolean
        get() = pingStatus == CheckStatus.Checking ||
            portResults.any { it.status == CheckStatus.Checking }
}