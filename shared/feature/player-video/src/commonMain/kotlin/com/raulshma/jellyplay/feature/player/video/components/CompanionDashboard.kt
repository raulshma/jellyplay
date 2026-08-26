package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.animation.AnimatedVisibility
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.request.ImageRequest
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.feature.player.video.rememberIsPortraitOrientation
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.LocalArtworkColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.MediaItem as JellyfinMediaItem
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_audio_stream
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cast_and_crew
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cast_connected_streaming
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_cast_connecting_device
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_companion
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_disconnect
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_episode_title
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_episodes
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_lyrics
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_lyrics_subtitles_tab
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_media_tracks
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_no_episodes_found
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_overview_tab
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_pause
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_play
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_poster
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_rotate_screen
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_seek_backward_10s
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_seek_forward_30s
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_selected
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_subtitles
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_synopsis
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_unknown
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_volume
























import com.raulshma.jellyplay.feature.player.video.TrackOption
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanionDashboard(
    title: String,
    subtitle: String,
    overview: String,
    people: List<PersonInfo>,
    lyricsLines: List<LyricsLine>,
    artworkUrl: String?,
    isPlaying: Boolean,
    // StateFlow rather than a collected Long so the cast position tick (multiple
    // times per second during playback) is collected *inside* the leaf consumers
    // (CompanionControlBar + SubtitlesTabContent) instead of at the dashboard
    // root. Threading a raw Long here forced the whole ~850-line composable
    // (header, poster, tabs, tab content) to recompose per tick; only the slider
    // + two time labels and the lyrics active-line index actually need it.
    castPositionFlow: StateFlow<Long>,
    durationMs: Long,
    volume: Float,
    isConnecting: Boolean,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    episodes: List<JellyfinMediaItem>,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onDisconnect: () -> Unit,
    onSelectAudioTrack: (TrackOption) -> Unit,
    onSelectSubtitleTrack: (TrackOption) -> Unit,
    onPlayEpisode: (String) -> Unit,
    getImageUrl: (String) -> String,
    onToggleOrientation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artworkColors = LocalArtworkColors.current
    val coroutineScope = rememberCoroutineScope()
    val isPortrait = rememberIsPortraitOrientation()

    // Smooth backdrop colors derived from artwork
    val dominantColor = artworkColors?.dominant ?: MaterialTheme.colorScheme.primaryContainer
    val darkMutedColor = artworkColors?.darkMuted ?: MaterialTheme.colorScheme.background

    val animatedBgStart by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "bgStart"
    )
    val animatedBgEnd by animateColorAsState(
        targetValue = darkMutedColor,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "bgEnd"
    )

    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        Res.string.player_video_overview_tab,
        Res.string.player_video_lyrics_subtitles_tab,
        Res.string.player_video_episodes,
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        animatedBgEnd,
                        animatedBgStart.copy(alpha = 0.35f),
                        animatedBgEnd
                    )
                )
            )
    ) {
        // Floating ambient color blobs
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
                .align(Alignment.TopCenter)
                .blur(80.dp)
                .graphicsLayer { alpha = 0.45f }
                .background(
                    Brush.radialGradient(
                        colors = listOf(dominantColor, Color.Transparent),
                        radius = 800f
                    )
                )
        )

        if (isPortrait) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // Header Bar
                item { CompanionHeaderRow(isConnecting = isConnecting, onToggleOrientation = onToggleOrientation, onDisconnect = onDisconnect) }

                // Poster & Metadata Stack
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        MediaImage(
                            url = artworkUrl.orEmpty(),
                            contentDescription = stringResource(Res.string.player_video_poster),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(width = 135.dp, height = 202.dp)
                                .clip(ShapeCache.smooth16)
                                .border(1.dp, Color.White.copy(alpha = 0.12f), ShapeCache.smooth16)
                        )

                        Spacer(Modifier.height(16.dp))

                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            color = Color.White
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                // Controls panel
                item {
                    Box(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        CompanionControlBar(
                            isPlaying = isPlaying,
                            castPositionFlow = castPositionFlow,
                            durationMs = durationMs,
                            volume = volume,
                            onPlayPause = onPlayPause,
                            onSeekBack = onSeekBack,
                            onSeekForward = onSeekForward,
                            onSeekTo = onSeekTo,
                            onVolumeChange = onVolumeChange
                        )
                    }
                }

                // Tabs
                item {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        divider = {},
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        tabs.forEachIndexed { index, name ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = {
                                    Text(
                                        stringResource(name),
                                        fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            )
                        }
                    }
                }

                // Tab content frame
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(ShapeCache.smooth24)
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f))
                            .border(1.dp, Color.White.copy(alpha = 0.08f), ShapeCache.smooth24)
                            .heightIn(min = 250.dp, max = 500.dp)
                    ) {
                        when (selectedTab) {
                            0 -> OverviewTabContent(overview = overview, people = people, getImageUrl = getImageUrl)
                            1 -> SubtitlesTabContent(
                                lyricsLines = lyricsLines,
                                castPositionFlow = castPositionFlow,
                                audioTracks = audioTracks,
                                subtitleTracks = subtitleTracks,
                                onSelectAudioTrack = onSelectAudioTrack,
                                onSelectSubtitleTrack = onSelectSubtitleTrack
                            )
                            2 -> EpisodesTabContent(episodes = episodes, onPlayEpisode = onPlayEpisode, getImageUrl = getImageUrl)
                        }
                    }
                }
            }
        } else {
            // Landscape layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                // Header Bar
                CompanionHeaderRow(isConnecting = isConnecting, onToggleOrientation = onToggleOrientation, onDisconnect = onDisconnect)

                // Main Details and Metadata View
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Media Poster Card
                    MediaImage(
                        url = artworkUrl.orEmpty(),
                        contentDescription = stringResource(Res.string.player_video_poster),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 90.dp, height = 135.dp)
                            .clip(ShapeCache.smooth16)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), ShapeCache.smooth16)
                    )

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White
                        )
                        if (subtitle.isNotBlank()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Tab Rows
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    divider = {},
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    tabs.forEachIndexed { index, name ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    stringResource(name),
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                // Tab Content Frame
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(ShapeCache.smooth24)
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.35f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), ShapeCache.smooth24)
                ) {
                    when (selectedTab) {
                        0 -> OverviewTabContent(overview = overview, people = people, getImageUrl = getImageUrl)
                        1 -> SubtitlesTabContent(
                            lyricsLines = lyricsLines,
                            castPositionFlow = castPositionFlow,
                            audioTracks = audioTracks,
                            subtitleTracks = subtitleTracks,
                            onSelectAudioTrack = onSelectAudioTrack,
                            onSelectSubtitleTrack = onSelectSubtitleTrack
                        )
                        2 -> EpisodesTabContent(episodes = episodes, onPlayEpisode = onPlayEpisode, getImageUrl = getImageUrl)
                    }
                }

                // Playback Controls Panel
                CompanionControlBar(
                    isPlaying = isPlaying,
                    castPositionFlow = castPositionFlow,
                    durationMs = durationMs,
                    volume = volume,
                    onPlayPause = onPlayPause,
                    onSeekBack = onSeekBack,
                    onSeekForward = onSeekForward,
                    onSeekTo = onSeekTo,
                    onVolumeChange = onVolumeChange
                )
            }
        }
    }
}

@Composable
fun OverviewTabContent(
    overview: String,
    people: List<PersonInfo>,
    getImageUrl: (String) -> String,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (overview.isNotBlank()) {
            item {
                Column {
                    Text(
                        text = stringResource(Res.string.player_video_synopsis),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = overview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 22.sp
                    )
                }
            }
        }

        if (people.isNotEmpty()) {
            item {
                Column {
                    Text(
                        text = stringResource(Res.string.player_video_cast_and_crew),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(people, key = { it.id }, contentType = { "person" }) { person ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(72.dp)
                            ) {
                                val avatarUrl = remember(person.id) { getImageUrl(person.id) }
                                MediaImage(
                                    url = avatarUrl,
                                    contentDescription = person.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape),
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    text = person.name,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    color = Color.White
                                )
                                person.role?.let { role ->
                                    Text(
                                        text = role,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
fun SubtitlesTabContent(
    lyricsLines: List<LyricsLine>,
    castPositionFlow: StateFlow<Long>,
    audioTracks: List<TrackOption>,
    subtitleTracks: List<TrackOption>,
    onSelectAudioTrack: (TrackOption) -> Unit,
    onSelectSubtitleTrack: (TrackOption) -> Unit,
) {
    // Collect the cast position tick at this leaf — the derivedStateOf below
    // already gates recomposition to active-line crossings, but receiving the
    // raw Long from the dashboard root would still force the dashboard itself
    // to recompose on every tick.
    val currentPositionMs by castPositionFlow.collectAsStateWithLifecycle()
    var subTabSelected by remember { mutableIntStateOf(if (lyricsLines.isNotEmpty()) 0 else 1) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (lyricsLines.isNotEmpty()) {
            TabRow(
                selectedTabIndex = subTabSelected,
                containerColor = Color.Transparent,
                divider = {},
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Tab(
                    selected = subTabSelected == 0,
                    onClick = { subTabSelected = 0 },
                    text = { Text(stringResource(Res.string.player_video_lyrics)) }
                )
                Tab(
                    selected = subTabSelected == 1,
                    onClick = { subTabSelected = 1 },
                    text = { Text(stringResource(Res.string.player_video_media_tracks)) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            if (lyricsLines.isNotEmpty() && subTabSelected == 0) {
                // Real-time scrolling lyrics. derivedStateOf ensures recomposition fires
                // only when the active line index actually crosses a lyric boundary —
                // currentPositionMs ticks multiple times per second but most ticks do
                // not change the active line.
                val activeLineIndex by remember {
                    derivedStateOf {
                        lyricsLines.indexOfLast { it.timeMs <= currentPositionMs }.coerceAtLeast(0)
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 40.dp, horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Keyed by index — stable per line so slot identity survives any
                    // re-emission of the same lyrics list (e.g. a parent recomposition).
                    itemsIndexed(lyricsLines, key = { idx, _ -> "lyric_$idx" }, contentType = { _, _ -> "lyric" }) { index, line ->
                        val isActive = index == activeLineIndex
                        val textColor by animateColorAsState(
                            targetValue = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                            label = "lyricColor"
                        )
                        val scale by animateFloatAsState(
                            targetValue = if (isActive) 1.15f else 1.0f,
                            label = "lyricScale"
                        )

                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                            color = textColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .fillMaxWidth()
                        )
                    }
                }
            } else {
                // Subtitle and Audio selectors
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (audioTracks.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(Res.string.player_video_audio_stream),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            TrackSelectorGroup(tracks = audioTracks, onSelect = onSelectAudioTrack)
                        }
                    }

                    if (subtitleTracks.isNotEmpty()) {
                        item {
                            Text(
                                stringResource(Res.string.player_video_subtitles),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.height(8.dp))
                            TrackSelectorGroup(tracks = subtitleTracks, onSelect = onSelectSubtitleTrack)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TrackSelectorGroup(
    tracks: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tracks.forEach { track ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(ShapeCache.smooth12)
                    .background(
                        if (track.isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                        else Color.Transparent
                    )
                    .clickable { onSelect(track) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = track.label.ifBlank { stringResource(Res.string.player_video_unknown) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (track.isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (track.isSelected) MaterialTheme.colorScheme.primary else Color.White
                    )
                    track.language?.let { lang ->
                        Text(
                            text = lang.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (track.isSelected) {
                    Icon(
                        imageVector = Tabler.Outline.Check,
                        contentDescription = stringResource(Res.string.player_video_selected),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EpisodesTabContent(
    episodes: List<JellyfinMediaItem>,
    onPlayEpisode: (String) -> Unit,
    getImageUrl: (String) -> String,
) {
    if (episodes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(Res.string.player_video_no_episodes_found),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(episodes, key = { it.id }, contentType = { "episode" }) { episode ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(ShapeCache.smooth12)
                        .clickable { onPlayEpisode(episode.id) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val thumbUrl = remember(episode.id) { getImageUrl(episode.id) }
                    MediaImage(
                        url = thumbUrl,
                        contentDescription = episode.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 80.dp, height = 50.dp)
                            .clip(ShapeCache.smooth8)
                    )

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(Res.string.player_video_episode_title, episode.episodeNumber ?: 0, episode.name),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        episode.overview?.let { overview ->
                            Text(
                                text = overview,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CompanionControlBar(
    isPlaying: Boolean,
    castPositionFlow: StateFlow<Long>,
    durationMs: Long,
    volume: Float,
    onPlayPause: () -> Unit,
    onSeekBack: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onVolumeChange: (Float) -> Unit,
) {
    // Collect the high-frequency cast position tick here, at the leaf that
    // actually renders the slider + time labels, instead of receiving a
    // pre-collected Long from the dashboard root. This confines the per-tick
    // recomposition to CompanionControlBar (and the lyrics tab via its own
    // derivedStateOf) and stops the entire CompanionDashboard tree from
    // recomposing on every position update.
    val currentPositionMs by castPositionFlow.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth32)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), ShapeCache.smooth32)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Timeline & Slider
        val formattedPosition = remember(currentPositionMs) { formatDuration(currentPositionMs) }
        val formattedDuration = remember(durationMs) { formatDuration(durationMs) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formattedPosition, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formattedDuration, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Slider(
            value = if (durationMs > 0) currentPositionMs.toFloat() else 0f,
            valueRange = 0f..(if (durationMs > 0) durationMs.toFloat() else 1f),
            onValueChange = { pos -> onSeekTo(pos.toLong()) },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f)
            ),
            modifier = Modifier.fillMaxWidth()
        )

        // Buttons Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Volume controls
            var localVolume by remember(volume) { mutableStateOf(volume) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.width(100.dp)
            ) {
                Icon(
                    imageVector = if (localVolume <= 0.01f) Tabler.Outline.Volume3 else Tabler.Outline.Volume,
                    contentDescription = stringResource(Res.string.player_video_volume),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(6.dp))
                Slider(
                    value = localVolume,
                    onValueChange = {
                        localVolume = it
                        onVolumeChange(it)
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Seek Back
            val seekBackFocusState = rememberTvFocusState(focusedScale = 1.1f)
            IconButton(
                onClick = onSeekBack,
                modifier = Modifier
                    .then(seekBackFocusState.focusModifier)
                    .tvFocusIndicator(seekBackFocusState, CircleShape),
            ) {
                Icon(
                    imageVector = Tabler.Outline.PlayerSkipBack,
                    contentDescription = stringResource(Res.string.player_video_seek_backward_10s),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Play/Pause Floating Circle
            val playPauseFocusState = rememberTvFocusState(focusedScale = 1.08f)
            FloatingActionButton(
                onClick = onPlayPause,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(56.dp)
                    .then(playPauseFocusState.focusModifier)
                    .tvFocusIndicator(playPauseFocusState, CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Tabler.Outline.PlayerPause else Tabler.Outline.PlayerPlay,
                    contentDescription = if (isPlaying) stringResource(Res.string.player_video_pause) else stringResource(Res.string.player_video_play),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Seek Forward
            val seekForwardFocusState = rememberTvFocusState(focusedScale = 1.1f)
            IconButton(
                onClick = onSeekForward,
                modifier = Modifier
                    .then(seekForwardFocusState.focusModifier)
                    .tvFocusIndicator(seekForwardFocusState, CircleShape),
            ) {
                Icon(
                    imageVector = Tabler.Outline.PlayerSkipForward,
                    contentDescription = stringResource(Res.string.player_video_seek_forward_30s),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Dummy spacing to balance the volume row
            Spacer(modifier = Modifier.width(100.dp))
        }
    }
}

private fun formatDuration(durationMs: Long): String =
    com.raulshma.jellyplay.core.ui.components.formatDurationMs(durationMs)

/**
 * Top header bar of the Companion (cast) dashboard: cast icon + connection
 * status on the left, rotate-screen + disconnect buttons on the right. Shared
 * by the portrait (LazyColumn item) and landscape (Column) layouts, which
 * previously duplicated this block byte-for-byte apart from focus-state var
 * naming.
 */
@Composable
private fun CompanionHeaderRow(
    isConnecting: Boolean,
    onToggleOrientation: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Tabler.Outline.Cast,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(
                    text = if (isConnecting) stringResource(Res.string.player_video_cast_connecting_device) else stringResource(Res.string.player_video_cast_connected_streaming),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(Res.string.player_video_companion),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        val rotateFocusState = rememberTvFocusState(focusedScale = 1.1f)
        val disconnectFocusState = rememberTvFocusState(focusedScale = 1.05f)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleOrientation,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.size(36.dp)
                    .then(rotateFocusState.focusModifier)
                    .tvFocusIndicator(rotateFocusState, CircleShape)
            ) {
                Icon(
                    imageVector = Tabler.Outline.Rotate,
                    contentDescription = stringResource(Res.string.player_video_rotate_screen),
                    modifier = Modifier.size(18.dp)
                )
            }

            Button(
                onClick = onDisconnect,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                shape = ShapeCache.smoothPill,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                modifier = Modifier
                    .then(disconnectFocusState.focusModifier)
                    .tvFocusIndicator(disconnectFocusState, ShapeCache.smoothPill)
            ) {
                Icon(
                    imageVector = Tabler.Outline.X,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(Res.string.player_video_disconnect), style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
