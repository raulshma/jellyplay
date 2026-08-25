@file:OptIn(kotlinx.coroutines.FlowPreview::class)
package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tryRequestFocus
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Holds the Home screen's scroll/focus state and its side-effects, hoisted out
 * of the monolithic `MainHomeContent`.
 *
 * Owns:
 *  - the single shared [LazyListState] (restore from [HomeViewModel.getHomeScrollPosition]
 *    once on creation; TV always starts at the top so the hero anchors focus),
 *  - the debounced scroll-position persist effect (survives process death),
 *  - [scrollFraction] (how far the hero has scrolled under the top dock),
 *  - the per-row [androidx.compose.ui.focus.FocusRequester]s for D-pad row
 *    restoration, and
 *  - the TV back-stack pop focus-restore effect.
 *
 * Behaviour is identical to the inline implementation previously in
 * `HomeScreen.kt`; this groups it so `MainHomeContent` no longer owns scroll
 * plumbing.
 */
class HomeScrollState internal constructor(
    val listState: LazyListState,
    private val savePosition: (Int, Int) -> Unit,
) {
    /** 0..1 fraction of the hero parallax/transition range consumed by scrolling. */
    val scrollFraction: Float
        @Composable get() {
            val density = LocalDensity.current
            val transitionRangePx = remember(density) { with(density) { 140.dp.toPx() } }
            val fraction by remember {
                derivedStateOf {
                    if (listState.firstVisibleItemIndex > 0) 1f
                    else (listState.firstVisibleItemScrollOffset.toFloat() / transitionRangePx).coerceIn(0f, 1f)
                }
            }
            return fraction
        }
}

/**
 * Remembers a [HomeScrollState] and wires its persist + TV focus-restore effects.
 */
@Composable
internal fun rememberHomeScrollState(
    savePosition: (Int, Int) -> Unit,
    initialPosition: HomeScrollPosition,
): HomeScrollState {
    val isTv = LocalTvMode.current
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(
            firstVisibleItemIndex = if (isTv) 0 else initialPosition.firstVisibleItemIndex,
            firstVisibleItemScrollOffset = if (isTv) 0 else initialPosition.firstVisibleItemScrollOffset,
        )
    }
    val state = remember(listState) { HomeScrollState(listState, savePosition) }

    // Persist the scroll position as the user scrolls (debounced) so it
    // survives process death.
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .debounce(500)
            .distinctUntilChanged()
            .collect { (index, offset) ->
                savePosition(index, offset)
            }
    }

    return state
}

/**
 * Restores D-pad focus to the last-visited content row when returning to Home
 * (e.g. on a back-stack pop). `LaunchedEffect(Unit)` re-fires via the saveable
 * -state holder. Scrolled the saved row fully into view (so its FocusRequester
 * is attached, avoiding the half-clipped hero a minimal bring-into-view caused
 * previously) then re-requests focus.
 *
 * The scroll is deferred one frame (`withFrameNanos`) so the first frame
 * paints before the measure/layout work `scrollToItem` performs, avoiding
 * first-frame jank on a back-stack pop.
 */
@Composable
internal fun RestoreHomeRowFocus(
    listState: LazyListState,
    savedRow: Int,
    sectionCount: Int,
    newsletterBannerVisible: Boolean,
    rowFocusRequesters: () -> List<androidx.compose.ui.focus.FocusRequester>,
) {
    val isTv = LocalTvMode.current
    val savedRowIsValid = savedRow in 0..(sectionCount - 1)
    LaunchedEffect(Unit) {
        if (isTv && savedRowIsValid && sectionCount > 0) {
            withFrameNanos { }
            val headerOffset = 1 + (if (newsletterBannerVisible) 1 else 0)
            listState.scrollToItem(savedRow + headerOffset)
            rowFocusRequesters().getOrNull(savedRow)?.tryRequestFocus("home_row_restore")
        }
    }
}
