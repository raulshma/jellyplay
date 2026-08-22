package com.raulshma.jellyplay.core.data.util

import android.util.LruCache
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore

/**
 * Android implementation of the [ImageUrlProvider] seam (C4 part 2): body
 * moved verbatim from the legacy `:core:data` `ImageUrlProviderImpl` — the
 * Hilt annotations were stripped (Koin's
 * [com.raulshma.jellyplay.core.data.di.androidDataModule] constructs it; the
 * legacy DataModule bridges Hilt consumers to the same single).
 */
class ImageUrlProviderImpl(
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
        // Non-null caller widths are deliberately replaced by this single
        // effective width (perf-aware): one width per item keeps the Coil cache
        // key consolidated instead of fragmenting it across caller widths.
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

    override fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?): String {
        // Chapter thumbnails are small list-position-keyed images; perf-aware
        // width clamp + shared LRU keep the chapter row cheap to recompose.
        val effectiveWidth = if (performanceMode) PERF_MAX_WIDTH
        else ImageUrlProvider.DEFAULT_MAX_WIDTH
        val key = "c_$itemId|$imageIndex|${tag ?: ""}"
        urlCache.get(key)?.let { return it }
        val url = playbackRepository.getChapterImageUrl(itemId, imageIndex, tag, maxWidth = effectiveWidth)
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
