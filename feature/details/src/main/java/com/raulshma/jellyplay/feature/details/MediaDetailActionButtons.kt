package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.Heart
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

@Composable
internal fun DetailActionButtons(
    item: MediaItem,
    detail: MediaDetail,
    seasons: List<MediaItem>,
    episodes: Map<String, List<MediaItem>>,
    fetchedSeasonIds: Set<String>,
    smartPlayTarget: DetailUiState.SmartPlayTarget?,
    isAudio: Boolean,
    isAlbum: Boolean,
    albumTracks: List<MediaItem>,
    onPlayClick: (itemId: String, mediaSourceId: String?, startPosition: Long) -> Unit,
    onAudioClick: () -> Unit,
    onPlayAlbumTrack: (Int) -> Unit,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkPlayed: () -> Unit,
    onMarkUnplayed: () -> Unit,

    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    contentFocusRequester: FocusRequester? = null,
) {
    val isSeriesOrEpisode = item.mediaType == MediaType.SERIES || item.mediaType == MediaType.EPISODE
    val isSeries = item.mediaType == MediaType.SERIES
    val target = if (isSeriesOrEpisode) smartPlayTarget else null
    val itemProgressFraction = item.progressFraction()
    val hasProgress = itemProgressFraction != null && itemProgressFraction > 0f
    val allSeasonsFetched = seasons.isNotEmpty() && fetchedSeasonIds.size >= seasons.size
    val allEpisodesEmpty = seasons.isNotEmpty() && episodes.values.all { it.isEmpty() }
    val isResolvingSeriesTarget = isSeries &&
        target == null &&
        !allSeasonsFetched
    val hasNoEpisodes = isSeries && allSeasonsFetched && allEpisodesEmpty
    val canPlayPrimary = isAudio || !isSeries || target != null || hasNoEpisodes
    val progress = if (target != null) {
        val t = target.startPositionTicks
        val rt = target.episode.runTimeTicks
        if (t > 0 && rt != null && rt > 0) (t.toFloat() / rt).coerceIn(0f, 1f) else 0f
    } else if (hasProgress && itemProgressFraction != null) {
        itemProgressFraction
    } else 0f

    val playLabel = when {
        target != null -> target.label
        isResolvingSeriesTarget -> "Finding Episode"
        hasNoEpisodes -> "No Episodes Available"
        isSeries -> "No Episodes"
        hasProgress -> "Resume"
        else -> "Play"
    }

    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed && canPlayPrimary) 0.95f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "playButtonScale",
    )
    val markInteractionSource = remember { MutableInteractionSource() }
    val isMarkPressed by markInteractionSource.collectIsPressedAsState()
    val markScale by animateFloatAsState(
        targetValue = if (isMarkPressed) 0.9f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "markButtonScale",
    )
    val favoriteInteractionSource = remember { MutableInteractionSource() }
    val isFavoritePressed by favoriteInteractionSource.collectIsPressedAsState()
    val favoriteScale by animateFloatAsState(
        targetValue = if (isFavoritePressed) 0.9f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "favoriteButtonScale",
    )

    val isTv = LocalTvMode.current

    // Play button — full width in vertical mode, fixed width in horizontal
    val playButton: @Composable () -> Unit = {
        val playTvFocusState = rememberTvFocusState(focusedScale = 1.05f)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(ShapeCache.smooth14)
                .background(
                    if (isTv && playTvFocusState.isFocused) MaterialTheme.colorScheme.onPrimary
                    else if (canPlayPrimary) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                )
                .then(
                    contentFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                )
                .then(playTvFocusState.focusModifier)
                .then(Modifier.tvFocusIndicator(playTvFocusState, ShapeCache.smooth14, color = MaterialTheme.colorScheme.onPrimary))
                .graphicsLayer { scaleX = playScale; scaleY = playScale }
                .clickable(
                    interactionSource = playInteractionSource,
                    indication = null,
                    enabled = true,
                ) {
                    if (!canPlayPrimary) return@clickable
                    if (isAlbum && albumTracks.isNotEmpty()) {
                        onPlayAlbumTrack(0)
                        albumTracks.firstOrNull()?.let { track ->
                            onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.AudioPlayer(track.id))
                        }
                    } else if (isAudio) {
                        onAudioClick()
                    } else if (target != null) {
                        onPlayClick(target.episode.id, null, target.startPositionTicks)
                    } else {
                        val sourceId = detail.mediaSources.firstOrNull()?.id
                        val startPos = item.playbackPositionTicks ?: 0L
                        onPlayClick(item.id, sourceId, startPos)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (progress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress)
                        .align(Alignment.CenterStart)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Tabler.Outline.PlayerPlay,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (isTv && playTvFocusState.isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    playLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isTv && playTvFocusState.isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    // Icon action buttons — watch, favorite, download
    val iconsModifier = Modifier
        .fillMaxWidth()

    if (vertical) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FadingItem {
                playButton()
            }
            Row(
                modifier = iconsModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val isTv = LocalTvMode.current
                val markTvFocusState = rememberTvFocusState(focusedScale = 1.08f)
                val favoriteTvFocusState = rememberTvFocusState(focusedScale = 1.08f)

                FadingItem(modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(ShapeCache.smooth12)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            .then(markTvFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(markTvFocusState, ShapeCache.smooth12))
                            .graphicsLayer { scaleX = markScale; scaleY = markScale }
                            .clickable(interactionSource = markInteractionSource, indication = null) {
                                if (item.isPlayed) onMarkUnplayed() else onMarkPlayed()
                            }
                    ) {
                        Icon(
                            if (item.isPlayed) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                            contentDescription = if (item.isPlayed) "Mark as Unwatched" else "Mark as Watched",
                            tint = if (item.isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                FadingItem(modifier = Modifier.weight(1f)) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(ShapeCache.smooth12)
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                            .then(favoriteTvFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(favoriteTvFocusState, ShapeCache.smooth12))
                            .graphicsLayer { scaleX = favoriteScale; scaleY = favoriteScale }
                            .clickable(interactionSource = favoriteInteractionSource, indication = null) { onToggleFavorite() }
                    ) {
                        Icon(
                            if (item.isFavorite) Tabler.Filled.Heart else Tabler.Outline.Heart,
                            contentDescription = "Favorite",
                            tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
        ) {
            // Horizontal: play button fixed width, icon buttons fixed size
            val playHFocusState = rememberTvFocusState(focusedScale = 1.05f)
            FadingItem {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .width(200.dp)
                        .clip(ShapeCache.smooth16)
                        .background(
                            if (isTv && playHFocusState.isFocused) MaterialTheme.colorScheme.onPrimary
                            else if (canPlayPrimary) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                        )
                        .then(
                            contentFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier
                        )
                        .then(playHFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(playHFocusState, ShapeCache.smooth16, color = MaterialTheme.colorScheme.onPrimary))
                        .graphicsLayer { scaleX = playScale; scaleY = playScale }
                        .clickable(
                            interactionSource = playInteractionSource,
                            indication = null,
                            enabled = true,
                        ) {
                            if (!canPlayPrimary) return@clickable
                            if (isAudio) {
                                onAudioClick()
                            } else if (target != null) {
                                onPlayClick(target.episode.id, null, target.startPositionTicks)
                            } else {
                                val sourceId = detail.mediaSources.firstOrNull()?.id
                                val startPos = item.playbackPositionTicks ?: 0L
                                onPlayClick(item.id, sourceId, startPos)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (progress > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(progress)
                                .align(Alignment.CenterStart)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Tabler.Outline.PlayerPlay,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = if (isTv && playHFocusState.isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            playLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = if (isTv && playHFocusState.isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            val markHFocusState = rememberTvFocusState(focusedScale = 1.08f)
            val favoriteHFocusState = rememberTvFocusState(focusedScale = 1.08f)

            FadingItem {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(ShapeCache.smooth16)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        .graphicsLayer { scaleX = markScale; scaleY = markScale }
                        .then(markHFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(markHFocusState, ShapeCache.smooth16))
                        .clickable(interactionSource = markInteractionSource, indication = null) {
                            if (item.isPlayed) onMarkUnplayed() else onMarkPlayed()
                        }
                ) {
                    Icon(
                        if (item.isPlayed) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
                        contentDescription = if (item.isPlayed) "Mark as Unwatched" else "Mark as Watched",
                        tint = if (item.isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }

            FadingItem {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(ShapeCache.smooth16)
                        .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                        .graphicsLayer { scaleX = favoriteScale; scaleY = favoriteScale }
                        .then(favoriteHFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(favoriteHFocusState, ShapeCache.smooth16))
                        .clickable(interactionSource = favoriteInteractionSource, indication = null) { onToggleFavorite() }
                ) {
                    Icon(
                        if (item.isFavorite) Tabler.Filled.Heart else Tabler.Outline.Heart,
                        contentDescription = "Favorite",
                        tint = if (item.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }


            }
    }
}
