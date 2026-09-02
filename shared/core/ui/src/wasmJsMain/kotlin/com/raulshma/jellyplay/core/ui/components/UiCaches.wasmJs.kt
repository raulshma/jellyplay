package com.raulshma.jellyplay.core.ui.components

/**
 * Wasm half of the cache lock seam: JS execution is single-threaded per
 * isolate, so mutual exclusion has nothing to exclude — run [block] directly.
 * If wasm ever grows a threading story, revisit here before touching caches.
 */
internal actual fun <T> withUiLock(lock: Any, block: () -> T): T = block()
