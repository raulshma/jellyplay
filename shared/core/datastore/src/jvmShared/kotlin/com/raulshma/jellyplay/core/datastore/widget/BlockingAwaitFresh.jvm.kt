package com.raulshma.jellyplay.core.datastore.widget

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

internal actual fun <T> blockingAwaitFresh(
    flow: StateFlow<T>,
    seed: T,
    timeoutMillis: Long,
): T {
    val initial = flow.value
    if (initial != seed) return initial
    runBlocking {
        withTimeoutOrNull(timeoutMillis) { flow.first { it != seed } }
    }
    // A fresh emission replaced the seed, the payload legitimately equals the
    // seed, or the warm-up budget expired — the current value is the answer.
    return flow.value
}
