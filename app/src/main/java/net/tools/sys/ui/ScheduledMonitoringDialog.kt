package net.tools.sys.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.tools.sys.R
import net.tools.sys.monitoring.AvailabilityTracker
import net.tools.sys.monitoring.MonitoringIntervals

@Composable
fun ScheduledMonitoringDialog(
    viewModel: ScheduledMonitoringViewModel,
    onDismiss: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirm by remember { mutableStateOf(false) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSelectError by remember { mutableStateOf(false) }
    var notificationsDenied by remember { mutableStateOf(false) }
    var enableAfterPermission by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!enableAfterPermission) return@rememberLauncherForActivityResult
        enableAfterPermission = false
        viewModel.enable()
        notificationsDenied = !granted
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.scheduled_monitoring)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.scheduled_monitoring_enable),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = stringResource(R.string.scheduled_monitoring_summary),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { enable ->
                            if (enable) {
                                if (state.servers.isEmpty() || state.selectedIds.isEmpty()) {
                                    showSelectError = true
                                } else {
                                    showSelectError = false
                                    showConfirm = true
                                }
                            } else {
                                showSelectError = false
                                viewModel.disable()
                            }
                        },
                    )
                }
                Text(
                    text = stringResource(R.string.scheduled_monitoring_interval),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MonitoringIntervals.choices.forEach { minutes ->
                        IntervalOptionButton(
                            label = stringResource(R.string.scheduled_monitoring_minutes, minutes),
                            selected = state.intervalMinutes == minutes,
                            onClick = { viewModel.setIntervalMinutes(minutes) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.scheduled_monitoring_servers),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (state.servers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.scheduled_monitoring_no_servers),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    state.servers.forEach { server ->
                        val checked = server.id in state.selectedIds
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(role = Role.Checkbox) {
                                    showSelectError = false
                                    viewModel.setServerSelected(server.id, !checked)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = server.name.ifBlank { "—" },
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = server.host,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
                if (showSelectError) {
                    Text(
                        text = stringResource(R.string.scheduled_monitoring_select_one),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (notificationsDenied) {
                    Text(
                        text = stringResource(R.string.scheduled_monitoring_notifications_denied),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = {
            TextButton(onClick = { showHistory = true }) {
                Text(stringResource(R.string.scheduled_monitoring_history))
            }
        },
    )

    if (showHistory) {
        MonitoringHistoryDialog(onDismiss = { showHistory = false })
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            title = { Text(stringResource(R.string.scheduled_monitoring_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.scheduled_monitoring_confirm_body,
                        state.intervalMinutes,
                        AvailabilityTracker.FAILURES_BEFORE_UNAVAILABLE,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showConfirm = false
                        if (needsNotificationPermission(context)) {
                            enableAfterPermission = true
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            viewModel.enable()
                        }
                    },
                ) {
                    Text(stringResource(R.string.scheduled_monitoring_enable_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirm = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun IntervalOptionButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun needsNotificationPermission(context: android.content.Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS,
    ) != PackageManager.PERMISSION_GRANTED
}
