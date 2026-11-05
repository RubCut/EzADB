package dev.ezadb.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ezadb.R
import dev.ezadb.core.AppFilter
import dev.ezadb.core.AppSort
import dev.ezadb.core.Collections
import dev.ezadb.core.Formatters
import dev.ezadb.ui.components.AppRow
import dev.ezadb.ui.components.ConfirmDialog
import dev.ezadb.ui.components.EmptyState
import dev.ezadb.ui.components.ExpressiveButton
import dev.ezadb.ui.components.ExpressiveButtonVariant
import dev.ezadb.ui.components.ExpressiveToggleChip
import dev.ezadb.ui.components.ExpressiveWaveProgress
import dev.ezadb.ui.components.SectionHeader
import dev.ezadb.ui.viewmodel.EzUiState
import dev.ezadb.ui.viewmodel.EzViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        EmptyState(Icons.Filled.Apps, stringResource(R.string.apps), stringResource(R.string.apps_empty))
        return
    }

    val apps = remember(state.apps, state.appQuery, state.appFilter, state.appSort) {
        Collections.filterApps(state.apps, state.appQuery, state.appFilter, state.appSort)
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    LaunchedEffect(listState, apps) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            vm.prefetchIcons(apps.drop(index).take(24).map { it.packageName })
        }
    }
    val namesKnown = remember(state.apps) { state.apps.any { it.label != it.packageName } }

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
                placeholder = { Text(stringResource(R.string.app_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFilter.entries.forEach { filter ->
                    ExpressiveToggleChip(selected = state.appFilter == filter, onClick = { vm.setAppFilter(filter) }, label = appFilterLabel(filter))
                }
                Box {
                    TextButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(appSortLabel(state.appSort)) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        AppSort.entries.forEach { sort ->
                            DropdownMenuItem(text = { Text(appSortLabel(sort)) }, onClick = { vm.setAppSort(sort); sortMenu = false })
                        }
                    }
                }
            }
            if (state.appsLoading) ExpressiveWaveProgress(Modifier.fillMaxWidth().height(4.dp).padding(vertical = 4.dp))
            if (state.apps.isNotEmpty() && !namesKnown) {
                Text(
                    stringResource(if (state.appsLoading) R.string.app_names_loading else R.string.app_names_fallback),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            Text(stringResource(R.string.apps_count, apps.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 6.dp))
            LazyColumn(Modifier.weight(1f), state = listState) {
                items(apps, key = { it.packageName }) { app ->
                    Box(Modifier.animateItem()) {
                        AppRow(
                            app = app,
                            icon = state.appIcons[app.packageName],
                            onClick = { vm.selectApp(app); vm.loadAppDetails(app.packageName); showDetail = true },
                            onMore = { vm.selectApp(app); vm.loadAppDetails(app.packageName); showDetail = true },
                        )
                    }
                }
            }
        }
        LargeFloatingActionButton(
            onClick = { installLauncher.launch(arrayOf("application/vnd.android.package-archive")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Add, stringResource(R.string.install_apk)) }
    }

    val selected = state.selectedApp
    if (showDetail && selected != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val enableTitle = stringResource(R.string.app_enable_title)
        val disableTitle = stringResource(R.string.app_disable_title)
        val enableText = stringResource(R.string.app_enable_text, selected.packageName)
        val disableText = stringResource(R.string.app_disable_text, selected.packageName)
        val stopTitle = stringResource(R.string.app_stop_title)
        val stopText = stringResource(R.string.app_stop_text, selected.packageName)
        val clearTitle = stringResource(R.string.app_clear_title)
        val clearText = stringResource(R.string.app_clear_text, selected.packageName)
        val uninstallTitle = stringResource(R.string.app_uninstall_title)
        val uninstallText = stringResource(R.string.app_uninstall_text, selected.packageName)
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
                    if (selected.system) Badge { Text(stringResource(R.string.app_system_badge)) }
                    if (!selected.enabled) Badge(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) { Text(stringResource(R.string.app_disabled_badge)) }
                    selected.versionName?.let { Badge { Text(it) } }
                }
                Text(
                    stringResource(R.string.app_uid, selected.uid?.toString() ?: "—") + " · " +
                        stringResource(R.string.app_size, Formatters.bytes(selected.sizeBytes)) + " · " +
                        stringResource(R.string.app_version_code, selected.versionCode?.toString() ?: "—"),
                    style = MaterialTheme.typography.bodySmall,
                )
                selected.apkPath?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                SectionHeader(stringResource(R.string.app_actions))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton(onClick = { vm.launchApp(selected.packageName) }, text = stringResource(R.string.launch), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.PlayArrow, modifier = Modifier.weight(1f))
                    ExpressiveButton(
                        onClick = {
                            requestConfirm(
                                if (selected.enabled) disableTitle else enableTitle,
                                if (selected.enabled) disableText else enableText,
                                danger = selected.system && selected.enabled,
                            ) { vm.setAppEnabled(selected.packageName, !selected.enabled) }
                        },
                        text = stringResource(if (selected.enabled) R.string.disable else R.string.enable),
                        variant = ExpressiveButtonVariant.Tonal,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton(onClick = { requestConfirm(stopTitle, stopText) { vm.forceStop(selected.packageName) } }, text = stringResource(R.string.stop), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.Stop, modifier = Modifier.weight(1f))
                    ExpressiveButton(onClick = { requestConfirm(clearTitle, clearText, danger = true) { vm.clearAppData(selected.packageName) } }, text = stringResource(R.string.clear), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.CleaningServices, modifier = Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExpressiveButton(onClick = { requestConfirm(uninstallTitle, uninstallText, danger = true) { vm.uninstallApp(selected.packageName); showDetail = false } }, text = stringResource(R.string.uninstall), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.Delete, modifier = Modifier.weight(1f))
                    ExpressiveButton(onClick = { extractPackage = selected.packageName; extractLauncher.launch("${selected.packageName}.apk") }, text = stringResource(R.string.extract), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.Download, modifier = Modifier.weight(1f))
                }
                ExpressiveButton(onClick = { vm.openAppSettings(selected.packageName) }, text = stringResource(R.string.app_open_settings), variant = ExpressiveButtonVariant.Outlined, icon = Icons.Filled.Settings, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    confirm?.let { spec ->
        ConfirmDialog(spec.title, spec.text, danger = spec.danger, onConfirm = { confirm = null; spec.action() }, onDismiss = { confirm = null })
    }
}

private data class ConfirmSpec(val title: String, val text: String, val danger: Boolean, val action: () -> Unit)

@Composable
private fun appFilterLabel(filter: AppFilter): String = when (filter) {
    AppFilter.ALL -> stringResource(R.string.app_filter_all)
    AppFilter.USER -> stringResource(R.string.app_filter_user)
    AppFilter.SYSTEM -> stringResource(R.string.app_filter_system)
    AppFilter.DISABLED -> stringResource(R.string.app_filter_disabled)
}

@Composable
private fun appSortLabel(sort: AppSort): String = when (sort) {
    AppSort.NAME -> stringResource(R.string.app_sort_name)
    AppSort.PACKAGE -> stringResource(R.string.app_sort_package)
    AppSort.SIZE -> stringResource(R.string.app_sort_size)
    AppSort.UID -> stringResource(R.string.app_sort_uid)
}
