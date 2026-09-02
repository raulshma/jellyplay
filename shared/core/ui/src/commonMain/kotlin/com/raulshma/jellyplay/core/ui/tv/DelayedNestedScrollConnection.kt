package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/**
 * By "stealing" a fraction of every scroll delta in [onPreScroll], this connection slows
 * focus-driven scrolling for a smoother feel on TV. Used in screens that mix paged LazyColumn
 * content with focus-driven auto-scroll (e.g. series overview, lyrics).
 *

 *
 * Typical usage:
 * ```
 * val delayedScroll = remember { DelayedNestedScrollConnection(yDelay = 0.6f) }
 * LazyColumn(modifier = Modifier.nestedScroll(delayedScroll)) { ... }
 * ```
 *
 * @param xDelay Fraction of horizontal scroll to steal (0f = no effect, 1f = full block).
 * @param yDelay Fraction of vertical scroll to steal.
 */
class DelayedNestedScrollConnection(
    private val xDelay: Float = 0f,
    private val yDelay: Float = .6f,
) : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (source == NestedScrollSource.UserInput) {
            Offset(available.x * xDelay, available.y * yDelay)
        } else {
            Offset.Zero
        }
}
