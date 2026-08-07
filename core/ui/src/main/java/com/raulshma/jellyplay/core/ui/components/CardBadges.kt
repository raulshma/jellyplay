package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.filled.Star
import com.composables.icons.tabler.outline.Check
import com.raulshma.jellyplay.core.designsystem.theme.RatingColors
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.StatusColors

/**
 * A translucent "glass" overlay badge for poster/wide media cards.
 *
 * Sits on top of unpredictable poster artwork, so the fill is a neutral dark scrim
 * (independent of light/dark theme) with a faint top-side highlight border for a
 * frosted-glass feel. Content is laid out as a centered [Row].
 *
 * @param background  scrim fill, defaults to near-black for contrast over any art.
 * @param borderColor hairline border tint.
 * @param borderWidth hairline border weight.
 * @param horizontalPadding horizontal inset for badge content.
 * @param verticalPadding   vertical inset for badge content.
 * @param contentSpacing    gap between siblings inside the badge row.
 */
@Composable
fun GlassBadge(
    modifier: Modifier = Modifier,
    background: Color = Color.Black.copy(alpha = 0.45f),
    borderColor: Color = Color.White.copy(alpha = 0.18f),
    borderWidth: androidx.compose.ui.unit.Dp = 0.5.dp,
    shape: androidx.compose.ui.graphics.Shape = ShapeCache.smooth8,
    horizontalPadding: androidx.compose.ui.unit.Dp = 7.dp,
    verticalPadding: androidx.compose.ui.unit.Dp = 3.dp,
    contentSpacing: androidx.compose.ui.unit.Dp = 3.dp,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(contentSpacing),
        modifier = modifier
            .background(background, shape)
            .border(borderWidth, borderColor, shape)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        content = content,
    )
}

/**
 * IMDb-style star + numeric rating badge. Renders nothing when [rating] is null.
 *
 * Uses a vector star (Tabler Filled) instead of a text glyph so it renders
 * identically across fonts/devices at small sizes.
 */
@Composable
fun RatingBadge(
    rating: Number?,
    modifier: Modifier = Modifier,
    starColor: Color = RatingColors.star,
    textColor: Color = Color.White,
) {
    if (rating == null) return
    val ratingText = "%.1f".format(rating.toDouble())
    GlassBadge(modifier = modifier) {
        Icon(
            imageVector = Tabler.Filled.Star,
            contentDescription = null,
            tint = starColor,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = ratingText,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * "Watched" check badge. A single check icon in a glass pill. Optional
 * [accentTint] blends a dominant/brand color into the scrim for a subtle
 * personalized tint while keeping the glass treatment.
 */
@Composable
fun WatchedBadge(
    modifier: Modifier = Modifier,
    accentTint: Color? = null,
    iconColor: Color = Color.White,
) {
    val background = accentTint?.copy(alpha = 0.55f) ?: Color.Black.copy(alpha = 0.45f)
    GlassBadge(
        modifier = modifier,
        background = background,
        borderColor = accentTint?.copy(alpha = 0.7f) ?: Color.White.copy(alpha = 0.18f),
    ) {
        Icon(
            imageVector = Tabler.Outline.Check,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * High-contrast "Watched" tag for episode thumbnails (online detail + offline downloads).
 *
 * Pairs a check icon with the localized "Watched" label on a bold surface that "pops"
 * over unpredictable episode artwork (dark scenes, bright frames, busy key art) so it
 * stays legible at a glance while scrolling quickly.
 *
 * Prominence is achieved with three reinforcing cues rather than color alone:
 *  - a saturated success-green surface (the universal "completed" semantic) instead of
 *    the theme `primary`, which is branding-driven and can wash out on bright artwork;
 *  - a hairline white border so the pill separates from similarly-colored art behind it;
 *  - a deeper shadow (6.dp) plus tonal elevation so the tag lifts off the thumbnail.
 *
 * The [label] is passed in (rather than read from a string resource) so this core-ui
 * composable stays module-agnostic: each feature screen supplies its own localization.
 *
 * @param label localized "Watched" text (e.g. from a `stringResource`).
 * @param accentTint optional override for the surface color (personalized styling).
 * @param textColor optional override for the content color. Defaults to white for
 *  maximum contrast over the saturated surface.
 */
@Composable
fun EpisodeWatchedTag(
    label: String,
    modifier: Modifier = Modifier,
    accentTint: Color? = null,
    textColor: Color? = null,
) {
    val containerColor = accentTint ?: StatusColors.success
    val contentColor = textColor ?: Color.White
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = ShapeCache.smooth8,
        shadowElevation = 6.dp,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.White.copy(alpha = 0.55f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = Tabler.Outline.Check,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * Unwatched-episode-count badge for series/seasons/collections.
 */
@Composable
fun UnwatchedCountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    textColor: Color = Color.White,
) {
    GlassBadge(modifier = modifier) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * Bottom-left season/episode chip for episode cards in Latest Media rows.
 * Uses a primary-tinted glass so it reads as an accent while staying cohesive
 * with the other glass badges.
 */
@Composable
fun EpisodeChip(
    seasonNumber: Int?,
    episodeNumber: Int?,
    modifier: Modifier = Modifier,
) {
    val label = remember(seasonNumber, episodeNumber) {
        when {
            seasonNumber != null && episodeNumber != null ->
                "S${seasonNumber} E${episodeNumber.toString().padStart(2, '0')}"
            episodeNumber != null -> "E${episodeNumber.toString().padStart(2, '0')}"
            seasonNumber != null -> "S$seasonNumber"
            else -> null
        }
    }
    if (label == null) return
    val primary = MaterialTheme.colorScheme.primary
    GlassBadge(
        modifier = modifier,
        background = primary.copy(alpha = 0.85f),
        borderColor = Color.White.copy(alpha = 0.2f),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
