package dev.ezadb.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ezadb.ui.theme.EzButtonCorner
import dev.ezadb.ui.theme.EzButtonPressedCorner
import dev.ezadb.ui.theme.EzCardCorner
import dev.ezadb.ui.theme.EzCardPressedCorner
import dev.ezadb.ui.theme.EzMotion
import dev.ezadb.ui.theme.EzMotionSpecs
import kotlin.math.PI
import kotlin.math.sin

enum class ExpressiveButtonVariant { Filled, Tonal, Outlined, Text }

data class EzNavItem(val route: String, val label: String, val icon: ImageVector)

@Composable
fun ExpressiveCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val interactive = onClick != null
    val scale by animateFloatAsState(if (pressed && interactive) 0.975f else 1f, EzMotion.fastSpatial(), label = "cardScale")
    val corner by animateDpAsState(if (pressed && interactive) EzCardPressedCorner else EzCardCorner, EzMotion.fastSpatial(), label = "cardCorner")
    val clickModifier = if (onClick != null) {
        Modifier.clickable(interactionSource = interaction, indication = ripple(), onClick = onClick)
    } else Modifier
    Surface(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.then(clickModifier),
        shape = RoundedCornerShape(corner),
        color = containerColor,
        contentColor = contentColor,
        tonalElevation = 1.dp,
        shadowElevation = if (pressed && interactive) 0.dp else 1.dp,
    ) {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    variant: ExpressiveButtonVariant = ExpressiveButtonVariant.Filled,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.96f else 1f, EzMotion.fastSpatial(), label = "buttonScale")
    val corner by animateDpAsState(if (pressed && enabled) EzButtonPressedCorner else EzButtonCorner, EzMotion.fastSpatial(), label = "buttonCorner")
    val shape = RoundedCornerShape(corner)
    val mod = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    val content: @Composable RowScope.() -> Unit = {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        }
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    when (variant) {
        ExpressiveButtonVariant.Filled -> Button(onClick, mod, enabled, shape = shape, interactionSource = interaction, content = content)
        ExpressiveButtonVariant.Tonal -> FilledTonalButton(onClick, mod, enabled, shape = shape, interactionSource = interaction, content = content)
        ExpressiveButtonVariant.Outlined -> OutlinedButton(onClick, mod, enabled, shape = shape, interactionSource = interaction, content = content)
        ExpressiveButtonVariant.Text -> TextButton(onClick, mod, enabled, shape = CircleShape, interactionSource = interaction, content = content)
    }
}

@Composable
fun ExpressiveIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed && enabled) 0.9f else 1f, EzMotion.fastSpatial(), label = "iconScale")
    val corner by animateDpAsState(if (pressed && enabled) 18.dp else 28.dp, EzMotion.fastSpatial(), label = "iconCorner")
    Surface(
        modifier = modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(corner))
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(corner),
        color = if (enabled) containerColor else containerColor.copy(alpha = 0.4f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
        tonalElevation = 2.dp,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription, Modifier.size(24.dp))
        }
    }
}

@Composable
fun ExpressiveToggleChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val corner by animateDpAsState(if (selected || pressed) 50.dp else 16.dp, EzMotion.fastSpatial(), label = "chipCorner")
    val container by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f)
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        EzMotion.fastEffects(),
        label = "chipContainer",
    )
    val content by animateColorAsState(
        when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        EzMotion.fastEffects(),
        label = "chipContent",
    )
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(corner))
            .clickable(interactionSource = interaction, indication = ripple(), enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(corner),
        color = container,
        contentColor = content,
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.animation.AnimatedVisibility(visible = selected, enter = scaleIn(EzMotion.fastSpatial()) + fadeIn(EzMotion.fade()), exit = scaleOut(EzMotion.fastSpatial()) + fadeOut(EzMotion.fade())) {
                Row {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
                }
            }
            if (icon != null && !selected) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                androidx.compose.foundation.layout.Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun <T> SegmentedButtonRow(
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    Row(modifier.clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
        items.forEachIndexed { index, item ->
            val isSelected = item == selected
            val container by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, EzMotion.fastEffects(), label = "segContainer")
            val content by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, EzMotion.fastEffects(), label = "segContent")
            val shape = when {
                items.size == 1 -> CircleShape
                index == 0 -> RoundedCornerShape(topStart = 50.dp, bottomStart = 50.dp)
                index == items.lastIndex -> RoundedCornerShape(topEnd = 50.dp, bottomEnd = 50.dp)
                else -> RectangleShape
            }
            Surface(
                modifier = Modifier.weight(1f).clickable { onSelected(item) },
                shape = shape,
                color = container,
                contentColor = content,
            ) {
                Box(Modifier.fillMaxWidth().padding(vertical = 10.dp, horizontal = 6.dp), contentAlignment = Alignment.Center) {
                    Text(label(item), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                }
            }
        }
    }
}

@Composable
fun ExpressiveWaveProgress(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val reduced = EzMotion.reduced()
    val infinite = rememberInfiniteTransition(label = "wave")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "wavePhase",
    )
    Canvas(modifier) {
        val stroke = 4.dp.toPx()
        val path = Path()
        val amplitude = if (reduced) 0f else size.height * 0.28f
        var x = 0f
        while (x <= size.width) {
            val y = size.height / 2f + sin((x / size.width.coerceAtLeast(1f)) * 2f * PI.toFloat() + phase) * amplitude
            if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
            x += 3f
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
fun ExpressiveLoadingIndicator(modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    ExpressiveWaveProgress(modifier.size(width = 56.dp, height = 20.dp), color)
}

@Composable
fun FloatingNavBar(items: List<EzNavItem>, currentRoute: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val iconScale by animateFloatAsState(if (selected) 1.15f else 1f, EzMotion.fastSpatial(), label = "navIcon")
                val indicatorWidth by animateDpAsState(if (selected) 56.dp else 0.dp, EzMotion.fastSpatial(), label = "navIndicator")
                val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, EzMotion.fastEffects(), label = "navTint")
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clip(CircleShape).clickable { onNavigate(item.route) }.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Box(Modifier.height(32.dp).width(64.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.height(32.dp).width(indicatorWidth).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent))
                        Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }, tint = tint)
                    }
                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun FloatingNavRail(items: List<EzNavItem>, currentRoute: String, onNavigate: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxHeight().padding(horizontal = 8.dp, vertical = 12.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.fillMaxHeight().padding(vertical = 10.dp, horizontal = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically), horizontalAlignment = Alignment.CenterHorizontally) {
            items.forEach { item ->
                val selected = currentRoute == item.route
                val iconScale by animateFloatAsState(if (selected) 1.15f else 1f, EzMotion.fastSpatial(), label = "railIcon")
                val indicatorSize by animateDpAsState(if (selected) 56.dp else 0.dp, EzMotion.fastSpatial(), label = "railIndicator")
                val tint by animateColorAsState(if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant, EzMotion.fastEffects(), label = "railTint")
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(CircleShape).clickable { onNavigate(item.route) }.padding(6.dp)) {
                    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(indicatorSize).clip(CircleShape).background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent))
                        Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(24.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }, tint = tint)
                    }
                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun EzTopBar(
    title: String,
    subtitle: String?,
    connected: Boolean,
    demo: Boolean,
    onConnect: () -> Unit,
    connectContentDescription: String,
    modifier: Modifier = Modifier,
) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = title,
                    transitionSpec = {
                        (fadeIn(EzMotionSpecs.fade()) + slideInVertically(EzMotionSpecs.fastSpatial()) { it / 3 })
                            .togetherWith(fadeOut(EzMotionSpecs.fade()) + slideOutVertically(EzMotionSpecs.fastSpatial()) { -it / 3 })
                    },
                    label = "topTitle",
                ) { value ->
                    Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            StatusChip(connected = connected, demo = demo)
            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
            ExpressiveIconButton(
                onClick = onConnect,
                icon = if (connected) Icons.Filled.Link else Icons.Filled.Cable,
                contentDescription = connectContentDescription,
            )
        }
    }
}
