package com.raulshma.jellyplay.core.datastore.widget

import kotlinx.coroutines.flow.StateFlow

/**
 * Synchronous read of the eager snapshot's settled value for the widget
 * *Snapshot() accessors, which are synchronous by contract (called from
 * AppWidget providers with no coroutine scope).
 *
 * On a cold store the [flow] still holds its stateIn [seed] placeholder, so
 * the JVM actual waits (bounded by [timeoutMillis]) for the eager collector's
 * first REAL emission to replace it — a plain `flow.first()` here would only
 * ever hand back the seed itself. When the persisted payload legitimately
 * equals the seed, or the warm-up budget expires, the current value is the
 * answer and is returned after the wait.
 *
 * wasm has no runBlocking, so it returns the current StateFlow value
 * immediately (the widget cold-start warm-up pattern is Android-only in
 * practice).
 */
internal expect fun <T> blockingAwaitFresh(
    flow: StateFlow<T>,
    seed: T,
    timeoutMillis: Long,
): T
