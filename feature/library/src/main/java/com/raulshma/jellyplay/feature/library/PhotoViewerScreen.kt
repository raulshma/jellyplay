package com.raulshma.jellyplay.feature.library

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Share
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent

@Composable
fun PhotoViewerScreen(
    itemId: String,
    parentId: String?,
    onBack: () -> Unit,
    viewModel: PhotoViewerViewModel = hiltViewModel(),
) {
    val isTv = LocalTvMode.current
    val context = LocalContext.current
    val photo by viewModel.photo
    val siblings by viewModel.siblings
    val currentIndex by viewModel.currentIndex
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val photoDetail by viewModel.photoDetail
    val isSlideshowActive by viewModel.isSlideshowActive
    val isSaving by viewModel.isSaving
    val saveResult by viewModel.saveResult

    LaunchedEffect(itemId, parentId) {
        viewModel.load(itemId, parentId)
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var showFilmstrip by remember { mutableStateOf(false) }
    var filmstripFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(currentIndex) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    LaunchedEffect(showFilmstrip) {
        kotlinx.coroutines.delay(100)
        try {
            if (showFilmstrip) {
                filmstripFocusRequester.requestFocus()
            } else {
                rootFocusRequester.requestFocus()
            }
        } catch (e: Exception) {
            // Ignore if not attached
        }
    }

    LaunchedEffect(saveResult) {
        if (saveResult is SaveResult.Success) {
            kotlinx.coroutines.delay(2000)
            viewModel.clearSaveResult()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onDpadKeyEvent(
                onBack = { e ->
                    if (e.isKeyUp) {
                        when {
                            showInfo -> showInfo = false
                            showFilmstrip -> showFilmstrip = false
                            isSlideshowActive -> viewModel.stopSlideshow()
                            else -> onBack()
                        }
                    }
                    true
                },
                onLeft = { e ->
                    if (e.isKeyUp && !showFilmstrip && viewModel.hasPrevious()) {
                        viewModel.navigateTo(currentIndex - 1)
                    }
                    true
                },
                onRight = { e ->
                    if (e.isKeyUp && !showFilmstrip && viewModel.hasNext()) {
                        viewModel.navigateTo(currentIndex + 1)
                    }
                    true
                },
                onUp = { e ->
                    if (e.isKeyUp) {
                        showInfo = !showInfo
                        showFilmstrip = false
                    }
                    true
                },
                onDown = { e ->
                    if (e.isKeyUp && siblings.size > 1) {
                        showFilmstrip = !showFilmstrip
                        showInfo = false
                    }
                    true
                },
                onSelect = { e ->
                    if (e.isKeyUp && !isTv) showControls = !showControls
                    true
                },
                onPlayPause = { e ->
                    if (e.isKeyUp) viewModel.toggleSlideshow()
                    true
                },
            ),
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
                PhotoImage(
                    photo = photo!!,
                    viewModel = viewModel,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    onScaleChange = { scale = it },
                    onOffsetChange = { x, y -> offsetX = x; offsetY = y },
                    onTap = { if (!isTv) showControls = !showControls },
                    onDoubleTap = {
                        if (scale > 1f) {
                            scale = 1f; offsetX = 0f; offsetY = 0f
                        } else {
                            scale = 2.5f
                        }
                    },
                )

                AnimatedVisibility(
                    visible = showControls || isTv,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                OverlayIconButton(
                                    onClick = onBack,
                                ) {
                                    Icon(Tabler.Outline.X, contentDescription = "Close", tint = Color.White)
                                }
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = photo?.name ?: "",
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            if (!isTv) {
                                Spacer(modifier = Modifier.size(8.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    OverlayActionButton(
                                        onClick = {
                                            viewModel.sharePhoto(context) { errorMsg ->
                                                android.widget.Toast.makeText(context, errorMsg, android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    ) {
                                        Icon(Tabler.Outline.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }

                                    OverlayActionButton(
                                        onClick = { viewModel.savePhotoToGallery() },
                                    ) {
                                        if (isSaving) {
                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                                        } else {
                                            Icon(Tabler.Outline.Download, contentDescription = "Save", tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    OverlayActionButton(
                                        onClick = { viewModel.toggleSlideshow() },
                                    ) {
                                        Icon(
                                            if (isSlideshowActive) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                                            contentDescription = if (isSlideshowActive) "Stop slideshow" else "Start slideshow",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    OverlayActionButton(
                                        onClick = { showInfo = !showInfo },
                                    ) {
                                        Icon(Tabler.Outline.InfoCircle, contentDescription = "Info", tint = Color.White, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }

                        if (siblings.size > 1) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .padding(bottom = if (siblings.size > 1) 88.dp else 24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (!isTv && viewModel.hasPrevious()) {
                                    OverlayIconButton(
                                        onClick = { viewModel.navigateTo(currentIndex - 1) },
                                    ) {
                                        Icon(Tabler.Outline.ChevronLeft, contentDescription = "Previous", tint = Color.White.copy(alpha = 0.7f))
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
                                        Icon(Tabler.Outline.ChevronRight, contentDescription = "Next", tint = Color.White.copy(alpha = 0.7f))
                                    }
                                }

                                if (isSlideshowActive) {
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        shape = RoundedCornerShape(4.dp),
                                    ) {
                                        Text(
                                            text = "Slideshow",
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (saveResult is SaveResult.Success) {
                            Surface(
                                modifier = Modifier.align(Alignment.BottomStart),
                                color = Color.Black.copy(alpha = 0.7f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text(
                                    text = "Saved to gallery",
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = showInfo,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 3 },
                    exit = fadeOut(tween(200)) + slideOutVertically(tween(200)) { -it / 3 },
                ) {
                    PhotoInfoOverlay(
                        photo = photo,
                        detail = photoDetail,
                        onDismiss = { showInfo = false },
                    )
                }

                if (siblings.size > 1) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding(),
                    ) {
                        PhotoFilmstrip(
                            siblings = siblings,
                            currentIndex = currentIndex,
                            onPhotoClick = { index ->
                                viewModel.navigateTo(index)
                            },
                            getThumbnailUrl = { viewModel.getThumbnailUrl(it) },
                            focusRequester = filmstripFocusRequester,
                            onBack = { showFilmstrip = false },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoImage(
    photo: com.raulshma.jellyplay.core.model.MediaItem,
    viewModel: PhotoViewerViewModel,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (x: Float, y: Float) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
) {
    val imageUrl = remember(photo.id) {
        viewModel.getImageUrl(photo.id, maxWidth = 1920)
    }
    var lastTapTime by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photo.id) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown()
                    firstDown.consume()

                    var gestureScale = scale
                    var gestureOffsetX = offsetX
                    var gestureOffsetY = offsetY

                    val downPosition = firstDown.position
                    var isMultiTouch = false
                    var pastSlop = false
                    var previousDistance = 0f
                    var previousCentroid = firstDown.position
                    var dragDeltaX = 0f
                    var dragDeltaY = 0f

                    do {
                        val event = awaitPointerEvent()
                        val changes = event.changes

                        when {
                            changes.size >= 2 -> {
                                isMultiTouch = true
                                pastSlop = true
                                val pos0 = changes[0].position
                                val pos1 = changes[1].position
                                val distance = (pos0 - pos1).getDistance()
                                val centroid = (pos0 + pos1) / 2f

                                if (previousDistance > 0f) {
                                    val zoom = distance / previousDistance
                                    val pan = centroid - previousCentroid
                                    gestureScale = (gestureScale * zoom).coerceIn(0.5f, 5f)
                                    onScaleChange(gestureScale)
                                    if (gestureScale > 1f) {
                                        gestureOffsetX += pan.x
                                        gestureOffsetY += pan.y
                                        onOffsetChange(gestureOffsetX, gestureOffsetY)
                                    } else {
                                        gestureOffsetX = 0f
                                        gestureOffsetY = 0f
                                        onOffsetChange(0f, 0f)
                                    }
                                }

                                previousDistance = distance
                                previousCentroid = centroid
                            }
                            !isMultiTouch -> {
                                val change = changes.first()
                                val currentPos = change.position
                                dragDeltaX = currentPos.x - downPosition.x
                                dragDeltaY = currentPos.y - downPosition.y
                                val movement = (currentPos - downPosition).getDistance()

                                if (!pastSlop && movement > viewConfiguration.touchSlop) {
                                    pastSlop = true
                                }

                                if (pastSlop && gestureScale > 1f) {
                                    val posChange = change.positionChange()
                                    gestureOffsetX += posChange.x
                                    gestureOffsetY += posChange.y
                                    onOffsetChange(gestureOffsetX, gestureOffsetY)
                                }
                            }
                        }

                        changes.forEach { it.consume() }
                    } while (changes.any { it.pressed })

                    if (!pastSlop && !isMultiTouch) {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 300) {
                            onDoubleTap()
                            lastTapTime = 0L
                        } else {
                            onTap()
                            lastTapTime = now
                        }
                    } else if (!isMultiTouch && pastSlop && gestureScale <= 1f) {
                        val swipeThreshold = 150f // pixels
                        if (Math.abs(dragDeltaX) > swipeThreshold && Math.abs(dragDeltaX) > Math.abs(dragDeltaY)) {
                            if (dragDeltaX > 0) {
                                // Swipe right -> previous photo
                                if (viewModel.hasPrevious()) {
                                    viewModel.navigateTo(viewModel.currentIndex.value - 1)
                                }
                            } else {
                                // Swipe left -> next photo
                                if (viewModel.hasNext()) {
                                    viewModel.navigateTo(viewModel.currentIndex.value + 1)
                                }
                            }
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        MediaImage(
            url = imageUrl,
            contentDescription = photo.name,
            blurHash = photo.blurHashes.primary,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun PhotoInfoOverlay(
    photo: com.raulshma.jellyplay.core.model.MediaItem?,
    detail: com.raulshma.jellyplay.core.model.MediaDetail?,
    onDismiss: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        color = Color.Black.copy(alpha = 0.85f),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = photo?.name ?: "Photo Info",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                OverlayIconButton(onClick = onDismiss) {
                    Icon(Tabler.Outline.X, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            if (detail != null) {
                InfoRow("Date", detail.dateCreated)
                InfoRow("Tags", detail.tagItems.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name })
                InfoRow("People", detail.people.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.name })

                val mediaSource = detail.mediaSources.firstOrNull()
                if (mediaSource != null) {
                    InfoRow("File", mediaSource.name)
                    InfoRow("Size", mediaSource.size?.let { bytes ->
                        when {
                            bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
                            bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
                            else -> "%.1f KB".format(bytes / 1_000.0)
                        }
                    })
                    mediaSource.mediaStreams.firstOrNull { it.width != null && it.height != null }?.let { stream ->
                        InfoRow("Resolution", "${stream.width} x ${stream.height}")
                    }
                }

                val overview = photo?.overview
                if (overview != null) {
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 3,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value != null) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.width(80.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun PhotoFilmstrip(
    siblings: List<com.raulshma.jellyplay.core.model.MediaItem>,
    currentIndex: Int,
    onPhotoClick: (Int) -> Unit,
    getThumbnailUrl: (String) -> String,
    focusRequester: FocusRequester,
    onBack: () -> Unit,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        if (currentIndex in siblings.indices) {
            listState.animateScrollToItem((currentIndex - 2).coerceAtLeast(0))
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.Black.copy(alpha = 0.8f),
    ) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onDpadKeyEvent(
                    onBack = { e ->
                        if (e.isKeyUp) onBack()
                        true
                    },
                    onSelect = { e ->
                        true
                    },
                ),
        ) {
            itemsIndexed(
                items = siblings,
                key = { _, item -> item.id },
            ) { index, item ->
                val isSelected = index == currentIndex
                val interactionSource = remember { MutableInteractionSource() }
                val isFocused by interactionSource.collectIsFocusedAsState()
                val isHighlighted = isSelected || isFocused

                Box(
                    modifier = Modifier
                        .size(if (isHighlighted) 64.dp else 52.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.DarkGray.copy(alpha = 0.5f))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                        ) { onPhotoClick(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    MediaImage(
                        url = getThumbnailUrl(item.id),
                        contentDescription = item.name,
                        blurHash = item.blurHashes.primary,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    if (isHighlighted) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isFocused) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                )
                        )
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
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun OverlayActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
