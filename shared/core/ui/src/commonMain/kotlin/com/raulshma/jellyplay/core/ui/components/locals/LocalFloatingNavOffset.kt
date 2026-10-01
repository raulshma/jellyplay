package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf

/**
 * Provides the live floating-nav-bar vertical offset (px) as a `() -> Float`
 * getter rather than a raw `Float`.
 *
 * Reading a snapshot state (e.g. `MutableFloatState.floatValue`) is what
 * triggers recomposition; exposing a *deferred* getter means consumers can
 * choose to read it inside `Modifier.offset { … }` (layout phase) so the
 * per-frame nav-bar slide no longer forces recomposition of either the
 * provider (`MainContent`, which used to read `.floatValue` directly in the
 * `provides` expression and thus re-ran the whole TV/Phone/FullScreen branch
 * dispatch on every animation frame) or the screen-body root that just
 * forwards the value.
 */
val LocalFloatingNavOffset = compositionLocalOf<() -> Float> { { 0f } }
val LocalFloatingNavVisibility = compositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(true)
}

/**
 * Whether the floating bottom nav bar is actually painted above this subtree:
 * `true` only in the compact, non-full-screen phone shell (the one layout that
 * composes `ExpressiveFloatingNavigationBar`). Every other composition — the
 * signed-out auth host, TV, expanded/rail layouts, full-screen routes, and the
 * desktop window (which composes its own desktop shell, never the phone
 * shell) — reads the `false` default, so bottom-floating elements can skip the
 * nav clearance instead of reserving space for a bar that never paints.
 *
 * Deliberately a *presence* flag, not a [LocalFloatingNavVisibility] read:
 * hide-on-scroll translates the bar off-screen while it stays composed, and
 * riders must keep their clearance through that slide (the ride-up offset in
 * [clearFloatingNav] hands the space back). Presence flips only when the bar
 * leaves the tree entirely.
 */
val LocalFloatingNavPresent = compositionLocalOf { false }
