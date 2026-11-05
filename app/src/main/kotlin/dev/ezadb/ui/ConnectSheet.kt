package dev.ezadb.ui

import android.hardware.usb.UsbDevice
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ezadb.R
import dev.ezadb.core.DiscoveryType
import dev.ezadb.ui.components.ExpressiveButton
import dev.ezadb.ui.components.ExpressiveButtonVariant
import dev.ezadb.ui.components.ExpressiveToggleChip
import dev.ezadb.ui.components.SectionHeader
import dev.ezadb.ui.theme.EzMotionSpecs
import dev.ezadb.ui.viewmodel.EzUiState
import dev.ezadb.ui.viewmodel.EzViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectSheet(vm: EzViewModel, state: EzUiState, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableIntStateOf(0) }
    var host by remember { mutableStateOf("192.168.1.") }
    var port by remember { mutableStateOf("5555") }
    var pairHost by remember { mutableStateOf("") }
    var pairPort by remember { mutableStateOf("") }
    var pairCode by remember { mutableStateOf("") }
    var usbDevices by remember { mutableStateOf<List<UsbDevice>>(emptyList()) }

    LaunchedEffect(Unit) { usbDevices = vm.refreshUsbDevices() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.connect_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExpressiveToggleChip(selected = tab == 0, onClick = { tab = 0 }, label = stringResource(R.string.wifi_tab), icon = Icons.Filled.Wifi, modifier = Modifier.weight(1f))
                ExpressiveToggleChip(selected = tab == 1, onClick = { tab = 1 }, label = stringResource(R.string.usb_tab), icon = Icons.Filled.Usb, modifier = Modifier.weight(1f))
                ExpressiveToggleChip(selected = tab == 2, onClick = { tab = 2 }, label = stringResource(R.string.pair_tab), icon = Icons.Filled.Key, modifier = Modifier.weight(1f))
            }
            AnimatedContent(
                targetState = tab,
                transitionSpec = {
                    val direction = if (targetState > initialState) 1 else -1
                    (fadeIn(EzMotionSpecs.fade()) + slideInHorizontally(EzMotionSpecs.spatial()) { it / 4 * direction })
                        .togetherWith(fadeOut(EzMotionSpecs.fade()) + slideOutHorizontally(EzMotionSpecs.spatial()) { -it / 4 * direction })
                },
                label = "connectTab",
            ) { current ->
                when (current) {
                    0 -> WifiTab(vm, state, host, port, onHost = { host = it }, onPort = { port = it }, onPair = { h, p -> pairHost = h; pairPort = p; tab = 2 }, onDismiss = onDismiss)
                    1 -> UsbTab(vm, usbDevices, onRefresh = { usbDevices = vm.refreshUsbDevices() }, onDismiss = onDismiss)
                    else -> PairTab(vm, pairHost, pairPort, pairCode, onHost = { pairHost = it }, onPort = { pairPort = it }, onCode = { pairCode = it })
                }
            }
        }
    }
}

@Composable
private fun WifiTab(
    vm: EzViewModel,
    state: EzUiState,
    host: String,
    port: String,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onPair: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.wireless_debug_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(host, onHost, label = { Text(stringResource(R.string.ip_address)) }, modifier = Modifier.weight(2f), singleLine = true, shape = MaterialTheme.shapes.large)
            OutlinedTextField(port, onPort, label = { Text(stringResource(R.string.port)) }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
        }
        ExpressiveButton(
            onClick = {
                val p = port.toIntOrNull() ?: 5555
                vm.connectTcp(host.trim(), p)
                onDismiss()
            },
            text = stringResource(R.string.connect_button),
            modifier = Modifier.fillMaxWidth(),
            icon = Icons.Filled.Cable,
        )

        if (state.discovered.isNotEmpty()) {
            SectionHeader(stringResource(R.string.discovered))
            state.discovered.forEach { endpoint ->
                ListItem(
                    headlineContent = { Text(endpoint.name) },
                    supportingContent = { Text("${endpoint.host}:${endpoint.port} · ${if (endpoint.type == DiscoveryType.PAIR) stringResource(R.string.discovered_pair) else stringResource(R.string.discovered_connect)}") },
                    trailingContent = {
                        ExpressiveButton(
                            onClick = {
                                if (endpoint.type == DiscoveryType.PAIR) onPair(endpoint.host, endpoint.port.toString())
                                else {
                                    vm.connectTcp(endpoint.host, endpoint.port)
                                    onDismiss()
                                }
                            },
                            text = stringResource(if (endpoint.type == DiscoveryType.PAIR) R.string.pair_action else R.string.connect_action),
                            variant = ExpressiveButtonVariant.Tonal,
                        )
                    },
                )
            }
        }

        if (state.prefs.recentEndpoints.isNotEmpty()) {
            SectionHeader(stringResource(R.string.recent))
            state.prefs.recentEndpoints.forEach { endpoint ->
                ListItem(
                    headlineContent = { Text(endpoint.label) },
                    supportingContent = { Text(endpoint.kind.name) },
                    leadingContent = { Icon(Icons.Filled.History, null) },
                    trailingContent = {
                        Row {
                            ExpressiveButton(onClick = { vm.connectTcp(endpoint.host, endpoint.port); onDismiss() }, text = stringResource(R.string.connect_action), variant = ExpressiveButtonVariant.Tonal)
                            IconButton(onClick = { vm.removeRecent(endpoint) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun UsbTab(vm: EzViewModel, usbDevices: List<UsbDevice>, onRefresh: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.usb_otg_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ExpressiveButton(onClick = onRefresh, text = stringResource(R.string.refresh_usb), modifier = Modifier.fillMaxWidth(), variant = ExpressiveButtonVariant.Outlined, icon = Icons.Filled.Refresh)
        if (usbDevices.isEmpty()) {
            Text(stringResource(R.string.no_usb_devices), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            usbDevices.forEach { device ->
                ListItem(
                    headlineContent = { Text(device.productName ?: device.deviceName) },
                    supportingContent = { Text(stringResource(R.string.usb_vid_pid, device.manufacturerName ?: "", device.vendorId, device.productId)) },
                    leadingContent = { Icon(Icons.Filled.Usb, null) },
                    trailingContent = {
                        ExpressiveButton(onClick = { vm.onUsbAttached(device); onDismiss() }, text = stringResource(R.string.connect_action))
                    },
                )
            }
        }
    }
}

@Composable
private fun PairTab(
    vm: EzViewModel,
    pairHost: String,
    pairPort: String,
    pairCode: String,
    onHost: (String) -> Unit,
    onPort: (String) -> Unit,
    onCode: (String) -> Unit,
) {
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.pairing_code_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(pairHost, onHost, label = { Text(stringResource(R.string.ip_address)) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = MaterialTheme.shapes.large)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(pairPort, onPort, label = { Text(stringResource(R.string.pair_port)) }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
            OutlinedTextField(pairCode, onCode, label = { Text(stringResource(R.string.code)) }, modifier = Modifier.weight(1f), singleLine = true, shape = MaterialTheme.shapes.large)
        }
        ExpressiveButton(
            onClick = { vm.pair(pairHost.trim(), pairPort.toIntOrNull() ?: 0, pairCode.trim()) },
            text = stringResource(R.string.pair_button),
            modifier = Modifier.fillMaxWidth(),
            enabled = pairHost.isNotBlank() && pairPort.isNotBlank() && pairCode.isNotBlank(),
            icon = Icons.Filled.Key,
        )
        HorizontalDivider()
        Text(stringResource(R.string.pairing_port_hint), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
