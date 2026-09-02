package com.raulshma.jellyplay.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 * Returns a click-stable wrapper around [callback] so list-item composables
 * stay skippable across parent state emissions — the `remember(id) { { … } }`
 * grid idiom without the double-brace noise.
 *
 * Unlike a bare `remember(keys) { callback }`, the returned instance never
 * goes stale: it delegates through [rememberUpdatedState], so invocation
 * always runs the *latest* composition's callback even though the wrapper
 * instance (what children see as a parameter) is unchanged. Callers therefore
 * don't need to enumerate the values the lambda captures in keys — a capture
 * set that drifted from the key set was exactly the stale-invocation bug the
 * keyed form invited.
 */
@Composable
fun rememberStableCallback(callback: () -> Unit): () -> Unit {
    val current: State<() -> Unit> = rememberUpdatedState(callback)
    return remember { { current.value() } }
}
