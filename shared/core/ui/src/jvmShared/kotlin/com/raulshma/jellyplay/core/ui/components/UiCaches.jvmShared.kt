package com.raulshma.jellyplay.core.ui.components

/**
 * JVM half of the cache lock seam: android + desktop both run multithreaded
 * dispatchers under their UI caches, so this keeps `kotlin.synchronized`
 * verbatim (behavior-identical to the pre-wasm intrinsic call sites).
 */
internal actual fun <T> withUiLock(lock: Any, block: () -> T): T = synchronized(lock) { block() }
