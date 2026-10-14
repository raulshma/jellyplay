package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import com.raulshma.jellyplay.core.designsystem.theme.isLightColor
import com.raulshma.jellyplay.core.model.ItemQualityBadge

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
    val ratingText = formatOneDecimal(rating.toDouble())
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
            fontWeight = FontWeight.Bold,
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
    iconColor: Color? = null,
) {
    val background = accentTint?.copy(alpha = 0.85f) ?: MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val effectiveIconColor = iconColor ?: remember(background, onPrimaryContainer) {
        if (isLightColor(background)) onPrimaryContainer else Color.White
    }
    GlassBadge(
        modifier = modifier,
        background = background,
        borderColor = effectiveIconColor.copy(alpha = 0.25f),
    ) {
        Icon(
            imageVector = Tabler.Outline.Check,
            contentDescription = null,
            tint = effectiveIconColor,
            modifier = Modifier.size(12.dp),
        )
    }
}

/**
 * Theme-adaptive, high-contrast "Watched" tag for episode cards & detail screen headers.
 *
 * Pairs a check icon with the localized "Watched" label on a surface that dynamically
 * adapts to the active JellyPlay theme (MaterialTheme.colorScheme.primaryContainer or
 * [accentTint]) and automatically computes content color via [isLightColor] to guarantee
 * WCAG AA contrast (dark text on light containers, light text on dark containers).
 *
 * @param label localized "Watched" text (e.g. from a `stringResource`).
 * @param accentTint optional override for the surface color (e.g., extracted artwork color).
 * @param textColor optional override for the content color. When null, derived dynamically.
 */
@Composable
fun EpisodeWatchedTag(
    label: String,
    modifier: Modifier = Modifier,
    accentTint: Color? = null,
    textColor: Color? = null,
) {
    val containerColor = accentTint ?: MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer
    val contentColor = textColor ?: remember(containerColor, onPrimaryContainer) {
        if (isLightColor(containerColor)) onPrimaryContainer else Color.White
    }
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = ShapeCache.smooth12,
        shadowElevation = 2.dp,
        tonalElevation = 1.dp,
        border = BorderStroke(0.8.dp, contentColor.copy(alpha = 0.25f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
 * with the other glass badges. The label is [episodeCardCode]'s spaced form
 * (`S1 E01` / `E01` / `S1`) — the card style of the one SxxExx derivation.
 */
@Composable
fun EpisodeChip(
    seasonNumber: Int?,
    episodeNumber: Int?,
    modifier: Modifier = Modifier,
) {
    val label = remember(seasonNumber, episodeNumber) {
        episodeCardCode(seasonNumber, episodeNumber, separator = " ")
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

// ── Quality badges (H11) ─────────────────────────────────────────────────

/**
 * The badge-worthy quality marks in render-priority order — the resolution
 * tier first, then the format badges ([ItemQualityBadge.HD] / [ItemQualityBadge.SD]
 * are not badge-worthy: a plain "HD" chip is noise, and SD is never a
 * selling point). [QualityBadgeRow] caps its render to [QUALITY_BADGE_CAP]
 * marks from this ladder's head.
 */
private val qualityBadgeRenderOrder: List<ItemQualityBadge> = listOf(
    ItemQualityBadge.FOUR_K,
    ItemQualityBadge.DOLBY_VISION,
    ItemQualityBadge.HDR,
    ItemQualityBadge.ATMOS,
)

/** At most three chips fit a card's top-left stack legibly. */
internal const val QUALITY_BADGE_CAP = 3

/**
 * [badges]' badge-worthy marks ([qualityBadgeRenderOrder] members, ladder
 * order, capped at [QUALITY_BADGE_CAP]) — the pure admission/order/cap fold
 * [QualityBadgeRow] renders, kept Compose-free so the policy is directly
 * testable. Null or exhausted [badges] (the "streams not fetched" and
 * HD/SD-only cases) yield an empty list — render nothing.
 */
internal fun qualityBadgesToRender(
    badges: List<ItemQualityBadge>?,
    cap: Int = QUALITY_BADGE_CAP,
): List<ItemQualityBadge> =
    if (badges.isNullOrEmpty()) {
        emptyList()
    } else {
        qualityBadgeRenderOrder.filter { it in badges }.take(cap)
    }

/** The compact chip label per badge-worthy mark (format marks, not translatable copy). */
private fun ItemQualityBadge.renderLabel(): String = when (this) {
    ItemQualityBadge.FOUR_K -> "4K"
    ItemQualityBadge.DOLBY_VISION -> "DV"
    ItemQualityBadge.HDR -> "HDR"
    ItemQualityBadge.ATMOS -> "Atmos"
    // Never rendered (see [qualityBadgeRenderOrder]); the arm keeps the when exhaustive.
    ItemQualityBadge.HD, ItemQualityBadge.SD -> ""
}

/**
 * The cards' compact resolution/audio quality chips (H11): a trailing Row of
 * glass badges — "4K" / "DV" / "HDR" / "Atmos" — ordered, capped
 * ([QUALITY_BADGE_CAP]) and admitted by [qualityBadgesToRender]. Renders
 * nothing for the "not fetched" null, the HD/SD-only sets, and callers that
 * already suppressed the feature (the shells pass null then), so the
 * overlay stack stays empty rather than reserving a gap.
 */
@Composable
fun QualityBadgeRow(
    badges: List<ItemQualityBadge>?,
    modifier: Modifier = Modifier,
) {
    val renderBadges = remember(badges) { qualityBadgesToRender(badges) }
    if (renderBadges.isEmpty()) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = modifier,
    ) {
        renderBadges.forEach { badge ->
            GlassBadge {
                Text(
                    text = badge.renderLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ── NEW badge (H12) ──────────────────────────────────────────────────────

/**
 * The "NEW" arrival chip (H12): a primary-tinted glass pill with an accent
 * dot — same accent treatment as [EpisodeChip], so the card family reads one
 * design language. Rendered top-left on the cards that the row flagged as
 * added since the user's last home visit; the visit-stamp predicate lives on
 * the model ([com.raulshma.jellyplay.core.model.isNewForHome]) — this is a
 * pure renderer.
 */
@Composable
fun NewBadge(
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    GlassBadge(
        modifier = modifier,
        background = primary.copy(alpha = 0.85f),
        borderColor = Color.White.copy(alpha = 0.2f),
    ) {
        // The accent dot — the plan's "NEW dot", kept as the chip's leading mark.
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onPrimary),
        )
        Text(
            text = "NEW",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}
