package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass

/** Item counts per discover row on compact (phone) layouts. */
internal val COMPACT_DISCOVER_PATTERN = listOf(3, 2, 3)

/** Item counts per discover row on expanded (tablet/TV) layouts. */
internal val EXPANDED_DISCOVER_PATTERN = listOf(5, 4, 6, 5)

@Composable
fun rememberDiscoverRows(
    allDiscoverItems: List<SeerrSearchItem>,
): List<List<SeerrSearchItem>> {
    val adaptiveInfo = LocalAdaptiveInfo.current
    return remember(allDiscoverItems, adaptiveInfo.windowSizeClass) {
        val pattern = if (adaptiveInfo.windowSizeClass == WindowSizeClass.Compact) {
            COMPACT_DISCOVER_PATTERN
        } else {
            EXPANDED_DISCOVER_PATTERN
        }
        partitionDiscoverRows(allDiscoverItems, pattern)
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
