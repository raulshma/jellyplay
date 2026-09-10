package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.components.SeerrCardLoadingState

/** Item counts per discover row on compact (phone) layouts. */
internal val COMPACT_DISCOVER_PATTERN = listOf(3, 2, 3)

/** Item counts per discover row on expanded (tablet/TV) layouts. */
internal val EXPANDED_DISCOVER_PATTERN = listOf(5, 4, 6, 5)

/**
 * The discover pattern for a window size — the single source for both read
 * sites ([rememberDiscoverRows]' partition and HomeContentList's per-row
 * target sizes), which previously duplicated the if/else.
 */
internal fun discoverPatternFor(windowSizeClass: WindowSizeClass): List<Int> =
    if (windowSizeClass == WindowSizeClass.Compact) COMPACT_DISCOVER_PATTERN else EXPANDED_DISCOVER_PATTERN

/**
 * The card width fitting [targetSize] cards (plus their [spacing] gaps) into
 * [rowWidth] — the single source for the discover rows and the *arr
 * recently-grabbed row, which previously duplicated the formula.
 */
internal fun discoverItemWidth(rowWidth: Dp, spacing: Dp, targetSize: Int): Dp =
    (rowWidth - spacing * (targetSize - 1)) / targetSize

@Composable
fun rememberDiscoverRows(
    allDiscoverItems: List<SeerrSearchItem>,
): List<List<SeerrSearchItem>> {
    val adaptiveInfo = LocalAdaptiveInfo.current
    return remember(allDiscoverItems, adaptiveInfo.windowSizeClass) {
        partitionDiscoverRows(allDiscoverItems, discoverPatternFor(adaptiveInfo.windowSizeClass))
    }
}

/**
 * Chunks [items] into rows of [pattern]'s sizes, cycling the pattern; the
 * last row is truncated to what remains. Pure — [rememberDiscoverRows] only
 * supplies the layout-dependent pattern.
 */
internal fun partitionDiscoverRows(
    items: List<SeerrSearchItem>,
    pattern: List<Int>,
): List<List<SeerrSearchItem>> {
    val result = mutableListOf<List<SeerrSearchItem>>()
    var i = 0
    var patternIdx = 0
    while (i < items.size) {
        val targetSize = pattern[patternIdx % pattern.size]
        val rowSize = targetSize.coerceAtMost(items.size - i)
        // Copy the sublist so each row owns an independent list rather than
        // a live view backed by items — avoids pinning the parent
        // list in memory and guards against ConcurrentModificationException.
        result.add(items.subList(i, i + rowSize).toList())
        i += rowSize
        patternIdx++
    }
    return result
}

/**
 * The [SeerrDiscoverRow] arguments every discover-ish row in the home list
 * shares (padding, spacing, theming, clipping, and the loading / prefetch /
 * click / request sinks) — nine identical arguments at the two former call
 * sites, so [DiscoverRowSlot] call sites pass only what differs. Lives here
 * rather than in HomeRowChassis.kt because that file is the pure row-chassis
 * dispatch while this one owns the discover geometry (patterns, partition,
 * widths).
 */
@Immutable
internal data class DiscoverRowSlotArgs(
    val rowHorizontalPadding: Dp,
    val spacing: Dp,
    val backgroundColor: Color,
    val homeBackdropEnabled: Boolean,
    val clippingEnabled: Boolean,
    val seerrCardLoadingState: SeerrCardLoadingState,
    val seerrPrefetch: (Int, String, () -> Unit) -> Unit,
    val onSeerrItemClick: (Int, String) -> Unit,
    val onSeerrRequest: (SeerrSearchItem) -> Unit,
)

/**
 * The home list's ONE [SeerrDiscoverRow] invocation — the shared nine
 * arguments ride [args] once, so the discover rows and the *arr
 * recently-grabbed row pass only what differs (items, the pattern-derived
 * [targetSize] over the shared [rowWidth]). The lazy keys/contentTypes stay
 * at the call sites; only the row content is slotted here.
 */
@Composable
internal fun DiscoverRowSlot(
    items: List<SeerrSearchItem>,
    targetSize: Int,
    rowWidth: Dp,
    args: DiscoverRowSlotArgs,
) {
    val itemWidth = remember(rowWidth, args.spacing, targetSize) {
        discoverItemWidth(rowWidth, args.spacing, targetSize)
    }
    SeerrDiscoverRow(
        items = items,
        itemWidth = itemWidth,
        rowHorizontalPadding = args.rowHorizontalPadding,
        spacing = args.spacing,
        backgroundColor = args.backgroundColor,
        homeBackdropEnabled = args.homeBackdropEnabled,
        clippingEnabled = args.clippingEnabled,
        seerrCardLoadingState = args.seerrCardLoadingState,
        seerrPrefetch = args.seerrPrefetch,
        onSeerrItemClick = args.onSeerrItemClick,
        onSeerrRequest = args.onSeerrRequest,
    )
}
