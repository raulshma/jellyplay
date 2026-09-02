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
