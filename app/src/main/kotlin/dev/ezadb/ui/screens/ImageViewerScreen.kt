package dev.ezadb.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.ezadb.R
import dev.ezadb.core.Formatters
import dev.ezadb.ui.components.ExpressiveIconButton
import dev.ezadb.ui.viewmodel.EzViewModel
import dev.ezadb.ui.viewmodel.ImagePreview

/** Built-in viewer for screenshots and remote images: pinch zoom, pan, save and share. */
@Composable
fun ImageViewerScreen(vm: EzViewModel, preview: ImagePreview) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 8f)
        scale = newScale
        offset = if (newScale <= 1.01f) Offset.Zero else offset + panChange
    }
    val image = remember(preview.bitmap) { preview.bitmap.asImageBitmap() }
    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(preview.mime)
    ) { uri -> if (uri != null) vm.savePreview(uri) }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF0B0F0E)) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier.fillMaxSize()
                    .transformable(transformState)
                    .pointerInput(Unit) {
                        detectTapGestures(onDoubleTap = {
                            if (scale > 1.05f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    bitmap = image,
                    contentDescription = preview.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                )
            }

            Column(Modifier.fillMaxWidth().align(Alignment.TopCenter).systemBarsPadding()) {
                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ExpressiveIconButton(
                        onClick = { vm.closePreview() },
                        icon = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.close),
                        containerColor = Color(0x33FFFFFF),
                        contentColor = Color.White,
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            preview.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${preview.width}×${preview.height} · ${Formatters.bytes(preview.sizeBytes)}",
                            color = Color(0xB3FFFFFF),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).systemBarsPadding().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExpressiveIconButton(
                    onClick = { scale = (scale / 1.5f).coerceAtLeast(1f); if (scale == 1f) offset = Offset.Zero },
                    icon = Icons.Filled.ZoomOut,
                    contentDescription = stringResource(R.string.zoom_out),
                    containerColor = Color(0x33FFFFFF),
                    contentColor = Color.White,
                )
                ExpressiveIconButton(
                    onClick = { scale = (scale * 1.5f).coerceAtMost(8f) },
                    icon = Icons.Filled.ZoomIn,
                    contentDescription = stringResource(R.string.zoom_in),
                    containerColor = Color(0x33FFFFFF),
                    contentColor = Color.White,
                )
                ExpressiveIconButton(
                    onClick = { saveLauncher.launch(preview.name) },
                    icon = Icons.Filled.SaveAlt,
                    contentDescription = stringResource(R.string.save_to_device),
                )
                ExpressiveIconButton(
                    onClick = { vm.sharePreview() },
                    icon = Icons.Filled.Share,
                    contentDescription = stringResource(R.string.share),
                )
            }

            if (scale > 1.05f) {
                Text(
                    Formatters.percent((scale - 1f) / 7f),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.Center).background(Color(0x66000000)).padding(6.dp),
                )
            }
        }
    }
}
