package com.raulshma.jellyplay.core.data.session

internal actual fun <R> guardUnderLock(lock: Any, block: () -> R): R =
    synchronized(lock) { block() }
