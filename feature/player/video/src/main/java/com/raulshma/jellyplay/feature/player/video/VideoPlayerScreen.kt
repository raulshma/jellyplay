package com.raulshma.jellyplay.feature.player.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.ClosedCaptionOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.raulshma.jellyplay.core.data.playback.FrameRateMatcher
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.components.AspectRatioSheet
import com.raulshma.jellyplay.feature.player.video.components.AudioDelaySheet
import com.raulshma.jellyplay.feature.player.video.components.DecoderPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.CreditsSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.HdrBadge
import com.raulshma.jellyplay.feature.player.video.components.IntroSkipOverlay
import com.raulshma.jellyplay.feature.player.video.components.NextEpisodeOverlay
import com.raulshma.jellyplay.feature.player.video.components.PlaybackInfoOverlay
import com.raulshma.jellyplay.feature.player.video.components.SubtitleDownloadSheet
import com.raulshma.jellyplay.feature.player.video.components.CastButton
import com.raulshma.jellyplay.feature.player.video.components.ChapterPickerSheet
import com.raulshma.jellyplay.feature.player.video.components.GestureOverlay
import com.raulshma.jellyplay.feature.player.video.components.LabeledControlButton
import com.raulshma.jellyplay.feature.player.video.components.LabeledSpeedButton
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle

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
    var playerViewRef by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }

    var secondarySubtitleText by remember { mutableStateOf<String?>(null) }

    var seekOffsetMs by remember { mutableLongStateOf(0L) }
    var seekDirection by remember { mutableIntStateOf(0) }
    var seekTimestamp by remember { mutableLongStateOf(0L) }
    var brightnessOverlay by remember { mutableFloatStateOf(-1f) }
    var volumeOverlay by remember { mutableFloatStateOf(-1f) }
    var externalLaunched by remember { mutableStateOf(false) }

    LaunchedEffect(itemId) {
        viewModel.initialize(itemId, mediaSourceId, startPositionTicks)
    }

    val preferredPlayer = uiState.preferredPlayerType
    val streamUrl = uiState.streamUrl

    LaunchedEffect(preferredPlayer, streamUrl) {
        if (preferredPlayer == com.raulshma.jellyplay.core.model.PlayerType.EXTERNAL
            && streamUrl != null
            && !externalLaunched
        ) {
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
            playerViewRef?.player = null
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
        kotlinx.coroutines.delay(400)
        activity?.requestedOrientation = when (uiState.defaultOrientation) {
            com.raulshma.jellyplay.core.model.OrientationMode.SENSOR_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            com.raulshma.jellyplay.core.model.OrientationMode.SENSOR_PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            com.raulshma.jellyplay.core.model.OrientationMode.SENSOR ->
                ActivityInfo.SCREEN_ORIENTATION_SENSOR
            com.raulshma.jellyplay.core.model.OrientationMode.LOCKED_LANDSCAPE ->
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            com.raulshma.jellyplay.core.model.OrientationMode.LOCKED_PORTRAIT ->
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
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

    val exoPlayer = viewModel.exoPlayerRef
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

    LaunchedEffect(aspectRatio, detectedAspectRatio) {
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
        val eng = viewModel.playerEngineRef
        if (eng != null) {
            eng.setAspectRatio(resizeMode, targetRatio)
        } else {
            val pv = playerViewRef ?: return@LaunchedEffect
            pv.setResizeMode(resizeMode)
            if (targetRatio != null) {
                (pv as? AspectRatioFrameLayout)?.setAspectRatio(targetRatio)
            } else {
                (pv as? AspectRatioFrameLayout)?.setAspectRatio(0f)
            }
        }
    }

    val playMethod = uiState.playMethod
    val subtitleStyle = uiState.subtitleStyle
    val isInIntro = uiState.isInIntro
    val isInCredits = uiState.isInCredits
    val shouldShowUpNext = uiState.shouldShowUpNext
    val nextEpisode = uiState.nextEpisode
    val nextEpisodeImageUrl = nextEpisode?.let { viewModel.getImageUrl(it.id, 300) }

    val activeEngine = viewModel.playerEngineRef
    val doPlay: () -> Unit = { if (activeEngine != null) activeEngine.play() else exoPlayer?.play() }
    val doPause: () -> Unit = { if (activeEngine != null) activeEngine.pause() else exoPlayer?.pause() }
    val doTogglePlayPause: () -> Unit = {
        if (isPlaying) doPause() else doPlay()
    }
    val doSeekTo: (Long) -> Unit = { ms ->
        if (activeEngine != null) activeEngine.seekTo(ms) else exoPlayer?.seekTo(ms)
    }
    val doSeekBack: () -> Unit = {
        if (activeEngine != null) activeEngine.seekBack() else exoPlayer?.seekBack()
    }
    val doSeekForward: () -> Unit = {
        if (activeEngine != null) activeEngine.seekForward() else exoPlayer?.seekForward()
    }

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
                        val primaryText = viewModel.getCurrentPrimarySubtitleText()
                        val secondaryText = secondarySubtitleText
                        val text = listOfNotNull(primaryText, secondaryText)
                            .joinToString("\n")
                            .takeIf { it.isNotBlank() } ?: "No subtitle available"
                        currentSheet = PlayerSheet.TapToTranslate(text)
                    },
                )
            },
    ) {
        val engine = viewModel.playerEngineRef
        if (engine != null) {
            AndroidView(
                factory = { ctx -> engine.createPlayerView(ctx) },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            exoPlayer?.let { player ->
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = false
                            playerViewRef = this
                            val bgAlpha = (subtitleStyle.backgroundOpacity * 255).toInt()
                            val bgColorWithAlpha = (bgAlpha shl 24) or (subtitleStyle.backgroundColor.value and 0x00FFFFFF)
                            subtitleView?.setStyle(
                                androidx.media3.ui.CaptionStyleCompat(
                                    subtitleStyle.fontColor.value,
                                    bgColorWithAlpha,
                                    android.graphics.Color.TRANSPARENT,
                                    when (subtitleStyle.edgeType) {
                                        com.raulshma.jellyplay.core.model.SubtitleEdgeType.OUTLINE -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                                        com.raulshma.jellyplay.core.model.SubtitleEdgeType.DROP_SHADOW -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                                        com.raulshma.jellyplay.core.model.SubtitleEdgeType.RAISED -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_RAISED
                                        com.raulshma.jellyplay.core.model.SubtitleEdgeType.DEPRESSED -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DEPRESSED
                                        else -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                                    },
                                    subtitleStyle.edgeColor.value,
                                    null,
                                )
                            )
                            subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleStyle.fontSize.toFloat())
                            val bottomPaddingPx = (subtitleStyle.verticalPosition * height).toInt()
                            subtitleView?.setPadding(
                                subtitleView?.paddingLeft ?: 0,
                                subtitleView?.paddingTop ?: 0,
                                subtitleView?.paddingRight ?: 0,
                                bottomPaddingPx,
                            )
                        }
                    },
                    update = { view ->
                        val bgAlpha = (subtitleStyle.backgroundOpacity * 255).toInt()
                        val bgColorWithAlpha = (bgAlpha shl 24) or (subtitleStyle.backgroundColor.value and 0x00FFFFFF)
                        view.subtitleView?.setStyle(
                            androidx.media3.ui.CaptionStyleCompat(
                                subtitleStyle.fontColor.value,
                                bgColorWithAlpha,
                                android.graphics.Color.TRANSPARENT,
                                when (subtitleStyle.edgeType) {
                                    com.raulshma.jellyplay.core.model.SubtitleEdgeType.OUTLINE -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_OUTLINE
                                    com.raulshma.jellyplay.core.model.SubtitleEdgeType.DROP_SHADOW -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                                    com.raulshma.jellyplay.core.model.SubtitleEdgeType.RAISED -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_RAISED
                                    com.raulshma.jellyplay.core.model.SubtitleEdgeType.DEPRESSED -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DEPRESSED
                                    else -> androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_NONE
                                },
                                subtitleStyle.edgeColor.value,
                                null,
                            )
                        )
                        view.subtitleView?.setFixedTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, subtitleStyle.fontSize.toFloat())
                        val bottomPaddingPx = (subtitleStyle.verticalPosition * view.height).toInt()
                        view.subtitleView?.setPadding(
                            view.subtitleView?.paddingLeft ?: 0,
                            view.subtitleView?.paddingTop ?: 0,
                            view.subtitleView?.paddingRight ?: 0,
                            bottomPaddingPx,
                        )
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        GestureOverlay(
            seekDirection = seekDirection,
            seekOffsetMs = seekOffsetMs,
            brightnessValue = brightnessOverlay,
            volumeValue = volumeOverlay,
            gesturesEnabled = uiState.gesturesEnabled,
            swipeSeekMaxMs = uiState.swipeSeekMaxMs,
            onSeekGesture = { delta ->
                val eng = viewModel.playerEngineRef
                if (eng != null) {
                    val newPos = (eng.currentPositionMs + delta).coerceIn(0, eng.durationMs.coerceAtLeast(0))
                    eng.seekTo(newPos)
                } else {
                    exoPlayer?.let { player ->
                        val newPos = (player.currentPosition + delta).coerceIn(0, player.duration.coerceAtLeast(0))
                        player.seekTo(newPos)
                    }
                }
            },
            onBrightnessGesture = { delta ->
                activity?.let { act ->
                    val window = act.window
                    val layout = window.attributes
                    val current = layout.screenBrightness
                    val newBrightness = (current + delta).coerceIn(0f, 1f)
                    layout.screenBrightness = newBrightness
                    window.attributes = layout
                    brightnessOverlay = newBrightness
                }
            },
            onVolumeGesture = { delta ->
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
            },
            onClearOverlays = {
                if (brightnessOverlay in 0f..1f) {
                    viewModel.saveBrightness(brightnessOverlay)
                }
                seekDirection = 0
                seekOffsetMs = 0L
                brightnessOverlay = -1f
                volumeOverlay = -1f
            },
        )

        secondarySubtitleText?.let { text ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(top = 60.dp, start = 16.dp, end = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = text,
                    color = Color.Yellow,
                    fontSize = (uiState.subtitleStyle.fontSize - 4).sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        IntroSkipOverlay(
            isVisible = isInIntro,
            onSkip = { viewModel.skipIntro() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 200.dp),
        )

        CreditsSkipOverlay(
            isVisible = isInCredits && !shouldShowUpNext,
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

        var showAutoAspectBadge by remember { mutableStateOf(false) }
        LaunchedEffect(detectedAspectRatio, aspectRatio) {
            if (detectedAspectRatio != null && detectedAspectRatio != AspectRatio.FIT && aspectRatio == AspectRatio.AUTO) {
                showAutoAspectBadge = true
                delay(5000L)
                showAutoAspectBadge = false
            } else {
                showAutoAspectBadge = false
            }
        }

        AnimatedVisibility(
            visible = showAutoAspectBadge,
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
            hasChapters = uiState.chapters.isNotEmpty(),
            dialogueBoostEnabled = uiState.dialogueBoostEnabled,
            nightModeEnabled = uiState.nightModeEnabled,
            audioPassthrough = uiState.audioPassthrough,
            isCasting = isCasting,
            isOcrRunning = uiState.isOcrRunning,
            currentAspectRatio = aspectRatio,
            detectedAspectRatio = detectedAspectRatio,
            isVisible = showControls,
            onPlayPause = { doTogglePlayPause() },
            onSeekBack = { doSeekBack() },
            onSeekForward = { doSeekForward() },
            onSeek = { fraction ->
                if (duration > 0) {
                    doSeekTo((fraction * duration).toLong())
                }
            },
            onSeekStart = { isSeeking = true },
            onSeekEnd = { isSeeking = false },
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
            onAudioDelayClick = {
                val engine = viewModel.playerEngineRef
                if (engine == null || engine.supportsAudioDelay) {
                    currentSheet = PlayerSheet.AudioDelay
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Audio delay requires mpv or LibVLC player engine",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            },
            onDecoderClick = { currentSheet = PlayerSheet.Decoder },
            onPassthroughClick = {
                val engine = viewModel.playerEngineRef
                if (engine == null || engine.supportsAudioPassthrough) {
                    viewModel.setAudioPassthrough(!uiState.audioPassthrough)
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar(
                            message = "Audio passthrough requires mpv or LibVLC player engine",
                            duration = SnackbarDuration.Short
                        )
                    }
                }
            },
            onCastClick = { viewModel.castToDevice() },
            onOcrClick = {
                val pv = playerViewRef
                val bitmap = if (pv != null && pv.width > 0 && pv.height > 0) {
                    try {
                        android.graphics.Bitmap.createBitmap(pv.width, pv.height, android.graphics.Bitmap.Config.ARGB_8888).also {
                            pv.draw(android.graphics.Canvas(it))
                        }
                    } catch (_: Exception) { null }
                } else null
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
            visible = showControls && isSeeking,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            val trickplayImageUrl = viewModel.getTrickplayImageUrl(seekPositionMs)
            TrickplayOverlay(
                imageUrl = trickplayImageUrl,
                positionMs = seekPositionMs,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 80.dp),
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

    LaunchedEffect(exoPlayer, uiState.secondarySubtitleTrack) {
        val track = uiState.secondarySubtitleTrack ?: return@LaunchedEffect
        while (true) {
            val pos = activeEngine?.currentPositionMs ?: exoPlayer?.currentPosition ?: 0L
            secondarySubtitleText = viewModel.getSecondarySubtitleText(pos)
            delay(250)
        }
    }

    val dismissSheet: () -> Unit = { currentSheet = PlayerSheet.None }

    when (val sheet = currentSheet) {
        is PlayerSheet.Speed -> {
            SpeedPickerSheet(
                currentSpeed = playbackSpeed,
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
                    currentSheet = PlayerSheet.None
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
                    mediaSource = currentMediaSource,
                    mediaStreams = mediaStreams,
                    playMethod = playMethod,
                    hdrType = uiState.hdrType,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                )
            }
        }
        is PlayerSheet.AspectRatio -> {
            AspectRatioSheet(
                currentRatio = aspectRatio,
                detectedRatio = detectedAspectRatio,
                onSelect = { viewModel.setAspectRatio(it) },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.SubtitleStyle -> {
            SubtitleStyleSheet(
                currentStyle = subtitleStyle,
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
                    currentSheet = PlayerSheet.None
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
                    currentSheet = PlayerSheet.None
                },
                onDismiss = dismissSheet,
            )
        }
        is PlayerSheet.OcrResult -> {
            val ocrText = uiState.ocrText
            ModalBottomSheet(
                onDismissRequest = {
                    currentSheet = PlayerSheet.None
                    viewModel.clearOcrText()
                },
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
                    if (uiState.isOcrRunning) {
                        androidx.compose.material3.CircularProgressIndicator(
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
                            androidx.compose.material3.FilledTonalButton(
                                onClick = {
                                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                    clipboard.setPrimaryClip(
                                        android.content.ClipData.newPlainText("OCR Subtitle", ocrText)
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Copy") }
                            androidx.compose.material3.FilledTonalButton(
                                onClick = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(android.content.Intent.EXTRA_TEXT, ocrText)
                                    }
                                    context.startActivity(android.content.Intent.createChooser(intent, "Share"))
                                },
                                modifier = Modifier.weight(1f),
                            ) { Text("Share") }
                        }
                    }
                }
            }
        }
        PlayerSheet.None -> { }
    }
}
