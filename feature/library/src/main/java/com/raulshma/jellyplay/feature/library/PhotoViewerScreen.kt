package com.raulshma.jellyplay.feature.library

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.smoothCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ChevronLeft
import com.composables.icons.tabler.outline.ChevronRight
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Adjustments
import com.composables.icons.tabler.outline.InfoCircle
import com.composables.icons.tabler.outline.PlayerPause
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Share
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.LoadingScreen
import com.raulshma.jellyplay.core.ui.feedback.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.feedback.uiTextOf
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.feature.library.R

@Composable
fun PhotoViewerScreen(
    itemId: String,
    parentId: String?,
    onBack: () -> Unit,
    viewModel: PhotoViewerViewModel = hiltViewModel(),
) {
    val isTv = LocalTvMode.current
    val context = LocalContext.current
    val userMessageBus = LocalUserMessageBus.current
    val photo by viewModel.photo
    val siblings by viewModel.siblings
    val currentIndex by viewModel.currentIndex
    val isLoading by viewModel.isLoading
    val error by viewModel.error
    val photoDetail by viewModel.photoDetail
    val isSlideshowActive by viewModel.isSlideshowActive
    val isSaving by viewModel.isSaving
    val saveResult by viewModel.saveResult
    val showAdjustments by viewModel.showAdjustments
    val brightness by viewModel.brightness
    val contrast by viewModel.contrast
    val saturation by viewModel.saturation

    LaunchedEffect(itemId, parentId) {
        viewModel.load(itemId, parentId)
    }

    val scale = remember { mutableFloatStateOf(1f) }
    val offsetX = remember { mutableFloatStateOf(0f) }
    val offsetY = remember { mutableFloatStateOf(0f) }
    var showControls by remember { mutableStateOf(true) }
    var showInfo by remember { mutableStateOf(false) }
    var showFilmstrip by remember { mutableStateOf(false) }
    var filmstripFocusRequester = remember { FocusRequester() }
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(currentIndex) {
        resetPhotoTransform(scale, offsetX, offsetY)
    }

    LaunchedEffect(showFilmstrip) {
        kotlinx.coroutines.delay(100)
        if (showFilmstrip) {
            filmstripFocusRequester.tryRequestFocus("photo_filmstrip")
        } else {
            rootFocusRequester.tryRequestFocus("photo_root")
        }
    }

    LaunchedEffect(saveResult) {
        when (val result = saveResult) {
            is SaveResult.Success -> {
                userMessageBus.info(uiTextOf(R.string.library_photo_saved_to_gallery))
                viewModel.clearSaveResult()
            }
            is SaveResult.Error -> {
                userMessageBus.error(result.message)
                viewModel.clearSaveResult()
            }
            else -> {}
        }
    }

    // Register an OnBackInvokedCallback for the predictive-back gesture / system
    // back button. Without this the photo viewer — a full-screen scene with a
    // pointer-input layer that consumes edge events — has no back callback, and
    // the predictive-back swipe crashes while the close button (which calls
    // onBack directly) and the 3-button nav back work. This mirrors the TV
    // remote's onBack layer-peeling (info -> filmstrip -> slideshow -> exit).
    BackHandler {
        when {
            showInfo -> showInfo = false
            showFilmstrip -> showFilmstrip = false
            isSlideshowActive -> viewModel.stopSlideshow()
            else -> onBack()
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
                DelayedLoadingScreen()
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = error ?: stringResource(R.string.library_photo_failed_to_load),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            photo != null -> {
                val photoColorFilter = remember(brightness, contrast, saturation) {
                    if (brightness == 1f && contrast == 1f && saturation == 1f) {
                        null
                    } else {
                        val cm = android.graphics.ColorMatrix()
                        cm.setSaturation(saturation)
                        val ct = (1f - contrast) * 128f
                        val b = (brightness - 1f) * 128f
                        val contrastBrightness = android.graphics.ColorMatrix(floatArrayOf(
                            contrast, 0f, 0f, 0f, b + ct,
                            0f, contrast, 0f, 0f, b + ct,
                            0f, 0f, contrast, 0f, b + ct,
                            0f, 0f, 0f, 1f, 0f,
                        ))
                        cm.postConcat(contrastBrightness)
                        androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(cm.array)
                        )
                    }
                }
                PhotoImage(
                    photo = photo!!,
                    viewModel = viewModel,
                    scale = scale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    onScaleChange = { scale.value = it },
                    onOffsetChange = { x, y -> offsetX.value = x; offsetY.value = y },
                    onTap = { if (!isTv) showControls = !showControls },
                    onDoubleTap = { currentScale ->
                        if (currentScale > 1f) {
                            resetPhotoTransform(scale, offsetX, offsetY)
                        } else {
                            scale.value = 2.5f
                        }
                    },
                    colorFilter = photoColorFilter,
                )

                AnimatedVisibility(
                    visible = showControls || isTv,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                    exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Full-bleed scrim gradient behind the top controls: it
                        // spans from the very top of the screen (including the
                        // status bar inset) out to the left/right edges, so the
                        // controls stay legible over bright photos. The controls
                        // themselves are padded with the system insets below.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .align(Alignment.TopCenter)
                                .background(
                                    Brush.verticalGradient(
                                        0f to MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f),
                                        1f to Color.Transparent,
                                    )
                                )
                        )
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
                                    Icon(Tabler.Outline.X, contentDescription = stringResource(R.string.library_cd_close), tint = Color.White)
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
                                                userMessageBus.error(errorMsg)
                                            }
                                        },
                                    ) {
                                        Icon(Tabler.Outline.Share, contentDescription = stringResource(R.string.library_cd_share), tint = Color.White, modifier = Modifier.size(20.dp))
                                    }

                                    OverlayActionButton(
                                        onClick = { viewModel.savePhotoToGallery() },
                                    ) {
                                        if (isSaving) {
                                            com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                                        } else {
                                            Icon(Tabler.Outline.Download, contentDescription = stringResource(R.string.library_cd_save), tint = Color.White, modifier = Modifier.size(20.dp))
                                        }
                                    }

                                    OverlayActionButton(
                                        onClick = { viewModel.toggleSlideshow() },
                                    ) {
                                        Icon(
                                            if (isSlideshowActive) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                                            contentDescription = if (isSlideshowActive) stringResource(R.string.library_photo_stop_slideshow) else stringResource(R.string.library_photo_start_slideshow),
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    OverlayActionButton(
                                        onClick = {
                                            showInfo = !showInfo
                                            if (showInfo) {
                                                viewModel.hideAdjustments()
                                            }
                                        },
                                    ) {
                                        Icon(Tabler.Outline.InfoCircle, contentDescription = stringResource(R.string.library_cd_info), tint = Color.White, modifier = Modifier.size(20.dp))
                                    }

                                    OverlayActionButton(
                                        onClick = {
                                            viewModel.toggleAdjustments()
                                            if (viewModel.showAdjustments.value) {
                                                showInfo = false
                                            }
                                        },
                                    ) {
                                        Icon(Tabler.Outline.Adjustments, contentDescription = stringResource(R.string.library_cd_adjust), tint = Color.White, modifier = Modifier.size(20.dp))
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
                                        Icon(Tabler.Outline.ChevronLeft, contentDescription = stringResource(R.string.library_cd_previous), tint = Color.White.copy(alpha = 0.7f))
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
                                        Icon(Tabler.Outline.ChevronRight, contentDescription = stringResource(R.string.library_cd_next), tint = Color.White.copy(alpha = 0.7f))
                                    }
                                }

                                if (isSlideshowActive) {
                                    Spacer(modifier = Modifier.size(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                        shape = ShapeCache.smooth4,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.library_photo_slideshow),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                        }


                    }
                    }
                }

                AnimatedVisibility(
                    visible = showInfo,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 3 },
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 3 },
                ) {
                    PhotoInfoOverlay(
                        photo = photo,
                        detail = photoDetail,
                        onDismiss = { showInfo = false },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                AnimatedVisibility(
                    visible = showAdjustments,
                    enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()) +
                        slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it / 3 },
                    exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) +
                        slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it / 3 },
                ) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(16.dp),
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = ShapeCache.smooth12,
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.library_photo_adjustments),
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    androidx.compose.material3.TextButton(
                                        onClick = { viewModel.resetAdjustments() },
                                    ) {
                                        Text(stringResource(R.string.library_reset), color = MaterialTheme.colorScheme.primary)
                                    }
                                    OverlayIconButton(
                                        onClick = { viewModel.hideAdjustments() }
                                    ) {
                                        Icon(Tabler.Outline.X, contentDescription = stringResource(R.string.library_cd_close), tint = Color.White, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.library_photo_brightness, (brightness * 100).toInt()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                            androidx.compose.material3.Slider(
                                value = brightness,
                                onValueChange = { viewModel.setBrightness(it) },
                                valueRange = 0f..2f,
                            )
                            Text(stringResource(R.string.library_photo_contrast, (contrast * 100).toInt()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                            androidx.compose.material3.Slider(
                                value = contrast,
                                onValueChange = { viewModel.setContrast(it) },
                                valueRange = 0f..2f,
                            )
                            Text(stringResource(R.string.library_photo_saturation, (saturation * 100).toInt()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                            androidx.compose.material3.Slider(
                                value = saturation,
                                onValueChange = { viewModel.setSaturation(it) },
                                valueRange = 0f..2f,
                            )
                        }
                    }
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

/** Resets the pinch/pan transform to its identity — photo switch and double-tap zoom-out share it. */
private fun resetPhotoTransform(
    scale: MutableFloatState,
    offsetX: MutableFloatState,
    offsetY: MutableFloatState,
) {
    scale.value = 1f
    offsetX.value = 0f
    offsetY.value = 0f
}

@Composable
private fun PhotoImage(
    photo: com.raulshma.jellyplay.core.model.MediaItem,
    viewModel: PhotoViewerViewModel,
    scale: State<Float>,
    offsetX: State<Float>,
    offsetY: State<Float>,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (x: Float, y: Float) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: (currentScale: Float) -> Unit,
    colorFilter: androidx.compose.ui.graphics.ColorFilter? = null,
) {
    val imageUrl = remember(photo.id) {
        viewModel.getImageUrl(photo.id, maxWidth = null)
    }
    var lastTapTime by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(photo.id) {
                awaitEachGesture {
                    val firstDown = awaitFirstDown()
                    firstDown.consume()

                    var gestureScale = scale.value
                    var gestureOffsetX = offsetX.value
                    var gestureOffsetY = offsetY.value

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
                            onDoubleTap(gestureScale)
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
            // The photo is Fit (letterboxed) so the actual image keeps its
            // aspect ratio, but the blurHash background should fill the whole
            // screen behind it instead of letterboxing the same way.
            blurHashContentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale.value
                    scaleY = scale.value
                    translationX = offsetX.value
                    translationY = offsetY.value
                },
            contentScale = ContentScale.Fit,
            size = coil3.size.Size.ORIGINAL,
            colorFilter = colorFilter,
        )
    }
}

@Composable
private fun PhotoInfoOverlay(
    photo: com.raulshma.jellyplay.core.model.MediaItem?,
    detail: com.raulshma.jellyplay.core.model.MediaDetail?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(16.dp),
        color = Color.Black.copy(alpha = 0.85f),
        shape = ShapeCache.smooth12,
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
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                OverlayIconButton(onClick = onDismiss) {
                    Icon(Tabler.Outline.X, contentDescription = stringResource(R.string.library_cd_close), tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }

            if (detail != null) {
                val scrollState = androidx.compose.foundation.rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
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
                color = Color.White.copy(alpha = 0.7f),
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
                val placementSpec = lazyItemPlacementSpec()

                Box(
                    modifier = Modifier
                        .animateItem(placementSpec = placementSpec)
                        .size(if (isHighlighted) 64.dp else 52.dp)
                        .clip(smoothCornerShape(6.dp))
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
                                .clip(smoothCornerShape(6.dp))
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
            .focusIndicator(CircleShape)
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
            .focusIndicator(ShapeCache.smooth12)
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
