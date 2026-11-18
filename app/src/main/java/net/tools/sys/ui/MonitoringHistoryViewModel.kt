package net.tools.sys.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.tools.sys.R
import net.tools.sys.data.MonitoringHistoryEntity
import net.tools.sys.data.MonitoringRepository
import net.tools.sys.data.MonitoringStatistics
import net.tools.sys.data.MonitoringWindows
import net.tools.sys.data.ServerRepository
import net.tools.sys.model.ServerEntry

enum class HistoryPeriod(val windowMs: Long, val labelRes: Int) {
    DAY(MonitoringWindows.DAY_MS, R.string.history_period_24h),
    WEEK(MonitoringWindows.DAYS_7_MS, R.string.history_period_7d),
    MONTH(MonitoringWindows.DAYS_30_MS, R.string.history_period_30d),
}

data class MonitoringHistoryUiState(
    val servers: List<ServerEntry> = emptyList(),
    val serverId: String? = null,
    val period: HistoryPeriod = HistoryPeriod.DAY,
    val loading: Boolean = true,
    val entries: List<MonitoringHistoryEntity> = emptyList(),
    val statistics: MonitoringStatistics? = null,
    val failed: Boolean = false,
)

class MonitoringHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val history = MonitoringRepository.create(application)
    private val serverRepository = ServerRepository(application)

    private val _uiState = MutableStateFlow(MonitoringHistoryUiState())
    val uiState: StateFlow<MonitoringHistoryUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    /** Re-reads the server list and the stored history. Called whenever the dialog opens. */
    fun refresh() {
        viewModelScope.launch {
            val servers = serverRepository.currentServers()
            _uiState.update { state ->
                val keep = state.serverId?.takeIf { id -> servers.any { it.id == id } }
                state.copy(servers = servers, serverId = keep ?: servers.firstOrNull()?.id)
            }
            reload()
        }
    }

    fun selectServer(serverId: String) {
        if (_uiState.value.serverId == serverId) return
        _uiState.update { it.copy(serverId = serverId) }
        reload()
    }

    fun selectPeriod(period: HistoryPeriod) {
        if (_uiState.value.period == period) return
        _uiState.update { it.copy(period = period) }
        reload()
    }

    private fun reload() {
        loadJob?.cancel()
        val state = _uiState.value
        val serverId = state.serverId
        if (serverId == null) {
            _uiState.update {
                it.copy(loading = false, entries = emptyList(), statistics = null, failed = false)
            }
            return
        }
        _uiState.update { it.copy(loading = true, failed = false) }
        loadJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            val since = now - state.period.windowMs
            try {
                val entries = history.recentHistory(serverId, since, MAX_ROWS)
                val statistics = history.statistics(serverId, since, now + 1)
                _uiState.update {
                    if (it.serverId != serverId || it.period != state.period) it
                    else it.copy(loading = false, entries = entries, statistics = statistics)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update {
                    if (it.serverId != serverId || it.period != state.period) it
                    else it.copy(loading = false, entries = emptyList(), statistics = null, failed = true)
                }
            }
        }
    }

    companion object {
        const val MAX_ROWS = 200
    }
}
