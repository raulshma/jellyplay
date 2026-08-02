package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.model.subtitle.SubtitleLanguageCodes
import com.raulshma.jellyplay.core.model.subtitle.SubtitleSearchResult

/**
 * Renders the cross-provider metadata that a [SubtitleSearchResult] carries but
 * that neither the player nor the editor previously surfaced: the flag badges
 * (SDH / FORCED / AI) and a single compact metadata line.
 *
 * The metadata line joins only the segments the provider actually returned
 * (language · format · ★ rating · downloads · frame rate · perfect match), so a
 * row is never padded with "—" placeholders. Shared by the player's provider
 * search rows and the editor's `ProviderResultsSection` so the two render the
 * same provenance/richness even though their row shells differ (TV card vs.
 * Material3 `ListItem`).
 *
 * [perfectMatchLabel] is passed in by each caller so this module needs no string
 * resources (and no locale churn): the player/editor reuse their own existing
 * "Perfect Match" strings. [includeDownloadCount] is `false` on the player, whose
 * right-side status slot already shows the count when idle (avoids duplication).
 */
@Composable
fun SubtitleResultMetadata(
    result: SubtitleSearchResult,
    perfectMatchLabel: String,
    modifier: Modifier = Modifier,
    includeDownloadCount: Boolean = true,
) {
    val jf = result.jellyfinInfo
    val rating = result.rating ?: jf?.communityRating
    val frameRate = jf?.frameRate
    val isPerfectMatch = jf?.isHashMatch == true

    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (result.isHearingImpaired) SubtitleMetadataBadge("SDH")
        if (result.isForced) SubtitleMetadataBadge("FORCED")
        if (result.isAiTranslated == true) SubtitleMetadataBadge("AI")
    }

    // Build only the segments the provider actually supplied; join with " · ".
    val segments = buildList {
        // Prefer the short 2-letter code for density; fall back to the raw ISO 639-3.
        val lang = (SubtitleLanguageCodes.toIso1(result.language) ?: result.language)
            ?.takeIf { it.isNotBlank() }?.uppercase()
        if (!lang.isNullOrEmpty()) add(lang)
        result.format?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
        if (rating != null) add("\u2605 ${"%.1f".format(rating)}")
        if (includeDownloadCount && (result.downloadCount ?: 0) > 0) {
            add("${formatCompactCount(result.downloadCount!!)} \u2193")
        }
        if (frameRate != null) add("${frameRate.toInt()}fps")
        if (isPerfectMatch) add(perfectMatchLabel)
    }
    if (segments.isNotEmpty()) {
        val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
        Text(
            text = buildAnnotatedString {
                segments.forEachIndexed { index, segment ->
                    if (index > 0) append(" \u00b7 ")
                    // Emphasize "Perfect Match" like the Download tab does (primary color).
                    if (index == segments.lastIndex && isPerfectMatch) {
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                            ),
                        ) { append(segment) }
                    } else {
                        withStyle(SpanStyle(color = onSurfaceVariant)) { append(segment) }
                    }
                }
            },
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Small uppercase badge for subtitle flags (SDH / FORCED / AI). */
@Composable
private fun SubtitleMetadataBadge(text: String) {
    Surface(
        shape = ShapeCache.smooth8,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/**
 * Abbreviates a download/count figure for a compact metadata line: values under
 * 1,000 render as-is; thousands compact to one decimal (dropping a trailing
 * `.0`). Locale-neutral — uses ASCII digits and the "k" suffix so the line stays
 * short and stable regardless of device locale.
 */
internal fun formatCompactCount(count: Int): String =
    if (count < 1000) {
        count.toString()
    } else {
        val thousands = count / 1000.0
        // One decimal, then drop a redundant ".0" (1.0k → 1k, 1.2k stays).
        val formatted = "%.1f".format(thousands)
        val trimmed = formatted.removeSuffix(".0")
        "${trimmed}k"
    }
