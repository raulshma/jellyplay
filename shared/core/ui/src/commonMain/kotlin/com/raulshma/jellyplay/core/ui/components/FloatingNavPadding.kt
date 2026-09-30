package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.Dimensions

/**
 * The canonical vertical clearance (in dp) a bottom-anchored floating element
 * must reserve so it never sits under the floating navigation bar: the bar's
 * own height plus the system gesture/navigation-bar inset.
 *
 * Read via a `@Composable` getter because [WindowInsets.navigationBars] is a
 * composition-local. Use for [androidx.compose.foundation.lazy.LazyColumn]
 * `contentPadding` (so the last item scrolls clear of the FAB/nav) and anywhere
 * else a plain dp value is needed.
 *
 * Presence-aware: where the nav bar is not painted at all
 * ([LocalFloatingNavPresent] `false` — signed-out auth host, TV, expanded
 * layouts, full-screen routes) the bar's height collapses and only
 * the system inset remains, so callers don't reserve space for a bar that
 * never shows.
 */
val floatingNavClearanceDp: Dp
    @Composable get() {
        val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        if (!LocalFloatingNavPresent.current) return navBarBottomInset
        return Dimensions.floatingNavHeight + navBarBottomInset
    }

/**
 * Modifier that lifts a bottom-floating element (FAB, selection bar, mini-player)
 * clear of the floating navigation bar.
 *
 * Replaces the hand-duplicated `padding(...) { floatingNavHeight + navBars }` +
 * `offset { (-navOffset).coerceAtMost(max) }` idiom that was copy-pasted across
 * ~10 screens (see `UserManagementScreen` for the reference implementation).
 *
 * Two parts:
 *  1. **Static reservation** — [extraBottom] margin plus `Dimensions.floatingNavHeight`
 *     plus the system `navigationBars` bottom inset (unless [includeSystemInset]
 *     is `false`). This alone guarantees the
 *     element never overlaps the fully-shown nav bar. The bar's height term is
 *     presence-gated ([LocalFloatingNavPresent]): where no floating nav is
 *     painted (signed-out auth host, TV, expanded/rail layouts, full-screen
 *     routes) only the margin + inset remain, so the element sits at
 *     its natural resting place instead of a phantom bar-height above it.
 *  2. **Dynamic ride-up** — reads [LocalFloatingNavOffset] inside the `offset`
 *     lambda (layout phase, no recomposition) so the element translates upward
 *     with the nav bar's slide animation, negated and clamped to one nav-height
 *     of travel. Where the nav is absent the provided getter reads `0f`, so
 *     the offset collapses to zero with no extra gating at the call site.
 *
 * The caller keeps `.align(Alignment.BottomEnd)` and any TV focus wiring
 * (`.then(focusState.focusModifier)` / `.tvFocusIndicator(...)`) — those vary
 * per call site; only the clearance logic is shared here.
 *
 * @param extraBottom extra margin below the clearance (default 16.dp). Pass 0.dp
 *  if the caller already adds its own bottom margin.
 * @param includeSystemInset whether the system `navigationBars` bottom inset is
 *  part of the clearance (default). Pass `false` when the enclosing `Scaffold`
 *  already consumes that inset and hands it to content via its `PaddingValues`,
 *  so it isn't applied twice.
 */
@Composable
fun Modifier.clearFloatingNav(
    extraBottom: Dp = 16.dp,
    includeSystemInset: Boolean = true,
): Modifier {
    val navOffsetPx = LocalFloatingNavOffset.current
    val navBarBottomInset = if (includeSystemInset) {
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    } else {
        0.dp
    }
    val maxOffsetPx = with(LocalDensity.current) { Dimensions.floatingNavHeight.toPx() }
    val navClearance = if (LocalFloatingNavPresent.current) Dimensions.floatingNavHeight else 0.dp
    return this
        .padding(bottom = extraBottom + navClearance + navBarBottomInset)
        .offset {
            // navOffsetPx() is positive when the bar has slid down (hidden) —
            // negate so the element moves up, clamped to one nav-height.
            val yOffset = (-navOffsetPx()).coerceAtMost(maxOffsetPx)
            IntOffset(x = 0, y = yOffset.toInt())
        }
}
