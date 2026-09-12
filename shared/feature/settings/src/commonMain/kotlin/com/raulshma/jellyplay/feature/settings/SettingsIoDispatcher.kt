package com.raulshma.jellyplay.feature.settings

import kotlinx.coroutines.CoroutineDispatcher

/**
 * seam: the IO hop the blocking platform reads ride (AboutViewModel's
 * log collection, LicensesViewModel's aboutlibraries JSON read).
 * `Dispatchers.IO` is JVM-only, so the jvmShared actual supplies it (android +
 * desktop behavior unchanged) and wasm — which has no blocking IO — maps to
 * `Dispatchers.Default` (the log collector and JSON source are no-ops there).
 */
internal expect val settingsIoDispatcher: CoroutineDispatcher
