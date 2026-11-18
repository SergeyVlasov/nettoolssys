package net.tools.sys.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.tools.sys.data.ServerRepository
import net.tools.sys.model.CheckStatus
import net.tools.sys.model.PortCheckStatus
import net.tools.sys.model.PortTarget
import net.tools.sys.model.ServerEntry
import net.tools.sys.model.ServerUiState
import net.tools.sys.network.ServerConnectivityChecker

class ServerMonitorViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ServerRepository(application)
    private val checker = ServerConnectivityChecker()

    private val checkStates = MutableStateFlow<Map<String, ServerUiState>>(emptyMap())

    val serverStates: StateFlow<List<ServerUiState>> = combine(
        repository.servers,
        checkStates,
    ) { entries, states ->
        entries.map { entry ->
            val mergedEntry = entry.forPersistence()
            val prior = states[mergedEntry.id]
            prior?.copy(entry = mergedEntry) ?: ServerUiState(
                entry = mergedEntry,
                portResults = mergedEntry.resolvedPortTargets().map { target ->
                    PortCheckStatus(port = target.port, protocol = target.protocol)
                },
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    suspend fun exportServersJson(): String = repository.exportToJson()

    fun importServersJson(raw: String, onResult: (Boolean) -> Unit) {
        val parsed = repository.parseImportJson(raw)
        viewModelScope.launch {
            parsed.fold(
                onSuccess = { servers ->
                    repository.replaceAllServers(servers)
                    checkStates.value = emptyMap()
                    onResult(true)
                },
                onFailure = {
                    onResult(false)
                },
            )
        }
    }

    fun addServer(name: String, host: String, portTargets: List<PortTarget>) {
        val trimmed = host.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            repository.addServer(
                ServerEntry(
                    name = name.trim(),
                    host = trimmed,
                    portTargets = portTargets,
                ),
            )
        }
    }

    fun updateServer(id: String, name: String, host: String, portTargets: List<PortTarget>) {
        val trimmedHost = host.trim()
        if (trimmedHost.isEmpty()) return
        viewModelScope.launch {
            repository.updateServer(id, name, trimmedHost, portTargets)
            checkStates.value = checkStates.value - id
        }
    }

    fun removeServer(id: String) {
        viewModelScope.launch {
            repository.removeServer(id)
            checkStates.value = checkStates.value - id
        }
    }

    fun checkServer(id: String) {
        val target = serverStates.value.find { it.entry.id == id }?.entry ?: return
        viewModelScope.launch {
            setChecking(id, target)
            val result = checker.check(target)
            applyResult(id, target, result)
        }
    }

    fun checkAllServers() {
        val entries = serverStates.value.map { it.entry }
        entries.forEach { entry ->
            viewModelScope.launch {
                setChecking(entry.id, entry)
                val result = checker.check(entry)
                applyResult(entry.id, entry, result)
            }
        }
    }

    private fun setChecking(id: String, entry: ServerEntry) {
        val portChecking = entry.resolvedPortTargets().map { target ->
            PortCheckStatus(port = target.port, protocol = target.protocol, status = CheckStatus.Checking)
        }
        checkStates.value = checkStates.value + (
            id to ServerUiState(
                entry = entry,
                pingStatus = CheckStatus.Checking,
                portResults = portChecking,
            )
            )
    }

    private fun applyResult(
        id: String,
        entry: ServerEntry,
        result: net.tools.sys.network.ServerCheckResult,
    ) {
        val portResults = result.ports.map { portResult ->
            PortCheckStatus(
                port = portResult.port,
                protocol = portResult.protocol,
                status = if (portResult.result.reachable) CheckStatus.Online else CheckStatus.Offline,
                responseTimeMs = portResult.result.responseTimeMs,
            )
        }
        checkStates.value = checkStates.value + (
            id to ServerUiState(
                entry = entry,
                pingStatus = if (result.ping.reachable) CheckStatus.Online else CheckStatus.Offline,
                pingResponseTimeMs = result.ping.responseTimeMs,
                portResults = portResults,
                message = result.ping.detail,
            )
            )
    }
}