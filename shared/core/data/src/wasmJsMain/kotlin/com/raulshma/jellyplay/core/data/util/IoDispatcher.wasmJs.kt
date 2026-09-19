package com.raulshma.jellyplay.core.data.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** wasmJs actual: the single JS event loop has no dedicated IO pool. */
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
