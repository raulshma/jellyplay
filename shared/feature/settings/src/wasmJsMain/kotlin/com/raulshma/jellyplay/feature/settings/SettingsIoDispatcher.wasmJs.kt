package com.raulshma.jellyplay.feature.settings

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * No blocking IO exists on the JS heap — the seam's wasm consumers (log
 * collection, the aboutlibraries read) are inert no-ops anyway — so the hop
 * degrades to the shared default pool.
 */
internal actual val settingsIoDispatcher: CoroutineDispatcher = Dispatchers.Default
