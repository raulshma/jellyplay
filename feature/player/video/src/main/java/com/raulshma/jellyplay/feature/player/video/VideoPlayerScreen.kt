package com.raulshma.jellyplay.feature.player.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.raulshma.jellyplay.core.ui.navigation.LocalSharedTransitionScope
import com.raulshma.jellyplay.feature.player.video.components.AspectRatio
import com.raulshma.jellyplay.feature.player.video.components.AspectRatioSheet
import com.raulshma.jellyplay.feature.player.video.components.PlaybackInfoOverlay
import com.raulshma.jellyplay.feature.player.video.components.SubtitleStyleSheet
import com.raulshma.jellyplay.feature.player.video.components.TrickplayOverlay
import kotlinx.coroutines.delay

private val SPEED_OPTIONS = floatArrayOf(0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

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
    val sharedTransitionScope = LocalSharedTransitionScope.current

    var showControls by remember { mutableStateOf(true) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showAudioPicker by remember { mutableStateOf(false) }
    var showSubtitlePicker by remember { mutableStateOf(false) }
    var showChapterPicker by remember { mutableStateOf(false) }
    var showPlaybackInfo by remember { mutableStateOf(false) }
    var showAspectRatio by remember { mutableStateOf(false) }
    var showSubtitleStyle by remember { mutableStateOf(false) }
    var showSecondarySubtitlePicker by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekPositionMs by remember { mutableLongStateOf(0L) }
    var isCasting by remember { mutableStateOf(false) }
    var showOcrResult by remember { mutableStateOf(false) }
    var playerViewRef by remember { mutableStateOf<androidx.media3.ui.PlayerView?>(null) }

    var secondarySubtitleText by remember { mutableStateOf<String?>(null) }
    var showTapToTranslate by remember { mutableStateOf(false) }
    var tapToTranslateText by remember { mutableStateOf("") }

    var seekOffsetMs by remember { mutableLongStateOf(0L) }
    var seekDirection by remember { mutableIntStateOf(0) }
    var brightnessOverlay by remember { mutableFloatStateOf(-1f) }
    var volumeOverlay by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(itemId) {
        viewModel.initialize(itemId, mediaSourceId, startPositionTicks)
    }

    DisposableEffect(Unit) {
        activity?.let {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val window = it.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                val window = it.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            viewModel.release()
        }
    }

    BackHandler {
        if (showSpeedPicker || showAudioPicker || showSubtitlePicker || showChapterPicker ||
            showPlaybackInfo || showAspectRatio || showSubtitleStyle || showSecondarySubtitlePicker ||
            showTapToTranslate || showOcrResult
        ) {
            showSpeedPicker = false
            showAudioPicker = false
            showSubtitlePicker = false
            showChapterPicker = false
            showPlaybackInfo = false
            showAspectRatio = false
            showSubtitleStyle = false
            showSecondarySubtitlePicker = false
            showTapToTranslate = false
            showOcrResult = false
        } else {
            onBack()
        }
    }

    val exoPlayer = viewModel.exoPlayer
    val streamUrl = viewModel.streamUrl
    val title = viewModel.title
    val subtitle = viewModel.subtitle
    val isPlaying = viewModel.isPlaying
    val currentPosition = viewModel.currentPosition
    val duration = viewModel.duration
    val playbackSpeed = viewModel.playbackSpeed
    val currentMediaSource = viewModel.currentMediaSource
    val mediaStreams = viewModel.mediaStreams
    val aspectRatio = viewModel.aspectRatio
    val playMethod = viewModel.playMethod
    val trickplayUrl = viewModel.trickplayUrl
    val subtitleStyle = viewModel.subtitleStyle

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (sharedTransitionScope != null) {
                    with(sharedTransitionScope) {
                        Modifier.sharedElementWithCallerManagedVisibility(
                            rememberSharedContentState(key = "backdrop_$itemId"),
                            visible = true,
                        )
                    }
                } else {
                    Modifier
                }
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val width = size.width
                        when {
                            offset.x < width * 0.35 -> {
                                seekDirection = -1
                                seekOffsetMs = 10_000L
                                exoPlayer?.seekBack()
                            }
                            offset.x > width * 0.65 -> {
                                seekDirection = 1
                                seekOffsetMs = 10_000L
                                exoPlayer?.seekForward()
                            }
                            else -> {
                                if (exoPlayer?.isPlaying == true) exoPlayer.pause() else exoPlayer?.play()
                            }
                        }
                    },
                    onLongPress = {
                        val primaryText = viewModel.getCurrentPrimarySubtitleText()
                        val secondaryText = secondarySubtitleText
                        tapToTranslateText = listOfNotNull(primaryText, secondaryText)
                            .joinToString("\n")
                            .takeIf { it.isNotBlank() } ?: "No subtitle available"
                        showTapToTranslate = true
                    },
                )
            },
    ) {
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
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        GestureOverlay(
            seekDirection = seekDirection,
            seekOffsetMs = seekOffsetMs,
            brightnessValue = brightnessOverlay,
            volumeValue = volumeOverlay,
            onSeekGesture = { delta ->
                exoPlayer?.let { player ->
                    val newPos = (player.currentPosition + delta).coerceIn(0, player.duration.coerceAtLeast(0))
                    player.seekTo(newPos)
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
                    fontSize = (viewModel.subtitleStyle.fontSize - 4).sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PlayerControls(
                title = title,
                subtitle = subtitle,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                playbackSpeed = playbackSpeed,
                hasChapters = viewModel.chapters.isNotEmpty(),
                exoPlayer = exoPlayer,
                onPlayPause = {
                    if (exoPlayer?.isPlaying == true) exoPlayer.pause() else exoPlayer?.play()
                },
                onSeek = { fraction ->
                    if (duration > 0) {
                        exoPlayer?.seekTo((fraction * duration).toLong())
                    }
                },
                onSeekStart = {
                    isSeeking = true
                },
                onSeekEnd = {
                    isSeeking = false
                },
                onSeekPositionChange = { positionMs ->
                    seekPositionMs = positionMs
                },
                onBack = onBack,
                onSpeedClick = { showSpeedPicker = true },
                onAudioClick = { showAudioPicker = true },
                onSubtitleClick = { showSubtitlePicker = true },
                onSubtitleStyleClick = { showSubtitleStyle = true },
                onSecondarySubtitleClick = { showSecondarySubtitlePicker = true },
                onChapterClick = { showChapterPicker = true },
                onInfoClick = { showPlaybackInfo = true },
                onAspectRatioClick = { showAspectRatio = true },
                onDialogueBoostClick = { viewModel.toggleDialogueBoost() },
                dialogueBoostEnabled = viewModel.dialogueBoostEnabled,
                isCasting = isCasting,
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
                    showOcrResult = true
                },
                isOcrRunning = viewModel.isOcrRunning,
                modifier = Modifier.fillMaxSize(),
            )

            if (isSeeking) {
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
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
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

    LaunchedEffect(exoPlayer, viewModel.secondarySubtitleTrack) {
        val track = viewModel.secondarySubtitleTrack ?: return@LaunchedEffect
        while (true) {
            val pos = exoPlayer?.currentPosition ?: 0L
            secondarySubtitleText = viewModel.getSecondarySubtitleText(pos)
            delay(250)
        }
    }

    if (showSpeedPicker) {
        SpeedPickerSheet(
            currentSpeed = playbackSpeed,
            onSelect = { viewModel.setPlaybackSpeed(it) },
            onDismiss = { showSpeedPicker = false },
        )
    }

    if (showAudioPicker) {
        TrackPickerSheet(
            title = "Audio",
            tracks = viewModel.audioTracks,
            onSelect = { viewModel.selectAudioTrack(it) },
            onDismiss = { showAudioPicker = false },
        )
    }

    if (showSubtitlePicker) {
        TrackPickerSheet(
            title = "Subtitles",
            tracks = viewModel.subtitleTracks,
            onSelect = { viewModel.selectSubtitleTrack(it) },
            onDismiss = { showSubtitlePicker = false },
        )
    }

    if (showChapterPicker) {
        ChapterPickerSheet(
            chapters = viewModel.chapters,
            currentPositionMs = currentPosition,
            onSelect = { positionTicks ->
                exoPlayer?.seekTo(positionTicks / 10_000)
                showChapterPicker = false
            },
            onDismiss = { showChapterPicker = false },
        )
    }

    if (showPlaybackInfo) {
        ModalBottomSheet(
            onDismissRequest = { showPlaybackInfo = false },
            sheetState = rememberModalBottomSheetState(),
        ) {
            PlaybackInfoOverlay(
                mediaSource = currentMediaSource,
                mediaStreams = mediaStreams,
                playMethod = playMethod,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            )
        }
    }

    if (showAspectRatio) {
        AspectRatioSheet(
            currentRatio = aspectRatio,
            onSelect = { viewModel.setAspectRatio(it) },
            onDismiss = { showAspectRatio = false },
        )
    }

    if (showSubtitleStyle) {
        SubtitleStyleSheet(
            currentStyle = subtitleStyle,
            onStyleChange = { viewModel.setSubtitleStyle(it) },
            onDismiss = { showSubtitleStyle = false },
        )
    }

    if (showSecondarySubtitlePicker) {
        SecondarySubtitlePickerSheet(
            mediaStreams = viewModel.mediaStreams,
            currentSecondary = viewModel.secondarySubtitleTrack,
            onSelect = { stream ->
                viewModel.selectSecondarySubtitleStream(stream)
                showSecondarySubtitlePicker = false
            },
            onDismiss = { showSecondarySubtitlePicker = false },
        )
    }

    if (showTapToTranslate) {
        TapToTranslateSheet(
            text = tapToTranslateText,
            onDismiss = { showTapToTranslate = false },
        )
    }

    if (showOcrResult) {
        val ocrText = viewModel.ocrText
        ModalBottomSheet(
            onDismissRequest = {
                showOcrResult = false
                viewModel.clearOcrText()
            },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp),
            ) {
                Text("OCR Subtitle Text", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                if (viewModel.isOcrRunning) {
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
}

@Composable
private fun GestureOverlay(
    seekDirection: Int,
    seekOffsetMs: Long,
    brightnessValue: Float,
    volumeValue: Float,
    onSeekGesture: (Long) -> Unit,
    onBrightnessGesture: (Float) -> Unit,
    onVolumeGesture: (Float) -> Unit,
    onClearOverlays: () -> Unit,
) {
    val activity = LocalContext.current.findActivity()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = {},
                    onDragEnd = { onClearOverlays() },
                    onDragCancel = { onClearOverlays() },
                    onHorizontalDrag = { _, dragAmount ->
                        if (kotlin.math.abs(dragAmount) > 20) {
                            val seekDelta = ((dragAmount / size.width) * 120_000L).toLong()
                            onSeekGesture(seekDelta)
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = {},
                    onDragEnd = { onClearOverlays() },
                    onDragCancel = { onClearOverlays() },
                    onVerticalDrag = { change, dragAmount ->
                        if (kotlin.math.abs(dragAmount) > 10) {
                            val halfWidth = size.width / 2f
                            if (change.position.x > halfWidth) {
                                val delta = -(dragAmount / size.height) * 0.5f
                                onVolumeGesture(delta)
                            } else {
                                val delta = -(dragAmount / size.height) * 0.5f
                                onBrightnessGesture(delta)
                            }
                        }
                    },
                )
            },
    ) {
        if (seekDirection != 0 && seekOffsetMs > 0) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 120.dp),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val icon = if (seekDirection < 0) Icons.Default.SkipPrevious else Icons.Default.SkipNext
                        Icon(icon, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${if (seekDirection < 0) "-" else "+"}${seekOffsetMs / 1000}s",
                            color = Color.White,
                            fontSize = 16.sp,
                        )
                    }
                }
            }
        }

        if (brightnessValue >= 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 40.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("☀", fontSize = 14.sp, color = Color.White)
                        Spacer(Modifier.width(6.dp))
                        Text("${(brightnessValue * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }

        if (volumeValue >= 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 40.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${(volumeValue * 100).toInt()}%", color = Color.White, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerControls(
    title: String,
    subtitle: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    playbackSpeed: Float,
    hasChapters: Boolean,
    exoPlayer: androidx.media3.exoplayer.ExoPlayer?,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekStart: () -> Unit,
    onSeekEnd: () -> Unit,
    onSeekPositionChange: (Long) -> Unit,
    onBack: () -> Unit,
    onSpeedClick: () -> Unit,
    onAudioClick: () -> Unit,
    onSubtitleClick: () -> Unit,
    onSubtitleStyleClick: () -> Unit,
    onSecondarySubtitleClick: () -> Unit,
    onChapterClick: () -> Unit,
    onInfoClick: () -> Unit,
    onAspectRatioClick: () -> Unit,
    onDialogueBoostClick: () -> Unit,
    dialogueBoostEnabled: Boolean,
    isCasting: Boolean = false,
    onCastClick: () -> Unit = {},
    onOcrClick: () -> Unit = {},
    isOcrRunning: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(8.dp)
                .align(Alignment.TopCenter),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { exoPlayer?.seekBack() }) {
                Icon(
                    Icons.Default.SkipPrevious, "Rewind",
                    tint = Color.White, modifier = Modifier.size(36.dp)
                )
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = { exoPlayer?.seekForward() }) {
                Icon(
                    Icons.Default.SkipNext, "Forward",
                    tint = Color.White, modifier = Modifier.size(36.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatDuration(currentPosition),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
                Text(
                    if (duration > 0) formatDuration(duration) else "--:--",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
            }
            Slider(
                value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                onValueChange = { fraction ->
                    onSeek(fraction)
                    onSeekPositionChange((fraction * duration).toLong())
                },
                onValueChangeFinished = {
                    onSeekEnd()
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    IconButton(onClick = onSpeedClick) {
                        val speedText = if (playbackSpeed == 1.0f) "1x" else "${playbackSpeed}x"
                        Text(speedText, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    IconButton(onClick = onAudioClick) {
                        Icon(Icons.Default.Audiotrack, "Audio", tint = Color.White)
                    }
                    IconButton(onClick = onSubtitleClick) {
                        Icon(Icons.Default.ClosedCaption, "Subtitles", tint = Color.White)
                    }
                    IconButton(onClick = onSubtitleStyleClick) {
                        Icon(Icons.Default.Settings, "Subtitle Style", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onSecondarySubtitleClick) {
                        Icon(Icons.Default.ClosedCaptionOff, "Dual Subtitles", tint = Color.White)
                    }
                    if (hasChapters) {
                        IconButton(onClick = onChapterClick) {
                            Icon(Icons.Default.List, "Chapters", tint = Color.White)
                        }
                    }
                    IconButton(onClick = onAspectRatioClick) {
                        Icon(Icons.Default.AspectRatio, "Aspect Ratio", tint = Color.White)
                    }
                    IconButton(onClick = onInfoClick) {
                        Icon(Icons.Default.Info, "Playback Info", tint = Color.White)
                    }
                    IconButton(onClick = onDialogueBoostClick) {
                        Icon(
                            Icons.Default.RecordVoiceOver,
                            "Dialogue Boost",
                            tint = if (dialogueBoostEnabled) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    CastButton(isCasting = isCasting, onCast = onCastClick)
                    IconButton(
                        onClick = onOcrClick,
                        enabled = !isOcrRunning,
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "OCR Subtitle",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpeedPickerSheet(
    currentSpeed: Float,
    onSelect: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text("Playback Speed", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SPEED_OPTIONS.forEach { speed ->
                    FilterChip(
                        selected = speed == currentSpeed,
                        onClick = { onSelect(speed); onDismiss() },
                        label = { Text(if (speed == 1.0f) "1x" else "${speed}x") },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackPickerSheet(
    title: String,
    tracks: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                itemsIndexed(tracks) { _, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(track)
                                onDismiss()
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            track.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (track.isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (track.isSelected) {
                            Text("\u2713", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterPickerSheet(
    chapters: List<com.raulshma.jellyplay.core.model.ChapterInfo>,
    currentPositionMs: Long,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Chapters",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                itemsIndexed(chapters) { index, chapter ->
                    val chapterMs = chapter.startPositionTicks / 10_000
                    val isCurrentChapter = if (index < chapters.lastIndex) {
                        val nextChapterMs = chapters[index + 1].startPositionTicks / 10_000
                        currentPositionMs in chapterMs until nextChapterMs
                    } else {
                        currentPositionMs >= chapterMs
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(chapter.startPositionTicks) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                chapter.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isCurrentChapter) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (isCurrentChapter) FontWeight.SemiBold else FontWeight.Normal,
                            )
                            Text(
                                formatDuration(chapterMs),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (isCurrentChapter) {
                            Text("►", color = MaterialTheme.colorScheme.primary, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SecondarySubtitlePickerSheet(
    mediaStreams: List<com.raulshma.jellyplay.core.model.MediaStream>,
    currentSecondary: com.raulshma.jellyplay.core.model.MediaStream?,
    onSelect: (com.raulshma.jellyplay.core.model.MediaStream?) -> Unit,
    onDismiss: () -> Unit,
) {
    val subtitleStreams = mediaStreams.filter { it.type == com.raulshma.jellyplay.core.model.StreamType.SUBTITLE }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Secondary Subtitle",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(null) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Off",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (currentSecondary == null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                )
                if (currentSecondary == null) {
                    Text("\u2713", color = MaterialTheme.colorScheme.primary)
                }
            }

            LazyColumn {
                itemsIndexed(subtitleStreams) { _, stream ->
                    val isSelected = currentSecondary?.index == stream.index
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(stream) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stream.displayTitle ?: stream.language ?: "Unknown",
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            stream.codec?.let { codec ->
                                Text(
                                    codec.uppercase(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (isSelected) {
                            Text("\u2713", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TapToTranslateSheet(
    text: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                "Subtitle Text",
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                androidx.compose.material3.FilledTonalButton(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Subtitle", text)
                        clipboard.setPrimaryClip(clip)
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Copy")
                }
                androidx.compose.material3.FilledTonalButton(
                    onClick = {
                        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(android.content.Intent.createChooser(intent, "Share subtitle"))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Share")
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
private fun CastButton(isCasting: Boolean, onCast: () -> Unit) {
    val context = LocalContext.current
    IconButton(
        onClick = {
            if (isCasting) {
                onCast()
            } else {
                try {
                    val castContext = com.google.android.gms.cast.framework.CastContext.getSharedInstance(context)
                    val sessionManager = castContext.sessionManager
                    val session = sessionManager.currentCastSession
                    if (session?.isConnected == true) {
                        sessionManager.endCurrentSession(true)
                    } else {
                        onCast()
                    }
                } catch (_: Exception) {
                    onCast()
                }
            }
        },
    ) {
        Icon(
            if (isCasting) Icons.Default.CastConnected else Icons.Default.Cast,
            contentDescription = "Cast",
            tint = if (isCasting) MaterialTheme.colorScheme.primary else Color.White,
        )
    }
}

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

private fun mutableLongStateOf(initial: Long) = mutableStateOf(initial)
