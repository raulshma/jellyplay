package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.res.stringResource
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
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.rowCardWidth
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.preview.rememberMediaPeek
import com.raulshma.jellyplay.core.ui.preview.rememberReleaseDismiss
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.R

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
                text = stringResource(R.string.detail_section_seasons),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 24.dp),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(Modifier.height(16.dp))

        TvFocusableItemRow(
            items = seasons,
            key = { it.id },
            contentType = { _, _ -> "season" },
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
                            text = season.name ?: stringResource(R.string.detail_season_format, season.indexNumber ?: (index + 1)),
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
        // Capture in composable scope; AnimatedContent's transitionSpec is not composable.
        val seasonFadeIn = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        val seasonFadeOut = MaterialTheme.motionScheme.fastEffectsSpec<Float>()

        AnimatedContent(
            targetState = selectedSeasonIndex to (seasonEpisodes?.size ?: 0),
            transitionSpec = {
                fadeIn(
                    animationSpec = seasonFadeIn,
                ) togetherWith fadeOut(
                    animationSpec = seasonFadeOut,
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
                        contentType = { _, _ -> "episode" },
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
                            Text(stringResource(R.string.detail_no_episodes_available), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
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
    // Episode cards are wide (thumbnail + metadata), scaling ~1.5× the adaptive poster width.
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val cardWidth = (adaptiveInfo.rowCardWidth(isTv) * 1.5f).coerceAtLeast(260.dp)

    // Press-and-hold "peek" preview; no-op on TV / when no controller is wired.
    val peek = rememberMediaPeek(
        item = episode,
        posterUrl = getImageUrl(episode.id),
        backdropUrl = getImageUrl(episode.id),
        blurHash = episode.blurHashes.primary,
    )
    rememberReleaseDismiss(isCardPressed)

    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current
    // Read theme colors here (composable scope) so the remember block below
    // doesn't need to call composable functions.
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    // Depends only on the active theme, not on the per-episode data — wrap in
    // remember so the Modifier + gradient aren't rebuilt per card per recompose.
    val borderModifier = remember(isSynthwave, isSoothing, primaryColor, secondaryColor, outlineColor) {
        when {
            isSynthwave -> Modifier.border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(primaryColor, secondaryColor)
                ),
                shape = ShapeCache.smooth16
            )
            isSoothing -> Modifier.border(
                width = 0.8.dp,
                color = outlineColor.copy(alpha = 0.35f),
                shape = ShapeCache.smooth16
            )
            else -> Modifier
        }
    }

    Column(
        modifier = modifier
            .width(cardWidth)
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
            .then(peek.boundsModifier)
            .then(Modifier.tvFocusIndicator(cardFocusState, ShapeCache.smooth16))
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = onDetailClick,
                onLongClick = peek.onLongClick,
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
                    // Episode thumbnails render up to ~480 dp wide × 16:9. Decode a
                    // right-sized bitmap (4–8 cards compose simultaneously).
                    size = coil3.size.Size(640, 360),
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
                        text = stringResource(R.string.detail_spoiler),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val epPlayFocusState = rememberTvFocusState(focusedScale = 1.15f)
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = stringResource(R.string.detail_cd_episode_play),
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
                        text = stringResource(R.string.detail_watched_badge),
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
                        text = stringResource(R.string.detail_time_left_format, remainingTime),
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
