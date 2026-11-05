package dev.ezadb.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateFloatAsState
import dev.ezadb.R
import dev.ezadb.core.Formatters
import dev.ezadb.ui.theme.EzMotion
import dev.ezadb.ui.viewmodel.OperationProgress
import dev.ezadb.ui.viewmodel.ProgressPhase

/**
 * Determinate, cancellable progress for installs, transfers and screenshots.
 * Shown as a floating card so the rest of the UI stays visible.
 */
@Composable
fun ProgressOverlay(
    progress: OperationProgress?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = progress != null,
        modifier = modifier,
        enter = fadeIn(EzMotion.fade()) + slideInVertically(EzMotion.spatial()) { it / 2 },
        exit = fadeOut(EzMotion.fade()) + slideOutVertically(EzMotion.spatial()) { it / 2 },
    ) {
        val current = progress ?: return@AnimatedVisibility
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            current.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(
                                when (current.phase) {
                                    ProgressPhase.PREPARING -> R.string.progress_preparing
                                    ProgressPhase.WORKING -> R.string.progress_working
                                    ProgressPhase.TRANSFERRING -> R.string.progress_transferring
                                    ProgressPhase.FINISHING -> R.string.progress_finishing
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ExpressiveIconButton(
                        onClick = onCancel,
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cancel),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }

                val fraction = current.fraction
                if (fraction == null) {
                    ExpressiveWaveProgress(Modifier.fillMaxWidth().height(6.dp))
                } else {
                    val animated by animateFloatAsState(fraction, EzMotion.spatial(), label = "progressFraction")
                    Box(
                        Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    ) {
                        Box(
                            Modifier.fillMaxWidth(animated.coerceIn(0.02f, 1f)).height(10.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        if (current.total != null && current.total > 0) {
                            stringResource(R.string.progress_bytes, Formatters.bytes(current.transferred), Formatters.bytes(current.total))
                        } else {
                            Formatters.bytes(current.transferred)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val speed = current.bytesPerSecond()
                    val eta = current.etaSeconds()
                    Text(
                        when {
                            speed != null && eta != null -> stringResource(R.string.progress_speed, Formatters.rate(speed), Formatters.eta(eta))
                            speed != null -> Formatters.rate(speed)
                            fraction != null -> Formatters.percent(fraction)
                            else -> stringResource(R.string.progress_working)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
