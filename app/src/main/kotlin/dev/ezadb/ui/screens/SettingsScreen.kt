package dev.ezadb.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ezadb.core.Collections
import dev.ezadb.core.Risk
import dev.ezadb.core.SettingEntry
import dev.ezadb.core.SettingNamespace
import dev.ezadb.core.SettingType
import dev.ezadb.core.SettingsCatalog
import dev.ezadb.data.Accent
import dev.ezadb.data.ThemeMode
import dev.ezadb.ui.components.ConfirmDialog
import dev.ezadb.ui.components.EmptyState
import dev.ezadb.ui.components.SectionHeader
import dev.ezadb.ui.components.SettingRow
import dev.ezadb.ui.viewmodel.EzUiState
import dev.ezadb.ui.viewmodel.EzViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: EzViewModel, state: EzUiState) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Устройство") }, icon = { Icon(Icons.Filled.Settings, null) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Приложение") }, icon = { Icon(Icons.Filled.Palette, null) })
        }
        when (tab) {
            0 -> DeviceSettingsTab(vm, state)
            1 -> AppSettingsTab(vm, state)
        }
    }
}

@Composable
private fun DeviceSettingsTab(vm: EzViewModel, state: EzUiState) {
    var editTarget by remember { mutableStateOf<SettingEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<SettingEntry?>(null) }
    var showCustom by remember { mutableStateOf(false) }

    LaunchedEffect(state.connected, state.demo, state.settingsNamespace) {
        if (state.connected) vm.refreshSettings()
    }

    if (!state.connected) {
        EmptyState(Icons.Filled.Settings, "Настройки устройства", "Подключитесь к устройству, чтобы редактировать global/secure/system параметры.")
        return
    }

    val entries = Collections.filterSettings(state.settings, state.settingsQuery)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingNamespace.entries.forEach { ns ->
                    FilterChip(selected = state.settingsNamespace == ns, onClick = { vm.refreshSettings(ns) }, label = { Text(ns.adbName) })
                }
            }
            OutlinedTextField(
                value = state.settingsQuery,
                onValueChange = vm::setSettingsQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск параметра") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            if (state.settingsLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Text("${entries.size} параметров · ${state.settingsNamespace.adbName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.namespace.name + ":" + it.key }) { entry ->
                    SettingRow(entry, onEdit = { editTarget = entry }, onDelete = { deleteTarget = entry })
                }
            }
        }
        LargeFloatingActionButton(onClick = { showCustom = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Filled.Add, "Добавить параметр") }
    }

    editTarget?.let { entry ->
        var value by remember(entry.key) { mutableStateOf(entry.value ?: "") }
        val valid = SettingsCatalog.validate(entry.type, value)
        AlertDialog(
            onDismissRequest = { editTarget = null },
            icon = { Icon(if (entry.risk == Risk.HIGH) Icons.Filled.Warning else Icons.Filled.Settings, null) },
            title = { Text(entry.key) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${entry.namespace.adbName} · ${entry.type}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                    if (entry.risk == Risk.HIGH) Text("Высокий риск: изменение может повлиять на работу устройства.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(value, { value = it }, label = { Text("Значение") }, singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth())
                    if (!valid) Text("Некорректное значение для типа ${entry.type}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    if (entry.presets.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            entry.presets.forEach { preset -> AssistChip(onClick = { value = preset }, label = { Text(preset.ifBlank { "пусто" }) }) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editTarget = null; vm.putSetting(entry, value) }, enabled = valid) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text("Отмена") } },
        )
    }

    deleteTarget?.let { entry ->
        ConfirmDialog(
            title = "Удалить параметр?",
            text = "${entry.namespace.adbName}/${entry.key} будет сброшен к значению по умолчанию.",
            danger = entry.risk == Risk.HIGH,
            confirmLabel = "Удалить",
            onConfirm = { deleteTarget = null; vm.deleteSetting(entry) },
            onDismiss = { deleteTarget = null },
        )
    }

    if (showCustom) {
        var key by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text("Свой параметр") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Пространство: ${state.settingsNamespace.adbName}", style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(key, { key = it }, label = { Text("Ключ") }, singleLine = true)
                    OutlinedTextField(value, { value = it }, label = { Text("Значение") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { showCustom = false; vm.addCustomSetting(state.settingsNamespace, key.trim(), value) }, enabled = key.isNotBlank()) { Text("Записать") } },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AppSettingsTab(vm: EzViewModel, state: EzUiState) {
    val prefs = state.prefs
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader("Оформление")
        ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Тема", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(selected = prefs.themeMode == mode, onClick = { vm.setThemeMode(mode) }, label = { Text(mode.label()) })
                    }
                }
                ListItem(
                    headlineContent = { Text("Динамические цвета (Material You)") },
                    supportingContent = { Text("Android 12+: цвета из обоев системы") },
                    trailingContent = { Switch(checked = prefs.dynamicColor, onCheckedChange = vm::setDynamicColor) },
                )
                Text("Акцент", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Accent.entries.forEach { accent ->
                        FilterChip(selected = prefs.accent == accent, onClick = { vm.setAccent(accent) }, label = { Text(accent.label()) }, enabled = !prefs.dynamicColor)
                    }
                }
            }
        }

        SectionHeader("Терминал")
        ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Размер шрифта: ${prefs.terminalFontSize} sp", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
                Slider(value = prefs.terminalFontSize.toFloat(), onValueChange = { vm.setTerminalFontSize(it.toInt()) }, valueRange = 10f..22f, steps = 11)
            }
        }

        SectionHeader("Поведение")
        ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(8.dp)) {
                PrefSwitch("Подтверждать опасные действия", "Запрос перед удалением, очисткой и изменением критичных настроек", prefs.confirmDestructive, vm::setConfirmDestructive)
                PrefSwitch("Не выключать экран", "Держать экран включённым, пока EzADB открыт", prefs.keepScreenOn, vm::setKeepScreenOn)
                PrefSwitch("Виброотклик", "Тактильная отдача интерфейса", prefs.haptics, vm::setHaptics)
                PrefSwitch("Демо-режим", "Показывать вымышленное устройство без реального ADB", prefs.demoMode, vm::setDemoMode)
            }
        }

        SectionHeader("О приложении")
        ElevatedCard(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("EzADB 1.0.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("Material 3 · Kotlin · Jetpack Compose", style = MaterialTheme.typography.bodyMedium)
                Text("ADB-клиент: Kadb 2.1.5 (Apache 2.0). Лицензия EzADB: GPL-3.0.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Приложение управляет другим Android-устройством по USB OTG или Wi-Fi. Root на управляющем телефоне не требуется.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun PrefSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle, style = MaterialTheme.typography.bodySmall) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.SYSTEM -> "Системная"
    ThemeMode.LIGHT -> "Светлая"
    ThemeMode.DARK -> "Тёмная"
}

private fun Accent.label(): String = when (this) {
    Accent.GREEN -> "Зелёный"
    Accent.TEAL -> "Бирюзовый"
    Accent.BLUE -> "Синий"
    Accent.PURPLE -> "Фиолетовый"
    Accent.ORANGE -> "Оранжевый"
}
