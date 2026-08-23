package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer

fun FocusRequester.tryRequestFocus(tag: String? = null): Boolean =
    try {
        requestFocus()
        true
    } catch (_: IllegalStateException) {
        false
    }

/**
 * Conditional modifier helper — the standard idiom for attaching a [Modifier.focusRequester] (or
 * any modifier) to exactly one item in a recycled lazy list.
 *
 * Lazy items can't carry stable focus requesters because they get re-created on recompose, so the
 * pattern is `.ifElse(index == position, Modifier.focusRequester(firstFocus))` — only the item
 * whose index equals the saved position receives the requester; all others get an empty Modifier.
 * Combined with [tvFocusRestorer] on the container this is how the app remembers focus through
 * lazy-list recycling and back-stack pops.
 */
fun Modifier.ifElse(
    condition: Boolean,
    ifTrueModifier: Modifier,
    ifFalseModifier: Modifier = Modifier,
): Modifier = then(if (condition) ifTrueModifier else ifFalseModifier)

/**
 * Drives the initial TV focus grab for a screen on (re-)entry. Accepts a nullable requester so
 * callers can pass `focusRequesters.getOrNull(position)` and no-op when the position is out of
 * bounds.
 *
 * Used at the top of every detail page (MovieDetails, SeriesDetails, EpisodeDetails, etc.) — it
 * fires once on first composition via [LaunchedEffect], which combined with Navigation 3's
 * `rememberSaveableStateHolderNavEntryDecorator` runs again on back-stack pops to restore focus.
 */
@Composable
fun RequestOrRestoreFocus(
    focusRequester: FocusRequester?,
    debugKey: String? = null,
) {
    if (focusRequester != null) {
        val isTv = LocalTvMode.current
        LaunchedEffect(Unit) {
            if (isTv) {
                for (attempt in 1..3) {
                    androidx.compose.runtime.withFrameNanos { }
                    if (focusRequester.tryRequestFocus(debugKey)) break
                }
            } else {
                focusRequester.tryRequestFocus(debugKey)
            }
        }
    }
}

/**
 * Saveable cursor memory for rows/grids that need to remember which card was last focused.
 * Survives configuration changes and back-stack pops (via Navigation 3's saveable-state holder).

 */
@Composable
fun rememberInt(initial: Int = 0) = rememberSaveable { mutableIntStateOf(initial) }

/**
 * Row/column position saveable. Use for any grid that tracks both row + column. The saver
 * preserves both ints across process death so focus restoration lands on the exact card.
 */
data class RowColumn(val row: Int = -1, val column: Int = -1)

private val RowColumnSaver: Saver<RowColumn, *> = Saver(
    save = { listOf(it.row, it.column) },
    restore = { RowColumn(it[0], it[1]) },
)

@Composable
fun rememberRowColumn(initial: RowColumn = RowColumn(-1, -1)) =
    rememberSaveable(stateSaver = RowColumnSaver) { mutableStateOf(initial) }

/**
 * TV-gated [Modifier.focusRestorer].
 *
 * Ordering contract: apply BEFORE the focus target it should manage —
 * `Modifier.tvFocusRestorer(fallback).focusGroup()` for a lazy container. Focus
 * properties attach to the next INNER focus target, so a restorer placed after
 * `focusGroup()` attaches to the container's items (a no-op) instead of the
 * group; and because `onEnter`/`onExit` are single-slot properties where the
 * outermost aggregator wins, any restorer wrapping a whole screen clobbers the
 * enter/exit hooks of every focus group inside it. Never place one above
 * disparate sibling groups (e.g. a screen's content slot) — give each group its
 * own restorer instead.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun Modifier.tvFocusRestorer(fallback: FocusRequester? = null): Modifier {
    val isTv = LocalTvMode.current
    if (!isTv) return this
    return if (fallback != null) {
        this.focusRestorer(fallback)
    } else {
        this.focusRestorer()
    }
}

@Composable
fun rememberInitialFocus(): FocusRequester {
    val requester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        requester.tryRequestFocus()
    }
    return requester
}

/**
 * Drives the initial TV focus grab for a screen whose list/grid data loads asynchronously.
 * `Modifier.focusRestorer(fallback)` and `Modifier.focusProperties { onEnter = ... }` only react to
 * focus *entering* a group from outside; neither proactively grabs focus. So a grid/row whose data
 * arrives after first composition would stay unfocused (the first D-pad press does nothing). This
 * helper requests focus once on [focusRequester] the first time [itemCount] is non-empty, and runs
 * [onReady] (e.g. to clamp a saved focused index to the live item count) on every count change.
 *
 * The retry loop (3 attempts with one frame between each) defends against the layout race where
 * `LaunchedEffect(itemCount)` fires before the newly-composed items have been laid out — the
 * `FocusRequester` isn't attached to a placed node yet and `requestFocus()` would throw. This
 * mirrors the `for (attempt in 1..3) { withFrameNanos{}; if (tryRequestFocus) break }` idiom used
 * across the codebase, now consolidated here.
 *
 * [refreshGeneration] re-arms the grab: bump it when the backing data is replaced (filter change,
 * folder switch) so the one-shot "done" latch resets and focus returns to the refreshed content —
 * otherwise the reload's scroll reset orphans focus and the drawer rail captures it. The tracked
 * index reset is the caller's responsibility (the caller owns the index state).
 */
@Composable
fun TvGrabInitialFocus(
    focusRequester: FocusRequester,
    itemCount: Int,
    onReady: () -> Unit = {},
    tag: String = "tv_init",
    refreshGeneration: Int = 0,
) {
    val isTv = LocalTvMode.current
    val done = remember { mutableStateOf(false) }
    var lastGeneration by remember { mutableIntStateOf(refreshGeneration) }
    LaunchedEffect(itemCount, refreshGeneration) {
        if (itemCount > 0) {
            onReady()
            val isRefresh = refreshGeneration != lastGeneration
            if (isRefresh) lastGeneration = refreshGeneration
            if (isTv && (!done.value || isRefresh)) {
                done.value = true
                for (attempt in 1..3) {
                    androidx.compose.runtime.withFrameNanos { }
                    if (focusRequester.tryRequestFocus(tag)) break
                }
            }
        }
    }
}
