package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.foundation.gestures.BringIntoViewSpec

/**
 * Overrides scrolling so that the item being scrolled to lands at the top of the view offset by
 * the provided pixels, instead of the default BringIntoViewSpec which only scrolls until the
 * focused item is barely visible at the edge.
 *
 * On TV, when a details header sits above a grid, you want the focused card to land *below* the
 * header — so [spaceAbovePx] reserves room for the header.
 *
 * Note: this applies to ALL scrollable composables within its scope, so a LazyColumn of LazyRows
 * likely needs nested `LocalBringIntoViewSpec` overrides (revert the inner rows to the default
 * spec).
 */
class ScrollToTopBringIntoViewSpec(val spaceAbovePx: Float = 100f) : BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float {
        return offset - spaceAbovePx
    }
}

/** Common default: leaves a 100px gap above the focused item. */
val DefaultScrollToTopBringIntoViewSpec = ScrollToTopBringIntoViewSpec()

