package com.raulshma.jellyplay.navigation

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The phone shell's chrome-clearance placement fold — the bare dp numbers
 * PhoneContent's inline placement forks used to carry, named as one
 * declaration: which floating chrome element sits how far above the system
 * navigation bar in which layout (pure `element × layout → offset` dp math;
 * callers add the `systemNavBarBottom`/`statusBarTop` insets they already
 * compute). Pinned by ShellChromePlacementTest (plain JVM test — the values
 * below are the truth table).
 *
 * Pure on purpose: no composition, no density, no state — the px-valued
 * scroll-coupled offsets take their already-converted pixel inputs, so this
 * file stays a plain-JUnit-testable table (the FullScreenRoutePolicy
 * precedent).
 */
internal enum class ShellChromeElement {
    /** The audio mini player (AppMiniPlayerHost). */
    MiniPlayer,

    /** The persistent Play On transport bar (PlayOnMiniBar). */
    PlayOnMiniBar,

    /** The ExpressiveFloatingNavigationBar itself. */
    FloatingNavBar,
}

/**
 * Bottom clearance the element paddes above the system navigation bar,
 * by layout. The floating nav bar rides [ShellChromeElement.FloatingNavBar]'s
 * own clearance; the mini player and the Play On bar sit above it on phones
 * (no rail) and near-flush on expanded widths (rail layout, no floating
 * bar painted — LocalFloatingNavPresent is false there):
 *  - MiniPlayer: 60.dp above the bar on phones (clears the floating nav),
 *    2.dp on expanded widths (only the system inset to clear).
 *  - PlayOnMiniBar: 72.dp above the bar on phones (a taller bar), 8.dp on
 *    expanded widths.
 *  - FloatingNavBar: 4.dp — the bar's own breathing room.
 *
 * @param isExpanded whether the phone-family shell is in the expanded
 *   (NavigationRail) layout — the same `isExpanded` PhoneContent forks on.
 */
internal fun shellChromeBottomClearance(
    element: ShellChromeElement,
    isExpanded: Boolean,
): Dp = when (element) {
    ShellChromeElement.MiniPlayer -> if (isExpanded) 2.dp else 60.dp
    ShellChromeElement.PlayOnMiniBar -> if (isExpanded) 8.dp else 72.dp
    ShellChromeElement.FloatingNavBar -> 4.dp
}

/**
 * The compact mini player's scroll-coupled vertical offset, px math exactly
 * as PhoneContent's offset block folded it: rise with the hiding floating
 * nav bar (the hide offset runs NEGATIVE — it animates toward
 * `-2 × navHeight` fully hidden), but never travel further than the bar's
 * own height. The expanded-width mini player does not move — no floating
 * bar exists to follow.
 *
 * @param bottomNavHideOffsetPx the live hide-on-scroll offset
 *   (BottomNavScrollState's offsetHeightPx: 0 while visible, negative while
 *   hiding, bottoming at -2 × navHeight).
 * @param floatingNavHeightPx Dimensions.floatingNavHeight converted at the
 *   call site's density.
 */
internal fun miniPlayerNavCoupledOffsetYpx(
    bottomNavHideOffsetPx: Float,
    floatingNavHeightPx: Float,
): Int = (-bottomNavHideOffsetPx).coerceAtMost(floatingNavHeightPx).roundToInt()

/**
 * The floating nav bar's scroll-coupled vertical offset: the negated
 * hide-on-scroll offset, folded exactly as PhoneContent's nav-bar modifier
 * did (`-offset.roundToInt()` — the negation OUTSIDE the rounding, matching
 * the former expression bit for bit).
 */
internal fun floatingNavBarOffsetYpx(bottomNavHideOffsetPx: Float): Int =
    -bottomNavHideOffsetPx.roundToInt()

/**
 * The global "More" overflow menu's padding insets, by layout. The
 * ALIGNMENT stays at the call site (it picks TopStart vs BottomEnd, a
 * compose-side concern); only the bare numbers are folded here:
 *  - expanded: docked top-start beside the rail, a small margin keeping the
 *    pills flush to the drawer, anchored under the status bar
 *    (start 12.dp / top statusBarTop + 8.dp).
 *  - compact: docked bottom-end beside the floating nav toggle
 *    (end 16.dp / bottom 4.dp).
 *
 * @param statusBarTop the status-bar inset the expanded anchor hangs under
 *   (the call site's already-computed padding value).
 */
internal data class OverflowMenuInsets(
    val start: Dp,
    val top: Dp,
    val end: Dp,
    val bottom: Dp,
)

internal fun overflowMenuInsets(isExpanded: Boolean, statusBarTop: Dp): OverflowMenuInsets =
    if (isExpanded) {
        OverflowMenuInsets(start = 12.dp, top = statusBarTop + 8.dp, end = 0.dp, bottom = 0.dp)
    } else {
        OverflowMenuInsets(start = 0.dp, top = 0.dp, end = 16.dp, bottom = 4.dp)
    }
