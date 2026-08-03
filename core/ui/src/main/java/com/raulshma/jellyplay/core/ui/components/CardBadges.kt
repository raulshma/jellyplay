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
 * Accent "Watched" tag for episode thumbnails (online detail + offline downloads).
 *
 * Unlike the icon-only glass [WatchedBadge] used on grid cards, this pairs a check
 * icon with the localized "Watched" label on a high-contrast `primaryContainer` /
 * `onPrimaryContainer` pill. The accent fill makes the watched boundary pop at a
 * glance while quickly scrolling the episode list — the previous dim black/white
 * text pill was easy to miss.
 *
 * The [label] is passed in (rather than read from a string resource) so this
 * core-ui composable stays module-agnostic: each feature screen supplies its own
 * localized string.
 *
 * @param label localized "Watched" text (e.g. from a `stringResource`).
 */
@Composable
fun EpisodeWatchedTag(
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        modifier = modifier
            .clip(ShapeCache.smooth4)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Icon(
            imageVector = Tabler.Outline.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.SemiBold,
        )
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
