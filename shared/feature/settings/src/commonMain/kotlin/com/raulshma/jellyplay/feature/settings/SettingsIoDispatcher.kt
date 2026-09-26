package com.raulshma.jellyplay.feature.settings

import kotlinx.coroutines.CoroutineDispatcher

/**
 * seam: the IO hop the blocking platform reads ride (AboutViewModel's
 * log collection, LicensesViewModel's aboutlibraries JSON read).
 * `Dispatchers.IO` is JVM-only, so the jvmShared actual supplies it (android +
 * desktop behavior unchanged).
 */
internal expect val settingsIoDispatcher: CoroutineDispatcher
