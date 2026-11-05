package dev.ezadb.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SignalCellular4Bar
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ezadb.R
import dev.ezadb.core.AppInfo
import dev.ezadb.core.ConnectionState
import dev.ezadb.core.DeviceInfo
import dev.ezadb.core.Formatters
import dev.ezadb.core.QuickToggleState
import dev.ezadb.core.RemoteFile
import dev.ezadb.core.Risk
import dev.ezadb.core.SettingEntry
import dev.ezadb.core.TerminalKind
import dev.ezadb.ui.i18n.localizedSettingDescription
import dev.ezadb.ui.theme.EzMotion

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ExpressiveLoadingIndicator()
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(32.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            action()
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = stringResource(R.string.confirm),
    danger: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(if (danger) Icons.Filled.Warning else Icons.Filled.Info, contentDescription = null) },
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = if (danger) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors()) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun StatusChip(connected: Boolean, demo: Boolean, modifier: Modifier = Modifier) {
    val target = when {
        demo -> MaterialTheme.colorScheme.tertiary
        connected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    val color by animateColorAsState(target, EzMotion.fastEffects(), label = "statusColor")
    val label = when {
        demo -> stringResource(R.string.demo)
        connected -> stringResource(R.string.connected)
        else -> stringResource(R.string.disconnected)
    }
    Surface(modifier, shape = CircleShape, color = color.copy(alpha = 0.14f), contentColor = color) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Circle, contentDescription = null, modifier = Modifier.size(8.dp), tint = color)
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun DeviceHero(device: DeviceInfo?, connection: ConnectionState, demo: Boolean, onRefresh: () -> Unit, onDisconnect: () -> Unit) {
    ExpressiveCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PhoneAndroid, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(device?.model ?: stringResource(R.string.disconnected), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(device?.manufacturer?.takeIf { it.isNotBlank() }, device?.androidVersion?.takeIf { it.isNotBlank() }?.let { "Android $it" }, device?.endpointLabel?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { stringResource(R.string.usb_or_wifi) },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            StatusChip(connection is ConnectionState.Connected, demo)
        }
        if (connection is ConnectionState.Connecting) {
            ExpressiveWaveProgress(Modifier.fillMaxWidth().height(4.dp))
            Text(stringResource(R.string.connecting) + " " + connection.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (connection is ConnectionState.Error) {
            Text(connection.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
        if (device != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(Icons.Filled.BatteryFull, Formatters.battery(device.battery.level), device.battery.status, Modifier.weight(1f))
                Metric(Icons.Filled.Storage, Formatters.bytes(device.storage.freeBytes), stringResource(R.string.free), Modifier.weight(1f))
                Metric(Icons.Filled.Memory, Formatters.duration(device.uptimeSeconds), stringResource(R.string.uptime), Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric(Icons.Filled.Android, "SDK ${device.sdkInt}", device.abi, Modifier.weight(1f))
                Metric(Icons.Filled.Key, device.securityPatch.ifBlank { "—" }, stringResource(R.string.security_patch), Modifier.weight(1f))
                Metric(Icons.Filled.Link, device.ipAddresses.firstOrNull() ?: "—", stringResource(R.string.ip_address), Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpressiveButton(onClick = onRefresh, text = stringResource(R.string.refresh), variant = ExpressiveButtonVariant.Tonal, icon = Icons.Filled.Refresh)
            if (connection is ConnectionState.Connected) {
                ExpressiveButton(onClick = onDisconnect, text = stringResource(R.string.disconnect), variant = ExpressiveButtonVariant.Text, icon = Icons.Filled.PowerSettingsNew)
            }
        }
    }
}

@Composable
private fun Metric(icon: ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.padding(12.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

fun toggleIcon(id: String): ImageVector = when (id) {
    "wifi" -> Icons.Filled.Wifi
    "bluetooth" -> Icons.Filled.Bluetooth
    "airplane" -> Icons.Filled.SignalCellular4Bar
    "mobile_data" -> Icons.Filled.SignalCellular4Bar
    "location" -> Icons.Filled.LocationOn
    "auto_rotate" -> Icons.Filled.ScreenRotation
    "stay_awake" -> Icons.Filled.Visibility
    "dark_mode" -> Icons.Filled.DarkMode
    "animations" -> Icons.Filled.Tune
    "screen" -> Icons.Filled.PowerSettingsNew
    "show_touches" -> Icons.Filled.TouchApp
    "pointer_location" -> Icons.Filled.LocationOn
    "haptic" -> Icons.Filled.Vibration
    "auto_brightness" -> Icons.Filled.BrightnessAuto
    else -> Icons.Filled.Settings
}

@Composable
fun QuickToggleCard(state: QuickToggleState, onToggle: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    val enabled = state.enabled == true
    val container by animateColorAsState(
        if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceContainerLow,
        EzMotion.fastEffects(),
        label = "toggleContainer",
    )
    ExpressiveCard(
        modifier,
        onClick = { state.enabled?.let { onToggle(!it) } },
        containerColor = container,
        contentPadding = 14.dp,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(toggleIcon(state.id), null, Modifier.size(22.dp), tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(10.dp))
            Text(state.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (state.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Switch(checked = enabled, onCheckedChange = onToggle, enabled = state.enabled != null && !state.busy)
        }
        Text(state.error ?: state.subtitle, style = MaterialTheme.typography.labelSmall, color = if (state.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun AppRow(app: AppInfo, onClick: () -> Unit, onMore: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                buildString {
                    append(app.packageName)
                    app.versionName?.let { append(" · $it") }
                    app.uid?.let { append(" · UID $it") }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(if (app.system) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (app.system) Icons.Filled.Android else Icons.Filled.Apps, null, tint = if (app.system) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer)
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!app.enabled) Badge(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) { Text(stringResource(R.string.app_disabled_badge)) }
                IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, stringResource(R.string.actions)) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun FileRow(file: RemoteFile, onClick: () -> Unit, onMore: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium) },
        supportingContent = {
            Text(
                buildString {
                    append(if (file.isDirectory) stringResource(R.string.folder) else Formatters.bytes(file.sizeBytes))
                    append(" · ${file.permissions}")
                    if (file.mtimeSec > 0) append(" · ${Formatters.epoch(file.mtimeSec)}")
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
            )
        },
        leadingContent = {
            Icon(
                when {
                    file.isDirectory -> Icons.Filled.Folder
                    file.isSymlink -> Icons.Filled.Link
                    else -> Icons.Filled.InsertDriveFile
                },
                null,
                Modifier.size(34.dp),
                tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { IconButton(onClick = onMore) { Icon(Icons.Filled.MoreVert, stringResource(R.string.actions)) } },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

@Composable
fun SettingRow(entry: SettingEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    val description = localizedSettingDescription(entry)
    ListItem(
        modifier = Modifier.clickable(onClick = onEdit),
        headlineContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.key, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f, fill = false))
                if (entry.risk == Risk.HIGH) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Filled.Warning, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                }
            }
        },
        supportingContent = {
            Column {
                Text(entry.value ?: "null", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (description.isNotBlank()) Text(description, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        leadingContent = { Icon(Icons.Filled.Settings, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.delete), tint = MaterialTheme.colorScheme.error) }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

fun terminalColor(kind: TerminalKind, scheme: androidx.compose.material3.ColorScheme): Color = when (kind) {
    TerminalKind.INPUT -> scheme.primary
    TerminalKind.STDOUT -> scheme.onSurface
    TerminalKind.STDERR -> scheme.error
    TerminalKind.SYSTEM -> scheme.tertiary
    TerminalKind.EXIT -> scheme.onSurfaceVariant
}
