package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Scrolls the focused/bring-into-view target so it lands in the vertical center of the scrollable
 * container, instead of the default [BringIntoViewSpec] which only scrolls the minimum distance
 * required to make the item visible — leaving it pinned to the top or bottom edge.
 *
 * Used by the settings screens so a search result navigated to via [highlightSettingId] lands in
 * the middle of the viewport rather than at the bottom edge.
 *
 * The standard centering math: scroll so that the item's leading offset moves to
 * `(containerSize - itemSize) / 2`.
 */
object CenterBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        return offset - (containerSize - size) / 2f
    }
}
