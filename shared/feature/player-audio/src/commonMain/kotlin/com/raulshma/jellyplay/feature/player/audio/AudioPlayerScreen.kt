package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import org.koin.compose.viewmodel.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsLightTheme
import com.raulshma.jellyplay.core.data.playback.QueueUndoEvent
import com.raulshma.jellyplay.core.ui.components.ConfirmDialog
import com.raulshma.jellyplay.core.ui.components.JellyPlayBackHandler
import com.raulshma.jellyplay.core.ui.components.ConfirmTone
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_cancel
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_download_delete_confirm
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_download_delete_message
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_download_delete_title
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_error_retry
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_no_playlists
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_playlist_items_count
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_playlist_picker_title
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_undo_action
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_undo_queue_cleared
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_undo_skipped_next
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_undo_skipped_previous
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_undo_track_moved
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_undo_track_removed
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import com.raulshma.jellyplay.feature.player.audio.sheets.AudioEffectsSheet
import com.raulshma.jellyplay.feature.player.audio.sheets.AudioSleepTimerSheet
import com.raulshma.jellyplay.feature.player.audio.sheets.EqualizerSheet
import com.raulshma.jellyplay.feature.player.audio.sheets.LyricsSearchSheet
import com.raulshma.jellyplay.feature.player.audio.sheets.QueueSheet
import com.raulshma.jellyplay.feature.player.audio.sheets.SpeedPickerSheet
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import androidx.compose.ui.focus.FocusRequester
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus

private const val DOUBLE_TAP_SEEK_MS = 10_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    itemId: String,
    onBack: () -> Unit,
    onAmbientClick: (String?, String, String) -> Unit = { _, _, _ -> },
    onArtistClick: (String) -> Unit = {},
    viewModel: AudioPlayerViewModel = koinViewModel(),
) {
    val sharedTransitionScope = com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope.current

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // The effects slice is controller-owned state (AudioEffectsController's
    // EffectsCommandCore), re-exposed by the VM — not a uiState field.
    val effects by viewModel.effectsState.collectAsStateWithLifecycle()
    val lyricsState = uiState.lyrics
    val sleepTimer = uiState.sleepTimer
    val queueState = uiState.queue
    val playlistPicker by viewModel.playlistPicker.state.collectAsStateWithLifecycle()

    // The ONE sheet admission fold (ReaderSheetStack precedent): every
    // sheet/dialog flag lives here behind `sheets.open`, routed through the
    // AudioPlayerSheet vocabulary. Lyrics and the menu are declared
    // non-sheet arms inside (persisted preference / self-dismissing dropdown).
    val sheets = remember { AudioPlayerSheetStack() }

    val snackbarHostState = remember { SnackbarHostState() }

    var showErrorOverlay by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.playbackError) {
        // Show the persistent error overlay only. A snackbar was previously shown here too,
        // which was redundant with the overlay and auto-dismissed before the user could react.
        showErrorOverlay = uiState.playbackError != null
    }

    // Surface an "Undo" affordance after destructive queue operations
    // (clear / remove / skip / move) so an accidental action is recoverable.
    val undoClearedMessage = stringResource(Res.string.audio_undo_queue_cleared)
    val undoRemovedMessage = stringResource(Res.string.audio_undo_track_removed)
    val undoMovedMessage = stringResource(Res.string.audio_undo_track_moved)
    val undoSkippedNextMessage = stringResource(Res.string.audio_undo_skipped_next)
    val undoSkippedPrevMessage = stringResource(Res.string.audio_undo_skipped_previous)
    val undoActionLabel = stringResource(Res.string.audio_undo_action)
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
                // Undo affordances are recoverable actions, not errors; keep
                // them brief (≤4s) so they don't linger over the controls.
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onEvent(AudioPlayerUiEvent.UndoLastQueueOperation)
            }
        }
    }

    val artworkScale = remember { Animatable(0.8f) }
    val contentAlpha = remember { Animatable(0f) }
    // Capture scheme specs in composable scope; the animateTo calls below run in coroutines.
    val artworkScaleSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val contentFadeSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val swipeSpringSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    val boundsSpec = MaterialTheme.motionScheme.slowSpatialSpec<androidx.compose.ui.geometry.Rect>()

    val isTv = LocalTvMode.current
    val playFocusRequester = remember { FocusRequester() }

    LaunchedEffect(itemId) {
        viewModel.onEvent(AudioPlayerUiEvent.Play(itemId))
        artworkScale.animateTo(1f, artworkScaleSpec)
    }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, contentFadeSpec)
        if (isTv) {
            for (attempt in 1..20) {
                androidx.compose.runtime.withFrameNanos { }
                if (playFocusRequester.tryRequestFocus("audio_play_button")) break
            }
        }
    }

    JellyPlayBackHandler(enabled = true) {
        if (!sheets.consumeBack()) {
            // Lyrics visibility is a persisted preference, not a transient
            // overlay — back navigates away without hiding them, so the choice
            // survives the next time the player is opened.
            onBack()
        }
    }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    // Seed the lyrics overlay from the persisted preference, then keep them in
    // sync: toggling in the UI writes back so the choice survives across opens.
    LaunchedEffect(preferences.audioLyricsVisible) {
        sheets.showLyrics = preferences.audioLyricsVisible
    }
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

    val isDarkTheme = !LocalIsLightTheme.current



    // Animatables for swipe gestures
    val swipeDismissOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val horizontalSwipeOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    var dragDirection by remember { mutableStateOf<AudioDragDirection?>(null) }
    var totalDragX by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var totalDragY by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }

    ArtworkThemeWrapper(
        imageUrl = uiState.albumArtUrl.ifBlank { null },
        dynamicTheming = preferences.theme.dynamicTheming,
        darkTheme = isDarkTheme,
        oledMode = preferences.theme.oledMode,
        colorStyle = preferences.theme.colorStyle,
        accentColorSwatch = preferences.theme.accentColorSwatch,
    ) {
        @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
        val sharedContainerModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
            with(sharedTransitionScope) {
                Modifier.sharedBounds(
                    rememberSharedContentState(key = "audio_player_container"),
                    animatedVisibilityScope = animatedVisibilityScope,
                    boundsTransform = androidx.compose.animation.BoundsTransform { _, _ ->
                        boundsSpec
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
                                AudioDragDirection.VERTICAL -> when (
                                    audioVerticalSwipeAction(swipeDismissOffset.value, totalDragY)
                                ) {
                                    AudioVerticalSwipeAction.OpenQueue -> {
                                        coroutineScope.launch {
                                            swipeDismissOffset.animateTo(0f, swipeSpringSpec)
                                        }
                                        sheets.show(AudioPlayerSheet.Queue)
                                    }
                                    AudioVerticalSwipeAction.Dismiss -> {
                                        // Instantly reset visual translation offset to ensure morph begins from stable bounds
                                        coroutineScope.launch {
                                            swipeDismissOffset.snapTo(0f)
                                            onBack()
                                        }
                                    }
                                    AudioVerticalSwipeAction.Settle -> {
                                        coroutineScope.launch {
                                            swipeDismissOffset.animateTo(0f, swipeSpringSpec)
                                        }
                                    }
                                }
                                AudioDragDirection.HORIZONTAL -> when (
                                    audioHorizontalSwipeAction(horizontalSwipeOffset.value)
                                ) {
                                    AudioHorizontalSwipeAction.SkipNext -> { // Swipe Left -> Next
                                        coroutineScope.launch {
                                            viewModel.onEvent(AudioPlayerUiEvent.SkipToNext)
                                            horizontalSwipeOffset.animateTo(0f, swipeSpringSpec)
                                        }
                                    }
                                    AudioHorizontalSwipeAction.SkipPrevious -> { // Swipe Right -> Prev
                                        coroutineScope.launch {
                                            viewModel.onEvent(AudioPlayerUiEvent.SkipToPrevious)
                                            horizontalSwipeOffset.animateTo(0f, swipeSpringSpec)
                                        }
                                    }
                                    AudioHorizontalSwipeAction.Settle -> {
                                        coroutineScope.launch {
                                            horizontalSwipeOffset.animateTo(0f, swipeSpringSpec)
                                        }
                                    }
                                }
                                null -> {}
                            }
                            dragDirection = null
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                swipeDismissOffset.animateTo(0f, swipeSpringSpec)
                                horizontalSwipeOffset.animateTo(0f, swipeSpringSpec)
                            }
                            dragDirection = null
                        }
                    ) { change, dragAmount ->
                        totalDragX += dragAmount.x
                        totalDragY += dragAmount.y

                        if (dragDirection == null) {
                            dragDirection = audioDragDirection(totalDragX, totalDragY)
                        }

                        when (dragDirection) {
                            AudioDragDirection.VERTICAL -> {
                                change.consume()
                                coroutineScope.launch {
                                    val newOffset = (swipeDismissOffset.value + dragAmount.y).coerceAtLeast(-120f)
                                    swipeDismissOffset.snapTo(newOffset)
                                }
                            }
                            AudioDragDirection.HORIZONTAL -> {
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
                // Double-tap to seek (±10s) on the left/right thirds and
                // toggle play/pause on the middle third. Mirrors the video
                // player's gesture; audio exposes an absolute seek only, so
                // compute (currentPosition ± delta).coerceIn(0, duration).
                .pointerInput(Unit) {
                    val width = size.width
                    detectTapGestures(
                        onDoubleTap = { offset ->
                            when (doubleTapSeekAction(offset.x, width.toFloat())) {
                                DoubleTapSeekAction.SeekBack -> {
                                    val target = (viewModel.currentPositionState.value - DOUBLE_TAP_SEEK_MS)
                                        .coerceAtLeast(0L)
                                    viewModel.onEvent(AudioPlayerUiEvent.SeekTo(target))
                                }
                                DoubleTapSeekAction.SeekForward -> {
                                    val duration = uiState.duration
                                    val target = (viewModel.currentPositionState.value + DOUBLE_TAP_SEEK_MS)
                                        .coerceAtMost(duration)
                                    viewModel.onEvent(AudioPlayerUiEvent.SeekTo(target))
                                }
                                DoubleTapSeekAction.TogglePlayPause -> viewModel.onEvent(AudioPlayerUiEvent.TogglePlayPause)
                            }
                        },
                    )
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
                    lyricsVisible = sheets.showLyrics,
                    onLyricsClick = {
                        sheets.showLyrics = !sheets.showLyrics
                        viewModel.onEvent(AudioPlayerUiEvent.SetLyricsVisible(sheets.showLyrics))
                    },
                    onQueueClick = { sheets.show(AudioPlayerSheet.Queue) },
                    onMenuToggle = { sheets.showMenu = it },
                    showMenu = sheets.showMenu,
                    speed = uiState.speed,
                    dialogueBoostEnabled = effects.dialogueBoostEnabled,
                    dialogueBoostStrength = effects.dialogueBoostStrength,
                    nightModeEnabled = effects.nightModeEnabled,
                    nightModeStrength = effects.nightModeStrength,
                    onSpeedClick = { sheets.showFromMenu(AudioPlayerSheet.SpeedPicker) },
                    onEqualizerClick = { sheets.showFromMenu(AudioPlayerSheet.Equalizer) },
                    onEffectsClick = { sheets.showFromMenu(AudioPlayerSheet.Effects) },
                    onDialogueBoostClick = { sheets.showMenu = false; viewModel.onEvent(AudioPlayerUiEvent.ToggleDialogueBoost) },
                    onDialogueBoostStrengthChange = { viewModel.onEvent(AudioPlayerUiEvent.SetDialogueBoostStrength(it)) },
                    onNightModeClick = { sheets.showMenu = false; viewModel.onEvent(AudioPlayerUiEvent.ToggleNightMode) },
                    onNightModeStrengthChange = { viewModel.onEvent(AudioPlayerUiEvent.SetNightModeStrength(it)) },
                    onAmbientClick = { sheets.showMenu = false; onAmbientClick(uiState.albumArtUrl.ifBlank { null }, uiState.title, uiState.artist) },
                    onAddToPlaylistClick = { sheets.showMenu = false; viewModel.onEvent(AudioPlayerUiEvent.OpenPlaylistPicker) },
                    sleepTimerActive = sleepTimer.active,
                    sleepTimerEndOfEpisode = sleepTimer.endOfEpisode,
                    sleepTimerRemainingFlow = viewModel.sleepTimerRemainingMs,
                    onSleepTimerClick = { sheets.showFromMenu(AudioPlayerSheet.SleepTimer) },
                    karaokeMode = lyricsState.karaokeMode,
                    onKaraokeToggle = { viewModel.onEvent(AudioPlayerUiEvent.SetKaraokeModeEnabled(it)) },
                    hasKaraokeLyrics = lyricsState.hasKaraokeLyrics,
                    castController = viewModel.castController,
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
                                albumArtUrl = uiState.albumArtUrl,
                                albumArtBlurHash = uiState.albumArtBlurHash,
                                title = uiState.title,
                                scale = artworkScale.value,
                                isExpanded = true,
                                lyricsVisible = sheets.showLyrics,
                                lyrics = lyricsState.lyrics,
                                currentLyricIndex = lyricsState.currentLyricIndex,
                                isFetchingLyrics = lyricsState.isFetchingLyrics,
                                lyricsSource = lyricsState.lyricsSource,
                                onSearchClick = { sheets.show(AudioPlayerSheet.LyricsSearch) },
                                karaokeMode = lyricsState.karaokeMode,
                                currentPositionMs = viewModel.currentPositionState,
                                lyricsOffsetMs = lyricsState.lyricsOffsetMs,
                                onLyricsOffsetChange = { viewModel.onEvent(AudioPlayerUiEvent.SetLyricsOffset(it)) },
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
                                title = uiState.title,
                                artist = uiState.artist,
                                artistId = uiState.artistId,
                                onArtistClick = onArtistClick,
                            )
                            Spacer(Modifier.height(28.dp))
                            PixelProgressSection(
                                currentPosition = viewModel.currentPositionState,
                                duration = uiState.duration,
                                isPlaying = uiState.isPlaying,
                                accentColor = accentColor,
                                onSeek = { frac ->
                                    if (uiState.duration > 0) viewModel.onEvent(AudioPlayerUiEvent.SeekTo((frac * uiState.duration).toLong()))
                                },
                            )
                            Spacer(Modifier.height(16.dp))
                            PixelTransportControls(
                                isPlaying = uiState.isPlaying,
                                isLoading = uiState.isLoading,
                                onTogglePlayPause = { viewModel.onEvent(AudioPlayerUiEvent.TogglePlayPause) },
                                onSkipPrevious = { viewModel.onEvent(AudioPlayerUiEvent.SkipToPrevious) },
                                onSkipNext = { viewModel.onEvent(AudioPlayerUiEvent.SkipToNext) },
                                pillSurface = pillSurface,
                                accentColor = accentColor,
                                playFocusRequester = playFocusRequester,
                            )
                            Spacer(Modifier.height(12.dp))
                            PixelSecondaryControls(
                                shuffleMode = queueState.shuffleMode,
                                repeatMode = queueState.repeatMode,
                                isFavorite = uiState.isFavorite,
                                downloadItem = currentDownloadItem,
                                abLoopStartMs = abLoopStart,
                                abLoopEndMs = abLoopEnd,
                                showDownload = viewModel.isDownloadSupported,
                                onToggleShuffle = { viewModel.onEvent(AudioPlayerUiEvent.ToggleShuffle) },
                                onCycleRepeatMode = { viewModel.onEvent(AudioPlayerUiEvent.CycleRepeatMode) },
                                onToggleFavorite = { viewModel.onEvent(AudioPlayerUiEvent.ToggleFavorite) },
                                onDownloadClick = {
                                    if (currentDownloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) {
                                        sheets.show(AudioPlayerSheet.DeleteConfirm)
                                    } else {
                                        viewModel.onEvent(AudioPlayerUiEvent.DownloadCurrentTrack)
                                    }
                                },
                                onAbLoopClick = { viewModel.onEvent(AudioPlayerUiEvent.CycleAbLoop) },
                                pillSurfaceDark = pillSurfaceDark,
                                accentColor = accentColor,
                            )
                            Spacer(Modifier.height(16.dp))
                            NextTrackSection(
                                queue = queueState.queue,
                                currentIndex = queueState.currentIndex,
                                onSkipTrack = { viewModel.onEvent(AudioPlayerUiEvent.RemoveFromQueue(it)) },
                                accentColor = accentColor,
                            )
                        }
                    }
                } else {
                    // Phone: vertical layout (Pixel Player style)
                    Spacer(Modifier.weight(0.5f))

                    AlbumArtwork(
                        albumArtUrl = uiState.albumArtUrl,
                        albumArtBlurHash = uiState.albumArtBlurHash,
                        title = uiState.title,
                        scale = artworkScale.value,
                        isExpanded = false,
                        lyricsVisible = sheets.showLyrics,
                        lyrics = lyricsState.lyrics,
                        currentLyricIndex = lyricsState.currentLyricIndex,
                        isFetchingLyrics = lyricsState.isFetchingLyrics,
                        lyricsSource = lyricsState.lyricsSource,
                        onSearchClick = { sheets.show(AudioPlayerSheet.LyricsSearch) },
                        karaokeMode = lyricsState.karaokeMode,
                        currentPositionMs = viewModel.currentPositionState,
                        lyricsOffsetMs = lyricsState.lyricsOffsetMs,
                        onLyricsOffsetChange = { viewModel.onEvent(AudioPlayerUiEvent.SetLyricsOffset(it)) },
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
                            title = uiState.title,
                            artist = uiState.artist,
                            artistId = uiState.artistId,
                            onArtistClick = onArtistClick,
                        )
                        Spacer(Modifier.height(24.dp))
                        PixelProgressSection(
                            currentPosition = viewModel.currentPositionState,
                            duration = uiState.duration,
                            isPlaying = uiState.isPlaying,
                            accentColor = accentColor,
                            onSeek = { frac ->
                                if (uiState.duration > 0) viewModel.onEvent(AudioPlayerUiEvent.SeekTo((frac * uiState.duration).toLong()))
                            },
                        )
                        Spacer(Modifier.height(20.dp))
                        PixelTransportControls(
                            isPlaying = uiState.isPlaying,
                            isLoading = uiState.isLoading,
                            onTogglePlayPause = { viewModel.onEvent(AudioPlayerUiEvent.TogglePlayPause) },
                            onSkipPrevious = { viewModel.onEvent(AudioPlayerUiEvent.SkipToPrevious) },
                            onSkipNext = { viewModel.onEvent(AudioPlayerUiEvent.SkipToNext) },
                            pillSurface = pillSurface,
                            accentColor = accentColor,
                            playFocusRequester = playFocusRequester,
                        )
                        Spacer(Modifier.height(12.dp))
                        PixelSecondaryControls(
                            shuffleMode = queueState.shuffleMode,
                            repeatMode = queueState.repeatMode,
                            isFavorite = uiState.isFavorite,
                            downloadItem = currentDownloadItem,
                            abLoopStartMs = abLoopStart,
                            abLoopEndMs = abLoopEnd,
                            showDownload = viewModel.isDownloadSupported,
                            onToggleShuffle = { viewModel.onEvent(AudioPlayerUiEvent.ToggleShuffle) },
                            onCycleRepeatMode = { viewModel.onEvent(AudioPlayerUiEvent.CycleRepeatMode) },
                            onToggleFavorite = { viewModel.onEvent(AudioPlayerUiEvent.ToggleFavorite) },
                            onDownloadClick = {
                                if (currentDownloadItem?.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) {
                                    sheets.show(AudioPlayerSheet.DeleteConfirm)
                                } else {
                                    viewModel.onEvent(AudioPlayerUiEvent.DownloadCurrentTrack)
                                }
                            },
                            onAbLoopClick = { viewModel.onEvent(AudioPlayerUiEvent.CycleAbLoop) },
                            pillSurfaceDark = pillSurfaceDark,
                            accentColor = accentColor,
                        )
                        Spacer(Modifier.height(16.dp))
                        NextTrackSection(
                            queue = queueState.queue,
                            currentIndex = queueState.currentIndex,
                            onSkipTrack = { viewModel.onEvent(AudioPlayerUiEvent.RemoveFromQueue(it)) },
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
                val nextIndex = if (queueState.currentIndex >= 0 && queueState.queue.isNotEmpty()) {
                    (queueState.currentIndex + 1) % queueState.queue.size
                } else 0
                val nextTrack = queueState.queue.getOrNull(nextIndex)
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
                val prevIndex = if (queueState.currentIndex >= 0 && queueState.queue.isNotEmpty()) {
                    (queueState.currentIndex - 1 + queueState.queue.size) % queueState.queue.size
                } else 0
                val prevTrack = queueState.queue.getOrNull(prevIndex)
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

            com.raulshma.jellyplay.core.ui.components.JellyPlaySnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp),
            )

            AnimatedVisibility(
                visible = showErrorOverlay && uiState.playbackError != null,
                enter = fadeIn(MaterialTheme.motionScheme.defaultEffectsSpec()),
                exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val retryFocusState = rememberTvFocusState(focusedScale = 1.05f)
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
                            uiState.playbackError ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                showErrorOverlay = false
                                viewModel.onEvent(AudioPlayerUiEvent.Play(itemId))
                            },
                            modifier = Modifier
                                .then(retryFocusState.focusModifier)
                                .tvFocusIndicator(retryFocusState, ShapeCache.smooth12),
                        ) {
                            Text(stringResource(Res.string.audio_error_retry), color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        } // Close Box (root container)
    } // Close ArtworkThemeWrapper

    // ── Bottom sheets (unchanged functionality) ──
    if (sheets.showQueue && queueState.queue.isNotEmpty()) {
        QueueSheet(
            queue = queueState.queue,
            currentIndex = queueState.currentIndex,
            onSelect = { index ->
                viewModel.onEvent(AudioPlayerUiEvent.PlayFromQueue(index))
                sheets.hide(AudioPlayerSheet.Queue)
            },
            onRemove = { index -> viewModel.onEvent(AudioPlayerUiEvent.RemoveFromQueue(index)) },
            onDismiss = { sheets.hide(AudioPlayerSheet.Queue) },
        )
    }

    if (sheets.showSpeedPicker) {
        SpeedPickerSheet(
            currentSpeed = uiState.speed,
            onSelect = { viewModel.onEvent(AudioPlayerUiEvent.ChangePlaybackSpeed(it)) },
            onDismiss = { sheets.hide(AudioPlayerSheet.SpeedPicker) },
        )
    }

    if (sheets.showEqualizer) {
        EqualizerSheet(
            enabled = effects.equalizerEnabled,
            bandLevels = effects.equalizerSettings.bandLevels,
            currentPreset = effects.equalizerPreset,
            onToggle = { viewModel.onEvent(AudioPlayerUiEvent.ToggleEqualizer) },
            onBandChange = { index, level -> viewModel.onEvent(AudioPlayerUiEvent.SetEqualizerBand(index, level)) },
            onReset = { viewModel.onEvent(AudioPlayerUiEvent.ResetEqualizer) },
            onPresetChange = { viewModel.onEvent(AudioPlayerUiEvent.SetEqualizerPreset(it)) },
            onDismiss = { sheets.hide(AudioPlayerSheet.Equalizer) },
        )
    }

    if (sheets.showLyricsSearch) {
        LyricsSearchSheet(
            artist = uiState.artist,
            title = uiState.title,
            searchResults = lyricsState.searchResults,
            isSearching = lyricsState.isSearching,
            onSearch = { viewModel.onEvent(AudioPlayerUiEvent.SearchLyrics(it)) },
            onApplyTrack = { viewModel.onEvent(AudioPlayerUiEvent.ApplyLyrics(it)) },
            onDismiss = { sheets.hide(AudioPlayerSheet.LyricsSearch); viewModel.onEvent(AudioPlayerUiEvent.ClearLyricsSearch) },
        )
    }

    if (sheets.showSleepTimer) {
        val sleepTimerRemainingMs by viewModel.sleepTimerRemainingMs.collectAsStateWithLifecycle()
        AudioSleepTimerSheet(
            isActive = sleepTimer.active,
            isEndOfEpisodeMode = sleepTimer.endOfEpisode,
            remainingMs = sleepTimerRemainingMs,
            lastUsedDurationMs = sleepTimer.lastUsedDurationMs,
            onSelectDuration = { viewModel.onEvent(AudioPlayerUiEvent.StartSleepTimer(it)) },
            onSelectEndOfEpisode = { viewModel.onEvent(AudioPlayerUiEvent.StartSleepTimerEndOfEpisode) },
            onCancel = { viewModel.onEvent(AudioPlayerUiEvent.CancelSleepTimer) },
            onDismiss = { sheets.hide(AudioPlayerSheet.SleepTimer) },
        )
    }

    // Add-to-playlist picker.
    if (playlistPicker.visible) {
        com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet(
            onDismissRequest = { viewModel.onEvent(AudioPlayerUiEvent.DismissPlaylistPicker) },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(),
        ) {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp),
            ) {
                androidx.compose.material3.Text(
                    stringResource(Res.string.audio_playlist_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
                androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                when {
                    playlistPicker.loading -> {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            com.raulshma.jellyplay.core.ui.components.JellyPlayCircularProgressIndicator()
                        }
                    }
                    playlistPicker.playlists.isEmpty() -> {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            androidx.compose.material3.Text(
                                stringResource(Res.string.audio_no_playlists),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    else -> {
                        androidx.compose.foundation.layout.Column {
                            playlistPicker.playlists.forEach { playlist ->
                                androidx.compose.material3.ListItem(
                                    headlineContent = { Text(playlist.name) },
                                    supportingContent = playlist.itemCount.takeIf { it > 0 }?.let { count ->
                                        { Text(stringResource(Res.string.audio_playlist_items_count, count)) }
                                    },
                                    modifier = Modifier.clickable {
                                        viewModel.onEvent(AudioPlayerUiEvent.AddToPlaylist(playlist))
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (sheets.showEffectsSheet) {
        AudioEffectsSheet(
            state = effects,
            onDismiss = { sheets.hide(AudioPlayerSheet.Effects) },
            onOpenEqualizer = { sheets.hide(AudioPlayerSheet.Effects); sheets.show(AudioPlayerSheet.Equalizer) },
            onToggleEqualizer = { viewModel.onEvent(AudioPlayerUiEvent.ToggleEqualizer) },
            onToggleBassBoost = { viewModel.onEvent(AudioPlayerUiEvent.ToggleBassBoost) },
            onBassBoostStrength = { viewModel.onEvent(AudioPlayerUiEvent.SetBassBoostStrength(it)) },
            onToggleVirtualizer = { viewModel.onEvent(AudioPlayerUiEvent.ToggleVirtualizer) },
            onVirtualizerStrength = { viewModel.onEvent(AudioPlayerUiEvent.SetVirtualizerStrength(it)) },
            onReverbPreset = { viewModel.onEvent(AudioPlayerUiEvent.SetReverbPreset(it)) },
            onToggleDialogueBoost = { viewModel.onEvent(AudioPlayerUiEvent.ToggleDialogueBoost) },
            onDialogueBoostStrength = { viewModel.onEvent(AudioPlayerUiEvent.SetDialogueBoostStrength(it)) },
            onToggleNightMode = { viewModel.onEvent(AudioPlayerUiEvent.ToggleNightMode) },
            onNightModeStrength = { viewModel.onEvent(AudioPlayerUiEvent.SetNightModeStrength(it)) },
            onLrBalance = { viewModel.onEvent(AudioPlayerUiEvent.SetLrBalance(it)) },
            onPitchSemitones = { viewModel.onEvent(AudioPlayerUiEvent.SetPitchSemitones(it)) },
            onAutoEqByGenre = { viewModel.onEvent(AudioPlayerUiEvent.SetAutoEqByGenre(it)) },
        )
    }

    if (sheets.showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(Res.string.audio_download_delete_title),
            message = stringResource(Res.string.audio_download_delete_message),
            confirmText = stringResource(Res.string.audio_download_delete_confirm),
            dismissText = stringResource(Res.string.audio_cancel),
            tone = ConfirmTone.DESTRUCTIVE,
            onConfirm = {
                sheets.hide(AudioPlayerSheet.DeleteConfirm)
                viewModel.onEvent(AudioPlayerUiEvent.DownloadCurrentTrack)
            },
            onDismiss = { sheets.hide(AudioPlayerSheet.DeleteConfirm) },
        )
    }
}

/**
 * Surfaces the next queued track beneath the controls and lets the user skip
 * over (remove) it. Hidden when the queue is empty or only one track remains
 * (nothing to skip). Only shows a *real* next track — when the current track is
 * last and the queue doesn't wrap, there is no upcoming item to skip.
 */
@Composable
private fun NextTrackSection(
    queue: List<com.raulshma.jellyplay.core.data.playback.AudioQueueItem>,
    currentIndex: Int,
    onSkipTrack: (Int) -> Unit,
    accentColor: Color,
) {
    if (queue.size <= 1) return
    // A genuine upcoming track exists only when current isn't the last item.
    if (currentIndex < 0 || currentIndex >= queue.lastIndex) return
    val nextIndex = currentIndex + 1
    val nextTrack = queue.getOrNull(nextIndex) ?: return
    NextTrackBar(
        title = nextTrack.name,
        artist = nextTrack.artist,
        artworkUrl = nextTrack.imageUrl,
        onSkipTrack = { onSkipTrack(nextIndex) },
        accentColor = accentColor,
    )
}

