package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf

/**
 * One hide-on-scroll visibility policy, two mechanism adapters.
 *
 * The home dock (`HomeTopDockScrim` in `shared/feature/home`'s `HomeScreen.kt`)
 * and the floating bottom nav (`PhoneContent` in `:app`'s `JellyPlayApp.kt`)
 * implement the same policy — hide on scroll-down, reveal on scroll-up, with a
 * dead-zone threshold so slow drifts don't flicker — through two different
 * mechanisms. This class owns the policy; each call site keeps only a thin
 * feed:
 *
 *  - **LazyListState feed** — [onListScrolled], driven from a `snapshotFlow`
 *    over `(firstVisibleItemIndex, firstVisibleItemScrollOffset)` (dock). The
 *    dock is an overlay sibling of the list, not a scroll ancestor, so it
 *    cannot host a `NestedScrollConnection` and must derive direction from
 *    list state. Index changes bypass the threshold; offset changes are
 *    compared **per emission** against the previous emission (NOT accumulated
 *    across emissions), so many small same-direction steps never cross the
 *    threshold on their own. Only when [forceVisibleAtTop] is on does
 *    `index == 0 && offset == 0` force the state visible (dock ON, nav OFF —
 *    the nav never force-shows on returning to a list's top).
 *  - **NestedScrollConnection feed** — [onScrollDelta], driven from
 *    `onPreScroll`'s `available.y` (nav). Nav's sign convention: negative delta
 *    = scrolling down / content moving up → hide; positive → show; strict
 *    inequalities against the threshold, compared per callback (same
 *    non-accumulating semantics).
 *
 * Every hint funnels through one resolve: when the [canHide] gate returns
 * `false` the state is forced visible; otherwise the hint is applied. The gate
 * is evaluated only on updates that produce a hint — it never retroactively
 * flips the state on no-op emissions (the dock's `!canHide || isSearchFocused`
 * forced-visible path additionally drives [resetToVisible] from its
 * `LaunchedEffect`, so the gate here is belt-and-braces). Passing
 * `canHide = null` (the default) means "no forced gate" — exactly the nav's
 * semantics, whose only gate is the settings-off reset effect.
 *
 * First-emission priming: the very first [onListScrolled] (or an explicit
 * [prime]) only records the tracking position and makes no visibility
 * decision — there is no previous emission to compare against yet. Call
 * [prime] when a collection effect (re)starts so tracking re-syncs with the
 * live list position, mirroring the call site's former per-effect
 * initialization.
 *
 * State ownership: the backing [MutableState] is exposed as [visibleState] so
 * a call site can hand it directly to [LocalFloatingNavVisibility] (which
 * wants a `MutableState<Boolean>`); [visible] is the read-only convenience
 * view used by animation targets.
 *
 * @param thresholdPx Dead-zone in pixels. A hide/show hint from the offset or
 *   delta feed must strictly exceed it to be applied (dock: 12dp converted
 *   with the local density; nav: `15f` raw px).
 * @param forceVisibleAtTop Whether the list feed's at-top position
 *   (`index == 0 && offset == 0`) forces the state visible. Dock: `true`.
 *   Nav: `false`.
 * @param canHide Per-update forced-visible gate — evaluated whenever an
 *   update produces a hide/show hint; `false` forces the state visible
 *   regardless of the hint. Dock: `{ hideOnScrollSetting && !isTv &&
 *   !isSearchFocused }`; nav: `null` (no gate — see above).
 * @param visibleInitialState Starting state; both call sites start visible.
 */
class ScrollDirectionVisibility(
    private val thresholdPx: Float,
    private val forceVisibleAtTop: Boolean,
    private val canHide: (() -> Boolean)? = null,
    visibleInitialState: Boolean = true,
) {
    /** Backing state — exposed so nav can provide it via [LocalFloatingNavVisibility]. */
    val visibleState: MutableState<Boolean> = mutableStateOf(visibleInitialState)

    /** Read-only view of [visibleState] for animation targets and tests. */
    val visible: Boolean get() = visibleState.value

    private var prevIndex = 0
    private var prevOffset = 0f
    private var primed = false

    /**
     * (Re-)arms the list-feed tracking at [index]/[offsetPx] without making a
     * visibility decision. Call when a collection effect (re)starts so the
     * next [onListScrolled] compares against the live list position, exactly
     * like the dock's former per-effect `prevIndex`/`prevOffset` initialization.
     */
    fun prime(index: Int, offsetPx: Float) {
        prevIndex = index
        prevOffset = offsetPx
        primed = true
    }

    /**
     * LazyListState feed (dock). Applies the policy to one
     * `(firstVisibleItemIndex, firstVisibleItemScrollOffset)` emission. The
     * first call only primes tracking (no decision); afterwards every emission
     * is compared against the previous one:
     * at-top (when [forceVisibleAtTop]) → visible; index advance → hidden;
     * index regress → visible; offset delta past [thresholdPx] → hidden/show.
     */
    fun onListScrolled(index: Int, offsetPx: Float) {
        if (!primed) {
            prime(index, offsetPx)
            return
        }
        val hint = when {
            forceVisibleAtTop && index == 0 && offsetPx == 0f -> true
            index > prevIndex -> false
            index < prevIndex -> true
            offsetPx - prevOffset > thresholdPx -> false
            prevOffset - offsetPx > thresholdPx -> true
            else -> null
        }
        prevIndex = index
        prevOffset = offsetPx
        if (hint != null) resolve(hint)
    }

    /**
     * NestedScrollConnection feed (nav). Nav's sign convention: [deltaY]
     * negative = scrolling down / content moving up → hidden; positive =
     * scrolling up → visible. Strict inequalities, compared per callback —
     * no accumulation.
     */
    fun onScrollDelta(deltaY: Float) {
        when {
            deltaY < -thresholdPx -> resolve(hint = false)
            deltaY > thresholdPx -> resolve(hint = true)
        }
    }

    /**
     * Unconditional forced-visible — the settings-off reset (nav) and the
     * dock's `!canHide || isSearchFocused` forced branch. Not gated by
     * [canHide]: it IS the forced path.
     */
    fun resetToVisible() {
        visibleState.value = true
    }

    /** One funnel for every hint: the [canHide] gate vetoes any hide. */
    private fun resolve(hint: Boolean) {
        if (canHide?.invoke() == false) {
            visibleState.value = true
            return
        }
        visibleState.value = hint
    }
}
