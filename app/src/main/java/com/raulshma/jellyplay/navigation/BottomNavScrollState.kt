package com.raulshma.jellyplay.navigation

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import com.raulshma.jellyplay.core.designsystem.theme.Dimensions
import com.raulshma.jellyplay.core.ui.components.ScrollDirectionVisibility

/**
 * The floating nav-bar's hide-on-scroll state, extracted from `MainContent`
 * (the `NavRequestController` idiom: a plain class with narrow public members,
 * constructed in one `remember` call): core/ui's shared
 * [ScrollDirectionVisibility] policy object, the px-offset [MutableFloatState]
 * the phone layout's NestedScrollConnection animates, and the stable
 * [floatingNavOffset] getter [LocalFloatingNavVisibility]'s sibling local
 * provides.
 */
@Stable
internal class BottomNavScrollState(
    val scrollVisibility: ScrollDirectionVisibility,
    val offsetHeightPx: MutableFloatState,
    val floatingNavOffset: () -> Float,
)

/**
 * Constructs the [BottomNavScrollState] on the composition's stable seams.
 * The nav-bar height resolves from [Dimensions.floatingNavHeight] and the
 * current density here; the animation target recomputes from it on every
 * recomposition of the caller, exactly as the former inline block did.
 */
@Composable
internal fun rememberBottomNavScrollState(): BottomNavScrollState {
    val bottomNavHeight = Dimensions.floatingNavHeight // Canonical floating nav-bar height
    val bottomNavHeightPx = with(LocalDensity.current) { bottomNavHeight.toPx() }
    val bottomNavOffsetHeightPx = remember { mutableFloatStateOf(0f) }
    // Hide-on-scroll policy for the floating nav bar: core/ui's shared
    // ScrollDirectionVisibility — the same policy the home dock feeds from its
    // LazyListState. The nav's mechanism adapter is the NestedScrollConnection
    // in PhoneContent below. There is deliberately NO at-top rule
    // (forceVisibleAtTop = false — the nav never force-shows on returning to a
    // list's top) and NO per-update canHide gate (canHide = null — its only
    // gate is the settings-off reset LaunchedEffect in PhoneContent), matching
    // the former inline state machine exactly. The module owns the visible
    // state: `visibleState` is what LocalFloatingNavVisibility provides, and
    // the offset animation below reads `visible` exactly as it read the former
    // bare MutableState.
    val bottomNavScrollVisibility = remember {
        ScrollDirectionVisibility(thresholdPx = 15f, forceVisibleAtTop = false)
    }
    var isBottomNavVisible by bottomNavScrollVisibility.visibleState

    val animatedBottomNavOffset by animateFloatAsState(
        targetValue = if (isBottomNavVisible) 0f else -bottomNavHeightPx * 2,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "bottomNavOffset"
    )

    LaunchedEffect(animatedBottomNavOffset) {
        bottomNavOffsetHeightPx.floatValue = animatedBottomNavOffset
    }

    // Stable getter for the floating-nav offset. Reading
    // `bottomNavOffsetHeightPx.floatValue` here would force `MainContent`
    // to recompose on every animation frame of the nav-bar slide (which re-runs
    // the whole TV/Phone/FullScreen branch dispatch). Exposing a `() -> Float`
    // instead lets leaf consumers read the value inside Modifier.offset { … }
    // (layout phase) — no recomposition at all, just relayout.
    val floatingNavOffset: () -> Float = remember(bottomNavOffsetHeightPx) {
        { bottomNavOffsetHeightPx.floatValue }
    }

    return BottomNavScrollState(
        scrollVisibility = bottomNavScrollVisibility,
        offsetHeightPx = bottomNavOffsetHeightPx,
        floatingNavOffset = floatingNavOffset,
    )
}
