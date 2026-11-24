package net.tools.sys.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.tools.sys.R
import net.tools.sys.data.MonitoringCheckStatus
import net.tools.sys.data.MonitoringHistoryEntity
import net.tools.sys.data.MonitoringStatistics
import net.tools.sys.model.ServerEntry
import java.text.DateFormat
import java.util.Date

private val OnlineColor = Color(0xFF2E7D32)
private val OfflineColor = Color(0xFFFF0000)

@Composable
fun MonitoringHistoryDialog(
    onDismiss: () -> Unit,
    viewModel: MonitoringHistoryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    var serverExpanded by remember { mutableStateOf(false) }
    var periodExpanded by remember { mutableStateOf(false) }
    val selectedServer = state.servers.find { it.id == state.serverId }
    val serverLabel = selectedServer?.displayName() ?: stringResource(R.string.history_server_placeholder)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_title)) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.servers.isEmpty()) {
                    if (state.loading) {
                        CircularProgressIndicator()
                    } else {
                        Text(
                            text = stringResource(R.string.scheduled_monitoring_no_servers),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    return@Column
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.history_server),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    FormDropdownField(
                        value = serverLabel,
                        expanded = serverExpanded,
                        onExpandedChange = { serverExpanded = it },
                        modifier = Modifier.weight(1.4f),
                    ) {
                        state.servers.forEach { server ->
                            DropdownMenuTextItem(
                                label = server.displayName(),
                                onClick = {
                                    viewModel.selectServer(server.id)
                                    serverExpanded = false
                                },
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.history_period),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    FormDropdownField(
                        value = stringResource(state.period.labelRes),
                        expanded = periodExpanded,
                        onExpandedChange = { periodExpanded = it },
                        modifier = Modifier.weight(1.4f),
                    ) {
                        HistoryPeriod.entries.forEach { period ->
                            DropdownMenuTextItem(
                                label = stringResource(period.labelRes),
                                onClick = {
                                    viewModel.selectPeriod(period)
                                    periodExpanded = false
                                },
                            )
                        }
                    }
                }

                state.statistics?.let { StatisticsSummary(it) }

                when {
                    state.loading -> CircularProgressIndicator()
                    state.failed -> Text(
                        text = stringResource(R.string.history_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    state.entries.isEmpty() -> Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    else -> {
                        if (state.entries.size >= MonitoringHistoryViewModel.MAX_ROWS) {
                            Text(
                                text = stringResource(
                                    R.string.history_latest_only,
                                    MonitoringHistoryViewModel.MAX_ROWS,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                            items(state.entries, key = { it.id }) { entry ->
                                HistoryRow(entry)
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun StatisticsSummary(statistics: MonitoringStatistics) {
    val none = stringResource(R.string.history_value_none)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = statistics.availabilityPercent
                ?.let { stringResource(R.string.history_availability, it) }
                ?: stringResource(R.string.history_availability_none),
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = stringResource(
                R.string.history_response_summary,
                statistics.averageResponseTimeMs?.let { Math.round(it).toString() } ?: none,
                statistics.minResponseTimeMs?.toString() ?: none,
                statistics.maxResponseTimeMs?.toString() ?: none,
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(
                R.string.history_uptime_summary,
                formatDuration(statistics.uptimeMs),
                formatDuration(statistics.downtimeMs),
            ),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = stringResource(R.string.history_failures, statistics.failureCount),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun HistoryRow(entry: MonitoringHistoryEntity) {
    val online = entry.status == MonitoringCheckStatus.ONLINE
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(
                    if (online) R.string.status_available else R.string.status_unavailable,
                ),
                color = if (online) OnlineColor else OfflineColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = DateFormat
                    .getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM)
                    .format(Date(entry.checkedAt)),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        val detail = when {
            online && entry.responseTime != null ->
                stringResource(R.string.history_response_ms, entry.responseTime)
            !online && !entry.errorMessage.isNullOrBlank() -> entry.errorMessage
            else -> null
        }
        if (detail != null) {
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun formatDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    return stringResource(R.string.history_duration, totalMinutes / 60, totalMinutes % 60)
}

private fun ServerEntry.displayName(): String = name.trim().ifBlank { host }
