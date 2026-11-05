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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ezadb.core.Collections
import dev.ezadb.core.FileSort
import dev.ezadb.core.Formatters
import dev.ezadb.core.PathUtils
import dev.ezadb.core.RemoteFile
import dev.ezadb.ui.components.ConfirmDialog
import dev.ezadb.ui.components.EmptyState
import dev.ezadb.ui.components.FileRow
import dev.ezadb.ui.components.SectionHeader
import dev.ezadb.ui.viewmodel.EzUiState
import dev.ezadb.ui.viewmodel.EzViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(vm: EzViewModel, state: EzUiState) {
    var actionFile by remember { mutableStateOf<RemoteFile?>(null) }
    var showNewFolder by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var deleteTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var chmodTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var propertiesTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var downloadTarget by remember { mutableStateOf<RemoteFile?>(null) }
    var sortMenu by remember { mutableStateOf(false) }

    val uploadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.uploadFile(uri)
    }
    val downloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val file = downloadTarget
        if (uri != null && file != null) vm.downloadFile(file, uri)
        downloadTarget = null
    }

    LaunchedEffect(state.connected, state.demo) {
        if (state.connected) vm.refreshFiles()
    }

    if (!state.connected) {
        EmptyState(Icons.Filled.Folder, "Файлы", "Подключитесь к устройству, чтобы открыть файловый менеджер.")
        return
    }

    val files = Collections.filterFiles(state.files, state.fileQuery, state.fileSort)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = { vm.navigateUp() }, enabled = state.currentPath != "/") { Icon(Icons.Filled.ArrowUpward, "Вверх") }
                Spacer(Modifier.width(8.dp))
                Text(
                    state.currentPath,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                FilledTonalIconButton(onClick = { showNewFolder = true }) { Icon(Icons.Filled.CreateNewFolder, "Новая папка") }
            }
            OutlinedTextField(
                value = state.fileQuery,
                onValueChange = vm::setFileQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Поиск в текущей папке") },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${files.size} объектов", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(state.fileSort.label()) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        FileSort.entries.forEach { sort -> DropdownMenuItem(text = { Text(sort.label()) }, onClick = { vm.setFileSort(sort); sortMenu = false }) }
                    }
                }
            }
            if (state.filesLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.weight(1f)) {
                items(files, key = { it.path }) { file ->
                    FileRow(file, onClick = { vm.openFile(file) }, onMore = { actionFile = file })
                }
            }
        }
        LargeFloatingActionButton(
            onClick = { uploadLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Upload, "Загрузить файл") }
    }

    actionFile?.let { file ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { actionFile = null }, sheetState = sheetState) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(file.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(onClick = { actionFile = null; downloadTarget = file; downloadLauncher.launch(file.name) }, enabled = !file.isDirectory, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Download, null); Spacer(Modifier.width(8.dp)); Text("Скачать") }
                FilledTonalButton(onClick = { actionFile = null; renameTarget = file }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.DriveFileRenameOutline, null); Spacer(Modifier.width(8.dp)); Text("Переименовать") }
                FilledTonalButton(onClick = { actionFile = null; chmodTarget = file }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Lock, null); Spacer(Modifier.width(8.dp)); Text("Права доступа") }
                FilledTonalButton(onClick = { actionFile = null; propertiesTarget = file }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Info, null); Spacer(Modifier.width(8.dp)); Text("Свойства") }
                FilledTonalButton(onClick = { actionFile = null; deleteTarget = file }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error); Spacer(Modifier.width(8.dp)); Text("Удалить", color = MaterialTheme.colorScheme.error) }
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showNewFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text("Новая папка") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Имя папки") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { showNewFolder = false; if (name.isNotBlank()) vm.mkdir(name.trim()) }) { Text("Создать") } },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text("Отмена") } },
        )
    }

    renameTarget?.let { file ->
        var name by remember(file.path) { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Переименовать") },
            text = { OutlinedTextField(name, { name = it }, label = { Text("Новое имя") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renameTarget = null; if (name.isNotBlank()) vm.renameFile(file, name.trim()) }) { Text("Сохранить") } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Отмена") } },
        )
    }

    deleteTarget?.let { file ->
        ConfirmDialog(
            title = "Удалить ${file.name}?",
            text = if (file.isDirectory) "Папка и всё её содержимое будут удалены безвозвратно." else "Файл будет удалён безвозвратно.",
            danger = true,
            confirmLabel = "Удалить",
            onConfirm = { deleteTarget = null; vm.deleteFile(file) },
            onDismiss = { deleteTarget = null },
        )
    }

    chmodTarget?.let { file ->
        var mode by remember(file.path) { mutableStateOf("755") }
        AlertDialog(
            onDismissRequest = { chmodTarget = null },
            title = { Text("Права доступа") },
            text = {
                Column {
                    Text(file.permissions, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(mode, { mode = it }, label = { Text("chmod (например 644)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { chmodTarget = null; vm.chmodFile(file, mode.trim()) }) { Text("Применить") } },
            dismissButton = { TextButton(onClick = { chmodTarget = null }) { Text("Отмена") } },
        )
    }

    propertiesTarget?.let { file ->
        AlertDialog(
            onDismissRequest = { propertiesTarget = null },
            title = { Text("Свойства") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Имя: ${file.name}")
                    Text("Путь: ${file.path}")
                    Text("Тип: ${if (file.isDirectory) "папка" else if (file.isSymlink) "ссылка" else "файл"}")
                    Text("Размер: ${Formatters.bytes(file.sizeBytes)}")
                    Text("Права: ${file.permissions}")
                    Text("Изменён: ${Formatters.epoch(file.mtimeSec)}")
                    Text("Режим: ${file.mode.toString(8)}")
                }
            },
            confirmButton = { TextButton(onClick = { propertiesTarget = null }) { Text("Закрыть") } },
        )
    }

    state.editorFile?.let { file ->
        Dialog(onDismissRequest = { vm.closeEditor() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.closeEditor() }) { Icon(Icons.Filled.Close, "Закрыть") }
                        Column(Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(file.path + if (state.editorDirty) " · изменён" else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        FilledTonalButton(onClick = { vm.saveEditor() }, enabled = state.editorDirty) { Icon(Icons.Filled.Save, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Сохранить") }
                    }
                    OutlinedTextField(
                        value = state.editorText,
                        onValueChange = vm::updateEditor,
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                    )
                }
            }
        }
    }
}

private fun FileSort.label(): String = when (this) {
    FileSort.NAME -> "Имя"
    FileSort.SIZE -> "Размер"
    FileSort.MTIME -> "Дата"
    FileSort.TYPE -> "Тип"
}
