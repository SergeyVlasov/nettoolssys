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
data class ServerEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val host: String,
    val ports: List<Int> = emptyList(),
    private val port: Int = 0,
) {
    fun resolvedPorts(): List<Int> =
        ports.ifEmpty { if (port in 1..65535) listOf(port) else emptyList() }

    fun forPersistence(): ServerEntry = copy(ports = resolvedPorts(), port = 0)
}

data class PortCheckStatus(
    val port: Int,
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