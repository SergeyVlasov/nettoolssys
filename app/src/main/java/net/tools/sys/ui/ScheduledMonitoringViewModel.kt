package net.tools.sys.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.tools.sys.data.MonitoringSettingsRepository
import net.tools.sys.data.ServerRepository
import net.tools.sys.model.ServerEntry
import net.tools.sys.monitoring.MonitoringIntervals
import net.tools.sys.work.MonitoringScheduler

data class ScheduledMonitoringUiState(
    val enabled: Boolean = false,
    val intervalMinutes: Int = MonitoringIntervals.DEFAULT_MINUTES,
    val selectedIds: Set<String> = emptySet(),
    val servers: List<ServerEntry> = emptyList(),
)

class ScheduledMonitoringViewModel(application: Application) : AndroidViewModel(application) {

    private val monitoringRepository = MonitoringSettingsRepository(application)
    private val serverRepository = ServerRepository(application)
    private val updates = Mutex()

    val uiState: StateFlow<ScheduledMonitoringUiState> = combine(
        monitoringRepository.settings,
        serverRepository.servers,
    ) { settings, servers ->
        ScheduledMonitoringUiState(
            enabled = settings.enabled,
            intervalMinutes = settings.intervalMinutes,
            selectedIds = settings.selectedIds,
            servers = servers,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ScheduledMonitoringUiState(),
    )

    init {
        viewModelScope.launch {
            serverRepository.servers.collect { servers ->
                updates.withLock {
                    val existingIds = servers.map { it.id }.toSet()
                    val selected = monitoringRepository.currentSettings().selectedIds
                    val pruned = selected.intersect(existingIds)
                    if (pruned != selected) {
                        monitoringRepository.setSelectedIds(pruned)
                    }
                }
            }
        }
        viewModelScope.launch {
            runCatching {
                val settings = monitoringRepository.currentSettings()
                MonitoringScheduler.sync(
                    context = getApplication(),
                    enabled = settings.enabled,
                    intervalMinutes = settings.intervalMinutes,
                )
            }
        }
    }

    fun setIntervalMinutes(minutes: Int) {
        launchUpdate {
            monitoringRepository.setIntervalMinutes(minutes)
            val settings = monitoringRepository.currentSettings()
            if (settings.enabled) {
                MonitoringScheduler.enqueue(getApplication(), settings.intervalMinutes)
            }
        }
    }

    fun setServerSelected(id: String, selected: Boolean) {
        launchUpdate {
            val current = monitoringRepository.currentSettings().selectedIds
            val updated = if (selected) current + id else current - id
            monitoringRepository.setSelectedIds(updated)
        }
    }

    fun enable() {
        launchUpdate {
            val settings = monitoringRepository.currentSettings()
            if (settings.selectedIds.isEmpty()) return@launchUpdate
            monitoringRepository.setEnabled(true)
            val scheduled = runCatching {
                MonitoringScheduler.enqueue(getApplication(), settings.intervalMinutes)
            }
            if (scheduled.isFailure) {
                monitoringRepository.setEnabled(false)
            }
        }
    }

    fun disable() {
        launchUpdate {
            monitoringRepository.setEnabled(false)
            MonitoringScheduler.cancel(getApplication())
        }
    }

    private fun launchUpdate(block: suspend () -> Unit) {
        viewModelScope.launch {
            updates.withLock { block() }
        }
    }
}
