package com.raulshma.jellyplay.core.data.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The module's I/O dispatcher seam — the commonMain stand-in for
 * `Dispatchers.IO`, which does not exist on wasmJs (kotlinx.coroutines
 * declares IO only on JVM/Native).
 *
 *  - android/desktop actual (jvmShared): `Dispatchers.IO` — byte-identical to
 *    the pre-promotion call sites (Room suspend DAO calls off the caller's
 *    dispatcher onto the IO pool).
 *  - wasmJs actual: `Dispatchers.Default` — the browser build runs the whole
 *    coroutine graph on the single JS event loop, so IO-vs-Default has no
 *    observable difference; Room suspend calls hop to the worker driver
 *    either way.
 */
internal expect val ioDispatcher: CoroutineDispatcher
