package com.raulshma.jellyplay.feature.settings

/**
 * Platform seam behind the "clear audio cache" action: the legacy
 * AudioStreamCache lives in the Hilt-owned Android data shim, so the app
 * composition root provides the clearing impl at the Koin edge (Wave 2); the
 * interface only carries the suspend clear the ViewModel needs.
 */
fun interface AudioCacheClearer {
    suspend fun clear()
}
