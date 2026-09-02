package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.defaultContentSizeSpec
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.player.audio.generated.resources.Res
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_adjust_timing
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_finding
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_karaoke_off
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_karaoke_on
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_none_found
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_offset
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_search
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_lyrics_source_embedded
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_reset
import com.raulshma.jellyplay.feature.player.audio.generated.resources.audio_search

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LyricsOverlay(
    lyrics: List<com.raulshma.jellyplay.core.model.LyricsLine>,
    currentIndex: Int,
    isFetching: Boolean,
    lyricsSource: com.raulshma.jellyplay.core.model.LyricsSource,
    onSearchClick: () -> Unit,
    karaokeMode: Boolean = false,
    onKaraokeToggle: (Boolean) -> Unit = {},
    lyricsOffsetMs: Long = com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.DEFAULT_OFFSET_MS,
    onLyricsOffsetChange: (Long) -> Unit = {},
) {
    val listState = rememberLazyListState()
    val searchFocusState = rememberTvFocusState(focusedScale = 1.05f)
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
            .background(scrimBrush)
            .clip(ShapeCache.smooth24),
        contentAlignment = Alignment.Center,
    ) {
        if (isFetching) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                JellyPlayLoadingIndicator(
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.audio_lyrics_finding),
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
                    Tabler.Outline.Music,
                    null,
                    modifier = Modifier.size(36.dp),
                    tint = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.audio_lyrics_none_found),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = onSearchClick,
                    modifier = Modifier
                        .then(searchFocusState.focusModifier)
                        .tvFocusIndicator(searchFocusState, ShapeCache.smooth12)
                        .height(32.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                ) {
                    Icon(
                        Tabler.Outline.Search,
                        null,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(Res.string.audio_search), style = MaterialTheme.typography.labelMedium)
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
                    items(lyrics.size, key = { it }, contentType = { "lyricsLine" }) { index ->
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
                            animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            label = "lyricAlpha$index",
                        )
                        val animatedScale by animateFloatAsState(
                            targetValue = targetScale,
                            animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
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
                                .animateContentSize(animationSpec = defaultContentSizeSpec())
                                .graphicsLayer {
                                    scaleX = finalScale
                                    scaleY = finalScale
                                    this.alpha = finalAlpha
                                }
                                .padding(vertical = 6.dp, horizontal = 20.dp),
                            textAlign = TextAlign.Center,
                            // Let long lines wrap instead of being cut off with an
                            // ellipsis ("…") — the full lyric must stay readable.
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

                var showOffsetSlider by remember { mutableStateOf(false) }
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.End,
                ) {
                    // Lyrics timing offset adjustment. Only
                    // meaningful for time-synced lyrics.
                    if (hasSyncedLyrics) {
                        AnimatedVisibility(
                            visible = showOffsetSlider,
                            enter = fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()) + expandVertically(),
                            exit = fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()) + shrinkVertically(),
                        ) {
                            Surface(
                                shape = ShapeCache.smooth8,
                                color = Color.Black.copy(alpha = 0.6f),
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .width(220.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text(
                                            stringResource(Res.string.audio_lyrics_offset),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.8f),
                                        )
                                        Text(
                                            "${lyricsOffsetMs}ms",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (lyricsOffsetMs == 0L)
                                                Color.White.copy(alpha = 0.6f)
                                            else MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    Slider(
                                        value = lyricsOffsetMs.toFloat(),
                                        onValueChange = { onLyricsOffsetChange(it.toLong()) },
                                        valueRange =
                                            com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.MIN_OFFSET_MS.toFloat()..
                                                com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.MAX_OFFSET_MS.toFloat(),
                                        colors = SliderDefaults.colors(
                                            thumbColor = MaterialTheme.colorScheme.primary,
                                            activeTrackColor = MaterialTheme.colorScheme.primary,
                                        ),
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                    ) {
                                        Text(
                                            stringResource(Res.string.audio_reset),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            modifier = Modifier.focusIndicator().clickable {
                                                onLyricsOffsetChange(
                                                    com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.DEFAULT_OFFSET_MS
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    if (lyricsSource != com.raulshma.jellyplay.core.model.LyricsSource.UNKNOWN) {
                        val embeddedLabel = stringResource(Res.string.audio_lyrics_source_embedded)
                        val sourceLabel = when (lyricsSource) {
                            com.raulshma.jellyplay.core.model.LyricsSource.LRCLIB -> "lrclib"
                            com.raulshma.jellyplay.core.model.LyricsSource.EXTERNAL -> "Jellyfin"
                            com.raulshma.jellyplay.core.model.LyricsSource.EMBEDDED -> embeddedLabel
                            com.raulshma.jellyplay.core.model.LyricsSource.LRC_FILE -> "LRC"
                            else -> ""
                        }
                        if (sourceLabel.isNotBlank()) {
                            Surface(
                                shape = ShapeCache.smooth8,
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
                    if (lyrics.any { it.words.isNotEmpty() }) {
                        val karaokeFocus = rememberTvFocusState()
                        IconButton(
                            onClick = { onKaraokeToggle(!karaokeMode) },
                            modifier = Modifier
                                .size(28.dp)
                                .then(karaokeFocus.focusModifier)
                                .tvFocusIndicator(karaokeFocus, ShapeCache.smooth8)
                                .background(
                                    if (karaokeMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else Color.Black.copy(alpha = 0.4f),
                                    ShapeCache.smooth8,
                                ),
                        ) {
                            Icon(
                                if (karaokeMode) Tabler.Outline.Microphone2 else Tabler.Outline.Microphone,
                                if (karaokeMode) stringResource(Res.string.audio_lyrics_karaoke_on) else stringResource(Res.string.audio_lyrics_karaoke_off),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    if (hasSyncedLyrics) {
                        val offsetFocus = rememberTvFocusState()
                        IconButton(
                            onClick = { showOffsetSlider = !showOffsetSlider },
                            modifier = Modifier
                                .size(28.dp)
                                .then(offsetFocus.focusModifier)
                                .tvFocusIndicator(offsetFocus, ShapeCache.smooth8)
                                .background(
                                    if (lyricsOffsetMs != com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.DEFAULT_OFFSET_MS)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else Color.Black.copy(alpha = 0.4f),
                                    ShapeCache.smooth8,
                                ),
                        ) {
                            Icon(
                                Tabler.Outline.Adjustments,
                                stringResource(Res.string.audio_lyrics_adjust_timing),
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                        Spacer(Modifier.width(4.dp))
                    }
                    val searchFocus = rememberTvFocusState()
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .size(28.dp)
                            .then(searchFocus.focusModifier)
                            .tvFocusIndicator(searchFocus, ShapeCache.smooth8)
                            .background(
                                Color.Black.copy(alpha = 0.4f),
                                ShapeCache.smooth8,
                            ),
                    ) {
                        Icon(
                            Tabler.Outline.Search,
                            stringResource(Res.string.audio_lyrics_search),
                            modifier = Modifier.size(14.dp),
                            tint = Color.White.copy(alpha = 0.8f),
                        )
                    }
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
internal fun AlbumArtwork(
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
    karaokeMode: Boolean = false,
    currentPositionMs: LongState,
    lyricsOffsetMs: Long = com.raulshma.jellyplay.core.data.playback.AudioLyricsManager.DEFAULT_OFFSET_MS,
    onLyricsOffsetChange: (Long) -> Unit = {},
) {
    val sharedTransitionScope = com.raulshma.jellyplay.core.ui.components.LocalSharedTransitionScope.current
    val animatedVisibilityScope = com.raulshma.jellyplay.core.ui.components.LocalAnimatedVisibilityScope.current

    @OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                rememberSharedContentState(key = "audio_player_album_art"),
                animatedVisibilityScope = animatedVisibilityScope,
            )
        }
    } else Modifier

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
            .clip(ShapeCache.smooth24)
            .then(sharedModifier),
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
                    Tabler.Outline.Music,
                    contentDescription = null,
                    modifier = Modifier.size(72.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        AnimatedVisibility(
            visible = lyricsVisible,
            enter = fadeIn(MaterialTheme.motionScheme.slowEffectsSpec()),
            exit = fadeOut(MaterialTheme.motionScheme.defaultEffectsSpec()),
        ) {
            if (karaokeMode && lyrics.any { it.words.isNotEmpty() }) {
                com.raulshma.jellyplay.feature.player.audio.lyrics.KaraokeLyricsView(
                    lyrics = lyrics,
                    currentIndex = currentLyricIndex,
                    currentPositionMs = currentPositionMs,
                )
            } else {
                LyricsOverlay(
                    lyrics = lyrics,
                    currentIndex = currentLyricIndex,
                    isFetching = isFetchingLyrics,
                    lyricsSource = lyricsSource,
                    onSearchClick = onSearchClick,
                    lyricsOffsetMs = lyricsOffsetMs,
                    onLyricsOffsetChange = onLyricsOffsetChange,
                )
            }
        }

        if (title.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center,
            ) {
                JellyPlayLoadingIndicator(modifier = Modifier.size(48.dp))
            }
        }
    }
}

@Composable
internal fun SwipeTrackCard(
    title: String,
    artist: String,
    artworkUrl: String,
    isNext: Boolean,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = ShapeCache.smooth16,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f),
        ),
        modifier = modifier
            .width(260.dp)
            .height(80.dp)
            .shadow(12.dp, ShapeCache.smooth16),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isNext) {
                Icon(
                    Tabler.Outline.PlayerSkipForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
            } else {
                Icon(
                    Tabler.Outline.PlayerSkipBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(ShapeCache.smooth8)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                if (artworkUrl.isNotBlank()) {
                    MediaImage(
                        url = artworkUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Icon(
                        Tabler.Outline.Music,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
