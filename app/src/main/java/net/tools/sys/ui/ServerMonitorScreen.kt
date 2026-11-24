package net.tools.sys.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.tools.sys.R
import net.tools.sys.model.CheckStatus
import net.tools.sys.model.PortProtocol
import net.tools.sys.model.PortTarget
import net.tools.sys.locale.AppLanguages
import net.tools.sys.model.ServerUiState
import net.tools.sys.model.defaultPort
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.launch

private val ProtocolDropdownWidth = 112.dp

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
    val appLanguageTag by appSettingsViewModel.appLanguageTag.collectAsStateWithLifecycle()
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
    val haptics = LocalHapticFeedback.current

    // Order of ids while dragging and until the reordered list comes back from storage.
    var localOrder by remember { mutableStateOf<List<String>?>(null) }
    val latestServers by rememberUpdatedState(servers)
    val serverIds = remember(servers) { servers.map { it.entry.id } }
    val displayedServers = remember(servers, localOrder) {
        val order = localOrder ?: return@remember servers
        val byId = servers.associateBy { it.entry.id }
        order.mapNotNull(byId::get) + servers.filter { it.entry.id !in order }
    }
    val listState = rememberLazyListState()
    val reorderState = rememberReorderableListState(
        listState = listState,
        onMove = { fromKey, toKey ->
            val ids = (localOrder ?: latestServers.map { it.entry.id }).toMutableList()
            val from = ids.indexOf(fromKey)
            val to = ids.indexOf(toKey)
            if (from >= 0 && to >= 0) {
                ids.add(to, ids.removeAt(from))
                localOrder = ids
            }
        },
        onDragFinished = {
            val order = localOrder
            if (order == null || order == latestServers.map { it.entry.id }) {
                localOrder = null
            } else {
                viewModel.reorderServers(order)
            }
        },
    )
    LaunchedEffect(serverIds) {
        if (reorderState.draggedKey == null) localOrder = null
    }

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
                    state = listState,
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(displayedServers, key = { it.entry.id }) { server ->
                        val id = server.entry.id
                        val isDragging = reorderState.draggedKey == id
                        val liftScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.03f else 1f,
                            label = "liftScale",
                        )
                        val nudge = remember { Animatable(0f) }
                        LaunchedEffect(isDragging) {
                            if (isDragging) {
                                nudge.animateTo(
                                    targetValue = 0f,
                                    animationSpec = keyframes {
                                        durationMillis = 360
                                        14f at 70
                                        -10f at 160
                                        6f at 250
                                        0f at 360
                                    },
                                )
                            } else {
                                nudge.snapTo(0f)
                            }
                        }
                        ServerCard(
                            server = server,
                            isSelected = isDragging,
                            onCheck = { viewModel.checkServer(id) },
                            onDelete = { viewModel.removeServer(id) },
                            onSettings = { editingServer = server },
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .animateItem(placementSpec = if (isDragging) null else spring())
                                .graphicsLayer {
                                    translationX = nudge.value
                                    translationY = if (isDragging) reorderState.draggedItemOffset else 0f
                                    scaleX = liftScale
                                    scaleY = liftScale
                                }
                                .pointerInput(id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            reorderState.onDragStart(id)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            reorderState.onDrag(dragAmount.y)
                                        },
                                        onDragEnd = reorderState::onDragEnd,
                                        onDragCancel = reorderState::onDragEnd,
                                    )
                                },
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
            appLanguageTag = appLanguageTag,
            onLanguageChange = appSettingsViewModel::setAppLanguage,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppSettingsDialog(
    useDarkTheme: Boolean,
    onDarkThemeChange: (Boolean) -> Unit,
    appLanguageTag: String,
    onLanguageChange: (String) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var themeExpanded by remember { mutableStateOf(false) }
    var languageExpanded by remember { mutableStateOf(false) }
    val selectedThemeLabel = if (useDarkTheme) {
        stringResource(R.string.theme_dark)
    } else {
        stringResource(R.string.theme_light)
    }
    val selectedLanguage = AppLanguages.options.firstOrNull { it.tag == appLanguageTag }
        ?: AppLanguages.options.first()
    val selectedLanguageLabel = stringResource(selectedLanguage.labelRes)

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
                    ExposedDropdownMenuBox(
                        expanded = themeExpanded,
                        onExpandedChange = { themeExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        OutlinedTextField(
                            value = selectedThemeLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            singleLine = true,
                        )
                        DropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.theme_light)) },
                                onClick = {
                                    onDarkThemeChange(false)
                                    themeExpanded = false
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.theme_dark)) },
                                onClick = {
                                    onDarkThemeChange(true)
                                    themeExpanded = false
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
                        text = stringResource(R.string.settings_language),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    FormDropdownField(
                        value = selectedLanguageLabel,
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = it },
                        modifier = Modifier.weight(1f),
                    ) {
                        AppLanguages.options.forEach { option ->
                            DropdownMenuTextItem(
                                label = stringResource(option.labelRes),
                                onClick = {
                                    onLanguageChange(option.tag)
                                    languageExpanded = false
                                },
                            )
                        }
                    }
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
private fun ServerCard(
    server: ServerUiState,
    isSelected: Boolean,
    onCheck: () -> Unit,
    onDelete: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 1.dp,
        label = "cardElevation",
    )
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
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
    CheckStatus.Offline -> Color(0xFFFF0000)
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
    CheckStatus.Offline -> Color(0xFFFF0000)
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
    PortProtocol.HTTP -> stringResource(R.string.protocol_http)
    PortProtocol.HTTPS -> stringResource(R.string.protocol_https)
    PortProtocol.SSH -> stringResource(R.string.protocol_ssh)
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
    val fieldTextStyle = formInputTextStyle()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = {
                        Text(
                            text = stringResource(R.string.field_server_name),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    textStyle = fieldTextStyle,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = host,
                    onValueChange = {
                        host = it
                        showError = false
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.field_ip_or_host),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    textStyle = fieldTextStyle,
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

@Composable
private fun formInputTextStyle(): TextStyle = MaterialTheme.typography.bodyLarge.copy(
    platformStyle = PlatformTextStyle(includeFontPadding = true),
    lineHeightStyle = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
        mode = LineHeightStyle.Mode.Minimum,
    ),
)

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
            label = {
                Text(
                    text = stringResource(R.string.field_port),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            textStyle = formInputTextStyle(),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FormDropdownField(
            value = protocolLabel(row.protocol),
            expanded = protocolExpanded,
            onExpandedChange = { protocolExpanded = it },
            label = {
                Text(
                    text = stringResource(R.string.field_protocol),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier.width(ProtocolDropdownWidth),
        ) {
            PortProtocol.entries.forEach { option ->
                DropdownMenuTextItem(
                    label = protocolLabel(option),
                    onClick = {
                        val currentIsDefault = row.port.isBlank() ||
                            PortProtocol.entries.any { it.defaultPort?.toString() == row.port }
                        val newPort = option.defaultPort
                            ?.takeIf { currentIsDefault }
                            ?.toString()
                            ?: row.port
                        onRowChange(row.copy(port = newPort, protocol = option))
                        protocolExpanded = false
                    },
                )
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