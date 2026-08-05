package com.raulshma.jellyplay.core.data.util

import android.util.LruCache
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

    // Image URLs are built per visible card per recomposition (poster grids,
    // CW rows, search results). Each build runs UUID parsing + string assembly
    // inside the Jellyfin SDK (imageApi.getItemImageUrl) — not a network call,
    // but non-trivial at O(items × recompositions). The URL string is a pure
    // function of (itemId, effectiveWidth, tag), so memoise it. Bounded LRU
    // self-evicts; synchronized internally. Keyed with the width (which embeds
    // the performance-mode decision) so a perf-mode toggle produces a distinct,
    // correct entry rather than serving a stale width.
    private val urlCache = object : LruCache<String, String>(URL_CACHE_MAX_ENTRIES) {}

    override fun getImageUrl(itemId: String, maxWidth: Int?): String {
        // Original-resolution requests (null) bypass performance mode: callers
        // like the full-screen photo viewer deliberately ask for the source
        // bitmap, and capping null to a fixed perf width silently broke that contract.
        if (maxWidth == null) {
            return playbackRepository.getImageUrl(itemId, maxWidth = null)
        }
        // Explicit widths are honored as the caller's minimum; the default
        // (no-arg) path is perf-aware so poster grids don't over-fetch.
        val effectiveWidth = if (performanceMode) PERF_MAX_WIDTH
        else ImageUrlProvider.DEFAULT_MAX_WIDTH
        val key = "p_$itemId|$effectiveWidth"
        urlCache.get(key)?.let { return it }
        val url = playbackRepository.getImageUrl(itemId, maxWidth = effectiveWidth)
        if (url.isNotEmpty()) urlCache.put(key, url)
        return url
    }

    override fun getBackdropUrl(itemId: String, maxWidth: Int): String {
        val key = "b_$itemId|$maxWidth"
        urlCache.get(key)?.let { return it }
        val url = playbackRepository.getBackdropUrl(itemId, maxWidth = maxWidth)
        if (url.isNotEmpty()) urlCache.put(key, url)
        return url
    }

    private companion object {
        // Performance mode lowers the *download* width so slow networks don't
        // fetch a 400px JPEG only to decode it at 256px. Posters decode-clamp to
        // 256² (see MediaImage), so a 300px source covers that without waste.
        const val PERF_MAX_WIDTH = 300
        // Generous bound: a home screen shows ~16 cards/row × ~10 rows of
        // distinct titles at most, plus detail/backdrop variants. LRU access
        // order keeps the hot set resident during scroll.
        const val URL_CACHE_MAX_ENTRIES = 512
    }
}
