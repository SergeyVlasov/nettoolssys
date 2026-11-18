package net.tools.sys.model

import kotlinx.serialization.Serializable

@Serializable
data class ServerListExport(
    val version: Int = 1,
    val servers: List<ServerEntry>,
)