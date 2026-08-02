package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import javax.inject.Inject
import javax.inject.Singleton

interface ImageUrlProvider {
    // Nullable so callers (e.g. the photo viewer) can request original
    // resolution: a null maxWidth omits the Jellyfin param entirely. Coalescing
    // null to a fixed default silently capped full-res photos at 400px.
    fun getImageUrl(itemId: String, maxWidth: Int? = DEFAULT_MAX_WIDTH): String

    fun getBackdropUrl(itemId: String, maxWidth: Int = DEFAULT_BACKDROP_WIDTH): String

    companion object {
        const val DEFAULT_MAX_WIDTH = 400
        const val MUSIC_MAX_WIDTH = 300
        const val DEFAULT_BACKDROP_WIDTH = 1920
    }
}

@Singleton
class ImageUrlProviderImpl @Inject constructor(
    private val playbackRepository: PlaybackRepository,
    private val appearanceStore: AppearanceStore,
) : ImageUrlProvider {

    // True when performance mode is on. StateFlow.value is safe to read
    // synchronously on any thread once the flow has been collected; the store
    // seeds it from disk so this is never stale on the main thread.
    private val performanceMode: Boolean get() =
        appearanceStore.appearance.value.performanceMode

    override fun getImageUrl(itemId: String, maxWidth: Int?): String {
        // Original-resolution requests (null) bypass performance mode: callers
        // like the full-screen photo viewer deliberately ask for the source
        // bitmap, and capping null to a perf width silently broke that contract.
        if (maxWidth == null) {
            return playbackRepository.getImageUrl(itemId, maxWidth = null)
        }
        // Explicit widths are honored as the caller's minimum; the default
        // (no-arg) path is perf-aware so poster grids don't over-fetch.
        val effectiveWidth = if (performanceMode) PERF_MAX_WIDTH
        else ImageUrlProvider.DEFAULT_MAX_WIDTH
        return playbackRepository.getImageUrl(itemId, maxWidth = effectiveWidth)
    }

    private companion object {
        // Performance mode lowers the *download* width so slow networks don't
        // fetch a 400px JPEG only to decode it at 256px. Posters decode-clamp to
        // 256² (see MediaImage), so a 300px source covers that without waste.
        const val PERF_MAX_WIDTH = 300
    }

    override fun getBackdropUrl(itemId: String, maxWidth: Int): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = maxWidth)
}
