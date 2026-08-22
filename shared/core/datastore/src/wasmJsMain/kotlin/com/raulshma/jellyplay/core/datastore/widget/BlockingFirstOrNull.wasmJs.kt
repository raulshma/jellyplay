package com.raulshma.jellyplay.core.datastore.widget

import kotlinx.coroutines.flow.StateFlow

internal actual fun <T> blockingFirstOrNull(
    flow: StateFlow<T>,
    timeoutMillis: Long,
): T? = flow.value
