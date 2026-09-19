package com.raulshma.jellyplay.core.data.util

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/** JVM/Android actual: the exact dispatcher the pre-promotion call sites used. */
internal actual val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
