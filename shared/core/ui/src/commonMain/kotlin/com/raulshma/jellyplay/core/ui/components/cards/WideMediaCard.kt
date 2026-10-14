package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.MediaItem
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
 * @param bookProgressFractionOverride TOC-accurate book fraction for the
 *  footer's "% complete" label (the [PosterCard] parity param; the whole
 *  book-vs-time meta decision — admission, percent math, remaining/total
 *  ladder — lives in [mediaCardFooterMeta]).
 * @param trickplayPeek H13's loaded preview tile for the TV focus peek; null
 *  (the default — touch never loads one) renders nothing. The overlay mounts
 *  on TV whenever the row passes a loader, so its fade-out survives the peek
 *  flipping to null.
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
    bookProgressFractionOverride: Float? = null,
    trickplayPeek: TrickplayPeekTile? = null,
) {
    val isTv = LocalTvMode.current
    val dominantColor = rememberDominantColor(backdropUrl.ifBlank { imageUrl }, itemId = item.id)
    // Memoized on the item's identity and its playback position/runtime ticks
    // (was recomputed on every recomposition of the card).
    val progressPercent = item.rememberProgressFraction() ?: 0f
    val playButtonSize = if (isTv) 44.dp else 36.dp
    // H15 anti-spoiler: the one decision fold the title and the footer both
    // consume (see [spoilerSafeCardState]) — episode titles on resume/next-up
    // cards are watch-state spoilers, so the title degrades to the series
    // name when the mode is on.
    val cardPrefs = LocalCardDisplayPreferences.current
    val spoilerSafe = remember(cardPrefs) { spoilerSafeCardState(cardPrefs) }
    val title = if (spoilerSafe.showEpisodeTitles || item.mediaType != MediaType.EPISODE) {
        item.displayTitle()
    } else {
        item.seriesName.orEmpty()
    }
    // H11 quality chips: gated on the card prefs AND suppressed in
    // performance mode — the same gate the dominant-color extraction uses.
    val qualityBadges = if (!LocalPerformanceMode.current && cardPrefs.showQualityBadges) {
        item.qualityBadges
    } else {
        null
    }

    // Same rule as PosterCard: when the host screen provides a quick-action
    // controller, long-press opens the action sheet. Wide cards never wired the
    // peek preview, so there is no previewFactory to supersede here.
    val quickActionController = LocalMediaQuickActionController.current
    val onQuickActionsLongPress = quickActionController?.let { controller ->
        remember(item, controller) { { controller.show(item) } }
    }

    MediaCardScaffold(
        onClick = onClick,
        image = { imageModifier ->
            // One render pipeline for online and offline cards alike: the
            // backdrop fills the 16:9 frame (Crop) and falls back to the
            // primary image — the same chain the online row drives with
            // server URLs and the offline row with local file paths. Episode
            // primaries are landscape thumbs, so the fallback crop is
            // full-bleed both ways; a portrait poster without any backdrop
            // center-crops exactly like the online card does. (A letterbox
            // variant here made offline episode cards render blurred bands +
            // a fitted image instead of the online full-bleed — removed for
            // offline/online parity.)
            MediaImage(
                url = backdropUrl,
                fallbackUrls = remember(imageUrl) { if (imageUrl.isNotBlank()) listOf(imageUrl) else emptyList() },
                contentDescription = item.name,
                blurHash = item.blurHashes.backdrop,
                modifier = imageModifier,
                contentScale = ContentScale.Crop,
                crossfade = false,
            )
        },
        title = title,
        modifier = modifier,
        aspectRatio = 16f / 9f,
        clipToShape = clipToShape,
        cardWidth = cardWidth,
        play = onPlayClick?.let { onPlay ->
            PlayAffordance(
                onClick = onPlay,
                dominantColor = dominantColor,
                buttonSize = playButtonSize,
            )
        },
        scrim = CardScrim(brush = surfaceScrimBrush, height = 50.dp),
        onLongPress = onQuickActionsLongPress,
        titleColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
        showProgress = progressPercent > 0f,
        progressFraction = progressPercent,
        overlays = {
            // H13: the TV focus peek, anchored just above the progress bar
            // (z-order: under the bar itself, same stack as the badges — the
            // bar draws last in the scaffold). TV-only mount; the peek fades
            // itself out, so it stays mounted while [trickplayPeek] is null.
            if (isTv) {
                TrickplayPeekOverlay(
                    peek = trickplayPeek,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = TrickplayPeekBottomPadding),
                )
            }
            // Top-left stack: the H11 quality chips below the rating badge
            // (the poster card's column shape — a lone child renders exactly
            // where the former standalone rating badge sat). Rating badges
            // are never spoilers; quality chips are pre-suppressed via the
            // null [qualityBadges] when the pref is off / performance mode.
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp),
            ) {
                if (item.communityRating != null) {
                    RatingBadge(rating = item.communityRating)
                }
                QualityBadgeRow(badges = qualityBadges)
            }
        },
        footer = {
            // The whole book-vs-time meta decision is [mediaCardFooterMeta]'s
            // (see MediaCardFooters.kt); this shell only renders it — with the
            // wide card's "•"-only-after-leading-text rule and labelSmall style.
            // The H15 anti-spoiler mode drops the "Xm left" segment.
            val footerMeta = remember(item, bookProgressFractionOverride, spoilerSafe) {
                mediaCardFooterMeta(
                    item,
                    bookProgressFractionOverride,
                    showTimeLeft = spoilerSafe.showTimeLeft,
                )
            }

            val subtitleText = remember(item.seriesName, item.seasonNumber, item.episodeNumber) {
                val parts = mutableListOf<String>()
                item.seriesName?.takeIf { it.isNotBlank() }?.let { parts.add(it) }
                item.seasonNumber?.let { season ->
                    episodeCardCode(season, item.episodeNumber)?.let { parts.add(it) }
                }
                parts.joinToString(" · ")
            }

            val hasLeadingText = subtitleText.isNotEmpty() || item.year != null

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

                if (footerMeta != null) {
                    MediaCardFooterMetaRow(
                        footerMeta = footerMeta,
                        style = MaterialTheme.typography.labelSmall,
                        showDivider = hasLeadingText,
                        dividerColor = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        },
    )
}
