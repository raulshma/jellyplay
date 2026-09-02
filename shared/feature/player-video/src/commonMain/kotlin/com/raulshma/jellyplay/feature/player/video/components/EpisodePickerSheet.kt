package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import com.raulshma.jellyplay.core.designsystem.theme.playerOnScrim
import com.raulshma.jellyplay.core.designsystem.theme.playerScrimColor
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.raulshma.jellyplay.core.ui.components.JellyPlayLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.components.PlayerModalBottomSheet
import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_episodes
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_no_episodes_available
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_now_playing
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_season_n
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_time_left
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_watched






import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.ifElse
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.core.ui.tv.tvFocusExitHandler
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun EpisodePickerSheet(
    seasons: List<MediaItem>,
    episodes: List<MediaItem>,
    currentSeasonId: String?,
    currentEpisodeId: String?,
    isLoading: Boolean,
    onSeasonSelect: (String) -> Unit,
    onEpisodeSelect: (MediaItem) -> Unit,
    onDismiss: () -> Unit,
    getImageUrl: (String) -> String,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isTv = LocalTvMode.current

    PlayerModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            SheetHeader(
                title = stringResource(Res.string.player_video_episodes),
                icon = Tabler.Outline.Video,
            )
            Spacer(Modifier.height(16.dp))

            if (seasons.isNotEmpty()) {
                val seasonFocusRequester = remember { FocusRequester() }
                LaunchedEffect(isTv) {
                    if (isTv) {
                        seasonFocusRequester.tryRequestFocus("episode_season")
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .tvFocusRestorer()
                        .tvFocusExitHandler(),
                ) {
                    items(seasons, key = { it.id }, contentType = { "season" }) { season ->
                        val isSelected = season.id == currentSeasonId
                        val containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        }
                        val contentColor = if (isSelected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }

                        val seasonFocusState = rememberTvFocusState(focusedScale = 1.05f)
                        val shape = ShapeCache.smoothPill

                        Surface(
                            modifier = Modifier
                                .clip(shape)
                                .then(seasonFocusState.focusModifier)
                                .tvFocusIndicator(seasonFocusState, shape)
                                .ifElse(season.id == currentSeasonId || (currentSeasonId == null && season.id == seasons.firstOrNull()?.id), Modifier.focusRequester(seasonFocusRequester))
                                .clickable { onSeasonSelect(season.id) },
                            color = containerColor,
                            contentColor = contentColor,
                            tonalElevation = if (isSelected) 0.dp else 1.dp,
                        ) {
                            Text(
                                text = season.name ?: stringResource(Res.string.player_video_season_n, season.indexNumber ?: 1),
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    JellyPlayLoadingIndicator()
                }
            } else if (episodes.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(Res.string.player_video_no_episodes_available),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val episodeFocusRequester = remember { FocusRequester() }
                LaunchedEffect(isTv, seasons.isEmpty(), episodes) {
                    if (isTv && (seasons.isEmpty() || currentSeasonId != null) && episodes.isNotEmpty()) {
                        episodeFocusRequester.tryRequestFocus("episode_list")
                    }
                }

                // Resolve the current episode once in an O(n) pass instead of an
                // O(n^2) re-scan (episodes.none { it.id == ... }) per row.
                val currentEpisodeIndex = remember(episodes, currentEpisodeId) {
                    episodes.indexOfFirst { it.id == currentEpisodeId }
                }
                val focusTargetIndex = if (currentEpisodeIndex >= 0) currentEpisodeIndex else 0

                LazyColumn {
                    itemsIndexed(
                        episodes,
                        key = { _, ep -> ep.id },
                        contentType = { _, _ -> "episode" },
                    ) { index, episode ->
                        val isCurrent = episode.id == currentEpisodeId
                        val isFirstOrCurrent = index == focusTargetIndex
                        // Memoize the thumbnail URL per episode id so it isn't
                        // rebuilt for every visible row on each recomposition.
                        val imageUrl = remember(episode.id) { getImageUrl(episode.id) }

                        EpisodeRow(
                            episode = episode,
                            isCurrent = isCurrent,
                            imageUrl = imageUrl,
                            onClick = { onEpisodeSelect(episode) },
                            modifier = Modifier.ifElse(isFirstOrCurrent, Modifier.focusRequester(episodeFocusRequester))
                        )
                        if (index < episodes.lastIndex) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: MediaItem,
    isCurrent: Boolean,
    imageUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pos = episode.playbackPositionTicks
    val rt = episode.runTimeTicks
    val progress = if (pos != null && pos > 0 && rt != null && rt > 0) {
        (pos.toFloat() / rt).coerceIn(0f, 1f)
    } else 0f

    val focusState = rememberTvFocusState(focusedScale = 1.01f)
    val shape = ShapeCache.smooth12

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else Color.Transparent
            )
            .then(focusState.focusModifier)
            .tvFocusIndicator(focusState, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .clip(ShapeCache.smooth4)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(12.dp))
        } else {
            Spacer(Modifier.width(16.dp))
        }

        Box(
            modifier = Modifier
                .width(140.dp)
                .aspectRatio(16f / 9f)
                .clip(ShapeCache.smooth12),
        ) {
            MediaImage(
                url = imageUrl,
                contentDescription = episode.name,
                blurHash = episode.blurHashes.primary,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                )
            }
            if (!isCurrent) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(36.dp)
                        .background(playerScrimColor().copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Tabler.Outline.PlayerPlay,
                        contentDescription = null,
                        tint = playerOnScrim(),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .clip(RoundedCornerShape(topEnd = 2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
            if (episode.isPlayed && progress <= 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(ShapeCache.smooth4)
                        .background(playerScrimColor().copy(alpha = 0.6f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(Res.string.player_video_watched),
                        style = MaterialTheme.typography.labelSmall,
                        color = playerOnScrim().copy(alpha = 0.9f),
                    )
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        episode.indexNumber?.let { append("$it. ") }
                        append(episode.name)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                val runTime = episode.runTimeTicks
                val hasWatchProgress = episode.playbackPositionTicks != null && episode.playbackPositionTicks!! > 0 && !episode.isPlayed
                val remainingTime = if (hasWatchProgress && runTime != null) {
                    formatRemainingTimeFromTicks(runTime, episode.playbackPositionTicks!!)
                } else null
                val totalTime = if (runTime != null) {
                    formatDurationFromTicks(runTime)
                } else null
                
                if (remainingTime != null && totalTime != null) {
                    Text(
                        text = stringResource(Res.string.player_video_time_left, remainingTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = totalTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (totalTime != null) {
                    Text(
                        text = totalTime,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (isCurrent) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smoothPill)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = stringResource(Res.string.player_video_now_playing),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                )
            }
        }
    }
}
