package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun SeasonsSection(
    seriesItem: MediaItem,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    fetchedSeasonIds: Set<String>,
    smartPlayTarget: DetailUiState.SmartPlayTarget?,
    getImageUrl: (String) -> String,
    currentItemId: String? = null,
    currentSeasonId: String? = null,
    onEpisodePlayClick: (MediaItem) -> Unit,
    onEpisodeDetailClick: (MediaItem) -> Unit,
    onSeasonSelected: (seasonId: String) -> Unit = {},
    hideEpisodeThumbnails: Boolean = false,
) {
    val smartTargetSeasonId = smartPlayTarget?.episode?.seasonId
    val initialSeasonIndex = when {
        smartTargetSeasonId != null -> {
            seasons.indexOfFirst { it.id == smartTargetSeasonId }.coerceAtLeast(0)
        }
        currentSeasonId != null -> {
            seasons.indexOfFirst { it.id == currentSeasonId }.coerceAtLeast(0)
        }
        else -> 0
    }
    var selectedSeasonIndex by remember { mutableStateOf(initialSeasonIndex) }

    LaunchedEffect(selectedSeasonIndex) {
        val season = seasons.getOrNull(selectedSeasonIndex)
        if (season != null) {
            onSeasonSelected(season.id)
        }
    }

    Column {
        FadingItem {
            Text(
                text = "Seasons",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(16.dp))

        TvFocusableItemRow(
            items = seasons,
            key = { it.id },
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) { index, season, focusModifier ->
                val isSelected = index == selectedSeasonIndex
                val targetColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                val targetContentColor = if (isSelected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface
                val surfaceColor by animateColorAsState(
                    targetValue = targetColor,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "seasonColor",
                )
                val contentColor by animateColorAsState(
                    targetValue = targetContentColor,
                    animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    label = "seasonContentColor",
                )
                val seasonTabFocusState = rememberTvFocusState(focusedScale = 1.05f)
                FadingItem {
                    Surface(
                        modifier = focusModifier
                            .clip(ShapeCache.smooth16)
                            .then(seasonTabFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(seasonTabFocusState, ShapeCache.smooth16))
                            .clickable { selectedSeasonIndex = index },
                        color = surfaceColor,
                        contentColor = contentColor,
                    ) {
                        Text(
                            text = season.name ?: "Season ${season.indexNumber ?: index + 1}",
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
        }

        Spacer(Modifier.height(20.dp))

        val selectedSeason = seasons.getOrNull(selectedSeasonIndex)
        val seasonEpisodes = selectedSeason?.let { episodes[it.id] }
        val isFetched = selectedSeason?.id?.let { fetchedSeasonIds.contains(it) } ?: false
        val isLoading = seasonEpisodes == null && selectedSeason != null && !isFetched

        val defaultEffectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val fastEffectsSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
        AnimatedContent(
            targetState = selectedSeasonIndex to (seasonEpisodes?.size ?: 0),
            transitionSpec = {
                fadeIn(
                    animationSpec = tween(400),
                ) togetherWith fadeOut(
                    animationSpec = tween(300),
                )
            },
            label = "seasonEpisodes",
        ) { (seasonIdx, episodeCount) ->
            val currentEpisodes = seasons.getOrNull(seasonIdx)?.let { episodes[it.id] }
            val currentIsFetched = seasons.getOrNull(seasonIdx)?.id?.let { fetchedSeasonIds.contains(it) } ?: false
            val currentIsLoading = currentEpisodes == null && seasons.getOrNull(seasonIdx) != null && !currentIsFetched

            when {
                currentIsLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FadingItem {
                            JellyPlayLoadingIndicator()
                        }
                    }
                }
                currentEpisodes != null && currentEpisodes.isNotEmpty() -> {
                    TvFocusableItemRow(
                        items = currentEpisodes,
                        key = { "episode_${it.id}" },
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) { _, episode, focusModifier ->
                            FadingItem {
                                EpisodeCard(
                                    episode = episode,
                                    getImageUrl = getImageUrl,
                                    isCurrentEpisode = episode.id == currentItemId,
                                    onPlayClick = { onEpisodePlayClick(episode) },
                                    onDetailClick = { onEpisodeDetailClick(episode) },
                                    modifier = focusModifier,
                                    hideThumbnail = hideEpisodeThumbnails,
                                )
                            }
                    }
                }
                else -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        FadingItem {
                            Text("No episodes available", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EpisodeCardSkeleton() {
    JellyPlayLoadingIndicator()
}

@Composable
internal fun EpisodeCard(
    episode: MediaItem,
    getImageUrl: (String) -> String,
    isCurrentEpisode: Boolean = false,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit,
    modifier: Modifier = Modifier,
    hideThumbnail: Boolean = false,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "episodeCardScale",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "episodePlayScale",
    )

    val cardFocusState = rememberTvFocusState(focusedScale = 1.03f)

    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current
    val borderModifier = when {
        isSynthwave -> Modifier.border(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary
                )
            ),
            shape = ShapeCache.smooth16
        )
        isSoothing -> Modifier.border(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            shape = ShapeCache.smooth16
        )
        else -> Modifier
    }

    Column(
        modifier = modifier
            .width(280.dp)
            .then(borderModifier)
            .clip(ShapeCache.smooth16)
            .background(
                if (isCurrentEpisode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
            )
            .then(
                if (isCurrentEpisode) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                else Modifier
            )
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .then(cardFocusState.focusModifier)
            .then(Modifier.tvFocusIndicator(cardFocusState, ShapeCache.smooth16))
            .clickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = onDetailClick,
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (!hideThumbnail) {
                MediaImage(
                    url = getImageUrl(episode.id),
                    contentDescription = episode.name,
                    blurHash = episode.blurHashes.primary,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)))
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Spoiler",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val epPlayFocusState = rememberTvFocusState(focusedScale = 1.15f)
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                    .then(epPlayFocusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(epPlayFocusState, CircleShape))
                    .clickable(
                        interactionSource = playInteractionSource,
                        indication = null,
                        onClick = onPlayClick,
                    )
                    .padding(8.dp)
            )

            val positionTicks = episode.playbackPositionTicks
            if (positionTicks != null && positionTicks > 0) {
                val progress = episode.progressFraction() ?: 0f
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
            val cardPrefs = com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences.current
            if (episode.isPlayed && (positionTicks == null || positionTicks <= 0) && cardPrefs.showWatchedCheckmark) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(ShapeCache.smooth4)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = "Watched",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.9f),
                    )
                }
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = buildString {
                    episode.indexNumber?.let { append("$it. ") }
                    append(episode.name)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val runtimeTicks = episode.runTimeTicks
            val positionTicks = episode.playbackPositionTicks
            val hasWatchProgress = positionTicks != null && positionTicks > 0 && !episode.isPlayed
            val remainingTime = if (hasWatchProgress && runtimeTicks != null && positionTicks != null) {
                formatRemainingTimeFromTicks(runtimeTicks, positionTicks)
            } else null
            val totalTime = if (runtimeTicks != null) {
                formatDurationFromTicks(runtimeTicks)
            } else null
            
            if (remainingTime != null && totalTime != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = "$remainingTime left",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = totalTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (totalTime != null) {
                Text(
                    text = totalTime,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp)
                )
            }
        }
    }
}
