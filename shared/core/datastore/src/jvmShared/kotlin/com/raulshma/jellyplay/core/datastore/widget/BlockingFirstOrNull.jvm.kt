package com.raulshma.jellyplay.core.datastore.widget

import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

internal actual fun <T> blockingFirstOrNull(
    flow: StateFlow<T>,
    timeoutMillis: Long,
): T? = runBlocking { withTimeoutOrNull(timeoutMillis) { flow.first() } }
