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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFileRenameOutline
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFloatingActionButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ezadb.R
import dev.ezadb.core.Collections
import dev.ezadb.core.FileSort
import dev.ezadb.core.Formatters
import dev.ezadb.core.RemoteFile
import dev.ezadb.ui.components.ConfirmDialog
import dev.ezadb.ui.components.EmptyState
import dev.ezadb.ui.components.ExpressiveButton
import dev.ezadb.ui.components.ExpressiveButtonVariant
import dev.ezadb.ui.components.ExpressiveIconButton
import dev.ezadb.ui.components.ExpressiveWaveProgress
import dev.ezadb.ui.components.FileRow
import dev.ezadb.ui.viewmodel.EzUiState
import dev.ezadb.ui.viewmodel.EzViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
        EmptyState(Icons.Filled.Folder, stringResource(R.string.files), stringResource(R.string.files_empty))
        return
    }

    val files = Collections.filterFiles(state.files, state.fileQuery, state.fileSort)

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ExpressiveIconButton(onClick = { vm.navigateUp() }, icon = Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.file_up), enabled = state.currentPath != "/")
                Spacer(Modifier.width(8.dp))
                Text(
                    state.currentPath,
                    modifier = Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                ExpressiveIconButton(onClick = { showNewFolder = true }, icon = Icons.Filled.CreateNewFolder, contentDescription = stringResource(R.string.new_folder))
            }
            OutlinedTextField(
                value = state.fileQuery,
                onValueChange = vm::setFileQuery,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text(stringResource(R.string.file_search_hint)) },
                leadingIcon = { Icon(Icons.Filled.Search, null) },
                singleLine = true,
                shape = MaterialTheme.shapes.extraLarge,
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.files_count, files.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Box {
                    TextButton(onClick = { sortMenu = true }) { Icon(Icons.Filled.Sort, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(fileSortLabel(state.fileSort)) }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        FileSort.entries.forEach { sort -> DropdownMenuItem(text = { Text(fileSortLabel(sort)) }, onClick = { vm.setFileSort(sort); sortMenu = false }) }
                    }
                }
            }
            if (state.filesLoading) ExpressiveWaveProgress(Modifier.fillMaxWidth().height(4.dp))
            LazyColumn(Modifier.weight(1f)) {
                items(files, key = { it.path }) { file ->
                    Box(Modifier.animateItem()) {
                        FileRow(file, onClick = { vm.openFile(file) }, onMore = { actionFile = file })
                    }
                }
            }
        }
        LargeFloatingActionButton(
            onClick = { uploadLauncher.launch(arrayOf("*/*")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        ) { Icon(Icons.Filled.Upload, stringResource(R.string.file_upload)) }
    }

    actionFile?.let { file ->
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(onDismissRequest = { actionFile = null }, sheetState = sheetState) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(file.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(file.path, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                ExpressiveButton(onClick = { actionFile = null; downloadTarget = file; downloadLauncher.launch(file.name) }, text = stringResource(R.string.download), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.Download, enabled = !file.isDirectory, modifier = Modifier.fillMaxWidth())
                ExpressiveButton(onClick = { actionFile = null; renameTarget = file }, text = stringResource(R.string.rename), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.DriveFileRenameOutline, modifier = Modifier.fillMaxWidth())
                ExpressiveButton(onClick = { actionFile = null; chmodTarget = file }, text = stringResource(R.string.permissions), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.Lock, modifier = Modifier.fillMaxWidth())
                ExpressiveButton(onClick = { actionFile = null; propertiesTarget = file }, text = stringResource(R.string.properties), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.Info, modifier = Modifier.fillMaxWidth())
                ExpressiveButton(onClick = { actionFile = null; deleteTarget = file }, text = stringResource(R.string.delete), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.Delete, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showNewFolder) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolder = false },
            title = { Text(stringResource(R.string.new_folder)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.folder_name)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { showNewFolder = false; if (name.isNotBlank()) vm.mkdir(name.trim()) }) { Text(stringResource(R.string.create)) } },
            dismissButton = { TextButton(onClick = { showNewFolder = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    renameTarget?.let { file ->
        var name by remember(file.path) { mutableStateOf(file.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text(stringResource(R.string.rename)) },
            text = { OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.new_name)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renameTarget = null; if (name.isNotBlank()) vm.renameFile(file, name.trim()) }) { Text(stringResource(R.string.save)) } },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    deleteTarget?.let { file ->
        ConfirmDialog(
            title = stringResource(R.string.file_delete_title, file.name),
            text = if (file.isDirectory) stringResource(R.string.file_delete_folder_text) else stringResource(R.string.file_delete_file_text),
            danger = true,
            confirmLabel = stringResource(R.string.delete),
            onConfirm = { deleteTarget = null; vm.deleteFile(file) },
            onDismiss = { deleteTarget = null },
        )
    }

    chmodTarget?.let { file ->
        var mode by remember(file.path) { mutableStateOf("755") }
        AlertDialog(
            onDismissRequest = { chmodTarget = null },
            title = { Text(stringResource(R.string.permissions)) },
            text = {
                Column {
                    Text(file.permissions, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(mode, { mode = it }, label = { Text(stringResource(R.string.chmod_hint)) }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { chmodTarget = null; vm.chmodFile(file, mode.trim()) }) { Text(stringResource(R.string.apply)) } },
            dismissButton = { TextButton(onClick = { chmodTarget = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    propertiesTarget?.let { file ->
        val type = when {
            file.isDirectory -> stringResource(R.string.folder)
            file.isSymlink -> stringResource(R.string.symlink)
            else -> stringResource(R.string.file)
        }
        AlertDialog(
            onDismissRequest = { propertiesTarget = null },
            title = { Text(stringResource(R.string.properties)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${stringResource(R.string.name)}: ${file.name}")
                    Text(stringResource(R.string.file_path, file.path))
                    Text(stringResource(R.string.file_type, type))
                    Text(stringResource(R.string.file_size, Formatters.bytes(file.sizeBytes)))
                    Text(stringResource(R.string.file_permissions, file.permissions))
                    Text(stringResource(R.string.file_modified, Formatters.epoch(file.mtimeSec)))
                    Text(stringResource(R.string.file_mode, file.mode.toString(8)))
                }
            },
            confirmButton = { TextButton(onClick = { propertiesTarget = null }) { Text(stringResource(R.string.close)) } },
        )
    }

    state.editorFile?.let { file ->
        Dialog(onDismissRequest = { vm.closeEditor() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.closeEditor() }) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
                        Column(Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                            Text(file.path + if (state.editorDirty) " · " + stringResource(R.string.file_editor_dirty) else "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        ExpressiveButton(onClick = { vm.saveEditor() }, text = stringResource(R.string.save), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.Save, enabled = state.editorDirty)
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

@Composable
private fun fileSortLabel(sort: FileSort): String = when (sort) {
    FileSort.NAME -> stringResource(R.string.name)
    FileSort.SIZE -> stringResource(R.string.size)
    FileSort.MTIME -> stringResource(R.string.date)
    FileSort.TYPE -> stringResource(R.string.type)
}
