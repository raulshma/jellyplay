package com.raulshma.jellyplay.feature.player.audio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Nightlight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ArtworkThemeWrapper
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.ui.tv.tvFocusable
import com.raulshma.jellyplay.core.ui.image.MediaImage

private val SPEED_OPTIONS = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPlayerScreen(
    itemId: String,
    onBack: () -> Unit,
    onAmbientClick: (String?, String, String) -> Unit = { _, _, _ -> },
    viewModel: AudioPlayerViewModel = hiltViewModel(),
) {
    var showQueue by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    val artworkScale = remember { Animatable(0.8f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(itemId) {
        viewModel.play(itemId)
        artworkScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }

    LaunchedEffect(Unit) {
        contentAlpha.animateTo(1f, tween(600, delayMillis = 200))
    }

    BackHandler {
        if (showQueue || showSpeedPicker || showEqualizer || showLyrics) {
            showQueue = false
            showSpeedPicker = false
            showEqualizer = false
            showLyrics = false
        } else {
            onBack()
        }
    }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    ArtworkThemeWrapper(
        imageUrl = viewModel.albumArtUrl.ifBlank { null },
        dynamicTheming = preferences.dynamicTheming,
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            BlurredArtworkBackground(
                albumArtUrl = viewModel.albumArtUrl,
                albumArtBlurHash = viewModel.albumArtBlurHash,
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Black.copy(alpha = 0.15f),
                                Color.Black.copy(alpha = 0.6f),
                                Color.Black.copy(alpha = 0.85f),
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        )
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(WindowInsets.navigationBars.asPaddingValues()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PlayerTopBar(
                    onBack = onBack,
                    speed = viewModel.speed,
                    dialogueBoostEnabled = viewModel.dialogueBoostEnabled,
                    nightModeEnabled = viewModel.nightModeEnabled,
                    hasLyrics = viewModel.lyrics.isNotEmpty(),
                    albumArtUrl = viewModel.albumArtUrl,
                    title = viewModel.title,
                    artist = viewModel.artist,
                    showMenu = showMenu,
                    onMenuToggle = { showMenu = it },
                    onQueueClick = { showMenu = false; showQueue = true },
                    onSpeedClick = { showMenu = false; showSpeedPicker = true },
                    onEqualizerClick = { showMenu = false; showEqualizer = true },
                    onDialogueBoostClick = { showMenu = false; viewModel.toggleDialogueBoost() },
                    onNightModeClick = { showMenu = false; viewModel.toggleNightMode() },
                    onLyricsClick = { showMenu = false; showLyrics = true },
                    onAmbientClick = { showMenu = false; onAmbientClick(viewModel.albumArtUrl.ifBlank { null }, viewModel.title, viewModel.artist) },
                )

                Spacer(Modifier.weight(0.6f))

                AlbumArtwork(
                    albumArtUrl = viewModel.albumArtUrl,
                    albumArtBlurHash = viewModel.albumArtBlurHash,
                    title = viewModel.title,
                    scale = artworkScale.value,
                )

                Spacer(Modifier.weight(0.5f))

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
                    )

                    Spacer(Modifier.height(28.dp))

                    ProgressSlider(
                        currentPosition = viewModel.currentPosition,
                        duration = viewModel.duration,
                        onSeek = { fraction ->
                            if (viewModel.duration > 0) {
                                viewModel.seekTo((fraction * viewModel.duration).toLong())
                            }
                        },
                    )

                    Spacer(Modifier.height(12.dp))

                    TransportControls(
                        isPlaying = viewModel.isPlaying,
                        shuffleMode = viewModel.shuffleMode,
                        repeatMode = viewModel.repeatMode,
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSkipPrevious = { viewModel.skipToPrevious() },
                        onSkipNext = { viewModel.skipToNext() },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                    )

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

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
            onToggle = { viewModel.toggleEqualizer() },
            onBandChange = { index, level -> viewModel.setEqualizerBand(index, level) },
            onReset = { viewModel.resetEqualizer() },
            onDismiss = { showEqualizer = false },
        )
    }

    if (showLyrics) {
        LyricsSheet(
            lyrics = viewModel.lyrics,
            currentIndex = viewModel.currentLyricIndex,
            onDismiss = { showLyrics = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(
    queue: List<AudioQueueItem>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    onRemove: (Int) -> Unit,
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
                "Queue",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                itemsIndexed(
                    queue,
                    key = { _, item -> item.id },
                    contentType = { _, _ -> "queueItem" },
                ) { index, item ->
                    AnimatedQueueItem(
                        index = index,
                        currentIndex = currentIndex,
                        item = item,
                        onSelect = { onSelect(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedQueueItem(
    index: Int,
    currentIndex: Int,
    item: AudioQueueItem,
    onSelect: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "queueItemScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (isPressed) 0.7f else 1f,
        animationSpec = tween(100),
        label = "queueItemAlpha",
    )
    val isCurrentItem = index == currentIndex

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
            .tvFocusable()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrentItem) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isCurrentItem) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                item.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isCurrentItem) {
            Text(
                "\u25B6",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 12.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
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
                    androidx.compose.material3.FilterChip(
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
private fun EqualizerSheet(
    enabled: Boolean,
    bandLevels: List<Int>,
    onToggle: () -> Unit,
    onBandChange: (Int, Int) -> Unit,
    onReset: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Equalizer", style = MaterialTheme.typography.titleMedium)
                Row {
                    androidx.compose.material3.TextButton(onClick = onReset) {
                        Text("Reset")
                    }
                    androidx.compose.material3.Switch(
                        checked = enabled,
                        onCheckedChange = { onToggle() },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            val frequencies = listOf("60Hz", "170Hz", "310Hz", "600Hz", "1kHz", "3kHz", "6kHz", "12kHz", "14kHz", "16kHz")
            frequencies.forEachIndexed { index, freq ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        freq,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(48.dp),
                    )
                    Slider(
                        value = bandLevels.getOrElse(index) { 0 }.toFloat(),
                        onValueChange = { onBandChange(index, it.toInt()) },
                        valueRange = -1500f..1500f,
                        steps = 30,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${bandLevels.getOrElse(index) { 0 } / 100}dB",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(48.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LyricsSheet(
    lyrics: List<com.raulshma.jellyplay.core.model.LyricsLine>,
    currentIndex: Int,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
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
            Text("Lyrics", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().height(400.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                items(lyrics.size, contentType = { "lyricsLine" }) { index ->
                    val line = lyrics[index]
                    Text(
                        text = line.text,
                        style = if (index == currentIndex) {
                            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        color = if (index == currentIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(vertical = 6.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.animateScrollToItem(currentIndex.coerceAtMost(lyrics.lastIndex))
        }
    }
}

@Composable
private fun BlurredArtworkBackground(
    albumArtUrl: String,
    albumArtBlurHash: String?,
) {
    if (albumArtUrl.isNotBlank()) {
        MediaImage(
            url = albumArtUrl,
            contentDescription = null,
            blurHash = albumArtBlurHash,
            modifier = Modifier
                .fillMaxSize()
                .blur(60.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                .scale(1.3f)
                .graphicsLayer { alpha = 0.55f },
            contentScale = ContentScale.Crop,
        )
    }
}

@Composable
private fun PlayerTopBar(
    onBack: () -> Unit,
    speed: Float,
    dialogueBoostEnabled: Boolean,
    nightModeEnabled: Boolean,
    hasLyrics: Boolean,
    albumArtUrl: String,
    title: String,
    artist: String,
    showMenu: Boolean,
    onMenuToggle: (Boolean) -> Unit,
    onQueueClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onEqualizerClick: () -> Unit,
    onDialogueBoostClick: () -> Unit,
    onNightModeClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onAmbientClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.tvFocusable()) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack, "Back",
                tint = Color.White,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "NOW PLAYING",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.7f),
                letterSpacing = 2.sp,
            )
        }
        Box {
            IconButton(onClick = { onMenuToggle(true) }, modifier = Modifier.tvFocusable()) {
                Icon(Icons.Default.MoreVert, "More", tint = Color.White)
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { onMenuToggle(false) },
            ) {
                DropdownMenuItem(
                    text = { Text("Queue") },
                    onClick = onQueueClick,
                    leadingIcon = { Icon(Icons.Default.QueueMusic, null) },
                )
                DropdownMenuItem(
                    text = { Text("Speed (${if (speed == 1.0f) "1x" else "${speed}x"})") },
                    onClick = onSpeedClick,
                    leadingIcon = { Icon(Icons.Default.Speed, null) },
                )
                DropdownMenuItem(
                    text = { Text("Equalizer") },
                    onClick = onEqualizerClick,
                    leadingIcon = { Icon(Icons.Default.Tune, null) },
                )
                DropdownMenuItem(
                    text = { Text(if (dialogueBoostEnabled) "Dialogue Boost ✓" else "Dialogue Boost") },
                    onClick = onDialogueBoostClick,
                    leadingIcon = { Icon(Icons.Default.RecordVoiceOver, null) },
                )
                DropdownMenuItem(
                    text = { Text(if (nightModeEnabled) "Night Mode ✓" else "Night Mode") },
                    onClick = onNightModeClick,
                    leadingIcon = { Icon(Icons.Default.Nightlight, null) },
                )
                if (hasLyrics) {
                    DropdownMenuItem(
                        text = { Text("Lyrics") },
                        onClick = onLyricsClick,
                        leadingIcon = { Icon(Icons.Default.Mic, null) },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Ambient Mode") },
                    onClick = onAmbientClick,
                    leadingIcon = { Icon(Icons.Default.NightsStay, null) },
                )
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
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.75f)
            .aspectRatio(1f)
            .scale(scale)
            .shadow(
                elevation = 24.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
            )
            .clip(RoundedCornerShape(24.dp)),
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
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun TrackInfoSection(
    title: String,
    artist: String,
) {
    Text(
        title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        artist,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.65f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Start,
    )
}

@Composable
private fun ProgressSlider(
    currentPosition: Long,
    duration: Long,
    onSeek: (Float) -> Unit,
) {
    Slider(
        value = if (duration > 0) currentPosition.toFloat() / duration else 0f,
        onValueChange = onSeek,
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.2f),
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .offset(y = (-4).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            formatDuration(currentPosition),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )
        Text(
            if (duration > 0) formatDuration(duration) else "--:--",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
        )
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    shuffleMode: Boolean,
    repeatMode: Int,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonWithPressAnimation(
            onClick = onToggleShuffle,
            tint = if (shuffleMode) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
            icon = {
                Icon(
                    Icons.Default.Shuffle,
                    "Shuffle",
                    modifier = Modifier.size(22.dp),
                )
            },
        )
        IconButtonWithPressAnimation(
            onClick = onSkipPrevious,
            icon = {
                Icon(
                    Icons.Default.SkipPrevious, "Previous",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
            },
            size = 48.dp,
        )
        PlayPauseButtonWithAnimation(
            isPlaying = isPlaying,
            onClick = onTogglePlayPause,
        )
        IconButtonWithPressAnimation(
            onClick = onSkipNext,
            icon = {
                Icon(
                    Icons.Default.SkipNext, "Next",
                    modifier = Modifier.size(32.dp),
                    tint = Color.White,
                )
            },
            size = 48.dp,
        )
        IconButtonWithPressAnimation(
            onClick = onCycleRepeatMode,
            tint = if (repeatMode > 0) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.6f),
            icon = {
                Icon(
                    if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    when (repeatMode) {
                        0 -> "Repeat off"
                        1 -> "Repeat all"
                        else -> "Repeat one"
                    },
                    modifier = Modifier.size(22.dp),
                )
            },
        )
    }
}

@Composable
private fun IconButtonWithPressAnimation(
    onClick: () -> Unit,
    tint: Color = Color.White,
    icon: @Composable () -> Unit,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(stiffness = 600f),
        label = "transportScale",
    )

    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .tvFocusable(),
    ) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides tint
        ) {
            icon()
        }
    }
}

@Composable
private fun PlayPauseButtonWithAnimation(
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "playPauseScale",
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(stiffness = 400f),
        label = "playPauseIconScale",
    )

    FilledIconButton(
        onClick = onClick,
        modifier = Modifier
            .size(68.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .tvFocusable(),
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
    ) {
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isPlaying) "Pause" else "Play",
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer { scaleX = iconScale; scaleY = iconScale },
        )
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
