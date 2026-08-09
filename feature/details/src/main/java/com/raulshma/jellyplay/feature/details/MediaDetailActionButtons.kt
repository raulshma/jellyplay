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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.Heart
import com.composables.icons.tabler.outline.Eye
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.ui.components.progressFraction
import com.raulshma.jellyplay.core.ui.feedback.rememberConfirmHaptic
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Play / mark-watched / favorite buttons for the media-detail screen, in both a
 * stacked vertical layout (landscape left rail) and a horizontal row (portrait
 * body).
 *
 * Now takes [DetailContentState] + [DetailContentCallbacks] bundles instead of
 * ~18 flat parameters, making the composable skippable. The vertical/horizontal
 * play-button duplication is resolved by a shared [PlayButton] that accepts a
 * [PlayButtonStyle].
 *
 * Behaviour is identical to the former implementation.
 */
@Composable
internal fun DetailActionButtons(
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    modifier: Modifier = Modifier,
    vertical: Boolean = false,
    contentFocusRequester: FocusRequester? = null,
) {
    val detail = state.detail ?: return
    val item = detail.item
    val isAudio = item.mediaType.isAudioType
    val isAlbum = item.mediaType == MediaType.ALBUM

    val isSeriesOrEpisode = item.mediaType == MediaType.SERIES || item.mediaType == MediaType.EPISODE
    val isSeries = item.mediaType == MediaType.SERIES
    val target = if (isSeriesOrEpisode) state.smartPlayTarget else null
    val itemProgressFraction = item.progressFraction()
    val hasProgress = itemProgressFraction != null && itemProgressFraction > 0f
    val allSeasonsFetched = state.seasons.isNotEmpty() && state.fetchedSeasonIds.size >= state.seasons.size
    val allEpisodesEmpty = remember(state.seasons, state.episodes) {
        state.seasons.isNotEmpty() && state.episodes.values.all { it.isEmpty() }
    }
    val isResolvingSeriesTarget = isSeries &&
        target == null &&
        !allSeasonsFetched
    val hasNoEpisodes = isSeries && allSeasonsFetched && allEpisodesEmpty
    // A series with no episodes has no valid play target — never let the primary button
    // dispatch play on the series root item. The button already dims when this is false.
    val canPlayPrimary = isAudio || !isSeries || target != null
    val progress = if (target != null) {
        val t = target.startPositionTicks
        val rt = target.episode.runTimeTicks
        if (t > 0 && rt != null && rt > 0) (t.toFloat() / rt).coerceIn(0f, 1f) else 0f
    } else if (hasProgress) {
        itemProgressFraction
    } else 0f

    val playLabel = when {
        target != null -> target.label
        isResolvingSeriesTarget -> stringResource(R.string.detail_play_finding_episode)
        hasNoEpisodes -> stringResource(R.string.detail_play_no_episodes_available)
        isSeries -> stringResource(R.string.detail_play_no_episodes)
        hasProgress -> stringResource(R.string.detail_play_resume)
        else -> stringResource(R.string.detail_play_play)
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

    // Shared click handler — identical for vertical and horizontal so the two
    // branches can never diverge in play-resolution logic.
    val onPlay = remember(canPlayPrimary, isAlbum, isAudio, target, item, detail, callbacks, state.albumTracks) {
        {
            if (!canPlayPrimary) return@remember
            if (isAlbum && state.albumTracks.isNotEmpty()) {
                callbacks.onPlayAlbumTrack(0)
                state.albumTracks.firstOrNull()?.let { track ->
                    callbacks.onNavigate(Route.AudioPlayer(track.id))
                }
            } else if (isAudio) {
                callbacks.onAudioClick()
            } else if (target != null) {
                callbacks.onPlayClick(target.episode.id, null, target.startPositionTicks)
            } else {
                val sourceId = detail.mediaSources.firstOrNull()?.id
                val startPos = item.playbackPositionTicks ?: 0L
                callbacks.onPlayClick(item.id, sourceId, startPos)
            }
        }
    }

    if (vertical) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            FadingItem {
                PlayButton(
                    style = PlayButtonStyle.Vertical,
                    label = playLabel,
                    canPlayPrimary = canPlayPrimary,
                    progress = progress,
                    playScale = playScale,
                    interactionSource = playInteractionSource,
                    contentFocusRequester = contentFocusRequester,
                    onClick = onPlay,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val markTvFocusState = rememberTvFocusState(focusedScale = 1.08f)
                val favoriteTvFocusState = rememberTvFocusState(focusedScale = 1.08f)

                FadingItem(modifier = Modifier.weight(1f)) {
                    MarkWatchedButton(
                        style = IconButtonStyle.Vertical,
                        isPlayed = item.isPlayed,
                        scale = markScale,
                        interactionSource = markInteractionSource,
                        focusState = markTvFocusState,
                        onClick = { if (item.isPlayed) callbacks.onMarkUnplayed() else callbacks.onMarkPlayed() },
                    )
                }
                FadingItem(modifier = Modifier.weight(1f)) {
                    FavoriteButton(
                        style = IconButtonStyle.Vertical,
                        isFavorite = item.isFavorite,
                        scale = favoriteScale,
                        interactionSource = favoriteInteractionSource,
                        focusState = favoriteTvFocusState,
                        onClick = callbacks.onToggleFavorite,
                    )
                }
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.Start),
            ) {
            val playHFocusState = rememberTvFocusState(focusedScale = 1.05f)
            val markHFocusState = rememberTvFocusState(focusedScale = 1.08f)
            val favoriteHFocusState = rememberTvFocusState(focusedScale = 1.08f)

            FadingItem {
                PlayButton(
                    style = PlayButtonStyle.Horizontal,
                    label = playLabel,
                    canPlayPrimary = canPlayPrimary,
                    progress = progress,
                    playScale = playScale,
                    interactionSource = playInteractionSource,
                    contentFocusRequester = contentFocusRequester,
                    onClick = onPlay,
                )
            }

            FadingItem {
                MarkWatchedButton(
                    style = IconButtonStyle.Horizontal,
                    isPlayed = item.isPlayed,
                    scale = markScale,
                    interactionSource = markInteractionSource,
                    focusState = markHFocusState,
                    onClick = { if (item.isPlayed) callbacks.onMarkUnplayed() else callbacks.onMarkPlayed() },
                )
            }

            FadingItem {
                FavoriteButton(
                    style = IconButtonStyle.Horizontal,
                    isFavorite = item.isFavorite,
                    scale = favoriteScale,
                    interactionSource = favoriteInteractionSource,
                    focusState = favoriteHFocusState,
                    onClick = callbacks.onToggleFavorite,
                )
            }
            }
        }
    }
}

/** Distinguishes the vertical (full-width, 52dp) from horizontal (fixed 200×56dp) play button. */
private enum class PlayButtonStyle { Vertical, Horizontal }

/** Distinguishes vertical (weight 1f, 48dp) from horizontal (56×56dp) icon buttons. */
private enum class IconButtonStyle { Vertical, Horizontal }

@Composable
private fun PlayButton(
    style: PlayButtonStyle,
    label: String,
    canPlayPrimary: Boolean,
    progress: Float,
    playScale: Float,
    interactionSource: MutableInteractionSource,
    contentFocusRequester: FocusRequester?,
    onClick: () -> Unit,
) {
    val isTv = LocalTvMode.current
    val playFocusState = rememberTvFocusState(focusedScale = 1.05f)
    val shape = if (style == PlayButtonStyle.Vertical) ShapeCache.smooth14 else ShapeCache.smooth16
    val iconSize = if (style == PlayButtonStyle.Vertical) 22.dp else 24.dp
    val spacerSize = if (style == PlayButtonStyle.Vertical) 6.dp else 8.dp

    val baseModifier = if (style == PlayButtonStyle.Vertical) {
        Modifier.fillMaxWidth().height(52.dp)
    } else {
        Modifier.height(56.dp).width(200.dp)
    }

    Box(
        modifier = baseModifier
            .clip(shape)
            .background(
                if (isTv && playFocusState.isFocused) MaterialTheme.colorScheme.onPrimary
                else if (canPlayPrimary) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
            )
            .then(
                contentFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier,
            )
            .then(playFocusState.focusModifier)
            .then(Modifier.tvFocusIndicator(playFocusState, shape, color = MaterialTheme.colorScheme.onPrimary))
            .graphicsLayer { scaleX = playScale; scaleY = playScale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = true,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress)
                    .align(Alignment.CenterStart)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = if (isTv && playFocusState.isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(Modifier.size(spacerSize))
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (isTv && playFocusState.isFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
private fun MarkWatchedButton(
    style: IconButtonStyle,
    isPlayed: Boolean,
    scale: Float,
    interactionSource: MutableInteractionSource,
    focusState: com.raulshma.jellyplay.core.ui.tv.TvFocusState,
    onClick: () -> Unit,
) {
    val confirmHaptic = rememberConfirmHaptic()
    val isTv = LocalTvMode.current
    val shape = if (style == IconButtonStyle.Vertical) ShapeCache.smooth12 else ShapeCache.smooth16
    val baseModifier = if (style == IconButtonStyle.Vertical) {
        Modifier.fillMaxWidth().height(48.dp)
    } else {
        Modifier.size(56.dp)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = baseModifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, shape))
            .clickable(interactionSource = interactionSource, indication = null, onClick = { confirmHaptic(); onClick() }),
    ) {
        val contentDescription = if (isPlayed) {
            stringResource(R.string.detail_cd_mark_as_unwatched)
        } else {
            stringResource(R.string.detail_cd_mark_as_watched)
        }
        Icon(
            if (isPlayed) Tabler.Outline.Eye else Tabler.Outline.EyeOff,
            contentDescription = contentDescription,
            tint = if (isPlayed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FavoriteButton(
    style: IconButtonStyle,
    isFavorite: Boolean,
    scale: Float,
    interactionSource: MutableInteractionSource,
    focusState: com.raulshma.jellyplay.core.ui.tv.TvFocusState,
    onClick: () -> Unit,
) {
    val confirmHaptic = rememberConfirmHaptic()
    val shape = if (style == IconButtonStyle.Vertical) ShapeCache.smooth12 else ShapeCache.smooth16
    val baseModifier = if (style == IconButtonStyle.Vertical) {
        Modifier.fillMaxWidth().height(48.dp)
    } else {
        Modifier.size(56.dp)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = baseModifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(focusState.focusModifier)
            .then(Modifier.tvFocusIndicator(focusState, shape))
            .clickable(interactionSource = interactionSource, indication = null, onClick = { confirmHaptic(); onClick() }),
    ) {
        Icon(
            if (isFavorite) Tabler.Filled.Heart else Tabler.Outline.Heart,
            contentDescription = stringResource(R.string.detail_cd_favorite),
            tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
