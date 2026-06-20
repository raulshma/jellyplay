package com.raulshma.jellyplay.feature.player.audio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.QueueUndoEvent
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.player.audio.components.WaveformSeekBar
import com.raulshma.jellyplay.feature.player.audio.R
import com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.*
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.feature.player.audio.sheets.AudioEffectsSheet
import com.raulshma.jellyplay.feature.player.audio.sheets.AudioSleepTimerSheet
import com.raulshma.jellyplay.feature.player.audio.sheets.EqualizerSheet
import com.raulshma.jellyplay.feature.player.audio.sheets.LyricsSearchSheet
import com.raulshma.jellyplay.feature.player.audio.sheets.QueueSheet
import com.raulshma.jellyplay.feature.player.audio.sheets.SpeedPickerSheet
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    itemId: String,
    onBack: () -> Unit,
    onAmbientClick: (String?, String, String) -> Unit = { _, _, _ -> },
    onArtistClick: (String) -> Unit = {},
    viewModel: AudioPlayerViewModel = hiltViewModel(),
) {
    val sharedTransitionScope = com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope.current

    var showQueue by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showLyricsSearch by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    var showErrorOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.playbackError) {
        showErrorOverlay = viewModel.playbackError != null
        viewModel.playbackError?.let { error ->
            snackbarHostState.showSnackbar(error, duration = SnackbarDuration.Short)
        }
    }

    // Surface an "Undo" affordance after destructive queue operations
    // (clear / remove / skip / move) so an accidental action is recoverable
    // (enhancements §5.2).
    val undoClearedMessage = stringResource(R.string.audio_undo_queue_cleared)
    val undoRemovedMessage = stringResource(R.string.audio_undo_track_removed)
    val undoMovedMessage = stringResource(R.string.audio_undo_track_moved)
    val undoSkippedNextMessage = stringResource(R.string.audio_undo_skipped_next)
    val undoSkippedPrevMessage = stringResource(R.string.audio_undo_skipped_previous)
    val undoActionLabel = stringResource(R.string.audio_undo_action)
    LaunchedEffect(Unit) {
        viewModel.undoEvents.collect { event ->
            val message = when (event) {
                is QueueUndoEvent.QueueCleared -> undoClearedMessage
                is QueueUndoEvent.ItemRemoved -> undoRemovedMessage
                is QueueUndoEvent.ItemMoved -> undoMovedMessage
                is QueueUndoEvent.SkippedToNext -> undoSkippedNextMessage
                is QueueUndoEvent.SkippedToPrevious -> undoSkippedPrevMessage
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = undoActionLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLastQueueOperation()
            }
        }
    }

    val artworkScale = remember { Animatable(0.8f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(itemId) {
        viewModel.play(itemId)
        artworkScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, tween(600, delayMillis = 200, easing = AlphaEasing))
    }

    BackHandler {
        if (showQueue || showSpeedPicker || showEqualizer || showLyricsSearch || showEffectsSheet) {
            showQueue = false
            showSpeedPicker = false
            showEqualizer = false
            showLyricsSearch = false
            showEffectsSheet = false
        } else if (showLyrics) {
            showLyrics = false
        } else {
            onBack()
        }
    }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val currentDownloadItem by viewModel.currentDownloadItem.collectAsStateWithLifecycle()
    val abLoopStart by viewModel.abLoopStartMs.collectAsStateWithLifecycle(initialValue = null)
    val abLoopEnd by viewModel.abLoopEndMs.collectAsStateWithLifecycle(initialValue = null)

    val adaptiveInfo = com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass == com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass.Expanded
    val useSideBySide = isExpanded || adaptiveInfo.isLandscape

    val systemBgColor = MaterialTheme.colorScheme.background
    val systemBgColorLight = MaterialTheme.colorScheme.surfaceContainerHigh
    val accentColor = MaterialTheme.colorScheme.primary
    val pillSurface = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val pillSurfaceDark = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.7f)

    val navBarColor = com.raulshma.jellyplay.core.ui.components.LocalNavigationBarColor.current
    androidx.compose.runtime.DisposableEffect(systemBgColor) {
        val oldColor = navBarColor.value
        navBarColor.value = systemBgColor
        onDispose {
            navBarColor.value = oldColor
        }
    }

    val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
    val isDarkTheme = remember(preferences.themeMode, isSystemDark) {
        when (preferences.themeMode) {
            com.raulshma.jellyplay.core.model.ThemeMode.DARK -> true
            com.raulshma.jellyplay.core.model.ThemeMode.LIGHT -> false
            com.raulshma.jellyplay.core.model.ThemeMode.SYSTEM -> isSystemDark
        }
    }

    // Animatables for swipe gestures
    val swipeDismissOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val horizontalSwipeOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    var dragDirection by remember { mutableStateOf<DragDirection?>(null) }
    var totalDragX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var totalDragY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    ArtworkThemeWrapper(
        imageUrl = viewModel.albumArtUrl.ifBlank { null },
        dynamicTheming = preferences.dynamicTheming,
        darkTheme = isDarkTheme,
        oledMode = preferences.oledMode,
        colorStyle = preferences.colorStyle,
        accentColorSwatch = preferences.accentColorSwatch,
    ) {
        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        val sharedContainerModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    rememberSharedContentState(key = "audio_player_container"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = androidx.compose.animation.BoundsTransform { _, _ ->
                        spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow,
                        )
                    }
                )
            }
        } else Modifier

        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(sharedContainerModifier)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(systemBgColorLight, systemBgColor, systemBgColor),
                    )
                )
                .pointerInput(Unit) {
                    val scopeHeight = size.height.toFloat()
                    detectDragGestures(
                        onDragStart = {
                            dragDirection = null
                            totalDragX = 0f
                            totalDragY = 0f
                        },
                        onDragEnd = {
                            when (dragDirection) {
                                DragDirection.VERTICAL -> {
                                    if (swipeDismissOffset.value < -80f || totalDragY < -150f) {
                                        coroutineScope.launch {
                                            swipeDismissOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                        }
                                        showQueue = true
                                    } else if (swipeDismissOffset.value > 150f || totalDragY > 200f) {
                                        // Instantly reset visual translation offset to ensure morph begins from stable bounds
                                        coroutineScope.launch {
                                            swipeDismissOffset.snapTo(0f)
                                            onBack()
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            swipeDismissOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                        }
                                    }
                                }
                                DragDirection.HORIZONTAL -> {
                                    val threshold = 180f
                                    if (horizontalSwipeOffset.value < -threshold) { // Swipe Left -> Next
                                        coroutineScope.launch {
                                            viewModel.skipToNext()
                                            horizontalSwipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                        }
                                    } else if (horizontalSwipeOffset.value > threshold) { // Swipe Right -> Prev
                                        coroutineScope.launch {
                                            viewModel.skipToPrevious()
                                            horizontalSwipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                        }
                                    } else {
                                        coroutineScope.launch {
                                            horizontalSwipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                        }
                                    }
                                }
                                null -> {}
                            }
                            dragDirection = null
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                swipeDismissOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                                horizontalSwipeOffset.animateTo(0f, spring(stiffness = Spring.StiffnessMedium))
                            }
                            dragDirection = null
                        }
                    ) { change, dragAmount ->
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        if (dragDirection == null) {
                            val threshold = 10f
                            if (kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX) && kotlin.math.abs(totalDragY) > threshold) {
                                dragDirection = DragDirection.VERTICAL
                            } else if (kotlin.math.abs(totalDragX) > kotlin.math.abs(totalDragY) && kotlin.math.abs(totalDragX) > threshold) {
                                dragDirection = DragDirection.HORIZONTAL
                            }
                        }

                        when (dragDirection) {
                            DragDirection.VERTICAL -> {
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = (swipeDismissOffset.value + dragAmount.y).coerceAtLeast(-120f)
                                    swipeDismissOffset.snapTo(newOffset)
                                }
                            }
                            DragDirection.HORIZONTAL -> {
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = horizontalSwipeOffset.value + dragAmount.x
                                    horizontalSwipeOffset.snapTo(newOffset)
                                }
                            }
                            null -> {}
                        }
                    }
                }
                .graphicsLayer {
                    translationY = swipeDismissOffset.value
                },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(WindowInsets.navigationBars.asPaddingValues()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Top bar ──
                PixelPlayerTopBar(
                    onBack = onBack,
                    hasLyrics = true,
                    lyricsVisible = showLyrics,
                    onLyricsClick = { showLyrics = !showLyrics },
                    onQueueClick = { showQueue = true },
                    onMenuToggle = { showMenu = it },
                    showMenu = showMenu,
                    speed = viewModel.speed,
                    dialogueBoostEnabled = viewModel.dialogueBoostEnabled,
                    dialogueBoostStrength = viewModel.dialogueBoostStrength,
                    nightModeEnabled = viewModel.nightModeEnabled,
                    nightModeStrength = viewModel.nightModeStrength,
                    onSpeedClick = { showMenu = false; showSpeedPicker = true },
                    onEqualizerClick = { showMenu = false; showEqualizer = true },
                    onEffectsClick = { showMenu = false; showEffectsSheet = true },
                    onDialogueBoostClick = { showMenu = false; viewModel.toggleDialogueBoost() },
                    onDialogueBoostStrengthChange = { viewModel.setDialogueBoostStrength(it) },
                    onNightModeClick = { showMenu = false; viewModel.toggleNightMode() },
                    onNightModeStrengthChange = { viewModel.setNightModeStrength(it) },
                    onAmbientClick = { showMenu = false; onAmbientClick(viewModel.albumArtUrl.ifBlank { null }, viewModel.title, viewModel.artist) },
                    sleepTimerActive = viewModel.sleepTimerActive,
                    sleepTimerDisplayText = if (viewModel.sleepTimerEndOfEpisode) "End of episode" else com.raulshma.jellyplay.core.ui.components.formatDurationMs(viewModel.sleepTimerRemainingMs),
                    onSleepTimerClick = { showMenu = false; showSleepTimer = true },
                    karaokeMode = viewModel.karaokeMode,
                    onKaraokeToggle = { viewModel.setKaraokeModeEnabled(it) },
                    hasKaraokeLyrics = viewModel.hasKaraokeLyrics,
                )

                if (useSideBySide) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(horizontal = 32.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            AlbumArtwork(
                                albumArtUrl = viewModel.albumArtUrl,
                                albumArtBlurHash = viewModel.albumArtBlurHash,
                                title = viewModel.title,
                                scale = artworkScale.value,
                                isExpanded = true,
                                lyricsVisible = showLyrics,
                                lyrics = viewModel.lyrics,
                                currentLyricIndex = viewModel.currentLyricIndex,
                                isFetchingLyrics = viewModel.isFetchingLyrics,
                                lyricsSource = viewModel.lyricsSource,
                                onSearchClick = { showLyricsSearch = true },
                                karaokeMode = viewModel.karaokeMode,
                                currentPositionMs = viewModel.currentPosition,
                                lyricsOffsetMs = viewModel.lyricsOffsetMs,
                                onLyricsOffsetChange = { viewModel.setLyricsOffset(it) },
                            )
                        }
                        Spacer(Modifier.width(32.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .graphicsLayer { alpha = contentAlpha.value },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            TrackInfoSection(
                                title = viewModel.title,
                                artist = viewModel.artist,
                                artistId = viewModel.artistId,
                                onArtistClick = onArtistClick,
                            )
                            Spacer(Modifier.height(28.dp))
                            PixelProgressSection(
                                currentPosition = viewModel.currentPosition,
                                duration = viewModel.duration,
                                isPlaying = viewModel.isPlaying,
                                accentColor = accentColor,
                                onSeek = { frac ->
                                    if (viewModel.duration > 0) viewModel.seekTo((frac * viewModel.duration).toLong())
                                },
                            )
                            Spacer(Modifier.height(16.dp))
                            PixelTransportControls(
                                isPlaying = viewModel.isPlaying,
                                onTogglePlayPause = { viewModel.togglePlayPause() },
                                onSkipPrevious = { viewModel.skipToPrevious() },
                                onSkipNext = { viewModel.skipToNext() },
                                pillSurface = pillSurface,
                                accentColor = accentColor,
                            )
                            Spacer(Modifier.height(12.dp))
                            PixelSecondaryControls(
                                shuffleMode = viewModel.shuffleMode,
                                repeatMode = viewModel.repeatMode,
                                isFavorite = viewModel.isFavorite,
                                downloadItem = currentDownloadItem,
                                abLoopStartMs = abLoopStart,
                                abLoopEndMs = abLoopEnd,
                                onToggleShuffle = { viewModel.toggleShuffle() },
                                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                                onToggleFavorite = { viewModel.toggleFavorite() },
                                onDownloadClick = {
                                    if (currentDownloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) {
                                        showDeleteConfirm = true
                                    } else {
                                        viewModel.downloadCurrentTrack()
                                    }
                                },
                                onAbLoopClick = { viewModel.cycleAbLoop() },
                                pillSurfaceDark = pillSurfaceDark,
                                accentColor = accentColor,
                            )
                        }
                    }
                } else {
                    // Phone: vertical layout (Pixel Player style)
                    Spacer(Modifier.weight(0.5f))

                    AlbumArtwork(
                        albumArtUrl = viewModel.albumArtUrl,
                        albumArtBlurHash = viewModel.albumArtBlurHash,
                        title = viewModel.title,
                        scale = artworkScale.value,
                        isExpanded = false,
                        lyricsVisible = showLyrics,
                        lyrics = viewModel.lyrics,
                        currentLyricIndex = viewModel.currentLyricIndex,
                        isFetchingLyrics = viewModel.isFetchingLyrics,
                        lyricsSource = viewModel.lyricsSource,
                        onSearchClick = { showLyricsSearch = true },
                        karaokeMode = viewModel.karaokeMode,
                        currentPositionMs = viewModel.currentPosition,
                        lyricsOffsetMs = viewModel.lyricsOffsetMs,
                        onLyricsOffsetChange = { viewModel.setLyricsOffset(it) },
                    )

                    Spacer(Modifier.weight(0.4f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = contentAlpha.value }
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TrackInfoSection(
                            title = viewModel.title,
                            artist = viewModel.artist,
                            artistId = viewModel.artistId,
                            onArtistClick = onArtistClick,
                        )
                        Spacer(Modifier.height(24.dp))
                        PixelProgressSection(
                            currentPosition = viewModel.currentPosition,
                            duration = viewModel.duration,
                            isPlaying = viewModel.isPlaying,
                            accentColor = accentColor,
                            onSeek = { frac ->
                                if (viewModel.duration > 0) viewModel.seekTo((frac * viewModel.duration).toLong())
                            },
                        )
                        Spacer(Modifier.height(20.dp))
                        PixelTransportControls(
                            isPlaying = viewModel.isPlaying,
                            onTogglePlayPause = { viewModel.togglePlayPause() },
                            onSkipPrevious = { viewModel.skipToPrevious() },
                            onSkipNext = { viewModel.skipToNext() },
                            pillSurface = pillSurface,
                            accentColor = accentColor,
                        )
                        Spacer(Modifier.height(12.dp))
                        PixelSecondaryControls(
                            shuffleMode = viewModel.shuffleMode,
                            repeatMode = viewModel.repeatMode,
                            isFavorite = viewModel.isFavorite,
                            downloadItem = currentDownloadItem,
                            abLoopStartMs = abLoopStart,
                            abLoopEndMs = abLoopEnd,
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                            onToggleFavorite = { viewModel.toggleFavorite() },
                            onDownloadClick = {
                                if (currentDownloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) {
                                    showDeleteConfirm = true
                                } else {
                                    viewModel.downloadCurrentTrack()
                                }
                            },
                            onAbLoopClick = { viewModel.cycleAbLoop() },
                            pillSurfaceDark = pillSurfaceDark,
                            accentColor = accentColor,
                        )
                        Spacer(Modifier.height(16.dp))
                        Spacer(Modifier.height(80.dp))
                    }
                }
            } // Close Column (main layout)

            // Swipe track cards overlays
            val density = androidx.compose.ui.platform.LocalDensity.current
            val cardWidthDp = 260.dp
            val cardWidthPx = remember(density) { with(density) { cardWidthDp.toPx() } }

            if (horizontalSwipeOffset.value < 0) {
                val nextIndex = if (viewModel.currentIndex >= 0 && viewModel.queue.isNotEmpty()) {
                    (viewModel.currentIndex + 1) % viewModel.queue.size
                } else 0
                val nextTrack = viewModel.queue.getOrNull(nextIndex)
                if (nextTrack != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        val slideOffset = (cardWidthPx + horizontalSwipeOffset.value).coerceAtLeast(0f)
                        SwipeTrackCard(
                            title = nextTrack.name,
                            artist = nextTrack.artist,
                            artworkUrl = nextTrack.imageUrl ?: "",
                            isNext = true,
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = slideOffset
                                }
                                .padding(end = 16.dp)
                        )
                    }
                }
            } else if (horizontalSwipeOffset.value > 0) {
                val prevIndex = if (viewModel.currentIndex >= 0 && viewModel.queue.isNotEmpty()) {
                    (viewModel.currentIndex - 1 + viewModel.queue.size) % viewModel.queue.size
                } else 0
                val prevTrack = viewModel.queue.getOrNull(prevIndex)
                if (prevTrack != null) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        val slideOffset = (-cardWidthPx + horizontalSwipeOffset.value).coerceAtMost(0f)
                        SwipeTrackCard(
                            title = prevTrack.name,
                            artist = prevTrack.artist,
                            artworkUrl = prevTrack.imageUrl ?: "",
                            isNext = false,
                            modifier = Modifier
                                .graphicsLayer {
                                    translationX = slideOffset
                                }
                                .padding(start = 16.dp)
                        )
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 120.dp),
            )

            AnimatedVisibility(
                visible = showErrorOverlay && viewModel.playbackError != null,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(200)),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Surface(
                    shape = ShapeCache.smooth16,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f),
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .padding(bottom = 140.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            viewModel.playbackError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = {
                            showErrorOverlay = false
                            viewModel.play(itemId)
                        }) {
                            Text("Retry", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        } // Close Box (root container)
    } // Close ArtworkThemeWrapper

    // ── Bottom sheets (unchanged functionality) ──
    if (showQueue && viewModel.queue.isNotEmpty()) {
        QueueSheet(
            queue = viewModel.queue,
            currentIndex = viewModel.currentIndex,
            onSelect = { index ->
                viewModel.playFromQueue(index)
                showQueue = false
            },
            onRemove = { index -> viewModel.removeFromQueue(index) },
            onDismiss = { showQueue = false },
        )
    }

    if (showSpeedPicker) {
        SpeedPickerSheet(
            currentSpeed = viewModel.speed,
            onSelect = { viewModel.changePlaybackSpeed(it) },
            onDismiss = { showSpeedPicker = false },
        )
    }

    if (showEqualizer) {
        EqualizerSheet(
            enabled = viewModel.equalizerEnabled,
            bandLevels = viewModel.equalizerSettings.bandLevels,
            currentPreset = viewModel.equalizerPreset,
            onToggle = { viewModel.toggleEqualizer() },
            onBandChange = { index, level -> viewModel.setEqualizerBand(index, level) },
            onReset = { viewModel.resetEqualizer() },
            onPresetChange = { viewModel.applyEqualizerPreset(it) },
            onDismiss = { showEqualizer = false },
        )
    }

    if (showLyricsSearch) {
        LyricsSearchSheet(
            artist = viewModel.artist,
            title = viewModel.title,
            searchResults = viewModel.lyricsSearchResults,
            isSearching = viewModel.isSearchingLyrics,
            onSearch = { viewModel.searchLyrics(it) },
            onApplyTrack = { viewModel.applyLyrics(it) },
            onDismiss = { showLyricsSearch = false; viewModel.clearLyricsSearch() },
        )
    }

    if (showSleepTimer) {
        AudioSleepTimerSheet(
            isActive = viewModel.sleepTimerActive,
            isEndOfEpisodeMode = viewModel.sleepTimerEndOfEpisode,
            remainingMs = viewModel.sleepTimerRemainingMs,
            lastUsedDurationMs = viewModel.sleepTimerLastUsedDurationMs,
            onSelectDuration = { viewModel.startSleepTimer(it) },
            onSelectEndOfEpisode = { viewModel.startSleepTimerEndOfEpisode() },
            onCancel = { viewModel.cancelSleepTimer() },
            onDismiss = { showSleepTimer = false },
        )
    }

    if (showEffectsSheet) {
        AudioEffectsSheet(
            viewModel = viewModel,
            onDismiss = { showEffectsSheet = false },
            onOpenEqualizer = { showEffectsSheet = false; showEqualizer = true },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Download") },
            text = { Text("Remove the downloaded file for this track?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.downloadCurrentTrack()
                    },
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LyricsOverlay(
    lyrics: List<com.raulshma.jellyplay.core.model.LyricsLine>,
    currentIndex: Int,
    isFetching: Boolean,
    lyricsSource: com.raulshma.jellyplay.core.model.LyricsSource,
    onSearchClick: () -> Unit,
    karaokeMode: Boolean = false,
    onKaraokeToggle: (Boolean) -> Unit = {},
    lyricsOffsetMs: Long = com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.DEFAULT_OFFSET_MS,
    onLyricsOffsetChange: (Long) -> Unit = {},
) {
    val overlayAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400),
        label = "lyricsOverlayAlpha",
    )
    val listState = rememberLazyListState()
    val hasSyncedLyrics = lyrics.any { it.timeMs > 0 }
    val scrimBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.1f),
                Color.Black.copy(alpha = 0.7f),
                Color.Black.copy(alpha = 0.85f),
                Color.Black.copy(alpha = 0.7f),
                Color.Black.copy(alpha = 0.1f),
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(scrimBrush)
            .clip(ShapeCache.smooth24),
        contentAlignment = Alignment.Center,
    ) {
        if (isFetching) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                JellyPlayLoadingIndicator(
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Finding lyrics...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        } else if (lyrics.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(
                    Tabler.Outline.Music,
                    null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No lyrics found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onSearchClick,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(
                        Tabler.Outline.Search,
                        null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Search", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    contentPadding = PaddingValues(vertical = 200.dp),
                    userScrollEnabled = false,
                ) {
                    items(lyrics.size, key = { it }, contentType = { "lyricsLine" }) { index ->
                        val isCurrent = index == currentIndex && hasSyncedLyrics
                        val distance = if (currentIndex >= 0) kotlin.math.abs(index - currentIndex) else 99
                        val targetAlpha = when {
                            isCurrent -> 1f
                            distance == 1 -> 0.5f
                            distance == 2 -> 0.3f
                            else -> 0.15f
                        }
                        val targetScale = if (isCurrent) 1.08f else 1f
                        val isNearActive = distance <= 3
                        val animatedAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = AlphaEasing,
                            ),
                            label = "lyricAlpha$index",
                        )
                        val animatedScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = tween(
                                durationMillis = 300,
                                easing = PointToPointEasing,
                            ),
                            label = "lyricScale$index",
                        )
                        val finalAlpha = if (isNearActive) animatedAlpha else targetAlpha
                        val finalScale = if (isNearActive) animatedScale else targetScale
                        Text(
                            text = lyrics[index].text.ifBlank { "\u266A" },
                            style = if (isCurrent) {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = Color.White.copy(alpha = finalAlpha),
                            modifier = Modifier
                                .animateContentSize(animationSpec = tween(300))
                                .graphicsLayer {
                                    scaleX = finalScale
                                    scaleY = finalScale
                                    this.alpha = finalAlpha
                                }
                                .padding(vertical = 6.dp, horizontal = 20.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.9f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.9f),
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY,
                            )
                        ),
                )

                var showOffsetSlider by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    // Lyrics timing offset adjustment (enhancements §5.1). Only
                    // meaningful for time-synced lyrics.
                    if (hasSyncedLyrics) {
                        AnimatedVisibility(
                            visible = showOffsetSlider,
                            enter = fadeIn(tween(200)) + expandVertically(),
                            exit = fadeOut(tween(150)) + shrinkVertically(),
                        ) {
                            Surface(
                                shape = ShapeCache.smooth8,
                                color = Color.Black.copy(alpha = 0.6f),
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .width(220.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            "Lyrics offset",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.8f),
                                        )
                                        Text(
                                            "${lyricsOffsetMs}ms",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (lyricsOffsetMs == 0L)
                                                Color.White.copy(alpha = 0.6f)
                                            else MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Slider(
                                        value = lyricsOffsetMs.toFloat(),
                                        onValueChange = { onLyricsOffsetChange(it.toLong()) },
                                        valueRange =
                                            com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.MIN_OFFSET_MS.toFloat()..
                                                com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.MAX_OFFSET_MS.toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        Text(
                                            "Reset",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.clickable {
                                                onLyricsOffsetChange(
                                                    com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.DEFAULT_OFFSET_MS
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    if (lyricsSource != com.raulshma.jellyplay.core.model.LyricsSource.UNKNOWN) {
                        val sourceLabel = when (lyricsSource) {
                            com.raulshma.jellyplay.core.model.LyricsSource.LRCLIB -> "lrclib"
                            com.raulshma.jellyplay.core.model.LyricsSource.EXTERNAL -> "Jellyfin"
                            com.raulshma.jellyplay.core.model.LyricsSource.EMBEDDED -> "Embedded"
                            com.raulshma.jellyplay.core.model.LyricsSource.LRC_FILE -> "LRC"
                            else -> ""
                        }
                        if (sourceLabel.isNotBlank()) {
                            Surface(
                                shape = ShapeCache.smooth8,
                                color = Color.Black.copy(alpha = 0.4f),
                                modifier = Modifier.height(20.dp),
                            ) {
                                Text(
                                    sourceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    if (lyrics.any { it.words.isNotEmpty() }) {
                        val karaokeFocus = rememberTvFocusState()
                        IconButton(
                            onClick = { onKaraokeToggle(!karaokeMode) },
                            modifier = Modifier
                                .size(28.dp)
                                .then(karaokeFocus.focusModifier)
                                .tvFocusIndicator(karaokeFocus, ShapeCache.smooth8)
                                .background(
                                    if (karaokeMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else Color.Black.copy(alpha = 0.4f),
                                    ShapeCache.smooth8,
                                ),
                        ) {
                            Icon(
                                if (karaokeMode) Tabler.Outline.Microphone2 else Tabler.Outline.Microphone,
                                if (karaokeMode) "Karaoke on" else "Karaoke off",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (hasSyncedLyrics) {
                        val offsetFocus = rememberTvFocusState()
                        IconButton(
                            onClick = { showOffsetSlider = !showOffsetSlider },
                            modifier = Modifier
                                .size(28.dp)
                                .then(offsetFocus.focusModifier)
                                .tvFocusIndicator(offsetFocus, ShapeCache.smooth8)
                                .background(
                                    if (lyricsOffsetMs != com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.DEFAULT_OFFSET_MS)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else Color.Black.copy(alpha = 0.4f),
                                    ShapeCache.smooth8,
                                ),
                        ) {
                            Icon(
                                Tabler.Outline.Adjustments,
                                "Adjust lyrics timing",
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    val searchFocus = rememberTvFocusState()
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .size(28.dp)
                            .then(searchFocus.focusModifier)
                            .tvFocusIndicator(searchFocus, ShapeCache.smooth8)
                            .background(
                                Color.Black.copy(alpha = 0.4f),
                                ShapeCache.smooth8,
                            ),
                    ) {
                        Icon(
                            Tabler.Outline.Search,
                            "Search lyrics",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }
                    }
                }
            }
        }
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && lyrics.isNotEmpty()) {
            val targetIndex = currentIndex.coerceAtMost(lyrics.lastIndex)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = 0,
            )
        }
    }
}




@Composable
private fun PixelPlayerTopBar(
    onBack: () -> Unit,
    hasLyrics: Boolean,
    lyricsVisible: Boolean,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    speed: Float,
    dialogueBoostEnabled: Boolean,
    dialogueBoostStrength: com.raulshma.jellyplay.core.model.EffectStrength,
    nightModeEnabled: Boolean,
    nightModeStrength: com.raulshma.jellyplay.core.model.EffectStrength,
    onSpeedClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onEffectsClick: () -> Unit,
    onDialogueBoostClick: () -> Unit,
    onDialogueBoostStrengthChange: (com.raulshma.jellyplay.core.model.EffectStrength) -> Unit,
    onNightModeClick: () -> Unit,
    onNightModeStrengthChange: (com.raulshma.jellyplay.core.model.EffectStrength) -> Unit,
    onAmbientClick: () -> Unit,
    sleepTimerActive: Boolean = false,
    sleepTimerDisplayText: String = "",
    onSleepTimerClick: () -> Unit = {},
    karaokeMode: Boolean = false,
    onKaraokeToggle: (Boolean) -> Unit = {},
    hasKaraokeLyrics: Boolean = false,
) {
    val minimizeFocusState = rememberTvFocusState()
    val lyricsFocusState = rememberTvFocusState()
    val queueFocusState = rememberTvFocusState()
    val moreFocusState = rememberTvFocusState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .then(minimizeFocusState.focusModifier)
                .tvFocusIndicator(minimizeFocusState, CircleShape)
        ) {
            Icon(
                Tabler.Outline.ChevronDown, "Minimize",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            "Now Playing",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
            letterSpacing = 1.sp,
        )
        Row {
            if (hasLyrics) {
                IconButton(
                    onClick = onLyricsClick,
                    modifier = Modifier
                        .then(lyricsFocusState.focusModifier)
                        .tvFocusIndicator(lyricsFocusState, CircleShape)
                ) {
                    Icon(
                        if (lyricsVisible) Tabler.Outline.Microphone2 else Tabler.Outline.Microphone,
                        "Lyrics",
                        tint = if (lyricsVisible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            IconButton(
                onClick = onQueueClick,
                modifier = Modifier
                    .then(queueFocusState.focusModifier)
                    .tvFocusIndicator(queueFocusState, CircleShape)
            ) {
                Icon(Tabler.Outline.Playlist, "Queue", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
            }
            Box {
                IconButton(
                    onClick = { onMenuToggle(true) },
                    modifier = Modifier
                        .then(moreFocusState.focusModifier)
                        .tvFocusIndicator(moreFocusState, CircleShape)
                ) {
                    Icon(Tabler.Outline.DotsVertical, "More", tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(22.dp))
                }
                val itemColors = androidx.compose.material3.MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.onSurface,
                    leadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { onMenuToggle(false) }) {
                    DropdownMenuItem(
                        text = { Text("Speed (${if (speed == 1.0f) "1x" else "${speed}x"})") },
                        onClick = onSpeedClick,
                        leadingIcon = { Icon(Tabler.Outline.Gauge, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text("Equalizer") },
                        onClick = onEqualizerClick,
                        leadingIcon = { Icon(Tabler.Outline.Adjustments, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text("Audio Effects") },
                        onClick = onEffectsClick,
                        leadingIcon = { Icon(Tabler.Outline.Ear, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text(if (dialogueBoostEnabled) "Dialogue Boost · ${dialogueBoostStrength.displayName}" else "Dialogue Boost") },
                        onClick = { onDialogueBoostClick(); onMenuToggle(false) },
                        leadingIcon = { Icon(Tabler.Outline.Microphone2, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text(if (nightModeEnabled) "Night Mode · ${nightModeStrength.displayName}" else "Night Mode") },
                        onClick = { onNightModeClick(); onMenuToggle(false) },
                        leadingIcon = { Icon(Tabler.Outline.Moon, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text("Ambient Mode") },
                        onClick = onAmbientClick,
                        leadingIcon = { Icon(Tabler.Outline.MoonStars, null) },
                        colors = itemColors,
                    )
                    DropdownMenuItem(
                        text = { Text(if (sleepTimerActive) "Sleep Timer · $sleepTimerDisplayText" else "Sleep Timer") },
                        onClick = onSleepTimerClick,
                        leadingIcon = { Icon(Tabler.Outline.Stopwatch, null) },
                        colors = itemColors,
                    )
                    if (hasKaraokeLyrics) {
                        DropdownMenuItem(
                            text = { Text(if (karaokeMode) "Karaoke Mode · On" else "Karaoke Mode") },
                            onClick = { onKaraokeToggle(!karaokeMode); onMenuToggle(false) },
                            leadingIcon = { Icon(Tabler.Outline.Microphone2, null) },
                            colors = itemColors,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArtwork(
    albumArtUrl: String,
    albumArtBlurHash: String?,
    title: String,
    scale: Float,
    isExpanded: Boolean,
    lyricsVisible: Boolean = false,
    lyrics: List<com.raulshma.jellyplay.core.model.LyricsLine> = emptyList(),
    currentLyricIndex: Int = -1,
    isFetchingLyrics: Boolean = false,
    lyricsSource: com.raulshma.jellyplay.core.model.LyricsSource = com.raulshma.jellyplay.core.model.LyricsSource.UNKNOWN,
    onSearchClick: () -> Unit = {},
    karaokeMode: Boolean = false,
    currentPositionMs: Long = 0L,
    lyricsOffsetMs: Long = com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.DEFAULT_OFFSET_MS,
    onLyricsOffsetChange: (Long) -> Unit = {},
) {
    val sharedTransitionScope = com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope.current

    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "audio_player_album_art"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxWidth(if (isExpanded) 0.85f else 0.75f)
            .aspectRatio(1f)
            .scale(scale)
            .shadow(
                elevation = 24.dp,
                shape = ShapeCache.smooth24,
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            .clip(ShapeCache.smooth24)
            .then(sharedModifier),
        contentAlignment = Alignment.Center,
    ) {
        if (albumArtUrl.isNotBlank()) {
            MediaImage(
                url = albumArtUrl,
                contentDescription = title,
                blurHash = albumArtBlurHash,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceVariant,
                                MaterialTheme.colorScheme.surface,
                            )
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Music,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }

        AnimatedVisibility(
            visible = lyricsVisible,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(300)),
        ) {
            if (karaokeMode && lyrics.any { it.words.isNotEmpty() }) {
                com.raulshma.jellyplay.feature.player.audio.lyrics.KaraokeLyricsView(
                    lyrics = lyrics,
                    currentIndex = currentLyricIndex,
                    currentPositionMs = currentPositionMs,
                )
            } else {
                LyricsOverlay(
                    lyrics = lyrics,
                    currentIndex = currentLyricIndex,
                    isFetching = isFetchingLyrics,
                    lyricsSource = lyricsSource,
                    onSearchClick = onSearchClick,
                    lyricsOffsetMs = lyricsOffsetMs,
                    onLyricsOffsetChange = onLyricsOffsetChange,
                )
            }
        }

        if (title.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                JellyPlayLoadingIndicator(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun TrackInfoSection(
    title: String,
    artist: String,
    artistId: String? = null,
    onArtistClick: (String) -> Unit = {},
) {
    val artistFocusState = rememberTvFocusState()
    Text(
        title,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    val artistClickable = !artistId.isNullOrBlank() && artist.isNotBlank()
    Text(
        artist,
        style = MaterialTheme.typography.bodyLarge,
        color = if (artistClickable) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (artistClickable) {
                    Modifier
                        .then(artistFocusState.focusModifier)
                        .tvFocusIndicator(artistFocusState, ShapeCache.smooth8)
                        .clip(ShapeCache.smooth8)
                        .clickable { onArtistClick(artistId!!) }
                } else Modifier
            ),
        textAlign = TextAlign.Center,
    )
}

/** Waveform seek bar + timestamp row — Pixel Player style. */
@Composable
private fun PixelProgressSection(
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    accentColor: Color,
    onSeek: (Float) -> Unit,
) {
    WaveformSeekBar(
        progress = if (duration > 0) currentPosition.toFloat() / duration else 0f,
        isPlaying = isPlaying,
        activeColor = accentColor,
        inactiveColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.25f),
        onSeek = onSeek,
        modifier = Modifier.fillMaxWidth(),
        durationMs = duration,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            com.raulshma.jellyplay.core.ui.components.formatDurationMs(currentPosition),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor.copy(alpha = 0.8f),
        )
        Text(
            if (duration > 0) com.raulshma.jellyplay.core.ui.components.formatDurationMs(duration) else "--:--",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

/** Primary transport row in a pill container: |◁  ‖  ▷| */
@Composable
private fun PixelTransportControls(
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    pillSurface: Color,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smoothPill)
            .background(pillSurface)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sharedTransitionScope = com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope.current
        val animatedVisibilityScope = com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope.current

        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        val sharedNextModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedElement(
                    rememberSharedContentState(key = "audio_player_skip_next"),
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        } else Modifier

        IconButtonWithPressAnimation(
            onClick = onSkipPrevious,
            icon = {
                Icon(
                    Tabler.Outline.PlayerSkipBack, "Previous",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            },
            size = 48.dp,
        )
        // Central play/pause — larger, rounded-square, light accent bg
        PixelPlayPauseButton(
            isPlaying = isPlaying,
            onClick = onTogglePlayPause,
            accentColor = accentColor,
        )
        IconButtonWithPressAnimation(
            onClick = onSkipNext,
            icon = {
                Icon(
                    Tabler.Outline.PlayerSkipForward, "Next",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            },
            size = 48.dp,
            modifier = sharedNextModifier,
        )
    }
}

/** Secondary controls row: Shuffle, Repeat, Favorite — in darker pill */
@Composable
private fun PixelSecondaryControls(
    shuffleMode: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    downloadItem: com.raulshma.jellyplay.core.model.DownloadItem?,
    abLoopStartMs: Long?,
    abLoopEndMs: Long?,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDownloadClick: () -> Unit,
    onAbLoopClick: () -> Unit,
    pillSurfaceDark: Color,
    accentColor: Color,
) {
    val abLabelSetA = stringResource(R.string.audio_ab_set_point_a)
    val abLabelSetB = stringResource(R.string.audio_ab_set_point_b)
    val abLabelClear = stringResource(R.string.audio_ab_clear)
    Row(
        modifier = Modifier
            .fillMaxWidth(0.8f)
            .clip(ShapeCache.smoothPill)
            .background(pillSurfaceDark)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonWithPressAnimation(
            onClick = onToggleShuffle,
            tint = if (shuffleMode) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                Icon(Tabler.Outline.ArrowsShuffle, "Shuffle", modifier = Modifier.size(22.dp))
            },
        )
        IconButtonWithPressAnimation(
            onClick = onCycleRepeatMode,
            tint = if (repeatMode > 0) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                Icon(
                    if (repeatMode == 2) Tabler.Outline.RepeatOnce else Tabler.Outline.Repeat,
                    when (repeatMode) {
                        0 -> "Repeat off"; 1 -> "Repeat all"; else -> "Repeat one"
                    },
                    modifier = Modifier.size(22.dp),
                )
            },
        )
        // A→B repeat (enhancements §5.4): cycles set-A → set-B → clear.
        IconButtonWithPressAnimation(
            onClick = onAbLoopClick,
            tint = if (abLoopStartMs != null) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                val label = when {
                    abLoopStartMs != null && abLoopEndMs != null -> abLabelClear
                    abLoopStartMs != null -> abLabelSetB
                    else -> abLabelSetA
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(22.dp)
                        .semantics { this.contentDescription = label },
                ) {
                    Text(
                        text = if (abLoopEndMs != null) "A-B" else "A",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (abLoopStartMs != null) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
        IconButtonWithPressAnimation(
            onClick = onToggleFavorite,
            tint = if (isFavorite) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                Icon(
                    if (isFavorite) Tabler.Filled.Heart else Tabler.Outline.Heart,
                    "Favorite",
                    modifier = Modifier.size(22.dp),
                )
            },
        )
        IconButtonWithPressAnimation(
            onClick = onDownloadClick,
            tint = if (downloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            icon = {
                if (downloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.DOWNLOADING || downloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.PENDING) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                } else {
                    Icon(
                        imageVector = if (downloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) Tabler.Outline.Check else Tabler.Outline.Download,
                        contentDescription = "Download",
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
        )
    }
}

/** Pixel Player play/pause: rounded-square, light accent background */
@Composable
private fun PixelPlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    accentColor: Color,
) {
    val focusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "pixelPlayScale",
    )

    val sharedTransitionScope = com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope.current

    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "audio_player_play_pause"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

    val buttonBg = MaterialTheme.colorScheme.primaryContainer
    val iconColor = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = Modifier
            .then(sharedModifier)
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, ShapeCache.smooth20)
            .clip(ShapeCache.smooth20)
            .background(buttonBg)
            
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
            if (isPlaying) "Pause" else "Play",
            modifier = Modifier.size(36.dp),
            tint = iconColor,
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconButtonWithPressAnimation(
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    icon: @Composable () -> Unit,
    size: androidx.compose.ui.unit.Dp = 40.dp,
    modifier: Modifier = Modifier,
) {
    val focusState = rememberTvFocusState()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "transportScale",
    )

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, CircleShape)
            ,
        shapes = androidx.compose.material3.IconButtonDefaults.shapes(),
        interactionSource = interactionSource,
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint
        ) {
            icon()
        }
    }
}

private enum class DragDirection { VERTICAL, HORIZONTAL }

@Composable
private fun SwipeTrackCard(
    title: String,
    artist: String,
    artworkUrl: String,
    isNext: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = ShapeCache.smooth16,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        ),
        modifier = modifier
            .width(260.dp)
            .height(80.dp)
            .shadow(12.dp, ShapeCache.smooth16),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isNext) {
                Icon(
                    Tabler.Outline.PlayerSkipForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(
                    Tabler.Outline.PlayerSkipBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (artworkUrl.isNotBlank()) {
                    MediaImage(
                        url = artworkUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Tabler.Outline.Music,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

