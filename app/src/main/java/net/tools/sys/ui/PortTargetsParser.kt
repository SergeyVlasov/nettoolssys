package net.tools.sys.ui

import net.tools.sys.model.PortProtocol
import net.tools.sys.model.PortTarget

/** For legacy free-text port lists (import/migration helpers). */
fun parsePortTargets(input: String): List<PortTarget>? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return emptyList()

    val parts = trimmed.split(',', ';', '\n')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

    val targets = parts.mapNotNull { part -> parseSinglePortTarget(part) }
    if (targets.size != parts.size) return null
    return targets.distinctBy { it.port to it.protocol }
}

private fun parseSinglePortTarget(part: String): PortTarget? {
    val slashIndex = part.indexOf('/')
    val (portPart, protocolPart) = if (slashIndex >= 0) {
        part.substring(0, slashIndex).trim() to part.substring(slashIndex + 1).trim()
    } else {
        part.trim() to ""
    }
    val port = portPart.toIntOrNull() ?: return null
    if (port !in 1..65535) return null

    val protocol = when (protocolPart.uppercase()) {
        "", "TCP" -> PortProtocol.TCP
        "UDP" -> PortProtocol.UDP
        else -> return null
    }
    return PortTarget(port = port, protocol = protocol)
}