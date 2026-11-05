package dev.ezadb.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ezadb.R
import dev.ezadb.core.Formatters
import dev.ezadb.ui.components.ConfirmDialog
import dev.ezadb.ui.components.DeviceHero
import dev.ezadb.ui.components.EmptyState
import dev.ezadb.ui.components.ExpressiveButton
import dev.ezadb.ui.components.ExpressiveButtonVariant
import dev.ezadb.ui.components.ExpressiveCard
import dev.ezadb.ui.components.ExpressiveToggleChip
import dev.ezadb.ui.components.QuickToggleCard
import dev.ezadb.ui.components.SectionHeader
import dev.ezadb.ui.viewmodel.EzUiState
import dev.ezadb.ui.viewmodel.EzViewModel

@Composable
fun DashboardScreen(vm: EzViewModel, state: EzUiState, onConnect: () -> Unit) {
    var showReboot by remember { mutableStateOf(false) }
    var confirmRebootMode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.connected, state.demo) {
        if (state.connected) {
            vm.refreshDevice()
            vm.refreshToggles()
        }
    }

    if (!state.connected) {
        EmptyState(
            icon = Icons.Filled.Cable,
            title = stringResource(R.string.connect_to_device),
            subtitle = stringResource(R.string.connect_hint),
            action = { ExpressiveButton(onClick = onConnect, text = stringResource(R.string.connect_button), icon = Icons.Filled.Add) },
        )
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DeviceHero(state.device, state.connection, state.demo, onRefresh = { vm.refreshDevice(); vm.refreshToggles() }, onDisconnect = { vm.disconnect() })

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveToggleChip(selected = false, onClick = { vm.captureScreenshot() }, label = stringResource(R.string.screenshot), icon = Icons.Filled.Screenshot)
            ExpressiveToggleChip(selected = false, onClick = { vm.enableWifiAdb() }, label = stringResource(R.string.action_wifi_adb), icon = Icons.Filled.Wifi)
            ExpressiveToggleChip(selected = false, onClick = { vm.setScreen(true) }, label = stringResource(R.string.wake), icon = Icons.Filled.PowerSettingsNew)
            ExpressiveToggleChip(selected = false, onClick = { showReboot = true }, label = stringResource(R.string.reboot), icon = Icons.Filled.RestartAlt)
            ExpressiveToggleChip(selected = false, onClick = { vm.refreshDevice(); vm.refreshToggles() }, label = stringResource(R.string.refresh), icon = Icons.Filled.Refresh)
        }

        SectionHeader(stringResource(R.string.quick_toggles), action = {
            TextButton(onClick = { vm.refreshToggles() }) { Text(stringResource(R.string.refresh)) }
        })
        if (state.togglesLoading && state.toggles.isEmpty()) {
            Text(stringResource(R.string.toggles_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            state.toggles.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { toggle ->
                        QuickToggleCard(toggle, onToggle = { vm.setToggle(toggle.id, it) }, modifier = Modifier.weight(1f))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        state.device?.let { device ->
            SectionHeader(stringResource(R.string.device_info))
            ExpressiveCard(Modifier.fillMaxWidth()) {
                DetailRow(stringResource(R.string.build), device.buildId)
                DetailRow(stringResource(R.string.serial), device.serial)
                DetailRow(stringResource(R.string.screen), if (device.screenWidth != null) "${device.screenWidth}×${device.screenHeight} · ${device.densityDpi} dpi" else "—")
                DetailRow(stringResource(R.string.battery), "${Formatters.battery(device.battery.level)} · ${device.battery.status} · ${device.battery.temperatureC ?: "—"}°C")
                DetailRow(stringResource(R.string.storage), "${Formatters.bytes(device.storage.usedBytes)} / ${Formatters.bytes(device.storage.totalBytes)}")
                DetailRow(stringResource(R.string.root), if (device.suAvailable) stringResource(R.string.su_found) else stringResource(R.string.su_not_found))
                DetailRow(stringResource(R.string.ip_addresses), device.ipAddresses.joinToString(", ").ifBlank { "—" })
            }
        }

        if (state.demo) {
            ExpressiveCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.demo_banner), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (showReboot) {
        AlertDialog(
            onDismissRequest = { showReboot = false },
            icon = { Icon(Icons.Filled.RestartAlt, null) },
            title = { Text(stringResource(R.string.reboot)) },
            text = { Text(stringResource(R.string.reboot_choose)) },
            confirmButton = {
                TextButton(onClick = { showReboot = false; confirmRebootMode = "" }) { Text(stringResource(R.string.system)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showReboot = false; confirmRebootMode = "recovery" }) { Text(stringResource(R.string.recovery)) }
                    TextButton(onClick = { showReboot = false; confirmRebootMode = "bootloader" }) { Text(stringResource(R.string.bootloader)) }
                }
            },
        )
    }

    confirmRebootMode?.let { mode ->
        ConfirmDialog(
            title = stringResource(R.string.reboot_confirm),
            text = if (mode.isBlank()) stringResource(R.string.reboot_confirm_text) else stringResource(R.string.reboot_confirm_mode_text, mode),
            danger = mode.isNotBlank(),
            confirmLabel = stringResource(R.string.reboot),
            onConfirm = { confirmRebootMode = null; vm.reboot(mode) },
            onDismiss = { confirmRebootMode = null },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.4f))
    }
}
