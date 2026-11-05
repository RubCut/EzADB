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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ezadb.core.Formatters
import dev.ezadb.ui.components.ConfirmDialog
import dev.ezadb.ui.components.DeviceHero
import dev.ezadb.ui.components.EmptyState
import dev.ezadb.ui.components.QuickToggleCard
import dev.ezadb.ui.components.SectionHeader
import dev.ezadb.ui.viewmodel.EzUiState
import dev.ezadb.ui.viewmodel.EzViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(vm: EzViewModel, state: EzUiState, onConnect: () -> Unit) {
    var showReboot by remember { mutableStateOf(false) }
    var confirmRebootMode by remember { mutableStateOf<String?>(null) }
    val screenshotLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
        if (uri != null) vm.screenshot(uri)
    }

    LaunchedEffect(state.connected, state.demo) {
        if (state.connected) {
            vm.refreshDevice()
            vm.refreshToggles()
        }
    }

    if (!state.connected) {
        EmptyState(
            icon = Icons.Filled.Cable,
            title = "Подключите Android-устройство",
            subtitle = "Используйте USB OTG или Wi-Fi ADB. Можно также включить демо-режим в настройках приложения.",
            action = { FilledTonalButton(onClick = onConnect) { Icon(Icons.Filled.Add, null); Spacer(Modifier.width(8.dp)); Text("Подключиться") } },
        )
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DeviceHero(state.device, state.connection, state.demo, onRefresh = { vm.refreshDevice(); vm.refreshToggles() }, onDisconnect = { vm.disconnect() })

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { screenshotLauncher.launch("ezadb-screen.png") }, label = { Text("Скриншот") }, leadingIcon = { Icon(Icons.Filled.Screenshot, null) })
            AssistChip(onClick = { vm.enableWifiAdb() }, label = { Text("Wi-Fi ADB") }, leadingIcon = { Icon(Icons.Filled.Wifi, null) })
            AssistChip(onClick = { vm.setScreen(true) }, label = { Text("Разбудить") }, leadingIcon = { Icon(Icons.Filled.PowerSettingsNew, null) })
            AssistChip(onClick = { showReboot = true }, label = { Text("Перезагрузка") }, leadingIcon = { Icon(Icons.Filled.RestartAlt, null) })
            AssistChip(onClick = { vm.refreshDevice(); vm.refreshToggles() }, label = { Text("Обновить") }, leadingIcon = { Icon(Icons.Filled.Refresh, null) })
        }

        SectionHeader("Быстрые переключатели", action = {
            TextButton(onClick = { vm.refreshToggles() }) { Text("Обновить") }
        })
        if (state.togglesLoading && state.toggles.isEmpty()) {
            Text("Чтение состояния переключателей…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            SectionHeader("Сведения об устройстве")
            ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DetailRow("Сборка", device.buildId)
                    DetailRow("Серийный номер", device.serial)
                    DetailRow("Экран", if (device.screenWidth != null) "${device.screenWidth}×${device.screenHeight} · ${device.densityDpi} dpi" else "—")
                    DetailRow("Батарея", "${Formatters.battery(device.battery.level)} · ${device.battery.status} · ${device.battery.temperatureC ?: "—"}°C")
                    DetailRow("Хранилище", "${Formatters.bytes(device.storage.usedBytes)} / ${Formatters.bytes(device.storage.totalBytes)}")
                    DetailRow("Root (su)", if (device.suAvailable) "su найден" else "не обнаружен")
                    DetailRow("IP-адреса", device.ipAddresses.joinToString(", ").ifBlank { "—" })
                }
            }
        }

        if (state.demo) {
            ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
                Row(Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(12.dp))
                    Text("Демо-режим: данные вымышлены, команды не выполняются на реальном устройстве.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }

    if (showReboot) {
        AlertDialog(
            onDismissRequest = { showReboot = false },
            icon = { Icon(Icons.Filled.RestartAlt, null) },
            title = { Text("Перезагрузка") },
            text = { Text("Выберите режим перезагрузки целевого устройства.") },
            confirmButton = {
                TextButton(onClick = { showReboot = false; confirmRebootMode = "" }) { Text("Система") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { showReboot = false; confirmRebootMode = "recovery" }) { Text("Recovery") }
                    TextButton(onClick = { showReboot = false; confirmRebootMode = "bootloader" }) { Text("Bootloader") }
                }
            },
        )
    }

    confirmRebootMode?.let { mode ->
        ConfirmDialog(
            title = "Подтвердите перезагрузку",
            text = if (mode.isBlank()) "Устройство будет перезагружено." else "Устройство будет перезагружено в $mode. Это может прервать текущую сессию ADB.",
            danger = mode.isNotBlank(),
            confirmLabel = "Перезагрузить",
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
