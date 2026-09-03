package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox as Material3PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp

/**
 * Drop-in replacement for
 * [androidx.compose.material3.pulltorefresh.PullToRefreshBox] that stops
 * mouse-wheel/trackpad scrolling from triggering a refresh.
 *
 * Material3's nested-scroll connection accumulates pull distance from every
 * unconsumed `UserInput` delta regardless of which pointer produced it. On
 * desktop and web, scrolling with a wheel or trackpad at the top of a page
 * leaves unconsumed backward delta, so a few wheel ticks pull out the refresh
 * indicator and can fire [onRefresh] — "pull to refresh" is a touch idiom,
 * not a wheel one (same reasoning as [mouseDragToScroll]).
 *
 * The fix: a guard [NestedScrollConnection] placed between the content and
 * Material3's connection (as a descendant it sees the unconsumed leftover
 * first) that swallows backward vertical deltas while no touch pointer is
 * down. Touch pulls pass through untouched, so phones and tablets keep
 * native pull-to-refresh, and touch screens still respond to a finger.
 *
 * The wrapper also publishes its [onRefresh] into [LocalPullToRefreshRegistry]
 * while composed and enabled, so a shell-level refresh affordance (the desktop
 * File→Refresh menu item) reaches every pull-to-refresh screen without
 * per-screen wiring.
 */
@Composable
fun PullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    state: PullToRefreshState = rememberPullToRefreshState(),
    contentAlignment: Alignment = Alignment.TopStart,
    indicator: @Composable BoxScope.() -> Unit = {
        Indicator(
            modifier = Modifier.align(Alignment.TopCenter),
            isRefreshing = isRefreshing,
            state = state,
        )
    },
    enabled: Boolean = true,
    threshold: Dp = PullToRefreshDefaults.PositionalThreshold,
    content: @Composable BoxScope.() -> Unit,
) {
    val isTouchDown = remember { mutableStateOf(false) }
    val wheelGuard = remember(enabled) { WheelPullGuard(enabled, isTouchDown) }
    // Expose this container's refresh action to the shell's global refresh
    // affordance (desktop File→Refresh / Ctrl+R). Registration is gated on
    // `enabled`, so a screen that takes itself out of pull-to-refresh (TV
    // mode, an overlaid search) is equally out of the menu's reach.
    val registry = LocalPullToRefreshRegistry.current
    val activeOnRefresh by rememberUpdatedState(onRefresh)
    DisposableEffect(registry, enabled) {
        val unregister = if (enabled) registry.register { activeOnRefresh() } else null
        onDispose { unregister?.invoke() }
    }
    Material3PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.pointerInput(Unit) {
            // Purely observational — nothing is consumed, so the content's
            // own gestures are unaffected.
            awaitEachGesture {
                // A cancelled pointer stream can end without the all-up event
                // that breaks the loop below; clear at gesture start so a
                // stale `true` can't leave the wheel guard disabled.
                isTouchDown.value = false
                while (true) {
                    val event = awaitPointerEvent()
                    isTouchDown.value =
                        event.changes.any { it.pressed && it.type == PointerType.Touch }
                    if (event.changes.none { it.pressed }) break
                }
            }
        },
        state = state,
        contentAlignment = contentAlignment,
        indicator = indicator,
        enabled = enabled,
        threshold = threshold,
    ) {
        // Wrap-free size pass-through: the guard only has to sit between the
        // scrolling content and Material3's connection, so the box takes no
        // size modifier and propagates constraints unchanged. BoxScope.align()
        // calls inside `content` resolve to this box, which occupies exactly
        // the bounds the outer box would have given them.
        Box(
            modifier = Modifier.nestedScroll(wheelGuard),
            contentAlignment = contentAlignment,
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/**
 * Consumes backward (pull-direction) vertical leftovers from user input
 * whenever no touch pointer is down — the wheel/trackpad overscroll case —
 * starving Material3's connection before it can grow the pull distance.
 * SideEffect-sourced (programmatic) scrolls are left alone: nothing user-
 * driven should be swallowed outside real pointer input.
 */
private class WheelPullGuard(
    private val enabled: Boolean,
    private val isTouchDown: State<Boolean>,
) : NestedScrollConnection {
    override fun onPostScroll(
        consumed: Offset,
        available: Offset,
        source: NestedScrollSource,
    ): Offset {
        if (!enabled || source != NestedScrollSource.UserInput || isTouchDown.value || available.y <= 0f) {
            return Offset.Zero
        }
        return Offset(0f, available.y)
    }
}
