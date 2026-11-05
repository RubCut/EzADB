package dev.ezadb.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ezadb.core.Risk
import dev.ezadb.core.RiskAssessor
import dev.ezadb.core.TerminalKind
import dev.ezadb.ui.components.ConfirmDialog
import dev.ezadb.ui.components.EmptyState
import dev.ezadb.ui.components.terminalColor
import dev.ezadb.ui.viewmodel.EzUiState
import dev.ezadb.ui.viewmodel.EzViewModel
import dev.ezadb.util.ShareUtils

private val quickCommands = listOf(
    "getprop ro.product.model",
    "pm list packages -3",
    "settings list global",
    "ls -la /sdcard",
    "dumpsys battery",
    "df -h",
    "wm size",
    "ip route",
    "logcat -d -t 50",
    "top -n 1",
    "help",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalScreen(vm: EzViewModel, state: EzUiState) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var pendingCommand by remember { mutableStateOf<String?>(null) }

    fun requestSend(command: String) {
        val risk = RiskAssessor.assess(command)
        if (risk.level == Risk.HIGH && state.prefs.confirmDestructive) pendingCommand = command
        else vm.sendTerminal(command)
    }

    LaunchedEffect(state.connected, state.demo) {
        if (state.connected) vm.ensureTerminal()
    }
    LaunchedEffect(state.terminalLines.size) {
        if (state.terminalLines.isNotEmpty()) listState.animateScrollToItem(state.terminalLines.lastIndex)
    }

    if (!state.connected) {
        EmptyState(Icons.Filled.Terminal, "Терминал недоступен", "Подключитесь к устройству, чтобы открыть ADB-оболочку.")
        return
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickCommands.forEach { command ->
                AssistChip(onClick = { requestSend(command) }, label = { Text(command, maxLines = 1) })
            }
        }
        Surface(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        ) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(12.dp)) {
                items(state.terminalLines) { line ->
                    Text(
                        text = line.text,
                        fontFamily = FontFamily.Monospace,
                        fontSize = state.prefs.terminalFontSize.sp,
                        lineHeight = (state.prefs.terminalFontSize + 5).sp,
                        color = terminalColor(line.kind, MaterialTheme.colorScheme),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilledTonalIconButton(onClick = { vm.terminalHistoryPrevious()?.let(vm::setTerminalInput) }) { Icon(Icons.Filled.KeyboardArrowUp, "Предыдущая команда") }
            FilledTonalIconButton(onClick = { vm.terminalHistoryNext()?.let(vm::setTerminalInput) }) { Icon(Icons.Filled.KeyboardArrowDown, "Следующая команда") }
            OutlinedTextField(
                value = state.terminalInput,
                onValueChange = vm::setTerminalInput,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Введите команду…") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { requestSend(state.terminalInput) }),
                shape = MaterialTheme.shapes.large,
            )
            FilledIconButton(onClick = { requestSend(state.terminalInput) }) { Icon(Icons.Filled.Send, "Отправить") }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { vm.interruptTerminal() }, label = { Text("Ctrl+C") }, leadingIcon = { Icon(Icons.Filled.Stop, null, Modifier.size(16.dp)) })
            AssistChip(onClick = { vm.clearTerminal() }, label = { Text("Очистить") }, leadingIcon = { Icon(Icons.Filled.ClearAll, null, Modifier.size(16.dp)) })
            AssistChip(
                onClick = { ShareUtils.copyText(context, state.terminalLines.joinToString("\n") { it.text }) },
                label = { Text("Копировать") },
                leadingIcon = { Icon(Icons.Filled.ContentCopy, null, Modifier.size(16.dp)) },
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (state.terminalInteractive) "PTY" else if (state.demo) "DEMO" else "RAW",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }

    pendingCommand?.let { command ->
        val risk = RiskAssessor.assess(command)
        ConfirmDialog(
            title = "Опасная команда",
            text = "${risk.reason}.\n\n$command\n\nВыполнить на подключённом устройстве?",
            danger = true,
            confirmLabel = "Выполнить",
            onConfirm = { pendingCommand = null; vm.sendTerminal(command) },
            onDismiss = { pendingCommand = null },
        )
    }
}
