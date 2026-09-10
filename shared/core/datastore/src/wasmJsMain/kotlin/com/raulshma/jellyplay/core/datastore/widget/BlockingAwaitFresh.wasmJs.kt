package com.raulshma.jellyplay.core.datastore.widget

import kotlinx.coroutines.flow.StateFlow

internal actual fun <T> blockingAwaitFresh(
    flow: StateFlow<T>,
    seed: T,
    timeoutMillis: Long,
): T = flow.value
