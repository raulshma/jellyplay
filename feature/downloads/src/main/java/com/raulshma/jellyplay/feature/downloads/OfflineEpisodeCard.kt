package com.raulshma.jellyplay.feature.downloads

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Trash
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSoothingTheme
import com.raulshma.jellyplay.core.designsystem.theme.LocalIsSynthwave
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences
import com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks
import com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks
import com.raulshma.jellyplay.core.ui.components.formatRelativeTime
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Offline episode card — mirrors the online EpisodeCard layout (280dp wide card,
 * 16:9 thumbnail with scrim + centered play affordance, progress bar, watched
 * badge, info column) so online and offline series episodes look identical.
 *
 * Two offline-only differences:
 *  - the image is the locally-saved [OfflineMediaItem.posterPath] (the episode
 *    Primary image, mirroring the online card), and
 *  - a trash affordance is overlaid on the thumbnail so individual episode
 *    downloads can be removed (online episodes have no delete action).
 *
 * Interactions mirror online: tapping the card body opens the episode detail
 * screen ([onDetailClick]); the centered play icon plays the episode ([onPlayClick]).
 */
@Composable
internal fun OfflineEpisodeCard(
    episode: OfflineMediaItem,
    isCurrentEpisode: Boolean = false,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    sharedThumbnailModifier: Modifier = Modifier,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.96f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "offlineEpisodeCardScale",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "offlineEpisodePlayScale",
    )

    val cardFocusState = rememberTvFocusState(focusedScale = 1.03f)

    // Resolved once at card scope so both the thumbnail overlays and the info
    // column can read watch progress.
    val positionTicks = episode.playbackPositionTicks

    val isSynthwave = LocalIsSynthwave.current
    val isSoothing = LocalIsSoothingTheme.current
    val borderModifier = when {
        isSynthwave -> Modifier.border(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.secondary,
                ),
            ),
            shape = ShapeCache.smooth16,
        )
        isSoothing -> Modifier.border(
            width = 0.8.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
            shape = ShapeCache.smooth16,
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
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            )
            .then(
                if (isCurrentEpisode) Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                else Modifier,
            )
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .then(cardFocusState.focusModifier)
            .then(Modifier.tvFocusIndicator(cardFocusState, ShapeCache.smooth16))
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = onDetailClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .then(sharedThumbnailModifier),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = episode.posterPath ?: episode.backdropPath
            if (!thumb.isNullOrBlank()) {
                MediaImage(
                    url = thumb,
                    contentDescription = episode.name,
                    blurHash = episode.blurHashBackdrop ?: episode.blurHashPrimary,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (episode.isPlayed) Modifier.graphicsLayer { alpha = 0.65f } else Modifier),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)))

            // Centered play affordance (plays the episode directly).
            val epPlayFocusState = rememberTvFocusState(focusedScale = 1.15f)
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = stringResource(R.string.downloads_action_play),
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
                    .padding(8.dp),
            )

            // Per-episode delete (offline only — online episodes have no delete).
            val deleteFocusState = rememberTvFocusState(focusedScale = 1.1f)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .then(deleteFocusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(deleteFocusState, CircleShape))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDelete,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Trash,
                    contentDescription = stringResource(R.string.downloads_delete_episode_cd),
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(16.dp),
                )
            }

            // Watch-progress bar.
            if (positionTicks != null && positionTicks > 0 && !episode.isPlayed) {
                val runtimeTicks = episode.runTimeTicks
                val progress = if (runtimeTicks != null && runtimeTicks > 0) {
                    (positionTicks.toFloat() / runtimeTicks).coerceIn(0f, 1f)
                } else {
                    (episode.playedPercentage / 100f).toFloat().coerceIn(0f, 1f)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            } else if (episode.isPlayed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                )
            }
            // Watched badge (mirrors online logic).
            val cardPrefs = LocalCardDisplayPreferences.current
            if (episode.isPlayed && cardPrefs.showWatchedCheckmark) {
                com.raulshma.jellyplay.core.ui.components.EpisodeWatchedTag(
                    label = stringResource(R.string.downloads_watched),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 6.dp, bottom = 8.dp),
                )
            }
        }

        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = buildString {
                    episode.episodeNumber?.let { append("$it. ") }
                    append(episode.name)
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val runtimeTicks = episode.runTimeTicks
            val hasWatchProgress = positionTicks != null && positionTicks > 0 && !episode.isPlayed
            val remainingTime = if (hasWatchProgress && runtimeTicks != null) {
                formatRemainingTimeFromTicks(runtimeTicks, positionTicks)
            } else null
            val totalTime = runtimeTicks?.takeIf { it > 0 }?.let { formatDurationFromTicks(it) }
            val lastWatched = formatRelativeTime(episode.lastPlayedDate)

            if (remainingTime != null && totalTime != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Text(
                        text = stringResource(R.string.downloads_time_left, remainingTime),
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
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Last-watched timestamp (e.g. "2d ago"). Shown when the episode has
            // any watch activity and we could format the stored timestamp.
            if (lastWatched != null && (episode.isPlayed || (positionTicks != null && positionTicks > 0))) {
                Text(
                    text = lastWatched,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
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
                    lineHeight = TextUnit(16f, TextUnitType.Sp),
                )
            }
        }
    }
}

/**
 * Compact, mobile-first episode row for the offline series detail's optional
 * vertical episode list. Mirrors [OfflineEpisodeCard] semantics — tap opens the
 * episode detail screen, the centered play icon plays the file, the trash badge
 * deletes it — but laid out as a single-line row (thumbnail + metadata column)
 * so a whole season scrolls vertically on a phone.
 *
 * Two offline-only differences from the online compact row: the thumbnail is the
 * locally-saved [OfflineMediaItem.posterPath] (the episode Primary image, as on
 * the online row), and a trailing trash affordance lets the user remove the
 * downloaded file.
 */
@Composable
internal fun OfflineCompactEpisodeRow(
    episode: OfflineMediaItem,
    isCurrentEpisode: Boolean = false,
    onPlayClick: () -> Unit,
    onDetailClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    sharedThumbnailModifier: Modifier = Modifier,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "offlineCompactEpisodeRowScale",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "offlineCompactEpisodeRowPlayScale",
    )

    val positionTicks = episode.playbackPositionTicks

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth16)
            .background(
                if (isCurrentEpisode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
            )
            .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = null,
                onClick = onDetailClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(width = 128.dp, height = 72.dp)
                .clip(ShapeCache.smooth16)
                .then(sharedThumbnailModifier),
            contentAlignment = Alignment.Center,
        ) {
            val thumb = episode.posterPath ?: episode.backdropPath
            if (!thumb.isNullOrBlank()) {
                MediaImage(
                    url = thumb,
                    contentDescription = episode.name,
                    blurHash = episode.blurHashBackdrop ?: episode.blurHashPrimary,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (episode.isPlayed) Modifier.graphicsLayer { alpha = 0.65f } else Modifier),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
            Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)))

            // Centered play affordance (plays the file directly).
            Icon(
                Tabler.Outline.PlayerPlay,
                contentDescription = stringResource(R.string.downloads_action_play),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .size(32.dp)
                    .graphicsLayer { scaleX = playScale; scaleY = playScale }
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), CircleShape)
                    .clickable(
                        interactionSource = playInteractionSource,
                        indication = null,
                        onClick = onPlayClick,
                    )
                    .padding(6.dp),
            )

            // Watch-progress bar.
            if (positionTicks != null && positionTicks > 0 && !episode.isPlayed) {
                val runtimeTicks = episode.runTimeTicks
                val progress = if (runtimeTicks != null && runtimeTicks > 0) {
                    (positionTicks.toFloat() / runtimeTicks).coerceIn(0f, 1f)
                } else {
                    (episode.playedPercentage / 100f).toFloat().coerceIn(0f, 1f)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            } else if (episode.isPlayed) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                )
            }
            // Watched tag (mirrors OfflineEpisodeCard).
            val cardPrefs = LocalCardDisplayPreferences.current
            if (episode.isPlayed && cardPrefs.showWatchedCheckmark) {
                com.raulshma.jellyplay.core.ui.components.EpisodeWatchedTag(
                    label = stringResource(R.string.downloads_watched),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 6.dp),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                text = buildString {
                    episode.episodeNumber?.let { append("$it. ") }
                    append(episode.name)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val runtimeTicks = episode.runTimeTicks
            val hasWatchProgress = positionTicks != null && positionTicks > 0 && !episode.isPlayed
            val remainingTime = if (hasWatchProgress && runtimeTicks != null) {
                formatRemainingTimeFromTicks(runtimeTicks, positionTicks)
            } else null
            val totalTime = runtimeTicks?.takeIf { it > 0 }?.let { formatDurationFromTicks(it) }

            if (remainingTime != null && totalTime != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.downloads_time_left, remainingTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = totalTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (totalTime != null) {
                Text(
                    text = totalTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }

        // Per-episode delete (offline only). Sits at the trailing edge of the row
        // rather than overlaid on the thumbnail (as on the card) — the compact row
        // has room for a dedicated affordance.
        val deleteFocusState = rememberTvFocusState(focusedScale = 1.1f)
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .size(32.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
                .then(deleteFocusState.focusModifier)
                .then(Modifier.tvFocusIndicator(deleteFocusState, CircleShape))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDelete,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Tabler.Outline.Trash,
                contentDescription = stringResource(R.string.downloads_delete_episode_cd),
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}