package com.raulshma.jellyplay.feature.downloads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflinePlayerScreen(
    filePath: String,
    title: String,
    onBack: () -> Unit,
    preferencesStore: UserPreferencesStore = hiltViewModel<OfflinePlayerViewModel>().preferencesStore,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val preferences by preferencesStore.preferences.collectAsStateWithLifecycle(initialValue = com.raulshma.jellyplay.core.model.UserPreferences())
    val seekDurationMs = preferences.videoSeekDurationMs

    val resumePrefs = remember { context.getSharedPreferences("offline_player_resume", 0) }
    val resumeKey = remember(filePath) { "pos_$filePath" }

    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    val exoPlayer = remember {
        val savedPos = resumePrefs.getLong(resumeKey, 0L)
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(filePath.toUri())
            setMediaItem(mediaItem)
            prepare()
            if (savedPos > 0) seekTo(savedPos)
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        exoPlayer.addListener(listener)

        activity?.let {
            it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            val window = it.window
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (dur > 0 && pos > 0 && pos < dur - 5000) {
                resumePrefs.edit().putLong(resumeKey, pos).apply()
            } else {
                resumePrefs.edit().remove(resumeKey).apply()
            }
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            activity?.let {
                it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                val window = it.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        onBack()
    }

    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            delay(250)
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(5000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(seekDurationMs) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = { offset ->
                        val width = size.width.toFloat()
                        when {
                            offset.x < width * 0.35 -> {
                                val newPos = (exoPlayer.currentPosition - seekDurationMs).coerceAtLeast(0)
                                exoPlayer.seekTo(newPos)
                            }
                            offset.x > width * 0.65 -> {
                                val newPos = (exoPlayer.currentPosition + seekDurationMs).coerceAtMost(exoPlayer.duration.coerceAtLeast(0))
                                exoPlayer.seekTo(newPos)
                            }
                            else -> if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        }
                    },
                )
            },
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(tween(AnimationTokens.QuickDuration, easing = AlphaEasing)),
            exit = fadeOut(tween(AnimationTokens.DefaultDuration, easing = AlphaEasing)),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                        .statusBarsPadding()
                        .padding(8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { 
                        val newPos = (exoPlayer.currentPosition - seekDurationMs).coerceAtLeast(0)
                        exoPlayer.seekTo(newPos)
                    }) {
                        Icon(
                            Icons.Default.SkipPrevious, "Rewind",
                            tint = Color.White, modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(onClick = {
                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp),
                        )
                    }
                    Spacer(Modifier.width(24.dp))
                    IconButton(onClick = { 
                        val newPos = (exoPlayer.currentPosition + seekDurationMs).coerceAtMost(exoPlayer.duration.coerceAtLeast(0))
                        exoPlayer.seekTo(newPos)
                    }) {
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
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            formatOfflineDuration(currentPosition),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                        Text(
                            if (duration > 0) formatOfflineDuration(duration) else "--:--",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                        )
                    }
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
                        onValueChange = { fraction ->
                            if (duration > 0) {
                                exoPlayer.seekTo((fraction * duration).toLong())
                            }
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun formatOfflineDuration(ms: Long): String {
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

private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
