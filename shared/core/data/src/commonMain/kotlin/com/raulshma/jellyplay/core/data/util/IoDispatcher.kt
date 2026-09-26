package com.raulshma.jellyplay.core.data.util

import kotlinx.coroutines.CoroutineDispatcher

/**
 * The module's I/O dispatcher seam — the commonMain stand-in for
 * `Dispatchers.IO` (kotlinx.coroutines
 * declares IO only on JVM/Native).
 *
 *  - android/desktop actual (jvmShared): `Dispatchers.IO` — byte-identical to
 *    the pre-promotion call sites (Room suspend DAO calls off the caller's
 *    dispatcher onto the IO pool).
 */
internal expect val ioDispatcher: CoroutineDispatcher
