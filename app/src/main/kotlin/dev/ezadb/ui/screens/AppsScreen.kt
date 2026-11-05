package dev.ezadb.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ezadb.core.AppFilter
import dev.ezadb.core.AppInfo
import dev.ezadb.core.AppSort
import dev.ezadb.core.Collections
import dev.ezadb.core.Formatters
import dev.ezadb.ui.components.AppRow
import dev.ezadb.ui.components.ConfirmDialog
import dev.ezadb.ui.components.EmptyState
import dev.ezadb.ui.components.SectionHeader
import dev.ezadb.ui.viewmodel.EzUiState
import dev.ezadb.ui.viewmodel.EzViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(vm: EzViewModel, state: EzUiState) {
    var showDetail by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var extractPackage by remember { mutableStateOf<String?>(null) }
    var confirm by remember { mutableStateOf<ConfirmSpec?>(null) }

    val installLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.installApk(uri)
    }
    val extractLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")) { uri ->
        val pkg = extractPackage
        if (uri != null && pkg != null) vm.extractApk(pkg, uri)
        extractPackage = null
    }

    LaunchedEffect(state.connected, state.demo) {
        if (state.connected) vm.refreshApps()
    }

    if (!state.connected) {
        EmptyState(Icons.Filled.Apps, "Приложения", "Подключитесь к устройству, чтобы управлять приложениями.")
        return
    }

    val apps = Collections.filterApps(state.apps, state.appQuery, state.appFilter, state.appSort)

    fun requestConfirm(title: String, text: String, danger: Boolean = false, action: () -> Unit) {
        if (state.prefs.confirmDestructive || danger) confirm = ConfirmSpec(title, text, danger, action)
        else action()
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            OutlinedTextField(
                value = state.appQuery,
                onValueChange = vm::setAppQuery,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                placeholder = { Text("Поиск по имени или пакету") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = state.appFilter == filter,
                        onClick = { vm.setAppFilter(filter) },
                        label = { Text(filter.label()) },
                    )
                }
                Box {
                    TextButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(state.appSort.label()) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        AppSort.entries.forEach { sort ->
                            DropdownMenuItem(text = { Text(sort.label()) }, onClick = { vm.setAppSort(sort); sortMenu = false })
                        }
                    }
                }
            }
            if (state.appsLoading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Text("${apps.size} приложений", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(apps, key = { it.packageName }) { app ->
                    AppRow(
                        app = app,
                        onClick = { vm.selectApp(app); vm.loadAppDetails(app.packageName); showDetail = true },
                        onMore = { vm.selectApp(app); vm.loadAppDetails(app.packageName); showDetail = true },
                    )
                }
            }
        }
        LargeFloatingActionButton(
            onClick = { installLauncher.launch(arrayOf("application/vnd.android.package-archive")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, "Установить APK") }
    }

    val selected = state.selectedApp
    if (showDetail && selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showDetail = false; vm.selectApp(null) }, sheetState = sheetState) {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (selected.system) Icons.Filled.Android else Icons.Filled.Apps, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(selected.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(selected.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selected.system) Badge { Text("системное") }
                    if (!selected.enabled) Badge(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) { Text("отключено") }
                    selected.versionName?.let { Badge { Text(it) } }
                }
                Text("UID: ${selected.uid ?: "—"} · Размер: ${Formatters.bytes(selected.sizeBytes)} · Код версии: ${selected.versionCode ?: "—"}", style = MaterialTheme.typography.bodySmall)
                selected.apkPath?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                SectionHeader("Действия")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { vm.launchApp(selected.packageName) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Запуск") }
                    FilledTonalButton(onClick = {
                        requestConfirm(
                            if (selected.enabled) "Отключить приложение?" else "Включить приложение?",
                            "${selected.packageName} будет ${if (selected.enabled) "отключено для пользователя 0" else "включено"}.",
                            danger = selected.system && selected.enabled,
                        ) { vm.setAppEnabled(selected.packageName, !selected.enabled) }
                    }, modifier = Modifier.weight(1f)) { Text(if (selected.enabled) "Отключить" else "Включить") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { requestConfirm("Остановить приложение?", "Принудительная остановка ${selected.packageName}.") { vm.forceStop(selected.packageName) } }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Stop, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Стоп") }
                    FilledTonalButton(onClick = { requestConfirm("Очистить данные?", "Все данные ${selected.packageName} будут удалены.", danger = true) { vm.clearAppData(selected.packageName) } }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.CleaningServices, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Очистить") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { requestConfirm("Удалить приложение?", "${selected.packageName} будет удалено для пользователя 0.", danger = true) { vm.uninstallApp(selected.packageName); showDetail = false } }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Delete, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Удалить") }
                    FilledTonalButton(onClick = { extractPackage = selected.packageName; extractLauncher.launch("${selected.packageName}.apk") }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("APK") }
                }
                FilledTonalButton(onClick = { vm.openAppSettings(selected.packageName) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Settings, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Открыть настройки приложения") }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    confirm?.let { spec ->
        ConfirmDialog(spec.title, spec.text, danger = spec.danger, onConfirm = { confirm = null; spec.action() }, onDismiss = { confirm = null })
    }
}

private data class ConfirmSpec(val title: String, val text: String, val danger: Boolean, val action: () -> Unit)

private fun AppFilter.label(): String = when (this) {
    AppFilter.ALL -> "Все"
    AppFilter.USER -> "Пользовательские"
    AppFilter.SYSTEM -> "Системные"
    AppFilter.DISABLED -> "Отключённые"
}

private fun AppSort.label(): String = when (this) {
    AppSort.NAME -> "Имя"
    AppSort.PACKAGE -> "Пакет"
    AppSort.SIZE -> "Размер"
    AppSort.UID -> "UID"
}
