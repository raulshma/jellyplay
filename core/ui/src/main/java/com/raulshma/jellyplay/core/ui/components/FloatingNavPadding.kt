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
 */
val floatingNavClearanceDp: Dp
    @Composable get() =
        Dimensions.floatingNavHeight + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
 *     plus the system `navigationBars` bottom inset. This alone guarantees the
 *     element never overlaps the fully-shown nav bar.
 *  2. **Dynamic ride-up** — reads [LocalFloatingNavOffset] inside the `offset`
 *     lambda (layout phase, no recomposition) so the element translates upward
 *     with the nav bar's slide animation, negated and clamped to one nav-height
 *     of travel.
 *
 * The caller keeps `.align(Alignment.BottomEnd)` and any TV focus wiring
 * (`.then(focusState.focusModifier)` / `.tvFocusIndicator(...)`) — those vary
 * per call site; only the clearance logic is shared here.
 *
 * @param extraBottom extra margin below the clearance (default 16.dp). Pass 0.dp
 *  if the caller already adds its own bottom margin.
 */
@Composable
fun Modifier.clearFloatingNav(extraBottom: Dp = 16.dp): Modifier {
    val navOffsetPx = LocalFloatingNavOffset.current
    val navBarBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val maxOffsetPx = with(LocalDensity.current) { Dimensions.floatingNavHeight.toPx() }
    return this
        .padding(bottom = extraBottom + Dimensions.floatingNavHeight + navBarBottomInset)
        .offset {
            // navOffsetPx() is positive when the bar has slid down (hidden) —
            // negate so the element moves up, clamped to one nav-height.
            val yOffset = (-navOffsetPx()).coerceAtMost(maxOffsetPx)
            IntOffset(x = 0, y = yOffset.toInt())
        }
}
