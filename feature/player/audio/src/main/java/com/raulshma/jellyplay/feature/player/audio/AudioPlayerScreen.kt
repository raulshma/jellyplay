package com.raulshma.jellyplay.feature.player.audio

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import com.raulshma.jellyplay.core.designsystem.theme.AlphaEasing
import com.raulshma.jellyplay.core.designsystem.theme.PointToPointEasing
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.animation.AnimationTokens
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var showLyricsSearch by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }

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
        if (showQueue || showSpeedPicker || showEqualizer || showLyricsSearch) {
            showQueue = false
            showSpeedPicker = false
            showEqualizer = false
            showLyricsSearch = false
        } else if (showLyrics) {
            showLyrics = false
        } else {
            onBack()
        }
    }

    val preferences by viewModel.preferences.collectAsStateWithLifecycle()

    val adaptiveInfo = com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo.current
    val isExpanded = adaptiveInfo.windowSizeClass == com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass.Expanded

    val artworkColors = rememberArtworkColors(viewModel.albumArtUrl.ifBlank { null })
    val tintedBg = remember(artworkColors) { artworkColors?.tintedBackground ?: Color(0xFF2D1F2D) }
    val tintedBgLight = remember(artworkColors) { artworkColors?.tintedBackgroundLight ?: Color(0xFF3D2F3D) }
    val accentColor = remember(artworkColors) { artworkColors?.accentColor ?: Color(0xFFE8B4C8) }
    val pillSurface = remember(artworkColors) { artworkColors?.pillSurface ?: Color(0xFF3A2A3A).copy(alpha = 0.55f) }
    val pillSurfaceDark = remember(artworkColors) { artworkColors?.pillSurfaceDark ?: Color(0xFF2A1A2A).copy(alpha = 0.7f) }

    ArtworkThemeWrapper(
        imageUrl = viewModel.albumArtUrl.ifBlank { null },
        dynamicTheming = preferences.dynamicTheming,
        oledMode = preferences.oledMode,
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
                    hasLyrics = true,
                    lyricsVisible = showLyrics,
                    onLyricsClick = { showLyrics = !showLyrics },
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
                    sleepTimerActive = viewModel.sleepTimerActive,
                    sleepTimerDisplayText = if (viewModel.sleepTimerEndOfEpisode) "End of episode" else formatDuration(viewModel.sleepTimerRemainingMs),
                    onSleepTimerClick = { showMenu = false; showSleepTimer = true },
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
                                lyricsVisible = showLyrics,
                                lyrics = viewModel.lyrics,
                                currentLyricIndex = viewModel.currentLyricIndex,
                                isFetchingLyrics = viewModel.isFetchingLyrics,
                                lyricsSource = viewModel.lyricsSource,
                                onSearchClick = { showLyricsSearch = true },
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
                        lyricsVisible = showLyrics,
                        lyrics = viewModel.lyrics,
                        currentLyricIndex = viewModel.currentLyricIndex,
                        isFetchingLyrics = viewModel.isFetchingLyrics,
                        lyricsSource = viewModel.lyricsSource,
                        onSearchClick = { showLyricsSearch = true },
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

    if (showLyricsSearch) {
        LyricsSearchSheet(
            artist = viewModel.artist,
            title = viewModel.title,
            searchResults = viewModel.lyricsSearchResults,
            isSearching = viewModel.isSearchingLyrics,
            onSearch = { viewModel.searchLyrics(it) },
            onApplyTrack = { viewModel.applyLyrics(it) },
            onDismiss = { showLyricsSearch = false; viewModel.clearLyricsSearch() },
        )
    }

    if (showSleepTimer) {
        AudioSleepTimerSheet(
            isActive = viewModel.sleepTimerActive,
            isEndOfEpisodeMode = viewModel.sleepTimerEndOfEpisode,
            remainingMs = viewModel.sleepTimerRemainingMs,
            lastUsedDurationMs = viewModel.sleepTimerLastUsedDurationMs,
            onSelectDuration = { viewModel.startSleepTimer(it) },
            onSelectEndOfEpisode = { viewModel.startSleepTimerEndOfEpisode() },
            onCancel = { viewModel.cancelSleepTimer() },
            onDismiss = { showSleepTimer = false },
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
private fun LyricsSearchSheet(
    artist: String,
    title: String,
    searchResults: List<com.raulshma.jellyplay.core.model.LrcLibTrack>,
    isSearching: Boolean,
    onSearch: (String) -> Unit,
    onApplyTrack: (com.raulshma.jellyplay.core.model.LrcLibTrack) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf(if (artist.isNotBlank()) "$artist - $title" else title) }

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
            Text("Find Lyrics", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search artist, song...") },
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, "Clear", modifier = Modifier.size(18.dp))
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = { onSearch(searchQuery) },
                    enabled = searchQuery.isNotBlank() && !isSearching,
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Search")
                    }
                }
            }

            if (searchResults.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
                ) {
                    items(searchResults.size, contentType = { "searchResult" }) { index ->
                        val track = searchResults[index]
                        Card(
                            onClick = { onApplyTrack(track) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        track.trackName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        track.artistName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                if (track.hasSyncedLyrics) {
                                    SuggestionChip(
                                        onClick = {},
                                        label = {
                                            Text("Synced", style = MaterialTheme.typography.labelSmall)
                                        },
                                        modifier = Modifier.height(24.dp),
                                    )
                                } else if (track.hasPlainLyrics) {
                                    Text(
                                        "Plain",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricsOverlay(
    lyrics: List<com.raulshma.jellyplay.core.model.LyricsLine>,
    currentIndex: Int,
    isFetching: Boolean,
    lyricsSource: com.raulshma.jellyplay.core.model.LyricsSource,
    onSearchClick: () -> Unit,
) {
    val overlayAlpha by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(400),
        label = "lyricsOverlayAlpha",
    )
    val listState = rememberLazyListState()
    val hasSyncedLyrics = lyrics.any { it.timeMs > 0 }
    val scrimBrush = remember {
        Brush.verticalGradient(
            colors = listOf(
                Color.Black.copy(alpha = 0.1f),
                Color.Black.copy(alpha = 0.7f),
                Color.Black.copy(alpha = 0.85f),
                Color.Black.copy(alpha = 0.7f),
                Color.Black.copy(alpha = 0.1f),
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = overlayAlpha }
            .background(scrimBrush)
            .clip(ShapeCache.smooth24),
        contentAlignment = Alignment.Center,
    ) {
        if (isFetching) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    strokeWidth = 2.dp,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Finding lyrics...",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
        } else if (lyrics.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                Icon(
                    Icons.Outlined.MusicNote,
                    null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "No lyrics found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onSearchClick,
                    modifier = Modifier.height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Search", style = MaterialTheme.typography.labelMedium)
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    contentPadding = PaddingValues(vertical = 200.dp),
                    userScrollEnabled = false,
                ) {
                    items(lyrics.size, contentType = { "lyricsLine" }) { index ->
                        val isCurrent = index == currentIndex && hasSyncedLyrics
                        val distance = if (currentIndex >= 0) kotlin.math.abs(index - currentIndex) else 99
                        val targetAlpha = when {
                            isCurrent -> 1f
                            distance == 1 -> 0.5f
                            distance == 2 -> 0.3f
                            else -> 0.15f
                        }
                        val targetScale = if (isCurrent) 1.08f else 1f
                        val isNearActive = distance <= 3
                        val animatedAlpha by animateFloatAsState(
                            targetValue = targetAlpha,
                            animationSpec = tween(
                                durationMillis = AnimationTokens.MediumDuration,
                                easing = AlphaEasing,
                            ),
                            label = "lyricAlpha$index",
                        )
                        val animatedScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = tween(
                                durationMillis = AnimationTokens.MediumDuration,
                                easing = PointToPointEasing,
                            ),
                            label = "lyricScale$index",
                        )
                        val finalAlpha = if (isNearActive) animatedAlpha else targetAlpha
                        val finalScale = if (isNearActive) animatedScale else targetScale
                        Text(
                            text = lyrics[index].text.ifBlank { "\u266A" },
                            style = if (isCurrent) {
                                MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                )
                            } else {
                                MaterialTheme.typography.bodyMedium
                            },
                            color = Color.White.copy(alpha = finalAlpha),
                            modifier = Modifier
                                .animateContentSize(animationSpec = tween(AnimationTokens.MediumDuration))
                                .graphicsLayer {
                                    scaleX = finalScale
                                    scaleY = finalScale
                                    this.alpha = finalAlpha
                                }
                                .padding(vertical = 6.dp, horizontal = 20.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.9f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(alpha = 0.9f),
                                ),
                                startY = 0f,
                                endY = Float.POSITIVE_INFINITY,
                            )
                        ),
                )

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (lyricsSource != com.raulshma.jellyplay.core.model.LyricsSource.UNKNOWN) {
                        val sourceLabel = when (lyricsSource) {
                            com.raulshma.jellyplay.core.model.LyricsSource.LRCLIB -> "lrclib"
                            com.raulshma.jellyplay.core.model.LyricsSource.EXTERNAL -> "Jellyfin"
                            com.raulshma.jellyplay.core.model.LyricsSource.EMBEDDED -> "Embedded"
                            com.raulshma.jellyplay.core.model.LyricsSource.LRC_FILE -> "LRC"
                            else -> ""
                        }
                        if (sourceLabel.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.4f),
                                modifier = Modifier.height(20.dp),
                            ) {
                                Text(
                                    sourceLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                    }
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .size(28.dp)
                            .background(
                                Color.Black.copy(alpha = 0.4f),
                                RoundedCornerShape(8.dp),
                            ),
                    ) {
                        Icon(
                            Icons.Default.Search,
                            "Search lyrics",
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && lyrics.isNotEmpty()) {
            val targetIndex = currentIndex.coerceAtMost(lyrics.lastIndex)
            listState.animateScrollToItem(
                index = targetIndex,
                scrollOffset = 0,
            )
        }
    }
}




@Composable
private fun PixelPlayerTopBar(
    onBack: () -> Unit,
    hasLyrics: Boolean,
    lyricsVisible: Boolean,
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
    sleepTimerActive: Boolean = false,
    sleepTimerDisplayText: String = "",
    onSleepTimerClick: () -> Unit = {},
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
                    Icon(
                        if (lyricsVisible) Icons.Filled.Lyrics else Icons.Default.Mic,
                        "Lyrics",
                        tint = if (lyricsVisible) Color(0xFFE8B4C8) else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
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
                    DropdownMenuItem(
                        text = { Text(if (sleepTimerActive) "Sleep Timer · $sleepTimerDisplayText" else "Sleep Timer") },
                        onClick = onSleepTimerClick,
                        leadingIcon = { Icon(Icons.Default.Timer, null) },
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
    lyricsVisible: Boolean = false,
    lyrics: List<com.raulshma.jellyplay.core.model.LyricsLine> = emptyList(),
    currentLyricIndex: Int = -1,
    isFetchingLyrics: Boolean = false,
    lyricsSource: com.raulshma.jellyplay.core.model.LyricsSource = com.raulshma.jellyplay.core.model.LyricsSource.UNKNOWN,
    onSearchClick: () -> Unit = {},
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

        AnimatedVisibility(
            visible = lyricsVisible,
            enter = fadeIn(tween(400)),
            exit = fadeOut(tween(300)),
        ) {
            LyricsOverlay(
                lyrics = lyrics,
                currentIndex = currentLyricIndex,
                isFetching = isFetchingLyrics,
                lyricsSource = lyricsSource,
                onSearchClick = onSearchClick,
            )
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

private val SLEEP_TIMER_PRESETS = listOf(
    15 * 60 * 1000L,
    30 * 60 * 1000L,
    45 * 60 * 1000L,
    60 * 60 * 1000L,
    90 * 60 * 1000L,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioSleepTimerSheet(
    isActive: Boolean,
    isEndOfEpisodeMode: Boolean,
    remainingMs: Long,
    lastUsedDurationMs: Long,
    onSelectDuration: (Long) -> Unit,
    onSelectEndOfEpisode: () -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
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
                "Sleep Timer",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            )
            Spacer(Modifier.height(20.dp))

            if (isActive) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            ShapeCache.smoothPill,
                        )
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (isEndOfEpisodeMode) "End of episode" else formatDuration(remainingMs),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Cancel",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.clickable { onCancel() },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }

            Text(
                "Duration",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SLEEP_TIMER_PRESETS.forEach { durationMs ->
                    val isSelected = !isEndOfEpisodeMode && isActive && remainingMs == durationMs
                    val isLastUsed = !isActive && durationMs == lastUsedDurationMs
                    val minutes = durationMs / (60 * 1000)
                    val label = if (minutes % 60L == 0L) "${minutes / 60}h" else "${minutes}m"
                    androidx.compose.material3.FilterChip(
                        selected = isSelected || isLastUsed,
                        onClick = { onSelectDuration(durationMs); onDismiss() },
                        label = { Text(label) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            val isEndSelected = isActive && isEndOfEpisodeMode
            androidx.compose.material3.FilterChip(
                selected = isEndSelected,
                onClick = { onSelectEndOfEpisode(); onDismiss() },
                label = { Text("End of episode") },
                modifier = Modifier.fillMaxWidth(),
            )
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

