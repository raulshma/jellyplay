package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.hasWatchProgress
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.ui.adaptive.LocalJellyPlayUi
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.enableMarqueeOnFocus

/**
 * Wide (16:9 landscape) media card for Home continue-watching / next-up rows.
 * A thin specialization over [MediaCardScaffold]: supplies a backdrop image,
 * a row-hoisted [scrimBrush], a rating badge, and a series/episode + runtime
 * meta footer. Delegates all card chrome (focus, press, border, scrim, play,
 * progress bar) to the scaffold.
 *
 * Lives in `core/ui` so any feature can host a wide media row (previously it
 * was private to `feature/home`).
 *
 * @param cardWidth fixed card width (the row computes this from adaptive info).
 * @param surfaceScrimBrush bottom scrim brush — hoisted and shared across every
 *  card in the row to avoid allocating a [Brush] per scrolling card.
 */
@Composable
fun WideMediaCard(
    item: MediaItem,
    imageUrl: String,
    backdropUrl: String,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    cardWidth: Dp,
    surfaceScrimBrush: Brush,
    modifier: Modifier = Modifier,
    clipToShape: Boolean = false,
) {
    val isTv = LocalTvMode.current
    val dominantColor = rememberDominantColor(backdropUrl.ifBlank { imageUrl }, itemId = item.id)
    // Memoize the progress fraction so it is recomputed only when the item's
    // identity or its playback position/runtime ticks change (was recomputed
    // on every recomposition of the card).
    val progressPercent = remember(item.id, item.playbackPositionTicks, item.runTimeTicks) {
        item.progressFraction() ?: 0f
    }
    val playButtonSize = if (isTv) 44.dp else 36.dp

    MediaCardScaffold(
        onClick = onClick,
        image = { imageModifier ->
            MediaImage(
                url = backdropUrl,
                fallbackUrls = remember(imageUrl) { if (imageUrl.isNotBlank()) listOf(imageUrl) else emptyList() },
                contentDescription = item.name,
                blurHash = item.blurHashes.backdrop,
                modifier = imageModifier,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                crossfade = false,
            )
        },
        title = item.displayTitle(),
        modifier = modifier,
        aspectRatio = 16f / 9f,
        clipToShape = clipToShape,
        cardWidth = cardWidth,
        onPlayClick = onPlayClick,
        playButtonDominantColor = dominantColor,
        playButtonSize = playButtonSize,
        scrimBrush = surfaceScrimBrush,
        scrimHeight = 50.dp,
        titleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        showProgress = progressPercent > 0f,
        progressFraction = progressPercent,
        overlays = {
            if (item.communityRating != null) {
                RatingBadge(
                    rating = item.communityRating,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                )
            }
        },
        footer = {
            val isSeries = item.mediaType == MediaType.SERIES
            val hasValidDuration = item.runTimeTicks != null && item.runTimeTicks!! > 0 && !isSeries
            val hasWatchProgress = item.hasWatchProgress
            val remainingTime = if (hasWatchProgress && hasValidDuration) {
                formatRemainingTimeFromTicks(item.runTimeTicks!!, item.playbackPositionTicks!!)
            } else null
            val totalTime = if (hasValidDuration && !hasWatchProgress) {
                formatDurationFromTicks(item.runTimeTicks!!)
            } else null

            val timeText = remainingTime ?: totalTime

            val subtitleText = remember(item.seriesName, item.seasonNumber, item.episodeNumber) {
                val parts = mutableListOf<String>()
                item.seriesName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
                item.seasonNumber?.let { season ->
                    item.episodeNumber?.let { ep ->
                        parts.add("S${season}E${ep.toString().padStart(2, '0')}")
                    } ?: parts.add("S$season")
                }
                parts.joinToString(" · ")
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (subtitleText.isNotEmpty()) {
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                } else if (item.year != null) {
                    Text(
                        text = item.year.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (timeText != null) {
                    if (subtitleText.isNotEmpty() || item.year != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Text(
                        text = if (remainingTime != null) "$timeText left" else timeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (remainingTime != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}
