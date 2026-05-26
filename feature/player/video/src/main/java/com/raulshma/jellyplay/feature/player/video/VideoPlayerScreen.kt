package com.raulshma.jellyplay.feature.player.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.NativeKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
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
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.SubtitleEdgeType
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.components.AspectRatioSheet
import com.raulshma.jellyplay.feature.player.video.components.AudioDelaySheet
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.feature.player.video.components.DecoderPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.EpisodePickerSheet
import com.raulshma.jellyplay.feature.player.video.components.HdrBadge
import com.raulshma.jellyplay.feature.player.video.components.SegmentSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.NextEpisodeOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlaybackInfoOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlaybackErrorDialog
import com.raulshma.jellyplay.feature.player.video.components.QualityPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleDownloadSheet
import com.raulshma.jellyplay.feature.player.video.components.ChapterPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.GestureOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlayerControls
import com.raulshma.jellyplay.feature.player.video.components.SpeedPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.SleepTimerSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleStyleSheet
import com.raulshma.jellyplay.feature.player.video.components.VideoStatsOverlay
import com.raulshma.jellyplay.feature.player.video.components.SyncPlayPlayerSheet

import com.raulshma.jellyplay.feature.player.video.components.TapToTranslateSheet
import com.raulshma.jellyplay.feature.player.video.components.TrackPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.TrickplayOverlay
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

    var showControls by remember { mutableStateOf(true) }
    var controlsHasFocus by remember { mutableStateOf(false) }
    var currentSheet by remember { mutableStateOf<PlayerSheet>(PlayerSheet.None) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf(0L) }
    var playerViewRef by remember { mutableStateOf<android.view.View?>(null) }
    var lastAppliedSubtitleStyle by remember { mutableStateOf<SubtitleStyle?>(null) }

    val isTv = LocalTvMode.current

    var seekOffsetMs by remember { mutableLongStateOf(0L) }
    var seekDirection by remember { mutableIntStateOf(0) }
    var seekTimestamp by remember { mutableLongStateOf(0L) }
    var brightnessOverlay by remember { mutableFloatStateOf(-1f) }
    var volumeOverlay by remember { mutableFloatStateOf(-1f) }
    var externalLaunched by remember { mutableStateOf(false) }
    var gestureSeekPositionMs by remember { mutableLongStateOf(0L) }
    var gestureStartPositionMs by remember { mutableLongStateOf(0L) }
    var gestureDeltaMs by remember { mutableLongStateOf(0L) }
    var isGestureSeeking by remember { mutableStateOf(false) }
    var gestureTrickplayVisible by remember { mutableStateOf(false) }

    var volumeGestureAccumulator by remember { mutableFloatStateOf(0f) }



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
    val mpvSubtitleText = uiState.currentSubtitleText
        ?.takeIf { uiState.usesSubtitleOverlay && it.isNotBlank() }

    LaunchedEffect(itemId) {
        viewModel.initialize(
            itemId = itemId,
            mediaSourceId = mediaSourceId,
            startPositionTicks = startPositionTicks,
            subtitleStreamIndex = subtitleStreamIndex,
            audioStreamIndex = audioStreamIndex,
        )
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
    LaunchedEffect(windowInfo.isWindowFocused) {
        if (windowInfo.isWindowFocused) {
            activity?.let { act ->
                val window = act.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val preferredPlayer = uiState.preferredPlayerType
    val streamUrl = uiState.streamUrl

    LaunchedEffect(preferredPlayer, streamUrl) {
        if (preferredPlayer == PlayerType.EXTERNAL && streamUrl != null && !externalLaunched) {
            externalLaunched = true
            val launched = ExternalPlayerLauncher.tryLaunch(
                context = context,
                playerType = preferredPlayer,
                streamUrl = streamUrl,
                title = uiState.title,
                startPositionMs = startPositionTicks / 10_000,
            )
            if (launched) {
                onBack()
            }
        }
    }

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
            if (!currentlyInPip) {
                activity?.let {
                    if (!it.isDestroyed && !it.isFinishing) {
                        it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

    LaunchedEffect(uiState.isPlaying) {
        activity?.let {
            if (!it.isDestroyed && !it.isFinishing) {
                if (uiState.isPlaying) {
                    it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(400)
        activity?.let {
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

    LaunchedEffect(uiState.frameRateMatching, uiState.videoFrameRate) {
        if (uiState.frameRateMatching && uiState.videoFrameRate != null) {
            activity?.let { if (!it.isDestroyed && !it.isFinishing) FrameRateMatcher.matchFrameRate(it, uiState.videoFrameRate) }
        }
    }

    LaunchedEffect(uiState.rememberBrightness) {
        if (uiState.rememberBrightness && uiState.brightnessLevel != 0.5f) {
            activity?.let { act ->
                if (!act.isDestroyed && !act.isFinishing) {
                    val layout = act.window.attributes
                    layout.screenBrightness = uiState.brightnessLevel
                    act.window.attributes = layout
                    brightnessOverlay = uiState.brightnessLevel
                }
            }
        }
    }

    BackHandler {
        if (currentSheet != PlayerSheet.None) {
            currentSheet = PlayerSheet.None
        } else if (isTv && showControls) {
            showControls = false
        } else if (uiState.isPlaying && uiState.engineCapabilities.supportsMiniMode) {
            viewModel.prepareForMiniMode(
                title = uiState.title,
                subtitle = uiState.subtitle,
            )
            onEnterMiniMode()
        } else {
            onBack()
        }
    }

    val engine = viewModel.playerEngineRef
    val title = uiState.title
    val subtitle = uiState.subtitle
    val isPlaying = uiState.isPlaying
    val currentPosition = uiState.currentPosition
    val duration = uiState.duration
    val playbackSpeed = uiState.playbackSpeed
    val currentMediaSource = uiState.currentMediaSource
    val mediaStreams = uiState.mediaStreams
    val aspectRatio = uiState.aspectRatio
    val detectedAspectRatio = uiState.detectedAspectRatio

    val syncPlayIgnoreWait by viewModel.syncPlayIgnoreWait.collectAsStateWithLifecycle()

    val isInIntro = uiState.isInIntro
    val isInCredits = uiState.isInCredits
    val shouldShowUpNext = uiState.shouldShowUpNext
    val activeSegment = uiState.activeSegment
    val activeSegmentBehavior = activeSegment?.let { uiState.behaviorForType(it.type) }

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

    val doPlay: () -> Unit = remember(engine, isInSyncPlaySession) {
        {
            if (isInSyncPlaySession) viewModel.syncPlayTogglePlayPause()
            else engine?.play()
        }
    }
    val doPause: () -> Unit = remember(engine, isInSyncPlaySession) {
        {
            if (isInSyncPlaySession) viewModel.syncPlayTogglePlayPause()
            else engine?.pause()
        }
    }
    val doSeekTo: (Long) -> Unit = remember(engine, isInSyncPlaySession) {
        { ms ->
            if (isInSyncPlaySession) viewModel.syncPlaySeekTo(ms)
            else engine?.seekTo(ms)
        }
    }
    val doSeekBack: () -> Unit = remember(engine, uiState.seekDurationMs, doSeekTo) {
        {
            engine?.let { eng ->
                val target = (eng.currentPositionMs - uiState.seekDurationMs).coerceAtLeast(0)
                doSeekTo(target)
            }
        }
    }
    val doSeekForward: () -> Unit = remember(engine, uiState.seekDurationMs, doSeekTo) {
        {
            engine?.let { eng ->
                val target = (eng.currentPositionMs + uiState.seekDurationMs).coerceAtMost(eng.durationMs.coerceAtLeast(0))
                doSeekTo(target)
            }
        }
    }
    val doTogglePlayPause: () -> Unit = remember(isPlaying, doPlay, doPause) {
        { if (isPlaying) doPause() else doPlay() }
    }
    val dismissSheet: () -> Unit = remember { { currentSheet = PlayerSheet.None } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (isTv && currentSheet == PlayerSheet.None) {
                    Modifier.onPreviewKeyEvent { keyEvent ->
                        if (keyEvent.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (keyEvent.nativeKeyEvent.keyCode) {
                            NativeKeyEvent.KEYCODE_DPAD_CENTER, NativeKeyEvent.KEYCODE_ENTER -> {
                                if (!showControls) {
                                    showControls = true
                                    true
                                } else {
                                    false
                                }
                            }
                            NativeKeyEvent.KEYCODE_DPAD_RIGHT -> {
                                if (!showControls) {
                                    doSeekForward()
                                    showControls = true
                                    true
                                } else {
                                    false
                                }
                            }
                            NativeKeyEvent.KEYCODE_DPAD_LEFT -> {
                                if (!showControls) {
                                    doSeekBack()
                                    showControls = true
                                    true
                                } else {
                                    false
                                }
                            }
                            NativeKeyEvent.KEYCODE_DPAD_UP, NativeKeyEvent.KEYCODE_DPAD_DOWN -> {
                                if (!showControls) {
                                    showControls = true
                                    true
                                } else {
                                    false
                                }
                            }
                            NativeKeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                doTogglePlayPause()
                                true
                            }
                            NativeKeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                                doSeekForward()
                                showControls = true
                                true
                            }
                            NativeKeyEvent.KEYCODE_MEDIA_REWIND -> {
                                doSeekBack()
                                showControls = true
                                true
                            }
                            NativeKeyEvent.KEYCODE_SPACE -> {
                                doTogglePlayPause()
                                showControls = true
                                true
                            }
                            else -> false
                        }
                    }
                } else Modifier
            )
            .pointerInput(uiState.gesturesEnabled, uiState.seekDurationMs) {
                if (!uiState.gesturesEnabled) return@pointerInput
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val width = size.width
                        when {
                            offset.x < width * 0.35 -> {
                                seekDirection = -1
                                seekOffsetMs = uiState.seekDurationMs
                                seekTimestamp++
                                doSeekBack()
                            }
                            offset.x > width * 0.65 -> {
                                seekDirection = 1
                                seekOffsetMs = uiState.seekDurationMs
                                seekTimestamp++
                                doSeekForward()
                            }
                            else -> {
                                doTogglePlayPause()
                            }
                        }
                    },
                    onLongPress = {
                            if (!uiState.gesturesEnabled) return@detectTapGestures
                            if (!uiState.engineCapabilities.supportsOcr) return@detectTapGestures
                            val primaryText = viewModel.getCurrentPrimarySubtitleText() ?: return@detectTapGestures
                            val text = primaryText.takeIf { it.isNotBlank() } ?: return@detectTapGestures
                            currentSheet = PlayerSheet.TapToTranslate(text)
                        },
                )
            },
    ) {
        if (engine != null) {
            key(engine) {
                AndroidView(
                    factory = { ctx ->
                        engine.createSurfaceView(ctx).also { view ->
                            playerViewRef = view
                            lastAppliedSubtitleStyle = uiState.subtitleStyle
                            viewModel.applySubtitleStyleToView(view)
                        }
                    },
                    update = { view ->
                        val currentStyle = uiState.subtitleStyle
                        if (lastAppliedSubtitleStyle != currentStyle) {
                            lastAppliedSubtitleStyle = currentStyle
                            viewModel.applySubtitleStyleToView(view)
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        MpvSubtitleOverlay(
            text = mpvSubtitleText,
            style = uiState.subtitleStyle,
            visible = !isInPipMode,
        )

        GestureOverlay(
            seekDirection = seekDirection,
            seekOffsetMs = seekOffsetMs,
            brightnessValue = brightnessOverlay,
            volumeValue = volumeOverlay,
            gesturesEnabled = uiState.gesturesEnabled,
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
            onVolumeGesture = remember(context) {
                { delta ->
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
                    audioManager?.let { am ->
                        val max = am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        val current = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
                        val currentNorm = current.toFloat() / max.toFloat()
                        val stepThreshold = 1f / max.toFloat()
                        volumeGestureAccumulator += delta
                        volumeOverlay = (currentNorm + volumeGestureAccumulator).coerceIn(0f, 1f)
                        val steps = (volumeGestureAccumulator / stepThreshold).toInt()
                        if (steps != 0) {
                            volumeGestureAccumulator -= steps * stepThreshold
                            val newVol = (current + steps).coerceIn(0, max)
                            am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                        }
                    }
                }
            },
            onClearOverlays = {
                if (isGestureSeeking) {
                    doSeekTo(gestureSeekPositionMs)
                }
                if (brightnessOverlay in 0f..1f) {
                    viewModel.saveBrightness(brightnessOverlay)
                }
                seekDirection = 0
                seekOffsetMs = 0L
                brightnessOverlay = -1f
                volumeOverlay = -1f
                volumeGestureAccumulator = 0f
                isGestureSeeking = false
            },
            showControls = showControls,
            onEdgeSwipe = {
                if (!showControls) {
                    showControls = true
                } else {
                    onBack()
                }
            },
        )

        // Trickplay overlay for seek gestures
        AnimatedVisibility(
            visible = uiState.trickplayOnSeekGesture && gestureTrickplayVisible,
            enter = fadeIn(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)),
            exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            TrickplayOverlay(
                bitmap = gestureTrickplayBitmap,
                positionMs = gestureSeekPositionMs,
                deltaMs = gestureDeltaMs,
                durationMs = duration,
            )
        }

        

        if (activeSegment != null && activeSegmentBehavior == com.raulshma.jellyplay.core.model.SegmentBehavior.SHOW_BUTTON && !isInPipMode) {
            val hideForUpNext = activeSegment.type == com.raulshma.jellyplay.core.model.MediaSegmentType.OUTRO && shouldShowUpNext
            if (!hideForUpNext) {
                SegmentSkipOverlay(
                    isVisible = true,
                    segmentType = activeSegment.type,
                    onSkip = { viewModel.skipSegment(activeSegment) },
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
            isOcrRunning = uiState.isOcrRunning,
            segments = uiState.segments,

            currentAspectRatio = aspectRatio,
            detectedAspectRatio = detectedAspectRatio,
            isVisible = showControls && !isInPipMode,
            supportsSubtitleStyle = uiState.engineCapabilities.supportsSubtitleStyle,
            supportsDialogueBoost = uiState.engineCapabilities.supportsDialogueBoost,
            supportsNightMode = uiState.engineCapabilities.supportsNightMode,
            supportsAudioDelay = uiState.engineCapabilities.supportsAudioDelay,
            supportsAudioPassthrough = uiState.engineCapabilities.supportsAudioPassthrough,
            supportsOcr = uiState.engineCapabilities.supportsOcr,
            hasEpisodes = hasEpisodes,
            episodeBrowserEnabled = episodeBrowserEnabled,
            onPlayPause = { doTogglePlayPause() },
            onSeekBack = { doSeekBack() },
            onSeekForward = { doSeekForward() },
            onSeek = { },
            onSeekStart = { isSeeking = true },
            onSeekEnd = {
                isSeeking = false
                if (duration > 0) doSeekTo(seekPositionMs)
            },
            onSeekPositionChange = { positionMs -> seekPositionMs = positionMs },
            onBack = onBack,
            onSpeedClick = { currentSheet = PlayerSheet.Speed },
            onAudioClick = { currentSheet = PlayerSheet.Audio },
            onSubtitleClick = { currentSheet = PlayerSheet.Subtitle },
            onSubtitleStyleClick = { currentSheet = PlayerSheet.SubtitleStyle },
            onChapterClick = { currentSheet = PlayerSheet.Chapter },
            onInfoClick = { currentSheet = PlayerSheet.PlaybackInfo },
            onAspectRatioClick = { currentSheet = PlayerSheet.AspectRatio },
            onDialogueBoostClick = { viewModel.toggleDialogueBoost() },
            onDialogueBoostStrengthChange = { viewModel.setDialogueBoostStrength(it) },
            onNightModeClick = { viewModel.toggleNightMode() },
            onNightModeStrengthChange = { viewModel.setNightModeStrength(it) },
            onAudioDelayClick = { currentSheet = PlayerSheet.AudioDelay },
            onDecoderClick = { currentSheet = PlayerSheet.Decoder },
            onPassthroughClick = { viewModel.setAudioPassthrough(!uiState.audioPassthrough) },
            onOcrClick = {
                val bitmap = viewModel.capturePlayerViewBitmap()
                viewModel.captureOcrSubtitle(bitmap)
                currentSheet = PlayerSheet.OcrResult
            },
            onSubtitleDownloadClick = {
                viewModel.loadRemoteSubtitles()
                currentSheet = PlayerSheet.SubtitleDownload
            },
            onEpisodesClick = { currentSheet = PlayerSheet.Episodes },
            onSyncPlayClick = { currentSheet = PlayerSheet.SyncPlay },
            onPipClick = {
                onEnterPip()
            },
            isInSyncPlaySession = isInSyncPlaySession,
            syncPlayGroupName = uiState.syncPlayGroupName,
            syncPlayParticipantCount = uiState.syncPlayParticipantCount,
            isSyncPlaySynced = uiState.isSyncPlaySynced,
            isSyncPlaySyncing = uiState.isSyncPlaySyncing,
            showVideoStats = uiState.showVideoStats,
            onVideoStatsClick = { viewModel.toggleVideoStats() },
            bufferedPosition = uiState.bufferedPosition,
            streamingQuality = uiState.streamingQuality,
            onQualityClick = { currentSheet = PlayerSheet.Quality },
            audioNormalizationMode = uiState.audioNormalizationMode,
            audioNormalizationEnabled = uiState.audioNormalizationEnabled,
            channelMixMode = uiState.channelMixMode,
            channelMixEnabled = uiState.channelMixEnabled,
            supportsAudioNormalization = uiState.engineCapabilities.supportsAudioNormalization,
            supportsChannelMixing = uiState.engineCapabilities.supportsChannelMixing,
            onAudioNormalizationClick = { viewModel.toggleAudioNormalization() },
            onAudioNormalizationModeChange = { viewModel.setAudioNormalizationMode(it) },
            onChannelMixClick = { viewModel.toggleChannelMix() },
            onChannelMixModeChange = { viewModel.setChannelMixMode(it) },
            sleepTimerActive = uiState.sleepTimerActive,
            sleepTimerDisplayText = if (uiState.sleepTimerEndOfEpisode) "End of episode" else formatDuration(uiState.sleepTimerRemainingMs),
            onSleepTimerClick = { currentSheet = PlayerSheet.SleepTimer },
            onControlsFocusChange = { controlsHasFocus = it },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = uiState.trickplayEnabled && showControls && isSeeking,
            enter = fadeIn(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)),
            exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
        ) {
            TrickplayOverlay(
                bitmap = seekTrickplayBitmap,
                positionMs = seekPositionMs,
                durationMs = duration,
            )
        }
    }

    LaunchedEffect(seekTimestamp) {
        if (seekDirection != 0) {
            delay(800)
            seekDirection = 0
            seekOffsetMs = 0L
        }
    }

    LaunchedEffect(isSeeking, seekPositionMs) {
        if (isSeeking && uiState.trickplayEnabled && uiState.trickplayInfo != null) {
            seekTrickplayBitmap = viewModel.getTrickplayThumbnail(seekPositionMs)
        } else if (!isSeeking) {
            seekTrickplayBitmap = null
        }
    }

    LaunchedEffect(isGestureSeeking, gestureSeekPositionMs) {
        if (isGestureSeeking && uiState.trickplayOnSeekGesture && uiState.trickplayInfo != null) {
            gestureTrickplayVisible = true
            gestureTrickplayBitmap = viewModel.getTrickplayThumbnail(gestureSeekPositionMs)
        }
    }

    LaunchedEffect(isGestureSeeking) {
        if (!isGestureSeeking && gestureTrickplayVisible) {
            delay(1000)
            gestureTrickplayVisible = false
            gestureTrickplayBitmap = null
        }
    }

    LaunchedEffect(showControls, controlsHasFocus) {
        if (showControls && !controlsHasFocus) {
            delay(uiState.controlsTimeoutMs)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch {
                viewModel.castSessionEvents.collect { event ->
                    when (event) {
                        is CastSessionEvent.Connected -> viewModel.castToDevice()
                        is CastSessionEvent.Disconnected -> { /* local playback continues */ }
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

    if (uiState.showPlaybackErrorDialog && uiState.playerError != null) {
        PlaybackErrorDialog(
            errorMessage = uiState.playerError!!,
            currentPlayerType = uiState.preferredPlayerType,
            onRetryWithEngine = { viewModel.retryWithEngine(it) },
            onDismiss = { viewModel.dismissPlaybackError() },
        )
    }
}

@Composable
private fun BoxScope.MpvSubtitleOverlay(
    text: String?,
    style: SubtitleStyle,
    visible: Boolean,
) {
    if (!visible || text.isNullOrBlank()) return

    val bottomPadding = (24 + style.verticalPosition.coerceIn(0f, 0.4f) * 240).dp
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
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 32.dp, end = 32.dp, bottom = bottomPadding)
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
                SubtitleOutlineOffsets.forEach { (x, y) ->
                    SubtitleTextLayer(
                        text = annotatedText,
                        color = edgeColor,
                        textStyle = textStyle,
                        modifier = Modifier.offset(x.dp, y.dp),
                    )
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
        enter = fadeIn(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)),
        exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)),
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
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.Subtitle -> {
            TrackPickerSheet(
                title = "Subtitles",
                tracks = uiState.subtitleTracks,
                onSelect = { viewModel.selectSubtitleTrack(it) },
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
            )
        }
        
        is PlayerSheet.TapToTranslate -> {
            TapToTranslateSheet(
                text = sheet.text,
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.AudioDelay -> {
            AudioDelaySheet(
                currentDelayMs = uiState.audioDelayMs,
                onDelayChange = { viewModel.setAudioDelay(it) },
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
        is PlayerSheet.OcrResult -> {
            OcrResultSheet(
                ocrText = uiState.ocrText,
                isOcrRunning = uiState.isOcrRunning,
                onDismiss = {
                    onSheetChange(PlayerSheet.None)
                    viewModel.clearOcrText()
                },
                context = context,
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
        PlayerSheet.None -> { }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OcrResultSheet(
    ocrText: String?,
    isOcrRunning: Boolean,
    onDismiss: () -> Unit,
    context: Context,
) {
    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "OCR Subtitle Text",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))
            if (isOcrRunning) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else if (ocrText != null) {
                Text(
                    ocrText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    "No subtitle text detected in current frame.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (ocrText != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(
                                android.content.ClipData.newPlainText("OCR Subtitle", ocrText)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smoothPill,
                    ) { Text("Copy") }
                    FilledTonalButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, ocrText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = ShapeCache.smoothPill,
                    ) { Text("Share") }
                }
            }
        }
    }
}
