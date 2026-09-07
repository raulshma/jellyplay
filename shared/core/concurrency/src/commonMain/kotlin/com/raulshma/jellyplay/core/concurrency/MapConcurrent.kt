package com.raulshma.jellyplay.core.concurrency

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Order-preserving bounded-parallel map over [this] semaphore's permits — the
 * acquire/try/finally (or `withPermit`) + async/awaitAll ladder every
 * fan-out site hand-copied. At most [Semaphore.availablePermits] items run
 * concurrently; results are returned in [items] order regardless of
 * completion order. Launches all items immediately (permits gate execution,
 * not launch), inside a [coroutineScope] owned by the caller's context — a
 * failure in one item cancels the siblings and propagates to the caller.
 *
 * The lambda receives each item; any per-item failure POLICY (drop it, turn
 * it into an empty contribution, fold it into a Result) stays at the call
 * site — only the permit ladder is centralized here. When the policy is
 * "drop the failed item", use [mapConcurrentCatching] instead.
 */
suspend fun <T, R> Semaphore.mapConcurrent(
    items: List<T>,
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    items.map { item -> async { withPermit { transform(item) } } }.awaitAll()
}

/**
 * [mapConcurrent] for the degrade-to-drop policy: an item whose transform
 * fails is dropped from the result instead of failing the whole call, while a
 * [CancellationException] still propagates (never masked as a drop —
 * [runCatchingRethrowingCancellation] semantics). Order of the survivors is
 * preserved.
 *
 * A null completion is indistinguishable from a failure and is dropped too;
 * only call sites whose transform may legitimately return `null` AND wants
 * nulls kept must not use this variant.
 */
suspend fun <T, R> Semaphore.mapConcurrentCatching(
    items: List<T>,
    transform: suspend (T) -> R,
): List<R> = coroutineScope {
    items
        .map { item -> async { runCatchingRethrowingCancellation { withPermit { transform(item) } } } }
        .awaitAll()
        .mapNotNull { it.getOrNull() }
}
