package com.raulshma.jellyplay.feature.home

/**
 * Owns the home list's saved scroll position — the restore anchor read once
 * by `rememberHomeScrollState` on (re)composition and re-saved on every scroll
 * idle. Pure synchronous state: no flows, no scope, deliberately outside
 * [HomeUiState] because it is not render state (a uiState field would change
 * object equality on every save and recompose the whole home body for a value
 * nothing composable reads).
 *
 * Writers: the scroll-state save callback (user scrolled), the VM's
 * user-switch/sign-out collectors and manual-refresh preamble (reset to top).
 * Lifted from HomeViewModel so the reset/save/clamp policy lives with the
 * state it guards — the VM keeps one-line delegates so the UI call sites are
 * unchanged.
 */
internal class ScrollPositionStore {

    private var position = HomeScrollPosition()

    /** The last saved position (default 0/0 before the first save or after [reset]). */
    fun get(): HomeScrollPosition = position

    /**
     * Records the first visible item's index/offset. Negative inputs are
     * clamped to 0: LazyListState can briefly report pre-first-item layout
     * coordinates during measurement, and a negative anchor would restore to
     * an invalid target.
     */
    fun save(firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) {
        position = HomeScrollPosition(
            firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0),
        )
    }

    /** Returns the anchor to the top — user switch, sign-out, manual refresh. */
    fun reset() {
        position = HomeScrollPosition()
    }
}
