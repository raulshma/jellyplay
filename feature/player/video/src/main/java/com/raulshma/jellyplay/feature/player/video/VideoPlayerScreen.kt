package com.raulshma.jellyplay.feature.player.video

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.data.playback.FrameRateMatcher
import com.raulshma.jellyplay.core.data.cast.CastSessionEvent
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.components.rememberDpadSeekState
import com.raulshma.jellyplay.core.ui.tv.input.onDpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.components.AspectRatioSheet
import com.raulshma.jellyplay.feature.player.video.components.AVSyncSheet
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.feature.player.video.components.DecoderPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.EpisodePickerSheet
import com.raulshma.jellyplay.feature.player.video.components.HdrBadge
import com.raulshma.jellyplay.feature.player.video.components.IntroSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.SegmentSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.NextEpisodeOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlaybackInfoOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlaybackErrorDialog
import com.raulshma.jellyplay.feature.player.video.components.QualityPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.PlaybackModeSheet
import com.raulshma.jellyplay.feature.player.video.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleDownloadSheet
import com.raulshma.jellyplay.feature.player.video.components.CastIndicatorOverlay
import com.raulshma.jellyplay.feature.player.video.components.CompanionDashboard
import com.raulshma.jellyplay.feature.player.video.components.ChapterPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.GestureOverlay
import com.raulshma.jellyplay.feature.player.video.components.PinLockOverlay
import com.raulshma.jellyplay.feature.player.video.components.SlideToUnlockOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlayerControls
import com.raulshma.jellyplay.feature.player.video.components.SpeedPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.SleepTimerSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleStyleSheet
import com.raulshma.jellyplay.feature.player.video.components.VideoStatsOverlay
import com.raulshma.jellyplay.feature.player.video.components.SyncPlayPlayerSheet

import com.raulshma.jellyplay.feature.player.video.components.TrackPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.TrickplayOverlay
import com.raulshma.jellyplay.feature.player.video.components.VideoFilterSheet
import com.raulshma.jellyplay.feature.player.video.findActivity
import com.raulshma.jellyplay.feature.player.video.subtitle.VttTagParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.media3.ui.AspectRatioFrameLayout

private val SubtitleOutlineOffsets = listOf(
    -1 to -1,
    0 to -1,
    1 to -1,
    -1 to 0,
    1 to 0,
    -1 to 1,
    0 to 1,
    1 to 1,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    itemId: String,
    mediaSourceId: String?,
    startPositionTicks: Long,
    subtitleStreamIndex: Int? = null,
    audioStreamIndex: Int? = null,
    onBack: () -> Unit,
    onEnterPip: () -> Unit = {},
    onEnterMiniMode: () -> Unit = {},
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val isInPipMode by viewModel.playerLifecycleManager.isInPipMode.collectAsStateWithLifecycle()

    // Ephemeral UI state. Migrated to `rememberSaveable` so a configuration
    // change (locale switch, rotation outside the player's locked orientation)
    // doesn't reset seek progress, the open sheet, or gesture state mid-stream.
    // References to non-saveable types (View, Bitmap, Job, SubtitleStyle cache)
    // remain on `remember` below — they're either re-derived or non-restorable.
    var showControls by rememberSaveable { mutableStateOf(true) }
    var controlsHasFocus by rememberSaveable { mutableStateOf(false) }
    var currentSheet by rememberSaveable(stateSaver = PlayerSheetSaver) {
        mutableStateOf(PlayerSheet.None)
    }
    var isSeeking by rememberSaveable { mutableStateOf(false) }
    var isOverflowMenuOpen by rememberSaveable { mutableStateOf(false) }
    var seekPositionMs by rememberSaveable { mutableLongStateOf(0L) }
    var playerViewRef by remember { mutableStateOf<android.view.View?>(null) }
    var lastAppliedSubtitleStyle by remember { mutableStateOf<SubtitleStyle?>(null) }
    var videoZoom by rememberSaveable { mutableFloatStateOf(1f) }

    val isTv = LocalTvMode.current

    val tvPlayerFocusRequester = remember { FocusRequester() }
    val tvSkipSegmentFocusRequester = remember { FocusRequester() }
    val tvCinemaIntroFocusRequester = remember { FocusRequester() }
    val tvNextEpisodeFocusRequester = remember { FocusRequester() }
    var userInteractionCount by rememberSaveable { mutableIntStateOf(0) }

    var brightnessOverlay by rememberSaveable { mutableFloatStateOf(-1f) }
    var volumeOverlay by rememberSaveable { mutableFloatStateOf(-1f) }
    var gestureSeekPositionMs by rememberSaveable { mutableLongStateOf(0L) }
    var gestureStartPositionMs by rememberSaveable { mutableLongStateOf(0L) }
    var gestureDeltaMs by rememberSaveable { mutableLongStateOf(0L) }
    var isGestureSeeking by rememberSaveable { mutableStateOf(false) }
    var gestureTrickplayVisible by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showControls) {
        viewModel.setControlsVisible(showControls)
    }

    var volumeGestureAccumulator by rememberSaveable { mutableFloatStateOf(0f) }
    var overlayDismissJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    val isScreenLocked = uiState.isScreenLocked



    val localSubtitleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "subtitle.srt"
            viewModel.addLocalSubtitle(uri, fileName)
            currentSheet = PlayerSheet.None
        }
    }

    var seekTrickplayBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var gestureTrickplayBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var tvTrickplayBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    val mpvSubtitleCues = uiState.currentSubtitleCues
        .takeIf { uiState.usesSubtitleOverlay && it.isNotEmpty() }
        ?: emptyList()

    LaunchedEffect(itemId) {
        if (viewModel.isBackgroundCasting) {
            viewModel.reattachFromBackgroundCast()
        } else {
            viewModel.initialize(
                itemId = itemId,
                mediaSourceId = mediaSourceId,
                startPositionTicks = startPositionTicks,
                subtitleStreamIndex = subtitleStreamIndex,
                audioStreamIndex = audioStreamIndex,
            )
        }
    }

    // Observe PiP dismiss as a StateFlow boolean. Using StateFlow (instead of SharedFlow)
    // ensures the dismiss signal survives lifecycle STOPPED→STARTED transitions.
    // The old SharedFlow approach lost the event because LaunchedEffect's coroutine is
    // cancelled during STOPPED and SharedFlow(replay=0) doesn't replay to new subscribers.
    val pipDismissed by viewModel.playerLifecycleManager.pipDismissed.collectAsStateWithLifecycle()
    LaunchedEffect(pipDismissed) {
        if (pipDismissed) {
            viewModel.playerLifecycleManager.clearPipDismissed()
            onBack()
        }
    }
    LaunchedEffect(Unit) {
        viewModel.closePlayer.collect { onBack() }
    }
    // Restore immersive mode when leaving PiP
    LaunchedEffect(isInPipMode) {
        if (!isInPipMode) {
            activity?.let {
                val window = it.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val isWindowFocused = rememberUpdatedState(windowInfo.isWindowFocused)
    LaunchedEffect(activity) {
        snapshotFlow { isWindowFocused.value }.distinctUntilChanged().collect { focused ->
            if (focused) {
                activity?.let { act ->
                    val window = act.window
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    controller.hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        }
    }

    // External-player handoff is handled centrally by the app-level
    // ActivityResultLauncher in JellyPlayApp's navigateFilter, which reads the
    // external player's returned position and credits watched progress. This
    // screen is never composed for the EXTERNAL case (navigation is intercepted
    // before reaching it), so no local launch logic is needed here.

    // Guard against releasing the engine when the composable is torn down
    // during a PiP transition. The engine must survive until PiP is dismissed.

    DisposableEffect(Unit) {
        activity?.let {
            val window = it.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            val currentlyInPip = viewModel.playerLifecycleManager.isInPipMode.value
            val isBgCasting = viewModel.isCastConnected && viewModel.castIsPlaying.value
            val restoreOrientation = if (isTv)
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            if (isBgCasting && !currentlyInPip) {
                activity?.let {
                    if (!it.isDestroyed && !it.isFinishing) {
                        it.requestedOrientation = restoreOrientation
                        it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        val window = it.window
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                activity?.let { act ->
                    if (!act.isDestroyed && !act.isFinishing) {
                        FrameRateMatcher.restoreOriginalMode(act)
                    }
                }
                playerViewRef = null
                viewModel.detachForBackgroundCast()
            } else if (!currentlyInPip) {
                activity?.let {
                    if (!it.isDestroyed && !it.isFinishing) {
                        it.requestedOrientation = restoreOrientation
                        it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        val window = it.window
                        val controller = WindowCompat.getInsetsController(window, window.decorView)
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
                activity?.let { act ->
                    if (!act.isDestroyed && !act.isFinishing) {
                        FrameRateMatcher.restoreOriginalMode(act)
                    }
                }
                playerViewRef = null
                viewModel.release()
            }
        }
    }

    LaunchedEffect(uiState.isPlaying, uiState.keepScreenOnDuringVideo) {
        activity?.let {
            if (!it.isDestroyed && !it.isFinishing) {
                if (uiState.isPlaying && uiState.keepScreenOnDuringVideo) {
                    it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }



    LaunchedEffect(uiState.frameRateMatching, uiState.videoFrameRate) {
        if (uiState.frameRateMatching && uiState.videoFrameRate != null) {
            val videoStream = uiState.mediaStreams.firstOrNull { it.type == com.raulshma.jellyplay.core.model.StreamType.VIDEO }
            activity?.let {
                if (!it.isDestroyed && !it.isFinishing) {
                    FrameRateMatcher.matchFrameRate(
                        activity = it,
                        frameRate = uiState.videoFrameRate,
                        targetWidth = videoStream?.width,
                        targetHeight = videoStream?.height,
                    )
                }
            }
        }
    }

    LaunchedEffect(uiState.rememberBrightness) {
        if (uiState.rememberBrightness && uiState.brightnessLevel != 0.5f) {
            activity?.let { act ->
                if (!act.isDestroyed && !act.isFinishing) {
                    val layout = act.window.attributes
                    layout.screenBrightness = uiState.brightnessLevel
                    act.window.attributes = layout
                }
            }
        }
    }

    BackHandler {
        if (currentSheet != PlayerSheet.None) {
            currentSheet = PlayerSheet.None
        } else if (isTv && showControls) {
            showControls = false
        } else if (!isTv && uiState.isPlaying && uiState.engineCapabilities.supportsMiniMode) {
            viewModel.prepareForMiniMode(
                title = uiState.title,
                subtitle = uiState.subtitle,
            )
            onEnterMiniMode()
        } else {
            onBack()
        }
    }

    val engine by viewModel.playerEngineFlow.collectAsStateWithLifecycle()
    val title = uiState.title
    val subtitle = uiState.subtitle
    val isCastConnected by viewModel.isConnectedFlow.collectAsStateWithLifecycle(initialValue = false)
    val isCastConnecting by viewModel.isConnectingFlow.collectAsStateWithLifecycle(initialValue = false)
    val castIsPlaying by viewModel.castIsPlaying.collectAsStateWithLifecycle(initialValue = false)
    val castPosition by viewModel.castPositionMs.collectAsStateWithLifecycle(initialValue = 0L)
    val castDuration by viewModel.castDurationMs.collectAsStateWithLifecycle(initialValue = 0L)
    val castVolume by viewModel.castVolumeFlow.collectAsStateWithLifecycle(initialValue = 1f)

    val isPlaying = if (isCastConnected) castIsPlaying else uiState.isPlaying
    val currentPosition = if (isCastConnected) castPosition else uiState.currentPosition
    val duration = if (isCastConnected) castDuration else uiState.duration
    val playbackSpeed = uiState.playbackSpeed
    val currentMediaSource = uiState.currentMediaSource
    val mediaStreams = uiState.mediaStreams
    val aspectRatio = uiState.aspectRatio
    val detectedAspectRatio = uiState.detectedAspectRatio

    val toggleOrientation: () -> Unit = remember(activity) {
        {
            activity?.let { act ->
                val current = act.requestedOrientation
                act.requestedOrientation = if (current == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
                    current == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE) {
                    ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                } else {
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                }
            }
        }
    }

    val syncPlayIgnoreWait by viewModel.syncPlayIgnoreWait.collectAsStateWithLifecycle()

    LaunchedEffect(isCastConnected, uiState.defaultOrientation) {
        activity?.let {
            if (!it.isDestroyed && !it.isFinishing) {
                if (isTv) {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                } else if (isCastConnected) {
                    it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                } else {
                    delay(400)
                    if (!it.isDestroyed && !it.isFinishing) {
                        it.requestedOrientation = when (uiState.defaultOrientation) {
                            OrientationMode.SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            OrientationMode.SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            OrientationMode.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                            OrientationMode.LOCKED_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            OrientationMode.LOCKED_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                        }
                    }
                }
            }
        }
    }

    val isInIntro = uiState.isInIntro
    val isInCredits = uiState.isInCredits
    val shouldShowUpNext = uiState.shouldShowUpNext
    val activeSegment = uiState.activeSegment
    val activeSegmentBehavior = activeSegment?.let { uiState.behaviorForType(it.type) }
    val cinemaIntroState = uiState.cinemaIntroState

    LaunchedEffect(aspectRatio, detectedAspectRatio, engine) {
        val effectiveRatio = if (aspectRatio == AspectRatio.AUTO) {
            detectedAspectRatio ?: AspectRatio.FIT
        } else {
            aspectRatio
        }

        val resizeMode = when (effectiveRatio) {
            AspectRatio.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            AspectRatio.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            AspectRatio.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            AspectRatio.RATIO_16_9, AspectRatio.RATIO_4_3, AspectRatio.RATIO_21_9 ->
                AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
            AspectRatio.AUTO -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        val targetRatio = effectiveRatio.ratio
        engine?.setAspectRatio(resizeMode, targetRatio)
    }

    val playMethod = uiState.playMethod
    val subtitleStyle = uiState.subtitleStyle
    val nextEpisode = uiState.nextEpisode
    val nextEpisodeImageUrl = remember(nextEpisode) {
        nextEpisode?.let { viewModel.getImageUrl(it.id, 300) }
    }
    val isInSyncPlaySession = uiState.isInSyncPlaySession

    val isNextEpisodeVisible = nextEpisode != null && shouldShowUpNext
    val isCinemaIntroVisible = cinemaIntroState != null && !isInPipMode
    val isSkipSegmentVisible = activeSegment != null &&
            activeSegmentBehavior == com.raulshma.jellyplay.core.model.SegmentBehavior.SHOW_BUTTON &&
            !isInPipMode &&
            !isCinemaIntroVisible &&
            !(activeSegment.type == com.raulshma.jellyplay.core.model.MediaSegmentType.OUTRO && shouldShowUpNext)

    LaunchedEffect(showControls, isTv, isNextEpisodeVisible, isSkipSegmentVisible, isCinemaIntroVisible) {
        if (isTv && !showControls) {
            when {
                isCinemaIntroVisible -> tvCinemaIntroFocusRequester.tryRequestFocus("tv_cinema_intro")
                isNextEpisodeVisible -> tvNextEpisodeFocusRequester.tryRequestFocus("tv_next_episode")
                isSkipSegmentVisible -> tvSkipSegmentFocusRequester.tryRequestFocus("tv_skip_segment")
                else -> tvPlayerFocusRequester.tryRequestFocus("tv_player")
            }
        }
    }

    val doPlay: () -> Unit = remember(engine, isInSyncPlaySession, isCastConnected) {
        {
            if (isInSyncPlaySession) viewModel.syncPlayTogglePlayPause()
            else if (isCastConnected) viewModel.castPlay()
            else viewModel.resumePlayback()
        }
    }
    val doPause: () -> Unit = remember(engine, isInSyncPlaySession, isCastConnected) {
        {
            if (isInSyncPlaySession) viewModel.syncPlayTogglePlayPause()
            else if (isCastConnected) viewModel.castPause()
            else engine?.pause()
        }
    }
    val doSeekTo: (Long) -> Unit = remember(engine, isInSyncPlaySession, isCastConnected) {
        { ms ->
            if (isInSyncPlaySession) viewModel.syncPlaySeekTo(ms)
            else if (isCastConnected) viewModel.castSeekTo(ms)
            else viewModel.seekTo(ms)
        }
    }
    val doSeekBack: () -> Unit = remember(engine, uiState.seekDurationMs, doSeekTo, isCastConnected) {
        {
            val pos = viewModel.playerEngineRef?.currentPositionMs ?: 0L
            val target = (pos - uiState.seekDurationMs).coerceAtLeast(0)
            doSeekTo(target)
        }
    }
    val doSeekForward: () -> Unit = remember(engine, uiState.seekDurationMs, doSeekTo, isCastConnected) {
        {
            val pos = viewModel.playerEngineRef?.currentPositionMs ?: 0L
            val dur = viewModel.playerEngineRef?.durationMs ?: 0L
            val target = (pos + uiState.seekDurationMs).coerceAtMost(dur.coerceAtLeast(0))
            doSeekTo(target)
        }
    }
    val doTogglePlayPause: () -> Unit = remember(isPlaying, doPlay, doPause) {
        { if (isPlaying) doPause() else doPlay() }
    }
    val currentDoSeekBack by rememberUpdatedState(doSeekBack)
    val currentDoSeekForward by rememberUpdatedState(doSeekForward)
    val currentDoTogglePlayPause by rememberUpdatedState(doTogglePlayPause)
    val currentSeekDurationMs by rememberUpdatedState(uiState.seekDurationMs)
    val seekState = rememberDpadSeekState(
        getBaseStepMs = { currentSeekDurationMs },
        getCurrentPositionMs = { viewModel.playerEngineRef?.currentPositionMs ?: 0L },
        getDurationMs = { viewModel.playerEngineRef?.durationMs ?: 0L },
        onCommit = { doSeekTo(it) },
    )
    val dismissSheet: () -> Unit = remember { { currentSheet = PlayerSheet.None } }

    if (isCastConnected) {
        CompanionDashboard(
            title = title,
            subtitle = subtitle,
            overview = uiState.overview,
            people = uiState.people,
            lyricsLines = uiState.lyricsLines,
            artworkUrl = uiState.artworkUrl,
            isPlaying = isPlaying,
            currentPositionMs = currentPosition,
            durationMs = duration,
            volume = castVolume,
            isConnecting = isCastConnecting,
            audioTracks = uiState.audioTracks,
            subtitleTracks = uiState.subtitleTracks,
            episodes = uiState.seasonEpisodes,
            onPlayPause = doTogglePlayPause,
            onSeekBack = doSeekBack,
            onSeekForward = doSeekForward,
            onSeekTo = doSeekTo,
            onVolumeChange = { vol -> viewModel.setCastVolume(vol) },
            onDisconnect = { viewModel.onCastDisconnected(); viewModel.castManagerField.disconnect(context) },
            onSelectAudioTrack = { viewModel.selectAudioTrack(it) },
            onSelectSubtitleTrack = { viewModel.selectSubtitleTrack(it) },
            onPlayEpisode = { epId -> viewModel.initialize(epId, null, 0L) },
            getImageUrl = { id -> viewModel.getImageUrl(id, 300) },
            onToggleOrientation = toggleOrientation,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .then(
                    if (isTv && currentSheet == PlayerSheet.None) {
                        Modifier
                            .focusRequester(tvPlayerFocusRequester)
                            .focusable()
                            .onKeyEvent { keyEvent ->
                                userInteractionCount++
                                viewModel.onUserInteraction()
                                if (keyEvent.type == KeyEventType.KeyDown &&
                                    keyEvent.nativeKeyEvent.keyCode == NativeKeyEvent.KEYCODE_SPACE
                                ) {
                                    doTogglePlayPause()
                                    showControls = true
                                    true
                                } else {
                                    false
                                }
                            }
                            .onDpadKeyEvent(
                                onRight = { dpadKey ->
                                    if (!showControls) {
                                        if (dpadKey.isKeyDown) {
                                            seekState.seekForward(dpadKey.repeatCount)
                                        } else if (dpadKey.isKeyUp) {
                                            seekState.commitForward()
                                        }
                                        true
                                    } else false
                                },
                                onLeft = { dpadKey ->
                                    if (!showControls) {
                                        if (dpadKey.isKeyDown) {
                                            seekState.seekBackward(dpadKey.repeatCount)
                                        } else if (dpadKey.isKeyUp) {
                                            seekState.commitBackward()
                                        }
                                        true
                                    } else false
                                },
                                onSelect = {
                                    if (!showControls) {
                                        showControls = true
                                        true
                                    } else false
                                },
                                onUp = {
                                    if (!showControls) {
                                        showControls = true
                                        true
                                    } else false
                                },
                                onDown = {
                                    if (!showControls) {
                                        showControls = true
                                        true
                                    } else false
                                },
                                onBack = {
                                    if (showControls) {
                                        showControls = false
                                        true
                                    } else false
                                },
                                onPlayPause = {
                                    doTogglePlayPause()
                                    true
                                },
                                onFastForward = {
                                    doSeekForward()
                                    showControls = true
                                    true
                                },
                                onRewind = {
                                    doSeekBack()
                                    showControls = true
                                    true
                                },
                            )
                    } else Modifier
                )
                .pointerInput(uiState.gesturesEnabled, isScreenLocked) {
                    if (isScreenLocked) return@pointerInput
                    if (!uiState.gesturesEnabled) return@pointerInput
                    detectTapGestures(
                        onTap = {
                            viewModel.onUserInteraction()
                            if (uiState.isHoldSpeedActive) {
                                viewModel.stopHoldSpeed()
                            } else {
                                showControls = !showControls
                            }
                        },
                        onLongPress = {
                            viewModel.onUserInteraction()
                            if (uiState.holdSpeedEnabled) viewModel.startHoldSpeed()
                        },
                        onDoubleTap = { offset ->
                            viewModel.onUserInteraction()
                            val width = size.width
                            when {
                                offset.x < width * 0.35 -> {
                                    seekState.addOffset(-1, currentSeekDurationMs)
                                    currentDoSeekBack()
                                }
                                offset.x > width * 0.65 -> {
                                    seekState.addOffset(1, currentSeekDurationMs)
                                    currentDoSeekForward()
                                }
                                else -> {
                                    if (videoZoom > 1f) {
                                        videoZoom = 1f
                                    } else {
                                        currentDoTogglePlayPause()
                                    }
                                }
                            }
                        },
                    )
                }
                .pointerInput(uiState.gesturesEnabled, isScreenLocked) {
                    if (isScreenLocked) return@pointerInput
                    if (!uiState.gesturesEnabled) return@pointerInput
                    awaitEachGesture {
                        var prevDistance = 0f
                        do {
                            val event = awaitPointerEvent()
                            val pointers = event.changes.filter { it.pressed }
                            if (pointers.size >= 2) {
                                val p0 = pointers[0].position
                                val p1 = pointers[1].position
                                val distance = kotlin.math.sqrt(
                                    (p0.x - p1.x) * (p0.x - p1.x) + (p0.y - p1.y) * (p0.y - p1.y)
                                )
                                if (prevDistance > 0f && distance > 0f) {
                                    val zoom = distance / prevDistance
                                    if (zoom != 1f) {
                                        videoZoom = (videoZoom * zoom).coerceIn(1f, 3f)
                                        viewModel.onUserInteraction()
                                    }
                                }
                                prevDistance = distance
                                pointers.forEach { it.consume() }
                            } else {
                                prevDistance = 0f
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            val currentEngine = engine
            if (currentEngine != null) {
                key(currentEngine) {
                    AndroidView(
                        factory = { ctx ->
                            val view = currentEngine.createSurfaceView(ctx)
                            lastAppliedSubtitleStyle = uiState.subtitleStyle
                            viewModel.applySubtitleStyleToView(view)
                            playerViewRef = view
                            view
                        },
                        update = { view ->
                            val currentStyle = uiState.subtitleStyle
                            if (lastAppliedSubtitleStyle != currentStyle) {
                                lastAppliedSubtitleStyle = currentStyle
                                viewModel.applySubtitleStyleToView(view)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = videoZoom
                                scaleY = videoZoom
                            },
                    )
                }
            }

            MpvSubtitleOverlay(
                cues = mpvSubtitleCues,
                style = uiState.subtitleStyle,
                visible = !isInPipMode,
            )

            GestureOverlay(
                seekDirection = seekState.direction,
                seekOffsetMs = seekState.offsetMs,
                brightnessValue = brightnessOverlay,
                volumeValue = volumeOverlay,
                gesturesEnabled = uiState.gesturesEnabled && !isScreenLocked,
                swipeSeekMaxMs = uiState.swipeSeekMaxMs,
                onSeekGesture = remember(engine) {
                    { totalDeltaMs ->
                        engine?.let { eng ->
                            if (!isGestureSeeking) {
                                gestureStartPositionMs = eng.currentPositionMs
                                isGestureSeeking = true
                            }
                            gestureDeltaMs = totalDeltaMs
                            val durationMs = eng.durationMs.coerceAtLeast(0)
                            gestureSeekPositionMs = (gestureStartPositionMs + totalDeltaMs).coerceIn(0, durationMs)
                        }
                    }
                },
                onBrightnessGesture = remember(activity) {
                    { delta ->
                        activity?.let { act ->
                            val window = act.window
                            val layout = window.attributes
                            val current = layout.screenBrightness
                            val newBrightness = (current + delta).coerceIn(0f, 1f)
                            layout.screenBrightness = newBrightness
                            window.attributes = layout
                            brightnessOverlay = newBrightness
                        }
                    }
                },
                onVolumeGesture = remember(context, isCastConnected, castVolume) {
                    { delta ->
                        if (isCastConnected) {
                            val currentNorm = castVolume
                            val newVolume = (currentNorm + delta * 0.02f).coerceIn(0f, 1f)
                            volumeOverlay = newVolume
                            volumeGestureAccumulator = 0f
                            viewModel.setCastVolume(newVolume)
                        } else {
                            val am = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                            am?.let { amRef ->
                                val max = amRef.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                val current = amRef.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                                val currentNorm = current.toFloat() / max.toFloat()
                                val stepThreshold = 1f / max.toFloat()
                                volumeGestureAccumulator += delta
                                volumeOverlay = (currentNorm + volumeGestureAccumulator).coerceIn(0f, 1f)
                                val steps = (volumeGestureAccumulator / stepThreshold).toInt()
                                if (steps != 0) {
                                    volumeGestureAccumulator -= steps * stepThreshold
                                    val newVol = (current + steps).coerceIn(0, max)
                                    amRef.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                                }
                            }
                        }
                    }
                },
                onClearOverlays = remember(doSeekTo, scope) {
                    {
                        if (isGestureSeeking) {
                            doSeekTo(gestureSeekPositionMs)
                        }
                        if (brightnessOverlay in 0f..1f) {
                            viewModel.saveBrightness(brightnessOverlay)
                        }
                        seekState.reset()
                        volumeGestureAccumulator = 0f
                        isGestureSeeking = false
                        overlayDismissJob?.cancel()
                        overlayDismissJob = scope.launch {
                            delay(800)
                            brightnessOverlay = -1f
                            volumeOverlay = -1f
                        }
                    }
                },
                showControls = showControls,
                onEdgeSwipe = remember(onBack) {
                    {
                        if (!showControls) {
                            showControls = true
                        } else {
                            onBack()
                        }
                    }
                },
                onHapticPulse = remember(activity, viewModel) {
                    {
                        if (viewModel.hapticsEnabled) {
                            activity?.let { act ->
                                val view = act.window.decorView
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                } else {
                                    @Suppress("DEPRECATION")
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                }
                            }
                        }
                    }
                },
            )

            AnimatedVisibility(
                visible = uiState.isHoldSpeedActive,
                enter = fadeIn(tween(100)),
                exit = fadeOut(tween(150)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 180.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${uiState.playbackSpeed}x",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                }
            }

            // Trickplay overlay for seek gestures
            AnimatedVisibility(
                visible = uiState.trickplayOnSeekGesture && gestureTrickplayVisible,
                enter = fadeIn(tween(150, easing = AlphaEasing)),
                exit = fadeOut(tween(200, easing = AlphaEasing)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                TrickplayOverlay(
                    bitmap = gestureTrickplayBitmap,
                    positionMs = gestureSeekPositionMs,
                    deltaMs = gestureDeltaMs,
                    durationMs = duration,
                )
            }

            if (cinemaIntroState != null && !isInPipMode) {
                IntroSkipOverlay(
                    isVisible = true,
                    onSkip = { viewModel.skipIntro() },
                    focusRequester = tvCinemaIntroFocusRequester,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 100.dp, end = 40.dp),
                )
            }

            if (activeSegment != null && activeSegmentBehavior == com.raulshma.jellyplay.core.model.SegmentBehavior.SHOW_BUTTON && !isInPipMode) {
                val hideForUpNext = activeSegment.type == com.raulshma.jellyplay.core.model.MediaSegmentType.OUTRO && shouldShowUpNext
                if (!hideForUpNext) {
                    SegmentSkipOverlay(
                        isVisible = true,
                        segmentType = activeSegment.type,
                        onSkip = { viewModel.skipSegment(activeSegment) },
                        focusRequester = tvSkipSegmentFocusRequester,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 100.dp, end = 40.dp),
                    )
                }
            }

            if (nextEpisode != null) {
                NextEpisodeOverlay(
                    isVisible = shouldShowUpNext,
                    episodeTitle = nextEpisode.name,
                    seriesName = nextEpisode.seriesName,
                    seasonNumber = nextEpisode.seasonNumber,
                    episodeNumber = nextEpisode.episodeNumber,
                    thumbnailUrl = nextEpisodeImageUrl,
                    countdownSeconds = 10,
                    onPlayNext = { viewModel.playNextEpisode() },
                    onCancel = {},
                    isPlaying = isPlaying,
                    focusRequester = tvNextEpisodeFocusRequester,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 40.dp, end = 40.dp),
                )
            }

            HdrBadge(
                hdrType = uiState.hdrType,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 60.dp, end = 16.dp),
            )

            if (uiState.isBuffering && uiState.playerError == null && !isPlaying) {
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    JellyPlayLoadingIndicator(color = Color.White)
                }
            }

            if (isScreenLocked && !isInPipMode) {
                val usePin = uiState.usePinForPlayerLock && uiState.pinHash != null
                if (usePin) {
                    PinLockOverlay(
                        visible = true,
                        onDismiss = { },
                        onUnlock = {
                            viewModel.setScreenLocked(false)
                            showControls = true
                        },
                        verifyPin = { pin -> viewModel.verifyPlayerLockPin(pin) },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    SlideToUnlockOverlay(
                        visible = true,
                        onDismiss = { },
                        onUnlock = {
                            viewModel.setScreenLocked(false)
                            showControls = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (uiState.showVideoStats) {
                VideoStatsOverlay(
                    stats = uiState.videoStats,
                    currentPositionMs = currentPosition,
                    durationMs = duration,
                    playbackSpeed = playbackSpeed,
                    isPlaying = isPlaying,
                    playbackState = when {
                        uiState.playerError != null -> "Error"
                        !isPlaying -> "Paused"
                        else -> "Playing"
                    },
                    playMethod = uiState.playMethod,
                    streamingQuality = uiState.preferredPlayerType.name,
                    playerType = uiState.preferredPlayerType.name,
                    decoderMode = uiState.decoderMode.displayName,
                    audioSessionId = 0,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 60.dp)
                        .width(280.dp),
                )
            }

            AutoAspectRatioBadge(
                detectedAspectRatio = detectedAspectRatio,
                aspectRatio = aspectRatio,
            )

            if (isCastConnected || isCastConnecting) {
                CastIndicatorOverlay(
                    isConnecting = isCastConnecting,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 60.dp, start = 16.dp),
                )
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 200.dp),
            ) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = ShapeCache.smoothPill,
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White,
                )
            }

            val hasEpisodes = uiState.seriesSeasons.isNotEmpty() && uiState.seasonEpisodes.isNotEmpty()
            val episodeBrowserEnabled = uiState.videoEpisodeBrowserEnabled

            // Hoist PlayerControls callbacks into remembered lambdas (M9).
            // Each fresh `{ ... }` passed inline below allocated a new lambda
            // per recomposition, defeating PlayerControls' skippability and
            // forcing the 1500-line controls tree to recompose on every
            // position tick. The lambdas below capture only stable handles
            // — viewModel (same Hilt instance for the screen's lifetime) and
            // the rememberSaveable property delegates (currentSheet,
            // showControls, isSeeking, controlsHasFocus, isOverflowMenuOpen)
            // whose MutableState references are stable across recomposition —
            // so they need no keys. The few that capture a value (onSeekEnd,
            // onPassthroughClick) are keyed on exactly that value so they
            // recreate only when it actually changes.
            val onPlayPause by remember(doTogglePlayPause) { mutableStateOf({ doTogglePlayPause() }) }
            val onSeekBack by remember(doSeekBack) { mutableStateOf({ doSeekBack() }) }
            val onSeekForward by remember(doSeekForward) { mutableStateOf({ doSeekForward() }) }
            val onSeekEnd by remember(duration, doSeekTo) {
                mutableStateOf({
                    isSeeking = false
                    if (duration > 0) doSeekTo(seekPositionMs)
                })
            }
            val onSeekStart by remember { mutableStateOf({ isSeeking = true }) }
            val onSeekPositionChange by remember { mutableStateOf({ positionMs: Long -> seekPositionMs = positionMs }) }
            val onSpeedClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Speed }) }
            val onAudioClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Audio }) }
            val onSubtitleClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Subtitle }) }
            val onSubtitleStyleClick by remember { mutableStateOf({ currentSheet = PlayerSheet.SubtitleStyle }) }
            val onChapterClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Chapter }) }
            val onInfoClick by remember { mutableStateOf({ currentSheet = PlayerSheet.PlaybackInfo }) }
            val onAspectRatioClick by remember { mutableStateOf({ currentSheet = PlayerSheet.AspectRatio }) }
            val onDialogueBoostClick by remember { mutableStateOf({ viewModel.toggleDialogueBoost() }) }
            val onDialogueBoostStrengthChange by remember { mutableStateOf({ strength: EffectStrength -> viewModel.setDialogueBoostStrength(strength) }) }
            val onNightModeClick by remember { mutableStateOf({ viewModel.toggleNightMode() }) }
            val onNightModeStrengthChange by remember { mutableStateOf({ strength: EffectStrength -> viewModel.setNightModeStrength(strength) }) }
            val onAVSyncClick by remember { mutableStateOf({ currentSheet = PlayerSheet.AVSync }) }
            val onDecoderClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Decoder }) }
            // uiState is a `by collectAsStateWithLifecycle()` delegate, so
            // `uiState.audioPassthrough` is read at invocation time — no key
            // needed and the lambda never goes stale.
            val onPassthroughClick by remember { mutableStateOf({ viewModel.setAudioPassthrough(!uiState.audioPassthrough) }) }
            val onSubtitleDownloadClick by remember { mutableStateOf({
                viewModel.loadRemoteSubtitles()
                currentSheet = PlayerSheet.SubtitleDownload
            }) }
            val onEpisodesClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Episodes }) }
            val onSyncPlayClick by remember { mutableStateOf({ currentSheet = PlayerSheet.SyncPlay }) }
            val onPipClick by remember(onEnterPip) { mutableStateOf({ onEnterPip() }) }
            val onMuteClick by remember { mutableStateOf({ viewModel.toggleMute() }) }
            val onVideoStatsClick by remember { mutableStateOf({ viewModel.toggleVideoStats() }) }
            val onQualityClick by remember { mutableStateOf({ currentSheet = PlayerSheet.Quality }) }
            val onPlaybackModeClick by remember { mutableStateOf({ currentSheet = PlayerSheet.PlaybackMode }) }
            val onAudioNormalizationClick by remember { mutableStateOf({ viewModel.toggleAudioNormalization() }) }
            val onAudioNormalizationModeChange by remember { mutableStateOf({ mode: AudioNormalizationMode -> viewModel.setAudioNormalizationMode(mode) }) }
            val onChannelMixClick by remember { mutableStateOf({ viewModel.toggleChannelMix() }) }
            val onChannelMixModeChange by remember { mutableStateOf({ mode: ChannelMixMode -> viewModel.setChannelMixMode(mode) }) }
            val onSleepTimerClick by remember { mutableStateOf({ currentSheet = PlayerSheet.SleepTimer }) }
            val onVideoFilterClick by remember { mutableStateOf({ currentSheet = PlayerSheet.VideoFilter }) }
            val onLockClick by remember { mutableStateOf({
                viewModel.setScreenLocked(true)
                showControls = false
            }) }
            val onControlsFocusChange by remember { mutableStateOf({ hasFocus: Boolean -> controlsHasFocus = hasFocus }) }
            val onOverflowMenuChange by remember { mutableStateOf({ open: Boolean -> isOverflowMenuOpen = open }) }

            PlayerControls(
                title = title,
                subtitle = subtitle,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                playbackSpeed = playbackSpeed,
                chapters = uiState.chapters,
                dialogueBoostEnabled = uiState.dialogueBoostEnabled,
                dialogueBoostStrength = uiState.dialogueBoostStrength,
                nightModeEnabled = uiState.nightModeEnabled,
                nightModeStrength = uiState.nightModeStrength,
                audioPassthrough = uiState.audioPassthrough,
                segments = uiState.segments,
                playMethod = uiState.playMethod,
                hdrType = uiState.hdrType,
                mediaStreams = uiState.mediaStreams,
                videoStats = uiState.videoStats,
                audioTracks = uiState.audioTracks,
                showPlaybackMetadata = uiState.showPlaybackMetadata,
                showClock = uiState.showClock,
                currentAspectRatio = aspectRatio,
                detectedAspectRatio = detectedAspectRatio,
                isVisible = showControls && !isInPipMode && !isScreenLocked,
                tvSkipSegmentFocusRequester = tvSkipSegmentFocusRequester,
                tvNextEpisodeFocusRequester = tvNextEpisodeFocusRequester,
                isSkipSegmentVisible = isSkipSegmentVisible,
                isNextEpisodeVisible = isNextEpisodeVisible,
                supportsSubtitleStyle = uiState.engineCapabilities.supportsSubtitleStyle,
                supportsDialogueBoost = uiState.engineCapabilities.supportsDialogueBoost,
                supportsNightMode = uiState.engineCapabilities.supportsNightMode,
                supportsAudioDelay = uiState.engineCapabilities.supportsAudioDelay,
                supportsAudioPassthrough = uiState.engineCapabilities.supportsAudioPassthrough,
                hasEpisodes = hasEpisodes,
                episodeBrowserEnabled = episodeBrowserEnabled,
                onPlayPause = onPlayPause,
                onSeekBack = onSeekBack,
                onSeekForward = onSeekForward,
                onSeek = { },
                onSeekStart = onSeekStart,
                onSeekEnd = onSeekEnd,
                onSeekPositionChange = onSeekPositionChange,
                tvTrickplayBitmap = if (isTv) tvTrickplayBitmap else null,
                onToggleOrientation = toggleOrientation,
                onBack = onBack,
                onSpeedClick = onSpeedClick,
                onAudioClick = onAudioClick,
                onSubtitleClick = onSubtitleClick,
                onSubtitleStyleClick = onSubtitleStyleClick,
                onChapterClick = onChapterClick,
                onInfoClick = onInfoClick,
                onAspectRatioClick = onAspectRatioClick,
                onDialogueBoostClick = onDialogueBoostClick,
                onDialogueBoostStrengthChange = onDialogueBoostStrengthChange,
                onNightModeClick = onNightModeClick,
                onNightModeStrengthChange = onNightModeStrengthChange,
                onAVSyncClick = onAVSyncClick,
                onDecoderClick = onDecoderClick,
                onPassthroughClick = onPassthroughClick,
                onSubtitleDownloadClick = onSubtitleDownloadClick,
                onEpisodesClick = onEpisodesClick,
                onSyncPlayClick = onSyncPlayClick,
                onPipClick = onPipClick,
                onMuteClick = onMuteClick,
                isMuted = uiState.isMuted,
                isInSyncPlaySession = isInSyncPlaySession,
                syncPlayGroupName = uiState.syncPlayGroupName,
                syncPlayParticipantCount = uiState.syncPlayParticipantCount,
                isSyncPlaySynced = uiState.isSyncPlaySynced,
                isSyncPlaySyncing = uiState.isSyncPlaySyncing,
                showVideoStats = uiState.showVideoStats,
                onVideoStatsClick = onVideoStatsClick,
                bufferedPosition = uiState.bufferedPosition,
                streamingQuality = uiState.streamingQuality,
                playbackMode = uiState.playbackMode,
                onQualityClick = onQualityClick,
                onPlaybackModeClick = onPlaybackModeClick,
                audioNormalizationMode = uiState.audioNormalizationMode,
                audioNormalizationEnabled = uiState.audioNormalizationEnabled,
                channelMixMode = uiState.channelMixMode,
                channelMixEnabled = uiState.channelMixEnabled,
                supportsAudioNormalization = uiState.engineCapabilities.supportsAudioNormalization,
                supportsChannelMixing = uiState.engineCapabilities.supportsChannelMixing,
                supportsLiveQualitySwitch = uiState.engineCapabilities.supportsLiveQualitySwitch,
                onAudioNormalizationClick = onAudioNormalizationClick,
                onAudioNormalizationModeChange = onAudioNormalizationModeChange,
                onChannelMixClick = onChannelMixClick,
                onChannelMixModeChange = onChannelMixModeChange,
                sleepTimerActive = uiState.sleepTimerActive,
                sleepTimerDisplayText = if (uiState.sleepTimerEndOfEpisode) "End of episode" else formatDuration(uiState.sleepTimerRemainingMs),
                onSleepTimerClick = onSleepTimerClick,
                supportsVideoFilters = uiState.engineCapabilities.supportsVideoFilters,
                videoFiltersActive = uiState.videoEffects != com.raulshma.jellyplay.core.model.VideoEffectsConfig(),
                onVideoFilterClick = onVideoFilterClick,
                onLockClick = onLockClick,
                onControlsFocusChange = onControlsFocusChange,
                onOverflowMenuChange = onOverflowMenuChange,
                castManager = viewModel.castManagerField,
                modifier = Modifier.fillMaxSize(),
            )

            AnimatedVisibility(
                visible = !isTv && uiState.trickplayEnabled && showControls && isSeeking,
                enter = fadeIn(tween(150, easing = AlphaEasing)),
                exit = fadeOut(tween(200, easing = AlphaEasing)),
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
            ) {
                TrickplayOverlay(
                    bitmap = seekTrickplayBitmap,
                    positionMs = seekPositionMs,
                    durationMs = duration,
                )
            }
        }
    }

    LaunchedEffect(seekState.timestamp) {
        if (seekState.direction != 0) {
            delay(800)
            seekState.reset()
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { seekPositionMs }
            .conflate()
            .collect { pos ->
                if (isSeeking && uiState.trickplayEnabled && uiState.trickplayInfo != null) {
                    val bitmap = viewModel.getTrickplayThumbnail(pos)
                    seekTrickplayBitmap = bitmap
                    if (isTv) {
                        tvTrickplayBitmap = bitmap
                    }
                }
            }
    }

    LaunchedEffect(isSeeking) {
        if (!isSeeking) {
            seekTrickplayBitmap = null
            tvTrickplayBitmap = null
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { gestureSeekPositionMs }
            .conflate()
            .collect { pos ->
                if (isGestureSeeking && uiState.trickplayOnSeekGesture) {
                    gestureTrickplayVisible = true
                    gestureTrickplayBitmap = if (uiState.trickplayInfo != null) {
                        viewModel.getTrickplayThumbnail(pos)
                    } else {
                        null
                    }
                }
            }
    }

    LaunchedEffect(isGestureSeeking) {
        if (!isGestureSeeking && gestureTrickplayVisible) {
            delay(1000)
            gestureTrickplayVisible = false
            gestureTrickplayBitmap = null
        }
    }

    LaunchedEffect(showControls, controlsHasFocus, isSeeking, currentSheet, isOverflowMenuOpen, userInteractionCount) {
        if (showControls && !isSeeking && currentSheet == PlayerSheet.None && !isOverflowMenuOpen) {
            if (!isTv && controlsHasFocus) {
                return@LaunchedEffect
            }
            val timeout = if (isTv) uiState.controlsTimeoutMs * 2 else uiState.controlsTimeoutMs
            delay(timeout)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch {
                viewModel.castSessionEvents.collect { event ->
                    when (event) {
                        is CastSessionEvent.Connected -> viewModel.castToDevice()
                        is CastSessionEvent.Disconnected -> viewModel.onCastDisconnected()
                    }
                }
            }
            launch {
                viewModel.syncPlayNotifications.collect { message ->
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = androidx.compose.material3.SnackbarDuration.Short,
                    )
                }
            }
            launch {
                viewModel.passOutEvents.collect { message ->
                    snackbarHostState.showSnackbar(
                        message = message,
                        duration = androidx.compose.material3.SnackbarDuration.Long,
                    )
                }
            }
        }
    }

    PlayerSheetRouter(
        currentSheet = currentSheet,
        onSheetChange = { sheet -> currentSheet = sheet },
        dismissSheet = dismissSheet,
        uiState = uiState,
        currentPosition = currentPosition,
        doSeekTo = doSeekTo,
        viewModel = viewModel,
        itemId = itemId,
        syncPlayIgnoreWait = syncPlayIgnoreWait,
        onLoadLocalSubtitle = {
            localSubtitleLauncher.launch(
                arrayOf(
                    "application/x-subrip",
                    "text/vtt",
                    "text/plain",
                    "text/x-ssa",
                    "application/ttml+xml",
                )
            )
        },
    )

    val playerError = uiState.playerError
    if (uiState.showPlaybackErrorDialog && playerError != null) {
        PlaybackErrorDialog(
            errorMessage = playerError,
            currentPlayerType = uiState.preferredPlayerType,
            onRetryWithEngine = { viewModel.retryWithEngine(it) },
            onDismiss = { viewModel.dismissPlaybackError() },
        )
    }
}

@Composable
private fun BoxScope.MpvSubtitleOverlay(
    cues: List<String>,
    style: SubtitleStyle,
    visible: Boolean,
) {
    if (!visible || cues.isEmpty()) return

    val bottomPadding = (24 + style.verticalPosition.coerceIn(0f, 0.4f) * 240).dp
    val topPadding = bottomPadding

    if (cues.size >= 2) {
        SubtitleCueBox(
            text = cues[0],
            style = style,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 32.dp, end = 32.dp, bottom = bottomPadding),
        )
        SubtitleCueBox(
            text = cues[1],
            style = style,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(start = 32.dp, end = 32.dp, top = topPadding),
        )
    } else {
        SubtitleCueBox(
            text = cues[0],
            style = style,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 32.dp, end = 32.dp, bottom = bottomPadding),
        )
    }
}

@Composable
private fun SubtitleCueBox(
    text: String,
    style: SubtitleStyle,
    modifier: Modifier = Modifier,
) {
    val backgroundOpacity = style.backgroundOpacity.coerceIn(0f, 1f)
    val backgroundColor = Color(style.backgroundColor.value)
        .copy(alpha = backgroundOpacity)
    val edgeColor = Color(style.edgeColor.value)
    val fontSize = style.fontSize.coerceIn(12, 56)
    val textStyle = TextStyle(
        fontSize = fontSize.sp,
        lineHeight = (fontSize + 4).sp,
        fontWeight = FontWeight.Bold,
    )

    val annotatedText = remember(text) { VttTagParser.parseAnnotated(text) }

    Box(
        modifier = modifier
            .then(
                if (backgroundOpacity > 0f) {
                    Modifier.background(backgroundColor, RoundedCornerShape(6.dp))
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (style.edgeType) {
            SubtitleEdgeType.NONE -> Unit
            SubtitleEdgeType.OUTLINE -> {
                val textMeasurer = rememberTextMeasurer()
                val measuredText = remember(annotatedText, textStyle) {
                    textMeasurer.measure(annotatedText, textStyle)
                }
                val density = androidx.compose.ui.platform.LocalDensity.current
                Canvas(modifier = Modifier.matchParentSize()) {
                    SubtitleOutlineOffsets.forEach { (x, y) ->
                        val offsetPx = with(density) { Offset(x.dp.toPx(), y.dp.toPx()) }
                        drawText(
                            measuredText,
                            topLeft = offsetPx,
                            color = edgeColor,
                        )
                    }
                }
            }
            SubtitleEdgeType.DROP_SHADOW -> {
                SubtitleTextLayer(
                    text = annotatedText,
                    color = edgeColor.copy(alpha = 0.85f),
                    textStyle = textStyle,
                    modifier = Modifier.offset(2.dp, 2.dp),
                )
            }
            SubtitleEdgeType.RAISED -> {
                SubtitleTextLayer(
                    text = annotatedText,
                    color = edgeColor.copy(alpha = 0.75f),
                    textStyle = textStyle,
                    modifier = Modifier.offset(1.dp, 1.dp),
                )
            }
            SubtitleEdgeType.DEPRESSED -> {
                SubtitleTextLayer(
                    text = annotatedText,
                    color = edgeColor.copy(alpha = 0.75f),
                    textStyle = textStyle,
                    modifier = Modifier.offset((-1).dp, (-1).dp),
                )
            }
        }

        SubtitleTextLayer(
            text = annotatedText,
            color = Color(style.fontColor.value),
            textStyle = textStyle,
        )
    }
}

@Composable
private fun SubtitleTextLayer(
    text: AnnotatedString,
    color: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        color = color,
        textAlign = TextAlign.Center,
        style = textStyle,
    )
}

@Composable
private fun BoxScope.AutoAspectRatioBadge(
    detectedAspectRatio: AspectRatio?,
    aspectRatio: AspectRatio,
) {
    var showBadge by remember { mutableStateOf(false) }
    LaunchedEffect(detectedAspectRatio, aspectRatio) {
        if (detectedAspectRatio != null && detectedAspectRatio != AspectRatio.FIT && aspectRatio == AspectRatio.AUTO) {
            showBadge = true
            delay(5000L)
            showBadge = false
        } else {
            showBadge = false
        }
    }

    AnimatedVisibility(
        visible = showBadge,
        enter = fadeIn(tween(150, easing = AlphaEasing)),
        exit = fadeOut(tween(200, easing = AlphaEasing)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 60.dp),
    ) {
        Surface(
            shape = ShapeCache.smoothPill,
            color = Color.White.copy(alpha = 0.12f),
        ) {
            Text(
                text = "Auto: ${detectedAspectRatio?.displayName ?: ""}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSheetRouter(
    currentSheet: PlayerSheet,
    onSheetChange: (PlayerSheet) -> Unit,
    dismissSheet: () -> Unit,
    uiState: VideoPlayerUiState,
    currentPosition: Long,
    doSeekTo: (Long) -> Unit,
    viewModel: VideoPlayerViewModel,
    itemId: String,
    syncPlayIgnoreWait: Boolean,
    onLoadLocalSubtitle: () -> Unit,
) {
    val context = LocalContext.current

    when (val sheet = currentSheet) {
        is PlayerSheet.Speed -> {
            SpeedPickerSheet(
                currentSpeed = uiState.playbackSpeed,
                onSelect = { viewModel.setPlaybackSpeed(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Audio -> {
            TrackPickerSheet(
                title = "Audio",
                tracks = uiState.audioTracks,
                onSelect = { viewModel.selectAudioTrack(it) },
                onReset = if (uiState.hasAudioOverride) { { viewModel.resetAudioTrack() } } else null,
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Subtitle -> {
            TrackPickerSheet(
                title = "Subtitles",
                tracks = uiState.subtitleTracks,
                onSelect = { viewModel.selectSubtitleTrack(it) },
                onReset = if (uiState.hasSubtitleOverride) { { viewModel.resetSubtitleTrack() } } else null,
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Chapter -> {
            ChapterPickerSheet(
                chapters = uiState.chapters,
                currentPositionMs = currentPosition,
                onSelect = { positionTicks ->
                    doSeekTo(positionTicks / 10_000)
                    onSheetChange(PlayerSheet.None)
                },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.PlaybackInfo -> {
            PlayerModalBottomSheet(
                onDismissRequest = dismissSheet,
                sheetState = rememberModalBottomSheetState(),
            ) {
                PlaybackInfoOverlay(
                    mediaSource = uiState.currentMediaSource,
                    mediaStreams = uiState.mediaStreams,
                    playMethod = uiState.playMethod,
                    hdrType = uiState.hdrType,
                    playerType = uiState.preferredPlayerType.name,
                    decoderMode = uiState.decoderMode.name,
                    aspectRatio = uiState.aspectRatio.name,
                    nightModeEnabled = uiState.nightModeEnabled,
                    nightModeStrength = uiState.nightModeStrength,
                    dialogueBoostEnabled = uiState.dialogueBoostEnabled,
                    dialogueBoostStrength = uiState.dialogueBoostStrength,
                    audioPassthrough = uiState.audioPassthrough,
                    audioTracks = uiState.audioTracks,
                    subtitleTracks = uiState.subtitleTracks,
                    playbackSpeed = uiState.playbackSpeed,
                    audioDelayMs = uiState.audioDelayMs,
                    playerError = uiState.playerError,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                )
            }
        }
        is PlayerSheet.AspectRatio -> {
            AspectRatioSheet(
                currentRatio = uiState.aspectRatio,
                detectedRatio = uiState.detectedAspectRatio,
                onSelect = { viewModel.setAspectRatio(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.SubtitleStyle -> {
            SubtitleStyleSheet(
                currentStyle = uiState.subtitleStyle,
                onStyleChange = { viewModel.setSubtitleStyle(it) },
                onDismiss = dismissSheet,
                capabilities = uiState.engineCapabilities,
            )
        }
        is PlayerSheet.AVSync -> {
            AVSyncSheet(
                currentAudioDelayMs = uiState.audioDelayMs,
                currentSubtitleDelayMs = uiState.subtitleStyle.offsetMs,
                onAudioDelayChange = { viewModel.setAudioDelay(it) },
                onSubtitleDelayChange = { viewModel.setSubtitleDelay(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Decoder -> {
            DecoderPickerSheet(
                currentMode = uiState.decoderMode,
                onSelect = { viewModel.setDecoderMode(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.SubtitleDownload -> {
            SubtitleDownloadSheet(
                subtitles = uiState.remoteSubtitles,
                isLoading = uiState.isLoadingRemoteSubtitles,
                onDownload = {
                    viewModel.downloadSubtitle(it)
                    onSheetChange(PlayerSheet.None)
                },
                onLoadLocalFile = onLoadLocalSubtitle,
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Episodes -> {
            EpisodePickerSheet(
                seasons = uiState.seriesSeasons,
                episodes = uiState.seasonEpisodes,
                currentSeasonId = uiState.currentSeasonId,
                currentEpisodeId = itemId,
                isLoading = uiState.isLoadingEpisodes,
                onSeasonSelect = { viewModel.loadSeasonEpisodes(it) },
                onEpisodeSelect = { episode ->
                    viewModel.playEpisode(episode.id, episode.playbackPositionTicks ?: 0L)
                    onSheetChange(PlayerSheet.None)
                },
                onDismiss = dismissSheet,
                getImageUrl = { id -> viewModel.getImageUrl(id, 300) },
            )
        }
        is PlayerSheet.SyncPlay -> {
            SyncPlayPlayerSheet(
                groupName = uiState.syncPlayGroupName ?: "Group",
                participantCount = uiState.syncPlayParticipantCount,
                isSynced = uiState.isSyncPlaySynced,
                isPlaying = uiState.isPlaying,
                ignoreWait = syncPlayIgnoreWait,
                repeatMode = uiState.syncPlayRepeatMode,
                shuffleMode = uiState.syncPlayShuffleMode,
                onRepeatModeChange = { viewModel.setSyncPlayRepeatMode(it) },
                onShuffleModeChange = { viewModel.setSyncPlayShuffleMode(it) },
                onTogglePlayPause = { viewModel.syncPlayTogglePlayPause() },
                onStop = { viewModel.syncPlayStop() },
                onLeave = {
                    viewModel.leaveSyncPlay()
                    onSheetChange(PlayerSheet.None)
                },
                onIgnoreWaitChange = { viewModel.syncPlaySetIgnoreWait(it) },
                 onDismiss = dismissSheet,
             )
         }
        is PlayerSheet.Quality -> {
            QualityPickerSheet(
                currentQuality = uiState.streamingQuality,
                onSelect = { viewModel.setStreamingQuality(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.PlaybackMode -> {
            PlaybackModeSheet(
                currentMode = uiState.playbackMode,
                onSelect = { viewModel.setPlaybackMode(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.SleepTimer -> {
            SleepTimerSheet(
                isActive = uiState.sleepTimerActive,
                isEndOfEpisodeMode = uiState.sleepTimerEndOfEpisode,
                remainingMs = uiState.sleepTimerRemainingMs,
                lastUsedDurationMs = uiState.sleepTimerLastUsedDurationMs,
                onSelectDuration = { viewModel.startSleepTimer(it) },
                onSelectEndOfEpisode = { viewModel.startSleepTimerEndOfEpisode() },
                onCancel = { viewModel.cancelSleepTimer() },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.VideoFilter -> {
            VideoFilterSheet(
                currentEffects = uiState.videoEffects,
                onEffectsChange = { viewModel.setVideoEffects(it) },
                onDismiss = dismissSheet,
            )
        }
        PlayerSheet.None -> { }
    }
}
