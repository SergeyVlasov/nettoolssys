package net.tools.sys.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.tools.sys.R
import net.tools.sys.model.CheckStatus
import net.tools.sys.model.ServerUiState

@Composable
fun ServerMonitorScreen(
    viewModel: ServerMonitorViewModel = viewModel(),
) {
    val servers by viewModel.serverStates.collectAsStateWithLifecycle()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
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
            Button(
                onClick = viewModel::checkAllServers,
                enabled = servers.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
            ) {
                Icon(Icons.Default.NetworkCheck, contentDescription = null)
                Text(
                    text = stringResource(R.string.check_all_servers),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (servers.isEmpty()) {
                Text(
                    text = stringResource(R.string.empty_server_list),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 24.dp),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(servers, key = { it.entry.id }) { server ->
                        ServerCard(
                            server = server,
                            onCheck = { viewModel.checkServer(server.entry.id) },
                            onDelete = { viewModel.removeServer(server.entry.id) },
                            onNameCommitted = { viewModel.updateServerName(server.entry.id, it) },
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddServerDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, host, ports ->
                viewModel.addServer(name, host, ports)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun ServerCard(
    server: ServerUiState,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
    onNameCommitted: (String) -> Unit,
) {
    var nameText by remember(server.entry.id) { mutableStateOf(server.entry.name) }
    LaunchedEffect(server.entry.name) {
        nameText = server.entry.name
    }

    val ports = server.entry.resolvedPorts()
    val portsLabel = if (ports.isEmpty()) {
        stringResource(R.string.ports_none)
    } else {
        stringResource(R.string.ports_list_format, ports.joinToString(", "))
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = nameText,
                onValueChange = { nameText = it },
                label = { Text(stringResource(R.string.field_server_name)) },
                placeholder = { Text(server.entry.host) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focus ->
                        if (!focus.isFocused && nameText.trim() != server.entry.name) {
                            onNameCommitted(nameText)
                        }
                    },
            )
            Text(
                text = server.entry.host,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = portsLabel,
                style = MaterialTheme.typography.bodyMedium,
            )
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
private fun StatusBlock(server: ServerUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        CheckRow(
            label = stringResource(R.string.check_ping_label),
            status = server.pingStatus,
            responseTimeMs = server.pingResponseTimeMs,
        )
        server.portResults.forEach { port ->
            CheckRow(
                label = stringResource(R.string.check_port_label, port.port),
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
    Column {
        Text(
            text = stringResource(
                R.string.check_row_format,
                label,
                checkStatusLabel(status),
            ),
            style = MaterialTheme.typography.labelLarge,
            color = statusColor,
        )
        responseTimeMs?.let { ms ->
            Text(
                text = stringResource(R.string.ping_response_time, ms),
                style = MaterialTheme.typography.bodySmall,
                color = statusColor,
            )
        }
    }
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
private fun AddServerDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, List<Int>) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var host by remember { mutableStateOf("") }
    var portsText by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_server)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_server_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
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
                OutlinedTextField(
                    value = portsText,
                    onValueChange = {
                        portsText = it
                        showError = false
                    },
                    label = { Text(stringResource(R.string.field_ports)) },
                    placeholder = { Text(stringResource(R.string.field_ports_hint)) },
                    supportingText = { Text(stringResource(R.string.field_ports_supporting)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    val ports = parsePortList(portsText)
                    if (host.isBlank() || ports == null) {
                        showError = true
                    } else {
                        onConfirm(name, host, ports)
                    }
                },
            ) {
                Text(stringResource(R.string.action_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

private fun parsePortList(input: String): List<Int>? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return emptyList()
    val parts = trimmed.split(',', ';', ' ', '\n', '\t')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    val ports = parts.mapNotNull { it.toIntOrNull() }
    if (ports.size != parts.size) return null
    if (ports.any { it !in 1..65535 }) return null
    return ports.distinct().sorted()
}