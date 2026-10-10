package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.JellyPlayRowEntry
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.hasPlaybackPosition
import com.raulshma.jellyplay.core.model.progressFraction
import com.raulshma.jellyplay.core.ui.components.LocalCardDisplayPreferences
import com.raulshma.jellyplay.core.ui.components.PosterCard

/**
 * The PLUGIN_ROW home row — the companion server plugin's seasonal row (and,
 * once the plugin grows a list-titles capability, its admin-defined titled
 * rows). One mixed curation-ordered scroller over [JellyPlayRowEntry]s:
 *  - an entry with a resolved [JellyPlayRowEntry.localItem] renders the SAME
 *    native [PosterCard] the media rows render (server artwork, progress bar,
 *    play affordance, played/favorite badges — the full home-card contract),
 *    clicking through the standard poster-row policy (details);
 *  - an entry without one renders a compact title+year fallback tile — the
 *    plugin matched nothing in the local library, and no remote-image loading
 *    is invented for it (the wire payload carries no art).
 *
 * Everything else mirrors [HomeMediaRow] exactly — [HomeItemRow] chassis (TV
 * focus / touch scroller / edge-pull refresh), the shared row metrics, the
 * hide-watched row filter (resolved entries only; a fallback tile carries no
 * played state), the poster scrim — so the plugin row reads as a sibling of
 * the media rows, not a bespoke surface. Like the Seerr discover rows it
 * bypasses [homeRowChassis] (dispatched directly from [HomeContentList]):
 * neither the online poster row nor the offline mirror can render a mixed
 * entry list.
 */
@Composable
internal fun JellyPlayHomeRow(
    title: String,
    entries: List<JellyPlayRowEntry>,
    imageUrlBuilder: (MediaItem) -> String,
    fallbackImageUrlBuilder: (MediaItem) -> List<String>,
    onItemClick: (MediaItem) -> Unit,
    onPlayClick: ((MediaItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    onRowFocused: (() -> Unit)? = null,
    clippingEnabled: Boolean = false,
    onSectionLongClick: (() -> Unit)? = null,
    // TV-only: reports the D-pad-focused entry's resolved item so the screen's
    // Menu key can open its quick actions (fallback tiles report nothing).
    onFocusedItemChange: ((MediaItem) -> Unit)? = null,
    // Touch-only edge-pull refresh wiring — forwarded to [HomeItemRow]; the
    // plugin rows are single-row refreshable (the fetcher's PLUGIN_ROW arm).
    edgeRefresh: EdgeRefreshContext? = null,
) {
    val cardPrefs = LocalCardDisplayPreferences.current
    val metrics = homeRowMetrics()
    // Same hide-watched row filter as HomeMediaRow, applied to the entries
    // the row can actually judge (resolved items); fallback tiles stay —
    // dropping them would silently rewrite the plugin's curation.
    val effectiveEntries = remember(entries, cardPrefs.hideWatchedItems) {
        if (cardPrefs.hideWatchedItems) {
            entries.filterNot { it.localItem?.isPlayed == true }
        } else {
            entries
        }
    }
    if (effectiveEntries.isEmpty()) return
    // Row-shared bottom scrim, matching the online poster rows (the scrim
    // builder lives file-private beside HomeMediaRow; same one-liner here so
    // the plugin row cannot drift from it visually).
    val posterSurfaceColor = MaterialTheme.colorScheme.surface
    val posterScrimBrush = remember(posterSurfaceColor) {
        androidx.compose.ui.graphics.Brush.verticalGradient(
            listOf(androidx.compose.ui.graphics.Color.Transparent, posterSurfaceColor.copy(alpha = 0.45f)),
        )
    }
    // Index-paired so the lazy-list key can disambiguate identical fallback
    // tiles (two "Untitled, no year" entries must not collide on a key).
    val keyedEntries = remember(effectiveEntries) {
        effectiveEntries.mapIndexed { index, entry -> index to entry }
    }

    Column(modifier = modifier) {
        HomeRowTitle(
            title = title,
            contentPad = metrics.contentPad,
            onLongClick = onSectionLongClick,
        )
        HomeItemRow(
            items = keyedEntries,
            key = { (index, entry) -> entry.localItem?.id ?: "plugin_fallback_$index" },
            cardWidth = metrics.cardWidth,
            spacing = metrics.spacing,
            contentPad = metrics.contentPad,
            clippingEnabled = clippingEnabled,
            focusRequester = focusRequester,
            onRowFocused = onRowFocused,
            onFocusedItemChange = { (_, entry) ->
                entry.localItem?.let { local -> onFocusedItemChange?.invoke(local) }
            },
            edgeRefresh = edgeRefresh,
        ) { (_, entry), mod ->
            val local = entry.localItem
            if (local != null) {
                val memoizedClick = remember(local) { { onItemClick(local) } }
                val memoizedPlayClick = onPlayClick?.let { click -> remember(local, click) { { click(local) } } }
                val progressPercent = remember(local.id, local.playbackPositionTicks, local.runTimeTicks) {
                    local.progressFraction() ?: 0f
                }
                PosterCard(
                    item = local,
                    imageUrl = imageUrlBuilder(local),
                    fallbackUrls = fallbackImageUrlBuilder(local),
                    onClick = memoizedClick,
                    modifier = mod.width(metrics.cardWidth),
                    showProgress = local.hasPlaybackPosition,
                    progressPercent = progressPercent,
                    onPlayClick = memoizedPlayClick,
                    sharedElementKey = "poster_${local.id}",
                    clipToShape = clippingEnabled,
                    gradientBrush = posterScrimBrush,
                )
            } else {
                PluginRowFallbackTile(
                    entry = entry,
                    modifier = mod.width(metrics.cardWidth),
                )
            }
        }
    }
}

/**
 * The compact fallback tile for a plugin row entry the plugin matched to no
 * local library item: title + year text in the poster card's 2:3 slot, no
 * image loading of any kind (the wire payload carries no art and the entry
 * has no library item to resolve art from). Display-only — there is no
 * navigation target for an unmatched title.
 */
@Composable
private fun PluginRowFallbackTile(
    entry: JellyPlayRowEntry,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            val yearText = entry.year
            if (!yearText.isNullOrBlank()) {
                Text(
                    text = yearText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
            }
        }
    }
}
