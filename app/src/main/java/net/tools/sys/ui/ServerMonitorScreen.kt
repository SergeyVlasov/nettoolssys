package net.tools.sys.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.tools.sys.R
import net.tools.sys.model.CheckStatus
import net.tools.sys.model.PortProtocol
import net.tools.sys.model.PortTarget
import net.tools.sys.model.ServerUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

private data class PortDraftRow(
    val id: String = UUID.randomUUID().toString(),
    val port: String = "",
    val protocol: PortProtocol = PortProtocol.TCP,
)

@Composable
fun ServerMonitorScreen(
    appSettingsViewModel: AppSettingsViewModel = viewModel(),
    viewModel: ServerMonitorViewModel = viewModel(),
    monitoringViewModel: ScheduledMonitoringViewModel = viewModel(),
) {
    val servers by viewModel.serverStates.collectAsStateWithLifecycle()
    val monitoringState by monitoringViewModel.uiState.collectAsStateWithLifecycle()
    val useDarkTheme by appSettingsViewModel.useDarkTheme.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }
    var showAppSettings by remember { mutableStateOf(false) }
    var showScheduledMonitoring by remember { mutableStateOf(false) }
    var editingServer by remember { mutableStateOf<ServerUiState?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val exportSuccess = stringResource(R.string.export_success)
    val exportFailed = stringResource(R.string.export_failed)
    val importSuccess = stringResource(R.string.import_success)
    val importFailed = stringResource(R.string.import_failed)

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val json = viewModel.exportServersJson()
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(json.toByteArray(Charsets.UTF_8))
                    } ?: error("openOutputStream")
                }
                snackbarHostState.showSnackbar(
                    if (result.isSuccess) exportSuccess else exportFailed,
                )
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            val json = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
            }.getOrNull()
            if (json.isNullOrBlank()) {
                scope.launch { snackbarHostState.showSnackbar(importFailed) }
            } else {
                viewModel.importServersJson(json) { ok ->
                    scope.launch {
                        snackbarHostState.showSnackbar(if (ok) importSuccess else importFailed)
                    }
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
            ) {
                IconButton(
                    onClick = { showAppSettings = true },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.settings),
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_server))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = viewModel::checkAllServers,
                    enabled = servers.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.NetworkCheck, contentDescription = null)
                    Text(
                        text = stringResource(R.string.check_all_servers),
                        modifier = Modifier.padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                IconButton(onClick = { showScheduledMonitoring = true }) {
                    Icon(
                        Icons.Default.HourglassEmpty,
                        contentDescription = stringResource(R.string.scheduled_monitoring),
                        tint = if (monitoringState.enabled) {
                            Color(0xFF2E7D32)
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }

            if (servers.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_server_list),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(servers, key = { it.entry.id }) { server ->
                        ServerCard(
                            server = server,
                            onCheck = { viewModel.checkServer(server.entry.id) },
                            onDelete = { viewModel.removeServer(server.entry.id) },
                            onSettings = { editingServer = server },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ServerFormDialog(
            title = stringResource(R.string.add_server),
            confirmLabel = stringResource(R.string.action_add),
            initialName = "",
            initialHost = "",
            initialPortTargets = emptyList(),
            onDismiss = { showAddDialog = false },
            onConfirm = { name, host, portTargets ->
                viewModel.addServer(name, host, portTargets)
                showAddDialog = false
            },
        )
    }

    editingServer?.let { server ->
        ServerFormDialog(
            title = stringResource(R.string.edit_server),
            confirmLabel = stringResource(R.string.action_save),
            initialName = server.entry.name,
            initialHost = server.entry.host,
            initialPortTargets = server.entry.resolvedPortTargets(),
            onDismiss = { editingServer = null },
            onConfirm = { name, host, portTargets ->
                viewModel.updateServer(server.entry.id, name, host, portTargets)
                editingServer = null
            },
        )
    }

    if (showScheduledMonitoring) {
        ScheduledMonitoringDialog(
            viewModel = monitoringViewModel,
            onDismiss = { showScheduledMonitoring = false },
        )
    }

    if (showAppSettings) {
        AppSettingsDialog(
            useDarkTheme = useDarkTheme,
            onDarkThemeChange = appSettingsViewModel::setUseDarkTheme,
            onExport = {
                showAppSettings = false
                val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
                exportLauncher.launch("servers-$stamp.json")
            },
            onImport = {
                showAppSettings = false
                importLauncher.launch(arrayOf("application/json", "text/plain", "application/*"))
            },
            onDismiss = { showAppSettings = false },
        )
    }
}

@Composable
private fun AppSettingsDialog(
    useDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    ThemeOptionButton(
                        label = stringResource(R.string.theme_light),
                        selected = !useDarkTheme,
                        onClick = { onDarkThemeChange(false) },
                        modifier = Modifier.weight(1f),
                    )
                    ThemeOptionButton(
                        label = stringResource(R.string.theme_dark),
                        selected = useDarkTheme,
                        onClick = { onDarkThemeChange(true) },
                        modifier = Modifier.weight(1f),
                    )
                }
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.export_servers))
                }
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.import_servers))
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
private fun ThemeOptionButton(
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

@Composable
private fun ServerCard(
    server: ServerUiState,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
    onSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val nameText = server.entry.name.ifBlank { "—" }
                Text(
                    text = nameText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val pingColor = pingResultColor(server.pingStatus)
                Text(
                    text = server.entry.host,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = pingColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                PingTimeInline(
                    responseTimeMs = server.pingResponseTimeMs,
                    color = pingColor,
                    modifier = Modifier.weight(0.6f),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                StatusBlock(server)
                Row {
                    IconButton(onClick = onCheck, enabled = !server.isChecking) {
                        if (server.isChecking) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.check_server),
                            )
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = stringResource(R.string.edit_server),
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete_server),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun pingResultColor(status: CheckStatus): Color = when (status) {
    CheckStatus.Online -> Color(0xFF2E7D32)
    CheckStatus.Offline -> MaterialTheme.colorScheme.error
    CheckStatus.Idle,
    CheckStatus.Checking,
    -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun PingTimeInline(
    responseTimeMs: Long?,
    color: Color,
    modifier: Modifier = Modifier,
) {
    if (responseTimeMs == null) return
    Text(
        text = stringResource(R.string.time_ms_only, responseTimeMs),
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        modifier = modifier,
    )
}

@Composable
private fun StatusBlock(server: ServerUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        server.portResults.forEach { port ->
            CheckRow(
                label = stringResource(
                    R.string.check_port_protocol_label,
                    port.port,
                    protocolLabel(port.protocol),
                ),
                status = port.status,
                responseTimeMs = port.responseTimeMs,
            )
        }
        server.message?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun CheckRow(
    label: String,
    status: CheckStatus,
    responseTimeMs: Long?,
) {
    val statusColor = checkStatusColor(status)
    val statusLabel = checkStatusLabel(status)
    val lineText = responseTimeMs?.let { ms ->
        stringResource(R.string.check_row_with_ms, label, statusLabel, ms)
    } ?: stringResource(R.string.check_row_format, label, statusLabel)
    Text(
        text = lineText,
        style = MaterialTheme.typography.labelLarge,
        color = statusColor,
    )
}

@Composable
private fun checkStatusColor(status: CheckStatus): Color = when (status) {
    CheckStatus.Online -> Color(0xFF2E7D32)
    CheckStatus.Offline -> MaterialTheme.colorScheme.error
    CheckStatus.Idle, CheckStatus.Checking -> MaterialTheme.colorScheme.onSurface
}

@Composable
private fun checkStatusLabel(status: CheckStatus): String = when (status) {
    CheckStatus.Idle -> stringResource(R.string.status_idle)
    CheckStatus.Checking -> stringResource(R.string.status_checking)
    CheckStatus.Online -> stringResource(R.string.status_online)
    CheckStatus.Offline -> stringResource(R.string.status_offline)
}

@Composable
private fun protocolLabel(protocol: PortProtocol): String = when (protocol) {
    PortProtocol.TCP -> stringResource(R.string.protocol_tcp)
    PortProtocol.UDP -> stringResource(R.string.protocol_udp)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServerFormDialog(
    title: String,
    confirmLabel: String,
    initialName: String,
    initialHost: String,
    initialPortTargets: List<PortTarget>,
    onDismiss: () -> Unit,
    onConfirm: (String, String, List<PortTarget>) -> Unit,
) {
    var name by remember(initialName, initialHost, initialPortTargets) { mutableStateOf(initialName) }
    var host by remember(initialName, initialHost, initialPortTargets) { mutableStateOf(initialHost) }
    var portRows by remember(initialName, initialHost, initialPortTargets) {
        mutableStateOf(portTargetsToDraftRows(initialPortTargets))
    }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.field_ip_or_host)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.field_ports),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp),
                )
                portRows.forEach { row ->
                    key(row.id) {
                        PortDraftRowEditor(
                            row = row,
                            showRemove = portRows.size > 1,
                            onRowChange = { updated ->
                                portRows = portRows.map { if (it.id == updated.id) updated else it }
                                showError = false
                            },
                            onRemove = {
                                portRows = portRows.filterNot { it.id == row.id }
                                showError = false
                            },
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(
                        onClick = {
                            portRows = portRows + PortDraftRow()
                            showError = false
                        },
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = stringResource(R.string.add_port),
                        )
                    }
                    Text(
                        text = stringResource(R.string.add_port),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (showError) {
                    Text(
                        text = stringResource(R.string.add_server_validation_error),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val portTargets = buildPortTargetsFromDrafts(portRows)
                    if (host.isBlank() || portTargets == null) {
                        showError = true
                    } else {
                        onConfirm(name, host, portTargets)
                    }
                },
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun portTargetsToDraftRows(targets: List<PortTarget>): List<PortDraftRow> {
    if (targets.isEmpty()) return listOf(PortDraftRow())
    return targets.map { target ->
        PortDraftRow(
            port = target.port.toString(),
            protocol = target.protocol,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortDraftRowEditor(
    row: PortDraftRow,
    showRemove: Boolean,
    onRowChange: (PortDraftRow) -> Unit,
    onRemove: () -> Unit,
) {
    var protocolExpanded by remember(row.id) { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = row.port,
            onValueChange = { value ->
                onRowChange(row.copy(port = value.filter { it.isDigit() }.take(5)))
            },
            label = { Text(stringResource(R.string.field_port)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        ExposedDropdownMenuBox(
            expanded = protocolExpanded,
            onExpandedChange = { protocolExpanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = protocolLabel(row.protocol),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.field_protocol)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded) },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
            )
            DropdownMenu(
                expanded = protocolExpanded,
                onDismissRequest = { protocolExpanded = false },
            ) {
                PortProtocol.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(protocolLabel(option)) },
                        onClick = {
                            onRowChange(row.copy(protocol = option))
                            protocolExpanded = false
                        },
                    )
                }
            }
        }
        if (showRemove) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = stringResource(R.string.remove_port),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

private fun buildPortTargetsFromDrafts(rows: List<PortDraftRow>): List<PortTarget>? {
    val filledRows = rows.filter { it.port.isNotBlank() }
    if (filledRows.isEmpty()) return emptyList()
    val targets = mutableListOf<PortTarget>()
    for (row in filledRows) {
        val port = row.port.toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        targets += PortTarget(port = port, protocol = row.protocol)
    }
    return targets.distinctBy { it.port to it.protocol }
}