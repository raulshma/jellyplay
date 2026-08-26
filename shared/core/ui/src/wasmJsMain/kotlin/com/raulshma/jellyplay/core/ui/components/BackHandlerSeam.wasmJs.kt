package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable

/**
 * Wasm back-navigation seam: deliberately inert for now, mirroring the
 * desktop decision. Browser-history integration is a depth decision still
 * owned by the web-shell work (plan §Phase W open item): honoring [enabled]
 * properly needs coordinated pushState/popState bookkeeping per navigation
 * stack entry, not a `window.history.back()` one-liner — a naive binding
 * would double-consume entries or fight NavDisplay's own stack. Until then
 * registered [onBack] callbacks stay unreachable on web; screens must keep
 * their own explicit close/back affordances.
 */
@Composable
actual fun JellyPlayBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Web shell v1: no browser-history interception.
}
