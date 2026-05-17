package com.raulshma.jellyplay.feature.player.audio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material.icons.filled.Check
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
import com.raulshma.jellyplay.feature.player.audio.components.WaveformSeekBar
import com.raulshma.jellyplay.core.designsystem.theme.rememberArtworkColors

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
        contentAlpha.animateTo(1f, tween(AnimationTokens.ExtendedDuration, delayMillis = 200, easing = AlphaEasing))
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

    val adaptiveInfo = com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass == com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass.Expanded

    val artworkColors = rememberArtworkColors(viewModel.albumArtUrl.ifBlank { null })
    val tintedBg = artworkColors?.tintedBackground ?: Color(0xFF2D1F2D)
    val tintedBgLight = artworkColors?.tintedBackgroundLight ?: Color(0xFF3D2F3D)
    val accentColor = artworkColors?.accentColor ?: Color(0xFFE8B4C8)
    val pillSurface = artworkColors?.pillSurface ?: Color(0xFF3A2A3A).copy(alpha = 0.55f)
    val pillSurfaceDark = artworkColors?.pillSurfaceDark ?: Color(0xFF2A1A2A).copy(alpha = 0.7f)

    ArtworkThemeWrapper(
        imageUrl = viewModel.albumArtUrl.ifBlank { null },
        dynamicTheming = preferences.dynamicTheming,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(tintedBgLight, tintedBg, tintedBg),
                    )
                ),
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
                    hasLyrics = viewModel.lyrics.isNotEmpty(),
                    onLyricsClick = { showLyrics = true },
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
                    onDialogueBoostClick = { showMenu = false; viewModel.toggleDialogueBoost() },
                    onDialogueBoostStrengthChange = { viewModel.setDialogueBoostStrength(it) },
                    onNightModeClick = { showMenu = false; viewModel.toggleNightMode() },
                    onNightModeStrengthChange = { viewModel.setNightModeStrength(it) },
                    onAmbientClick = { showMenu = false; onAmbientClick(viewModel.albumArtUrl.ifBlank { null }, viewModel.title, viewModel.artist) },
                )

                if (isExpanded) {
                    // Tablet: side-by-side layout
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
                            TrackInfoSection(title = viewModel.title, artist = viewModel.artist)
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
                                onToggleShuffle = { viewModel.toggleShuffle() },
                                onCycleRepeatMode = { viewModel.cycleRepeatMode() },
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
                    )

                    Spacer(Modifier.weight(0.4f))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer { alpha = contentAlpha.value }
                            .padding(horizontal = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TrackInfoSection(title = viewModel.title, artist = viewModel.artist)
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
                            onToggleShuffle = { viewModel.toggleShuffle() },
                            onCycleRepeatMode = { viewModel.cycleRepeatMode() },
                            pillSurfaceDark = pillSurfaceDark,
                            accentColor = accentColor,
                        )
                        Spacer(Modifier.height(16.dp))
                    }
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
        animationSpec = tween(AnimationTokens.InstantDuration),
        label = "queueItemAlpha",
    )
    val isCurrentItem = index == currentIndex

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; this.alpha = alpha }
            .tvFocusable().clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onSelect,
            )
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
private fun PixelPlayerTopBar(
    onBack: () -> Unit,
    hasLyrics: Boolean,
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
    onDialogueBoostClick: () -> Unit,
    onDialogueBoostStrengthChange: (com.raulshma.jellyplay.core.model.EffectStrength) -> Unit,
    onNightModeClick: () -> Unit,
    onNightModeStrengthChange: (com.raulshma.jellyplay.core.model.EffectStrength) -> Unit,
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
                Icons.Default.KeyboardArrowDown, "Minimize",
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Text(
            "Now Playing",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White.copy(alpha = 0.8f),
            letterSpacing = 1.sp,
        )
        Row {
            if (hasLyrics) {
                IconButton(onClick = onLyricsClick, modifier = Modifier.tvFocusable()) {
                    Icon(Icons.Default.Mic, "Lyrics", tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }
            IconButton(onClick = onQueueClick, modifier = Modifier.tvFocusable()) {
                Icon(Icons.Default.QueueMusic, "Queue", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Box {
                IconButton(onClick = { onMenuToggle(true) }, modifier = Modifier.tvFocusable()) {
                    Icon(Icons.Default.MoreVert, "More", tint = Color.White, modifier = Modifier.size(22.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { onMenuToggle(false) }) {
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
                        text = { Text(if (dialogueBoostEnabled) "Dialogue Boost · ${dialogueBoostStrength.displayName}" else "Dialogue Boost") },
                        onClick = { onDialogueBoostClick(); onMenuToggle(false) },
                        leadingIcon = { Icon(Icons.Default.RecordVoiceOver, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(if (nightModeEnabled) "Night Mode · ${nightModeStrength.displayName}" else "Night Mode") },
                        onClick = { onNightModeClick(); onMenuToggle(false) },
                        leadingIcon = { Icon(Icons.Default.Nightlight, null) },
                    )
                    DropdownMenuItem(
                        text = { Text("Ambient Mode") },
                        onClick = onAmbientClick,
                        leadingIcon = { Icon(Icons.Default.NightsStay, null) },
                    )
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
) {
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
            .clip(ShapeCache.smooth24),
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
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        artist,
        style = MaterialTheme.typography.bodyLarge,
        color = Color.White.copy(alpha = 0.7f),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
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
        inactiveColor = Color.White.copy(alpha = 0.25f),
        onSeek = onSeek,
        modifier = Modifier.fillMaxWidth(),
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            formatDuration(currentPosition),
            style = MaterialTheme.typography.labelSmall,
            color = accentColor.copy(alpha = 0.8f),
        )
        Text(
            if (duration > 0) formatDuration(duration) else "--:--",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.5f),
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
        IconButtonWithPressAnimation(
            onClick = onSkipPrevious,
            icon = {
                Icon(
                    Icons.Default.SkipPrevious, "Previous",
                    modifier = Modifier.size(28.dp),
                    tint = Color.White,
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
                    Icons.Default.SkipNext, "Next",
                    modifier = Modifier.size(28.dp),
                    tint = Color.White,
                )
            },
            size = 48.dp,
        )
    }
}

/** Secondary controls row: Shuffle, Repeat, Favorite — in darker pill */
@Composable
private fun PixelSecondaryControls(
    shuffleMode: Boolean,
    repeatMode: Int,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    pillSurfaceDark: Color,
    accentColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .clip(ShapeCache.smoothPill)
            .background(pillSurfaceDark)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButtonWithPressAnimation(
            onClick = onToggleShuffle,
            tint = if (shuffleMode) accentColor else Color.White.copy(alpha = 0.6f),
            icon = {
                Icon(Icons.Default.Shuffle, "Shuffle", modifier = Modifier.size(22.dp))
            },
        )
        IconButtonWithPressAnimation(
            onClick = onCycleRepeatMode,
            tint = if (repeatMode > 0) accentColor else Color.White.copy(alpha = 0.6f),
            icon = {
                Icon(
                    if (repeatMode == 2) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    when (repeatMode) {
                        0 -> "Repeat off"; 1 -> "Repeat all"; else -> "Repeat one"
                    },
                    modifier = Modifier.size(22.dp),
                )
            },
        )
        IconButtonWithPressAnimation(
            onClick = { /* TODO: Favorite via Jellyfin API */ },
            tint = Color.White.copy(alpha = 0.6f),
            icon = {
                Icon(Icons.Default.FavoriteBorder, "Favorite", modifier = Modifier.size(22.dp))
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
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.88f else 1f,
        animationSpec = spring(stiffness = 500f),
        label = "pixelPlayScale",
    )

    // Light tinted background (Pixel Player uses a cream/light pink)
    val buttonBg = accentColor.copy(alpha = 0.25f).let { c ->
        Color(
            red = (c.red + 0.6f).coerceAtMost(1f),
            green = (c.green + 0.55f).coerceAtMost(1f),
            blue = (c.blue + 0.6f).coerceAtMost(1f),
            alpha = 0.9f,
        )
    }

    Box(
        modifier = Modifier
            .size(64.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(ShapeCache.smooth20)
            .background(buttonBg)
            .tvFocusable()
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            if (isPlaying) "Pause" else "Play",
            modifier = Modifier.size(36.dp),
            tint = Color(0xFF1A1A1A),
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

