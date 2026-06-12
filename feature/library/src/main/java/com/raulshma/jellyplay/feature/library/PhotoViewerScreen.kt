package com.raulshma.jellyplay.feature.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

@Composable
fun PhotoViewerScreen(
    itemId: String,
    parentId: String?,
    onBack: () -> Unit,
    viewModel: PhotoViewerViewModel = hiltViewModel(),
) {
    val isTv = LocalTvMode.current
    val photo by viewModel.photo
    val siblings by viewModel.siblings
    val currentIndex by viewModel.currentIndex
    val isLoading by viewModel.isLoading
    val error by viewModel.error

    LaunchedEffect(itemId, parentId) {
        viewModel.load(itemId, parentId)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(currentIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { keyEvent ->
                if (keyEvent.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (keyEvent.key) {
                    Key.Back -> {
                        onBack()
                        true
                    }
                    Key.DirectionLeft -> {
                        if (viewModel.hasPrevious()) {
                            viewModel.navigateTo(currentIndex - 1)
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        if (viewModel.hasNext()) {
                            viewModel.navigateTo(currentIndex + 1)
                        }
                        true
                    }
                    else -> false
                }
            },
    ) {
        when {
            isLoading -> {
                LoadingScreen()
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = error ?: "Failed to load photo",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            photo != null -> {
                val imageUrl = remember(photo!!.id) {
                    viewModel.getImageUrl(photo!!.id, maxWidth = 1920)
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(0.5f, 5f)
                                if (scale > 1f) {
                                    offsetX += pan.x
                                    offsetY += pan.y
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    if (!isTv) showControls = !showControls
                                },
                                onDoubleTap = {
                                    if (scale > 1f) {
                                        scale = 1f
                                        offsetX = 0f
                                        offsetY = 0f
                                    } else {
                                        scale = 2.5f
                                    }
                                },
                            )
                        }
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offsetX,
                            translationY = offsetY,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaImage(
                        url = imageUrl,
                        contentDescription = photo!!.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }

                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .padding(16.dp),
                    ) {
                        if (!isTv) {
                            OverlayIconButton(
                                onClick = onBack,
                                modifier = Modifier.align(Alignment.TopEnd),
                            ) {
                                Icon(
                                    Tabler.Outline.X,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                )
                            }
                        }

                        if (siblings.size > 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!isTv && viewModel.hasPrevious()) {
                                    OverlayIconButton(
                                        onClick = { viewModel.navigateTo(currentIndex - 1) },
                                    ) {
                                        Icon(
                                            Tabler.Outline.ChevronLeft,
                                            contentDescription = "Previous",
                                            tint = Color.White.copy(alpha = 0.7f),
                                        )
                                    }
                                    Spacer(modifier = Modifier.size(12.dp))
                                }

                                Text(
                                    text = "${currentIndex + 1} / ${siblings.size}",
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.labelLarge,
                                )

                                if (!isTv && viewModel.hasNext()) {
                                    Spacer(modifier = Modifier.size(12.dp))
                                    OverlayIconButton(
                                        onClick = { viewModel.navigateTo(currentIndex + 1) },
                                    ) {
                                        Icon(
                                            Tabler.Outline.ChevronRight,
                                            contentDescription = "Next",
                                            tint = Color.White.copy(alpha = 0.7f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .pointerInput(onClick) {
                detectTapGestures { onClick() }
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
