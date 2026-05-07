package com.raulshma.jellyplay.feature.player.video

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.data.playback.FrameRateMatcher
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.components.AspectRatioSheet
import com.raulshma.jellyplay.feature.player.video.components.AudioDelaySheet
import com.raulshma.jellyplay.feature.player.video.components.CreditsSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.DecoderPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.HdrBadge
import com.raulshma.jellyplay.feature.player.video.components.IntroSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.NextEpisodeOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlaybackInfoOverlay
import com.raulshma.jellyplay.feature.player.video.components.SubtitleDownloadSheet
import com.raulshma.jellyplay.feature.player.video.components.ChapterPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.GestureOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlayerControls
import com.raulshma.jellyplay.feature.player.video.components.SecondarySubtitlePickerSheet
import com.raulshma.jellyplay.feature.player.video.components.SpeedPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.SubtitleStyleSheet
import com.raulshma.jellyplay.feature.player.video.components.SyncPlayOverlay
import com.raulshma.jellyplay.feature.player.video.components.TapToTranslateSheet
import com.raulshma.jellyplay.feature.player.video.components.TrackPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.TrickplayOverlay
import com.raulshma.jellyplay.feature.player.video.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.ui.AspectRatioFrameLayout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    itemId: String,
    mediaSourceId: String?,
    startPositionTicks: Long,
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showControls by remember { mutableStateOf(true) }
    var currentSheet by remember { mutableStateOf<PlayerSheet>(PlayerSheet.None) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf(0L) }
    var isCasting by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<android.view.View?>(null) }
    var secondarySubtitleText by remember { mutableStateOf<String?>(null) }

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

    var seekTrickplayBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var gestureTrickplayBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(itemId) {
        viewModel.initialize(itemId, mediaSourceId, startPositionTicks)
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

    DisposableEffect(Unit) {
        activity?.let {
            val window = it.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val window = it.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            activity?.let { act -> FrameRateMatcher.restoreOriginalMode(act) }
            playerViewRef = null
            viewModel.release()
        }
    }

    LaunchedEffect(showControls) {
        activity?.let {
            val window = it.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (showControls) {
                controller.show(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(400)
        activity?.requestedOrientation = when (uiState.defaultOrientation) {
            OrientationMode.SENSOR_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            OrientationMode.SENSOR_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            OrientationMode.SENSOR -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
            OrientationMode.LOCKED_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            OrientationMode.LOCKED_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    LaunchedEffect(uiState.frameRateMatching, uiState.videoFrameRate) {
        if (uiState.frameRateMatching && uiState.videoFrameRate != null) {
            activity?.let { FrameRateMatcher.matchFrameRate(it, uiState.videoFrameRate) }
        }
    }

    LaunchedEffect(uiState.rememberBrightness) {
        if (uiState.rememberBrightness && uiState.brightnessLevel != 0.5f) {
            activity?.let { act ->
                val layout = act.window.attributes
                layout.screenBrightness = uiState.brightnessLevel
                act.window.attributes = layout
                brightnessOverlay = uiState.brightnessLevel
            }
        }
    }

    BackHandler {
        if (currentSheet != PlayerSheet.None) {
            currentSheet = PlayerSheet.None
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

    val isInIntro = uiState.isInIntro
    val isInCredits = uiState.isInCredits
    val shouldShowUpNext = uiState.shouldShowUpNext

    val skipSegmentText: String? = when {
        isInIntro -> "Skip Intro"
        isInCredits && !shouldShowUpNext -> "Skip Credits"
        else -> null
    }
    val onSkipSegment: () -> Unit = when {
        isInIntro -> { { viewModel.skipIntro() } }
        isInCredits -> { { viewModel.skipCredits() } }
        else -> { {} }
    }

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
    val nextEpisodeImageUrl = nextEpisode?.let { viewModel.getImageUrl(it.id, 300) }

    val doPlay: () -> Unit = remember(engine) { { engine?.play() } }
    val doPause: () -> Unit = remember(engine) { { engine?.pause() } }
    val doSeekTo: (Long) -> Unit = remember(engine) { { ms -> engine?.seekTo(ms) } }
    val doSeekBack: () -> Unit = remember(engine, uiState.seekDurationMs) { { engine?.seekBack(uiState.seekDurationMs) } }
    val doSeekForward: () -> Unit = remember(engine, uiState.seekDurationMs) { { engine?.seekForward(uiState.seekDurationMs) } }
    val doTogglePlayPause: () -> Unit by remember(isPlaying, doPlay, doPause) {
        derivedStateOf { { if (isPlaying) doPause() else doPlay() } }
    }
    val dismissSheet: () -> Unit = remember { { currentSheet = PlayerSheet.None } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
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
                        if (!uiState.engineCapabilities.cues && secondarySubtitleText.isNullOrBlank()) return@detectTapGestures
                        val primaryText = if (uiState.engineCapabilities.cues) viewModel.getCurrentPrimarySubtitleText() else null
                        val secondaryText = secondarySubtitleText
                        val text = listOfNotNull(primaryText, secondaryText)
                            .joinToString("\n")
                            .takeIf { it.isNotBlank() } ?: return@detectTapGestures
                        currentSheet = PlayerSheet.TapToTranslate(text)
                    },
                )
            },
    ) {
        if (engine != null) {
            AndroidView(
                factory = { ctx ->
                    engine.createPlayerView(ctx).also { view ->
                        playerViewRef = view
                        viewModel.applySubtitleStyleToView(view)
                    }
                },
                update = { view ->
                    viewModel.applySubtitleStyleToView(view)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

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
                        val step = 1
                        val newVol = if (delta > 0) (current + step).coerceAtMost(max)
                        else (current - step).coerceAtLeast(0)
                        am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, newVol, 0)
                        volumeOverlay = newVol.toFloat() / max.toFloat()
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
                isGestureSeeking = false
            },
        )

        // Trickplay overlay for seek gestures
        AnimatedVisibility(
            visible = uiState.trickplayOnSeekGesture && gestureTrickplayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            TrickplayOverlay(
                bitmap = gestureTrickplayBitmap,
                positionMs = gestureSeekPositionMs,
                deltaMs = gestureDeltaMs,
                durationMs = duration,
            )
        }

        SecondarySubtitleOverlay(
            text = secondarySubtitleText,
            fontSize = uiState.subtitleStyle.fontSize,
        )

        IntroSkipOverlay(
            isVisible = isInIntro && !showControls,
            onSkip = { viewModel.skipIntro() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 200.dp),
        )

        CreditsSkipOverlay(
            isVisible = isInCredits && !shouldShowUpNext && !showControls,
            onSkip = { viewModel.skipCredits() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 200.dp),
        )

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
                    .padding(start = 16.dp, end = 16.dp, bottom = 200.dp)
                    .fillMaxWidth(0.7f),
            )
        }

        HdrBadge(
            hdrType = uiState.hdrType,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 60.dp, end = 16.dp),
        )

        AutoAspectRatioBadge(
            detectedAspectRatio = detectedAspectRatio,
            aspectRatio = aspectRatio,
        )

        if (uiState.isInSyncPlaySession) {
            SyncPlayOverlay(
                isVisible = true,
                groupName = uiState.syncPlayGroupName ?: "Group",
                participantCount = uiState.syncPlayParticipantCount,
                isSynced = uiState.isSyncPlaySynced,
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
                shape = RoundedCornerShape(12.dp),
                containerColor = Color.White.copy(alpha = 0.15f),
                contentColor = Color.White,
            )
        }

        PlayerControls(
            title = title,
            subtitle = subtitle,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            playbackSpeed = playbackSpeed,
            chapters = uiState.chapters,
            dialogueBoostEnabled = uiState.dialogueBoostEnabled,
            nightModeEnabled = uiState.nightModeEnabled,
            audioPassthrough = uiState.audioPassthrough,
            isCasting = isCasting,
            isOcrRunning = uiState.isOcrRunning,
            introTimestamps = uiState.introTimestamps,
            creditTimestamps = uiState.creditTimestamps,
            skipSegmentText = skipSegmentText,
            onSkipSegment = onSkipSegment,
            currentAspectRatio = aspectRatio,
            detectedAspectRatio = detectedAspectRatio,
            isVisible = showControls,
            supportsSubtitleStyle = uiState.engineCapabilities.subtitleStyle,
            supportsDialogueBoost = uiState.engineCapabilities.dialogueBoost,
            supportsNightMode = uiState.engineCapabilities.nightMode,
            supportsAudioDelay = uiState.engineCapabilities.audioDelay,
            supportsAudioPassthrough = uiState.engineCapabilities.audioPassthrough,
            supportsOcr = uiState.engineCapabilities.ocr,
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
            onSecondarySubtitleClick = { currentSheet = PlayerSheet.SecondarySubtitle },
            onChapterClick = { currentSheet = PlayerSheet.Chapter },
            onInfoClick = { currentSheet = PlayerSheet.PlaybackInfo },
            onAspectRatioClick = { currentSheet = PlayerSheet.AspectRatio },
            onDialogueBoostClick = { viewModel.toggleDialogueBoost() },
            onNightModeClick = { viewModel.toggleNightMode() },
            onAudioDelayClick = { currentSheet = PlayerSheet.AudioDelay },
            onDecoderClick = { currentSheet = PlayerSheet.Decoder },
            onPassthroughClick = { viewModel.setAudioPassthrough(!uiState.audioPassthrough) },
            onCastClick = { viewModel.castToDevice() },
            onOcrClick = {
                val bitmap = viewModel.capturePlayerViewBitmap()
                viewModel.captureOcrSubtitle(bitmap)
                currentSheet = PlayerSheet.OcrResult
            },
            onSubtitleDownloadClick = {
                viewModel.loadRemoteSubtitles()
                currentSheet = PlayerSheet.SubtitleDownload
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = uiState.trickplayEnabled && showControls && isSeeking,
            enter = fadeIn(),
            exit = fadeOut(),
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

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(uiState.controlsTimeoutMs)
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            try {
                val castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
                isCasting = castContext.sessionManager.currentCastSession?.isConnected == true
            } catch (_: Exception) {
                isCasting = false
            }
            delay(2000)
        }
    }

    LaunchedEffect(uiState.secondarySubtitleTrack) {
        val track = uiState.secondarySubtitleTrack ?: return@LaunchedEffect
        while (true) {
            val pos = engine?.currentPositionMs ?: 0L
            secondarySubtitleText = viewModel.getSecondarySubtitleText(pos)
            delay(250)
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
    )
}

@Composable
private fun BoxScope.SecondarySubtitleOverlay(
    text: String?,
    fontSize: Int,
) {
    text?.let {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 60.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = it,
                color = Color.Yellow,
                fontSize = (fontSize - 4).sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
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
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 60.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.15f),
        ) {
            Text(
                text = "Auto: ${detectedAspectRatio?.displayName ?: ""}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium,
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
            ModalBottomSheet(
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
                    dialogueBoostEnabled = uiState.dialogueBoostEnabled,
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
        is PlayerSheet.SecondarySubtitle -> {
            SecondarySubtitlePickerSheet(
                mediaStreams = uiState.mediaStreams,
                currentSecondary = uiState.secondarySubtitleTrack,
                onSelect = { stream ->
                    viewModel.selectSecondarySubtitleStream(stream)
                    onSheetChange(PlayerSheet.None)
                },
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
    ModalBottomSheet(
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
                    ) { Text("Share") }
                }
            }
        }
    }
}
