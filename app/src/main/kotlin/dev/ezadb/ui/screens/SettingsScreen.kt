package dev.ezadb.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ezadb.R
import dev.ezadb.core.Collections
import dev.ezadb.core.Risk
import dev.ezadb.core.SettingEntry
import dev.ezadb.core.SettingNamespace
import dev.ezadb.core.SettingsCatalog
import dev.ezadb.data.Accent
import dev.ezadb.data.AppLanguage
import dev.ezadb.data.ThemeMode
import dev.ezadb.ui.components.ConfirmDialog
import dev.ezadb.ui.components.EmptyState
import dev.ezadb.ui.components.ExpressiveButton
import dev.ezadb.ui.components.ExpressiveButtonVariant
import dev.ezadb.ui.components.ExpressiveCard
import dev.ezadb.ui.components.ExpressiveToggleChip
import dev.ezadb.ui.components.ExpressiveWaveProgress
import dev.ezadb.ui.components.SectionHeader
import dev.ezadb.ui.components.SegmentedButtonRow
import dev.ezadb.ui.components.SettingRow
import dev.ezadb.ui.i18n.localizedSettingDescription
import dev.ezadb.ui.theme.EzMotionSpecs
import dev.ezadb.ui.viewmodel.EzUiState
import dev.ezadb.ui.viewmodel.EzViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: EzViewModel, state: EzUiState) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        SegmentedButtonRow(
            items = listOf(0, 1),
            selected = tab,
            onSelected = { tab = it },
            label = { if (it == 0) stringResource(R.string.device_settings) else stringResource(R.string.app_settings) },
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        )
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (fadeIn(EzMotionSpecs.fade()) + slideInHorizontally(EzMotionSpecs.spatial()) { it / 5 * direction })
                    .togetherWith(fadeOut(EzMotionSpecs.fade()) + slideOutHorizontally(EzMotionSpecs.spatial()) { -it / 5 * direction })
            },
            label = "settingsTab",
        ) { current ->
            when (current) {
                0 -> DeviceSettingsTab(vm, state)
                else -> AppSettingsTab(vm, state)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DeviceSettingsTab(vm: EzViewModel, state: EzUiState) {
    var editTarget by remember { mutableStateOf<SettingEntry?>(null) }
    var deleteTarget by remember { mutableStateOf<SettingEntry?>(null) }
    var showCustom by remember { mutableStateOf(false) }

    LaunchedEffect(state.connected, state.demo, state.settingsNamespace) {
        if (state.connected) vm.refreshSettings()
    }

    if (!state.connected) {
        EmptyState(Icons.Filled.Settings, stringResource(R.string.device_settings), stringResource(R.string.settings_empty))
        return
    }

    val entries = Collections.filterSettings(state.settings, state.settingsQuery)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingNamespace.entries.forEach { ns ->
                    ExpressiveToggleChip(selected = state.settingsNamespace == ns, onClick = { vm.refreshSettings(ns) }, label = ns.adbName)
                }
            }
            OutlinedTextField(
                value = state.settingsQuery,
                onValueChange = vm::setSettingsQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.settings_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            if (state.settingsLoading) ExpressiveWaveProgress(Modifier.fillMaxWidth().height(4.dp).padding(vertical = 4.dp))
            Text(stringResource(R.string.setting_count, entries.size, state.settingsNamespace.adbName), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(entries, key = { it.namespace.name + ":" + it.key }) { entry ->
                    Box(Modifier.animateItem()) {
                        SettingRow(entry, onEdit = { editTarget = entry }, onDelete = { deleteTarget = entry })
                    }
                }
            }
        }
        LargeFloatingActionButton(onClick = { showCustom = true }, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) { Icon(Icons.Filled.Add, stringResource(R.string.add_setting)) }
    }

    editTarget?.let { entry ->
        var value by remember(entry.key) { mutableStateOf(entry.value ?: "") }
        val valid = SettingsCatalog.validate(entry.type, value)
        val description = localizedSettingDescription(entry)
        AlertDialog(
            onDismissRequest = { editTarget = null },
            icon = { Icon(if (entry.risk == Risk.HIGH) Icons.Filled.Warning else Icons.Filled.Settings, null) },
            title = { Text(entry.key) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setting_type_label, entry.namespace.adbName, entry.type), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
                    if (entry.risk == Risk.HIGH) Text(stringResource(R.string.setting_high_risk), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.setting_value)) }, singleLine = true, isError = !valid, modifier = Modifier.fillMaxWidth())
                    if (!valid) Text(stringResource(R.string.setting_invalid_value, entry.type), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    if (entry.presets.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            entry.presets.forEach { preset -> AssistChip(onClick = { value = preset }, label = { Text(preset.ifBlank { stringResource(R.string.setting_empty_preset) }) }) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { editTarget = null; vm.putSetting(entry, value) }, enabled = valid) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { editTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleteTarget?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.setting_delete_title),
            text = stringResource(R.string.setting_reset_confirm, entry.namespace.adbName, entry.key),
            danger = entry.risk == Risk.HIGH,
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { deleteTarget = null; vm.deleteSetting(entry) },
            onDismiss = { deleteTarget = null },
        )
    }

    if (showCustom) {
        var key by remember { mutableStateOf("") }
        var value by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCustom = false },
            title = { Text(stringResource(R.string.setting_custom_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.setting_namespace, state.settingsNamespace.adbName), style = MaterialTheme.typography.labelMedium)
                    OutlinedTextField(key, { key = it }, label = { Text(stringResource(R.string.setting_key)) }, singleLine = true)
                    OutlinedTextField(value, { value = it }, label = { Text(stringResource(R.string.setting_value)) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { showCustom = false; vm.addCustomSetting(state.settingsNamespace, key.trim(), value) }, enabled = key.isNotBlank()) { Text(stringResource(R.string.write_setting)) } },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun AppSettingsTab(vm: EzViewModel, state: EzUiState) {
    val prefs = state.prefs
    var showLanguage by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.appearance))
        ExpressiveCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.theme), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            SegmentedButtonRow(
                items = ThemeMode.entries,
                selected = prefs.themeMode,
                onSelected = vm::setThemeMode,
                label = { themeModeLabel(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.dynamic_color)) },
                supportingContent = { Text(stringResource(R.string.dynamic_color_desc)) },
                trailingContent = { Switch(checked = prefs.dynamicColor, onCheckedChange = vm::setDynamicColor) },
            )
            Text(stringResource(R.string.accent), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Accent.entries.forEach { accent ->
                    ExpressiveToggleChip(selected = prefs.accent == accent, onClick = { vm.setAccent(accent) }, label = accentLabel(accent), enabled = !prefs.dynamicColor)
                }
            }
            ListItem(
                modifier = Modifier.clickable { showLanguage = true },
                headlineContent = { Text(stringResource(R.string.language)) },
                supportingContent = { Text(languageLabel(prefs.language)) },
                trailingContent = { Icon(Icons.Filled.Language, null) },
            )
        }

        SectionHeader(stringResource(R.string.terminal))
        ExpressiveCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Terminal, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.terminal_font_size, prefs.terminalFontSize), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Slider(value = prefs.terminalFontSize.toFloat(), onValueChange = { vm.setTerminalFontSize(it.toInt()) }, valueRange = 10f..22f, steps = 11)
        }

        SectionHeader(stringResource(R.string.behavior))
        ExpressiveCard(Modifier.fillMaxWidth(), contentPadding = 8.dp) {
            PrefSwitch(stringResource(R.string.confirm_destructive), stringResource(R.string.confirm_destructive_desc), prefs.confirmDestructive, vm::setConfirmDestructive)
            PrefSwitch(stringResource(R.string.keep_screen_on), stringResource(R.string.keep_screen_on_desc), prefs.keepScreenOn, vm::setKeepScreenOn)
            PrefSwitch(stringResource(R.string.haptics), stringResource(R.string.haptics_desc), prefs.haptics, vm::setHaptics)
            PrefSwitch(stringResource(R.string.demo_mode), stringResource(R.string.demo_mode_desc), prefs.demoMode, vm::setDemoMode)
            ExpressiveButton(onClick = { vm.setIntroSeen(false) }, text = stringResource(R.string.show_intro_again), variant = ExpressiveButtonVariant.Outlined, modifier = Modifier.fillMaxWidth().padding(8.dp))
        }

        SectionHeader(stringResource(R.string.about))
        ExpressiveCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("EzADB 1.1.0", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Text(stringResource(R.string.about_desc), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.license_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(R.string.root_not_required), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showLanguage) {
        AlertDialog(
            onDismissRequest = { showLanguage = false },
            title = { Text(stringResource(R.string.language)) },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    AppLanguage.entries.forEach { language ->
                        ListItem(
                            modifier = Modifier.clickable { vm.setLanguage(language); showLanguage = false },
                            headlineContent = { Text(languageLabel(language)) },
                            leadingContent = { RadioButton(selected = prefs.language == language, onClick = { vm.setLanguage(language); showLanguage = false }) },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLanguage = false }) { Text(stringResource(R.string.close)) } },
        )
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

@Composable
private fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun accentLabel(accent: Accent): String = when (accent) {
    Accent.GREEN -> stringResource(R.string.accent_green)
    Accent.TEAL -> stringResource(R.string.accent_teal)
    Accent.BLUE -> stringResource(R.string.accent_blue)
    Accent.PURPLE -> stringResource(R.string.accent_purple)
    Accent.ORANGE -> stringResource(R.string.accent_orange)
}

@Composable
private fun languageLabel(language: AppLanguage): String = when (language) {
    AppLanguage.SYSTEM -> stringResource(R.string.language_system)
    AppLanguage.ENGLISH -> stringResource(R.string.language_english)
    AppLanguage.RUSSIAN -> stringResource(R.string.language_russian)
    AppLanguage.UKRAINIAN -> stringResource(R.string.language_ukrainian)
    AppLanguage.GERMAN -> stringResource(R.string.language_german)
    AppLanguage.SPANISH -> stringResource(R.string.language_spanish)
    AppLanguage.FRENCH -> stringResource(R.string.language_french)
    AppLanguage.CHINESE -> stringResource(R.string.language_chinese)
}
