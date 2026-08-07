package com.raulshma.jellyplay.feature.player.live

import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.raulshma.jellyplay.core.designsystem.theme.PlayerDarkTheme
import com.raulshma.jellyplay.core.ui.player.findActivity
import com.raulshma.jellyplay.core.ui.player.playerBottomControlsEnter
import com.raulshma.jellyplay.core.ui.player.playerBottomControlsExit
import com.raulshma.jellyplay.core.ui.player.playerTopControlsEnter
import com.raulshma.jellyplay.core.ui.player.playerTopControlsExit
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.feature.player.live.components.ChannelZapToast
import com.raulshma.jellyplay.feature.player.live.components.LiveChannelListSheet
import com.raulshma.jellyplay.feature.player.live.components.LiveErrorBanner
import com.raulshma.jellyplay.feature.player.live.components.LivePlayerBottomBar
import com.raulshma.jellyplay.feature.player.live.components.LivePlayerTopBar
import com.raulshma.jellyplay.feature.player.live.components.LiveRecordSheet
import com.raulshma.jellyplay.feature.player.live.components.LiveStreamOptionSheet
import kotlinx.coroutines.delay

private const val CONTROLS_AUTO_HIDE_MS = 4_000L
private const val ZAP_TOAST_MS = 3_000L
private const val POSITION_TICK_MS = 500L

/**
 * Which bottom sheet (if any) is open over the live player. The more-menu
 * can open either the channel list or the stream-option picker; the record
 * button opens the record sheet.
 */
private enum class LiveSheet { Channels, StreamOption, Record }

@Composable
@UnstableApi
fun LivePlayerScreen(
    channelId: String,
    channelName: String,
    audioStreamIndex: Int?,
    subtitleStreamIndex: Int?,
    onBack: () -> Unit,
    onEnterPip: () -> Unit = {},
    onEnterMiniMode: () -> Unit = {},
    viewModel: LiveTvPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var overlayVisible by remember { mutableStateOf(true) }
    var activeSheet by remember { mutableStateOf<LiveSheet?>(null) }
    var zapToastChannelId by remember { mutableStateOf<String?>(null) }
    val isTv = LocalTvMode.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }

    // Initialize playback once when the screen enters.
    LaunchedEffect(channelId) {
        viewModel.initialize(channelId, audioStreamIndex, subtitleStreamIndex)
    }

    // Release the engine when the screen leaves composition — not just on
    // activity destroy. The live VM is activity-scoped (nav3 entries here have
    // no per-entry ViewModelStore owner), so without this a back-press leaves
    // the ExoPlayer alive and audio keeps playing in the background.
    DisposableEffect(Unit) {
        onDispose { viewModel.stop() }
    }

    // Phone (non-TV): consume the system/gesture back so a predictive-back
    // gesture becomes a normal synchronous pop instead of a simultaneous
    // target+initial compose. The shared SaveableStateHolder crashes with
    // "Key ChannelDetail(...) was used multiple times" when the revealed
    // detail route and the player both compose against it during the gesture.
    // TV uses the D-pad onBack handler below instead.
    if (!isTv) {
        BackHandler {
            when {
                activeSheet != null -> activeSheet = null
                overlayVisible -> overlayVisible = false
                else -> onBack()
            }
        }
    }

    // Immersive fullscreen + orientation lock + keep-screen-on.
    DisposableEffect(activity) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val originalOrientation = activity?.requestedOrientation
        if (controller != null) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        activity?.requestedOrientation = if (isTv) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (originalOrientation != null && activity != null) {
                activity.requestedOrientation = originalOrientation
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Re-hide system bars when the window regains focus (PiP/multiwindow return).
    DisposableEffect(activity) {
        val window = activity?.window ?: return@DisposableEffect onDispose {}
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {}
    }

    // Position ticker — drives the seek bar + at-live-edge while playing.
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            viewModel.refreshPosition()
            delay(POSITION_TICK_MS)
        }
    }

    // Controls auto-hide: 4s after showing, unless the sheet is open.
    LaunchedEffect(overlayVisible, state.currentIndex) {
        if (overlayVisible && activeSheet == null) {
            delay(CONTROLS_AUTO_HIDE_MS)
            overlayVisible = false
        }
    }

    // Zap toast visibility: shows on channel change, hides after 3s.
    LaunchedEffect(state.currentIndex) {
        zapToastChannelId = state.currentChannel?.id
        delay(ZAP_TOAST_MS)
        if (zapToastChannelId == state.currentChannel?.id) zapToastChannelId = null
    }

    PlayerDarkTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .focusable(true)
                .then(
                    if (!isTv) Modifier.pointerInput(Unit) {
                        detectTapGestures {
                            // Toggle chrome on tap when the channel sheet is closed.
                            // Tapping also restarts the auto-hide timer via overlayVisible.
                            if (activeSheet == null) overlayVisible = !overlayVisible
                        }
                    } else Modifier,
                )
                .onDpadKeyEvent(
                    onUp = { key ->
                        if (activeSheet != null) return@onDpadKeyEvent false
                        if (key.isKeyUp) {
                            viewModel.channelUp(audioStreamIndex, subtitleStreamIndex)
                            overlayVisible = false
                            true
                        } else false
                    },
                    onDown = { key ->
                        if (activeSheet != null) return@onDpadKeyEvent false
                        if (key.isKeyUp) {
                            viewModel.channelDown(audioStreamIndex, subtitleStreamIndex)
                            overlayVisible = false
                            true
                        } else false
                    },
                    onSelect = {
                        if (activeSheet != null) {
                            activeSheet = null
                        } else {
                            overlayVisible = !overlayVisible
                        }
                        true
                    },
                    onBack = {
                        when {
                            activeSheet != null -> activeSheet = null
                            overlayVisible -> overlayVisible = false
                            else -> onBack()
                        }
                        true
                    },
                ),
        ) {
            // Surface layer
            val media3Player = remember(state.engineState) {
                viewModel.engineForRendering()?.media3Player
            }
            if (media3Player != null) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = media3Player
                            useController = false
                            // Buffering is rendered by our own full-screen
                            // LiveErrorBanner (M3 Expressive LoadingIndicator);
                            // disable Media3's native spinner to avoid doubles.
                            setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                        }
                    },
                )
            }

            val currentChannel = state.currentChannel

            // Chrome layer — top bar
            AnimatedVisibility(
                visible = overlayVisible && currentChannel != null,
                enter = playerTopControlsEnter(),
                exit = playerTopControlsExit(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                if (currentChannel != null) {
                    val currentProgram = state.currentProgram
                    LivePlayerTopBar(
                        channel = currentChannel,
                        logoUrl = viewModel.logoUrlFor(currentChannel),
                        isMuted = state.isMuted,
                        playMethod = state.playMethod,
                        // Record affordance only when a program is
                        // airing (no program → nothing to record). Red-tinted
                        // while a timer is already scheduled on the program.
                        isRecording = currentProgram?.let {
                            !it.timerId.isNullOrEmpty() || !it.seriesTimerId.isNullOrEmpty()
                        } ?: false,
                        canRecord = currentProgram != null,
                        onBack = onBack,
                        onMute = viewModel::toggleMute,
                        onRecord = { activeSheet = LiveSheet.Record },
                    )
                }
            }

            // Chrome layer — bottom bar
            AnimatedVisibility(
                visible = overlayVisible,
                enter = playerBottomControlsEnter(),
                exit = playerBottomControlsExit(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val canSeek = state.durationMs > 0L && !state.isAtLiveEdge
                LivePlayerBottomBar(
                    isPlaying = state.isPlaying,
                    canSeek = canSeek,
                    isAtLiveEdge = state.isAtLiveEdge,
                    positionMs = state.positionMs,
                    durationMs = state.durationMs,
                    onPlayPause = viewModel::togglePlayPause,
                    onSeekBack = {
                        viewModel.seekWithinDvr((state.positionMs - 10_000L).coerceAtLeast(0L))
                    },
                    onSeekForward = {
                        viewModel.seekWithinDvr(state.positionMs + 10_000L)
                    },
                    onPlayFromStart = viewModel::playFromStart,
                    onChannelUp = { viewModel.channelUp(audioStreamIndex, subtitleStreamIndex) },
                    onChannelDown = { viewModel.channelDown(audioStreamIndex, subtitleStreamIndex) },
                    onMore = { activeSheet = LiveSheet.StreamOption },
                    onChannels = { activeSheet = LiveSheet.Channels },
                    onSeek = viewModel::seekWithinDvr,
                    onSeekToLiveEdge = viewModel::seekToLiveEdge,
                )
            }

            // Zap toast
            AnimatedVisibility(
                visible = zapToastChannelId != null && currentChannel != null,
                enter = playerTopControlsEnter(),
                exit = playerTopControlsExit(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 96.dp),
            ) {
                if (currentChannel != null) {
                    ChannelZapToast(
                        channel = currentChannel,
                        currentProgram = state.currentProgram,
                        logoUrl = viewModel.logoUrlFor(currentChannel),
                    )
                }
            }

            // Sheets
            when (activeSheet) {
                LiveSheet.Channels -> {
                    LiveChannelListSheet(
                        channels = state.channels,
                        currentChannelId = state.currentChannel?.id,
                        favorites = state.favorites,
                        logoUrlFor = viewModel::logoUrlFor,
                        onChannelSelected = { id ->
                            viewModel.selectChannelById(id)
                            overlayVisible = false
                        },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onDismiss = { activeSheet = null },
                    )
                }
                LiveSheet.StreamOption -> {
                    LiveStreamOptionSheet(
                        currentOption = state.liveStreamOption,
                        onSelect = { viewModel.setLiveStreamOption(it) },
                        onDismiss = { activeSheet = null },
                    )
                }
                LiveSheet.Record -> {
                    LiveRecordSheet(
                        program = state.currentProgram,
                        onRecordOnce = viewModel::recordCurrentProgramOnce,
                        onRecordSeries = viewModel::recordCurrentProgramSeries,
                        onCancelTimer = viewModel::cancelCurrentProgramTimer,
                        onCancelSeries = viewModel::cancelCurrentProgramSeries,
                        onDismiss = { activeSheet = null },
                    )
                }
                null -> {}
            }

            // Error / loading
            if (state.isBuffering || state.errorMessage != null) {
                LiveErrorBanner(
                    isBuffering = state.isBuffering,
                    errorMessage = state.errorMessage,
                    errorDetail = state.errorDetail,
                    currentOption = state.liveStreamOption,
                    onRetry = { viewModel.retry(audioStreamIndex, subtitleStreamIndex) },
                    onRetryWithOption = { viewModel.setLiveStreamOption(it) },
                    onBack = onBack,
                )
            }
        }
    }
}
