package com.raulshma.jellyplay.core.datastore.widget

import kotlinx.coroutines.flow.StateFlow

/**
 * Blocking read of the first [flow] emission, bounded by [timeoutMillis].
 * Used by the widget snapshot accessors, which are synchronous by contract
 * (called from AppWidget providers with no coroutine scope).
 *
 * JVM/Android block the calling thread via runBlocking; wasm has no
 * runBlocking, so it returns the current StateFlow value immediately (the
 * widget warm-up pattern is Android-only in practice).
 */
internal expect fun <T> blockingFirstOrNull(
    flow: StateFlow<T>,
    timeoutMillis: Long,
): T?
