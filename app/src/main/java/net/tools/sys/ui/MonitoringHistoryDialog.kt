package net.tools.sys.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

@Composable
fun MonitoringHistoryDialog(
    onDismiss: () -> Unit,
    viewModel: MonitoringHistoryViewModel = viewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.servers.forEach { server ->
                        FilterChip(
                            selected = server.id == state.serverId,
                            onClick = { viewModel.selectServer(server.id) },
                            label = {
                                Text(
                                    text = server.displayName(),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HistoryPeriod.entries.forEach { period ->
                        FilterChip(
                            selected = period == state.period,
                            onClick = { viewModel.selectPeriod(period) },
                            label = { Text(stringResource(period.labelRes), maxLines = 1) },
                        )
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
                color = if (online) OnlineColor else MaterialTheme.colorScheme.error,
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
