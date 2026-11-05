package dev.ezadb.ui

import android.hardware.usb.UsbDevice
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ezadb.core.DiscoveryType
import dev.ezadb.ui.components.SectionHeader
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
            Text("Подключение", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 24.dp))
            Spacer(Modifier.height(12.dp))
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Wi-Fi") }, icon = { Icon(Icons.Filled.Wifi, null) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("USB") }, icon = { Icon(Icons.Filled.Usb, null) })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Сопряжение") }, icon = { Icon(Icons.Filled.Key, null) })
            }
            when (tab) {
                0 -> {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Включите «Беспроводная отладка» на целевом устройстве и укажите IP и порт подключения.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(host, { host = it }, label = { Text("IP-адрес") }, modifier = Modifier.weight(2f), singleLine = true)
                            OutlinedTextField(port, { port = it }, label = { Text("Порт") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Button(onClick = {
                            val p = port.toIntOrNull() ?: 5555
                            vm.connectTcp(host.trim(), p)
                            onDismiss()
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Cable, null); Spacer(Modifier.width(8.dp)); Text("Подключиться") }

                        if (state.discovered.isNotEmpty()) {
                            SectionHeader("Найдено в сети")
                            state.discovered.forEach { endpoint ->
                                ListItem(
                                    headlineContent = { Text(endpoint.name) },
                                    supportingContent = { Text("${endpoint.host}:${endpoint.port} · ${if (endpoint.type == DiscoveryType.PAIR) "сопряжение" else "подключение"}") },
                                    trailingContent = {
                                        FilledTonalButton(onClick = {
                                            if (endpoint.type == DiscoveryType.PAIR) {
                                                pairHost = endpoint.host
                                                pairPort = endpoint.port.toString()
                                                tab = 2
                                            } else {
                                                vm.connectTcp(endpoint.host, endpoint.port)
                                                onDismiss()
                                            }
                                        }) { Text(if (endpoint.type == DiscoveryType.PAIR) "Спарить" else "Подключить") }
                                    },
                                )
                            }
                        }

                        if (state.prefs.recentEndpoints.isNotEmpty()) {
                            SectionHeader("Недавние")
                            state.prefs.recentEndpoints.forEach { endpoint ->
                                ListItem(
                                    headlineContent = { Text(endpoint.label) },
                                    supportingContent = { Text(endpoint.kind.name) },
                                    leadingContent = { Icon(Icons.Filled.History, null) },
                                    trailingContent = {
                                        Row {
                                            FilledTonalButton(onClick = { vm.connectTcp(endpoint.host, endpoint.port); onDismiss() }) { Text("Подключить") }
                                            IconButton(onClick = { vm.removeRecent(endpoint) }) { Icon(Icons.Filled.Delete, "Удалить") }
                                        }
                                    },
                                )
                            }
                        }
                    }
                }
                1 -> {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Подключите целевое устройство к этому телефону кабелем OTG. На целевом устройстве должна быть включена отладка по USB.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { usbDevices = vm.refreshUsbDevices() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Filled.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Обновить список USB")
                        }
                        if (usbDevices.isEmpty()) {
                            Text("ADB-совместимые USB-устройства не найдены.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            usbDevices.forEach { device ->
                                ListItem(
                                    headlineContent = { Text(device.productName ?: device.deviceName) },
                                    supportingContent = { Text("${device.manufacturerName ?: ""} · VID ${device.vendorId} PID ${device.productId}") },
                                    leadingContent = { Icon(Icons.Filled.Usb, null) },
                                    trailingContent = {
                                        Button(onClick = { vm.onUsbAttached(device); onDismiss() }) { Text("Подключить") }
                                    },
                                )
                            }
                        }
                    }
                }
                2 -> {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Android 11+: откройте «Беспроводная отладка» → «Подключение с помощью кода». Введите IP, порт сопряжения и 6-значный код. После сопряжения подключитесь к основному порту.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(pairHost, { pairHost = it }, label = { Text("IP-адрес") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(pairPort, { pairPort = it }, label = { Text("Порт сопряжения") }, modifier = Modifier.weight(1f), singleLine = true)
                            OutlinedTextField(pairCode, { pairCode = it }, label = { Text("Код") }, modifier = Modifier.weight(1f), singleLine = true)
                        }
                        Button(onClick = {
                            vm.pair(pairHost.trim(), pairPort.toIntOrNull() ?: 0, pairCode.trim())
                        }, modifier = Modifier.fillMaxWidth(), enabled = pairHost.isNotBlank() && pairPort.isNotBlank() && pairCode.isNotBlank()) {
                            Icon(Icons.Filled.Key, null); Spacer(Modifier.width(8.dp)); Text("Выполнить сопряжение")
                        }
                        HorizontalDivider()
                        Text("Подсказка: порт сопряжения и порт подключения — разные. После успешного сопряжения используйте вкладку Wi-Fi или найденное устройство.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
