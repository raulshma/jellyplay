package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged

/**
 * The TV focus cursor (the glossary term) — everything "where does restored
 * focus land" knows about itself, in ONE module the three TV lazy containers
 * ([TvFocusableColumn], [TvFocusableGrid], [TvFocusableItemRow]) parameterize
 * by [TvFocusLayout]: the per-item fallback leaf ([TvItemFocusFallback]), the
 * clamp / one-shot-initial-grab / refresh-generation-re-grab effect ladder
 * ([rememberTvFocusCursor]), and the fallback-requester attachment policy.
 * Previously each container held a byte-identical private
 * `TvItemFocusFallback` plus a parallel cursor-memory ladder, held in
 * agreement by KDoc ("Mirrors the same handling in …").
 *
 * ## The fallback leaf contract (do not "optimize" it)
 *
 * The tracked focused-index [IntState] is read ONLY inside
 * [TvItemFocusFallback] — reading it in a container's body would recompose
 * every composed item on each focus move. The leaf attaches the container's
 * fallback [FocusRequester] to the single item at the tracked index, which is
 * what lazy-list recycling demands: requesters cannot be stable across
 * recycled items, so exactly one carries it ([Modifier.ifElse] is the same
 * idiom for non-lazy callers).
 *
 * ## The container modifier-order policy
 *
 * Column and grid wrap the same way: `tvFocusRestorer(fallback)` BEFORE
 * `focusGroup()` — the restorer must wrap the group's focus target, not sit
 * inside it. Placed after, it attaches to the container's items instead of
 * the group, and outer aggregated restorers clobber these single-slot
 * onEnter/onExit hooks; with this order, group entry restores the
 * last-focused item, falling back to the tracked cursor item. The horizontal
 * row ([TvFocusableItemRow]) is the one deliberate inversion — its chain
 * (onEnter hook → focusGroup → restorer → its own row requester) drives
 * entry through the row-level requester — and stays verbatim in that
 * container, pinned by the TV drawer focus-wiring suite.
 */
internal enum class TvFocusLayout(
    val initTag: String,
    val refreshTag: String,
) {
    /** Vertical list ([TvFocusableColumn] / [TvFocusablePagingColumn]). */
    Column("tv_column_init", "tv_column_refresh"),

    /** 2-D grid ([TvFocusableGrid]). */
    Grid("tv_grid_init", "tv_grid_refresh"),

    /**
     * Horizontal carousel ([TvFocusableItemRow]). Carries no
     * `refreshGeneration` — callers pass 0 and the re-grab ladder never fires.
     */
    ItemRow("tv_row_init", "tv_row_refresh"),
}

/**
 * One container's cursor: the saveable focused-index memory
 * ([rememberInt]) plus the fallback [FocusRequester] the leaf and the
 * container modifiers share. Created by [rememberTvFocusCursor], which also
 * runs the effect ladder.
 */
internal class TvFocusCursor(
    val layout: TvFocusLayout,
    val fallback: FocusRequester,
    private val index: MutableIntState,
    private val initialFocusRequested: MutableState<Boolean>,
) {
    /** Read-only view the leaf consumes (the read must stay inside the leaf). */
    val focusedIndexState: IntState get() = index

    /** The tracked cursor — written by the ladder below and the leaf's onFocused. */
    var focusedIndex: Int
        get() = index.intValue
        set(value) {
            index.intValue = value
        }

    internal var initialFocusGrabbed: Boolean
        get() = initialFocusRequested.value
        set(value) {
            initialFocusRequested.value = value
        }
}

/**
 * The cursor memory + effect ladder shared by all three TV lazy containers:
 *
 *  1. Clamp the saveable cursor to the live item count whenever it shrinks
 *     (a stale saved index must never point past the last item).
 *  2. One-shot initial grab: `tvFocusRestorer(fallback)` and
 *     `focusProperties { onEnter }` only react to focus *entering* the group;
 *     neither proactively grabs. When [requestInitialFocus] holds, grab focus
 *     on the tracked item the first time data arrives. The scroll-to-item
 *     before the grab ensures the saved item is composed before requesting
 *     focus (returning from a full-screen route can otherwise leave the saved
 *     index off-screen and the grab silently no-ops).
 *  3. Refresh re-grab: a completed refresh (filter/folder change) replaces
 *     the data under the old cursor — the caller scrolls to 0, so the saved
 *     index no longer matches what's composed. Reset the cursor and re-grab
 *     (retrying across frames for the recycled cell that hasn't re-attached
 *     the fallback requester yet); without this, focus falls out of the
 *     container during the reload and the drawer rail captures it. Item rows
 *     have no [refreshGeneration], so the guard never fires for them.
 *
 * [scrollToItem] abstracts the list/grid scroll seam; it is read through
 * [rememberUpdatedState] so a state swap mid-composition is honored like the
 * containers' other latest-value reads.
 */
@Composable
internal fun rememberTvFocusCursor(
    layout: TvFocusLayout,
    initialIndex: Int,
    isTv: Boolean,
    requestInitialFocus: Boolean,
    itemCount: Int,
    refreshGeneration: Int,
    scrollToItem: suspend (Int) -> Unit,
): TvFocusCursor {
    val fallback = remember { FocusRequester() }
    val index = rememberInt(initialIndex)
    val initialFocusRequested = remember { mutableStateOf(false) }
    val cursor = remember { TvFocusCursor(layout, fallback, index, initialFocusRequested) }
    val currentScrollToItem by rememberUpdatedState(scrollToItem)

    LaunchedEffect(itemCount) {
        if (itemCount > 0) {
            cursor.focusedIndex = cursor.focusedIndex.coerceIn(0, itemCount - 1)
        }
    }

    LaunchedEffect(itemCount > 0) {
        if (isTv && requestInitialFocus && itemCount > 0 && !cursor.initialFocusGrabbed) {
            cursor.initialFocusGrabbed = true
            currentScrollToItem(cursor.focusedIndex.coerceIn(0, itemCount - 1))
            cursor.fallback.tryRequestFocus(layout.initTag)
        }
    }

    LaunchedEffect(refreshGeneration) {
        if (isTv && refreshGeneration > 0 && itemCount > 0) {
            cursor.focusedIndex = 0
            currentScrollToItem(0)
            for (attempt in 1..3) {
                withFrameNanos { }
                if (cursor.fallback.tryRequestFocus(layout.refreshTag)) break
            }
        }
    }

    return cursor
}

/**
 * Renders [content] with [cursor.fallback] attached to the item at [index] only while the
 * tracked focus cursor points at [index]. The [TvFocusCursor.focusedIndexState] read must stay
 * inside this leaf — reading it in the container body recomposes every composed item on each
 * focus move.
 */
@Composable
internal fun TvItemFocusFallback(
    cursor: TvFocusCursor,
    itemCount: Int,
    index: Int,
    onFocused: () -> Unit,
    animatedModifier: Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    val currentFocusedIndex = cursor.focusedIndexState.intValue.coerceIn(0, itemCount - 1)
    val itemModifier = (if (currentFocusedIndex == index) {
        Modifier.focusRequester(cursor.fallback)
    } else {
        Modifier
    })
        .onFocusChanged {
            if (it.isFocused || it.hasFocus) {
                onFocused()
            }
        }
        .then(animatedModifier)
    content(itemModifier)
}
