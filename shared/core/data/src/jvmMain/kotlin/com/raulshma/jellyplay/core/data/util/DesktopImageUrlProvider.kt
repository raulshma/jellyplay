package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore

/**
 * Desktop implementation of the [ImageUrlProvider] seam: same memoisation
 * policy as the Android [ImageUrlProviderImpl] (bounded LRU keyed by
 * item+effective width, perf-mode width clamp), implemented over a
 * synchronized [LinkedHashMap] in access order — the JDK equivalent of
 * `android.util.LruCache`.
 */
class DesktopImageUrlProvider(
    private val playbackRepository: PlaybackRepository,
    private val appearanceStore: AppearanceStore,
) : ImageUrlProvider {

    private val performanceMode: Boolean get() =
        appearanceStore.appearance.value.performanceMode

    // accessOrder=true gives LinkedHashMap LRU eviction semantics; the
    // synchronized wrapper matches LruCache's thread safety.
    private val urlCache: MutableMap<String, String> = object : LinkedHashMap<String, String>(
        16, 0.75f, true,
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>): Boolean =
            size > URL_CACHE_MAX_ENTRIES
    }

    override fun getImageUrl(itemId: String, maxWidth: Int?): String {
        if (maxWidth == null) {
            return playbackRepository.getImageUrl(itemId, maxWidth = null)
        }
        val effectiveWidth = if (performanceMode) PERF_MAX_WIDTH
        else ImageUrlProvider.DEFAULT_MAX_WIDTH
        val key = "p_$itemId|$effectiveWidth"
        synchronized(urlCache) { urlCache[key] }?.let { return it }
        val url = playbackRepository.getImageUrl(itemId, maxWidth = effectiveWidth)
        if (url.isNotEmpty()) synchronized(urlCache) { urlCache[key] = url }
        return url
    }

    override fun getBackdropUrl(itemId: String, maxWidth: Int): String {
        val key = "b_$itemId|$maxWidth"
        synchronized(urlCache) { urlCache[key] }?.let { return it }
        val url = playbackRepository.getBackdropUrl(itemId, maxWidth = maxWidth)
        if (url.isNotEmpty()) synchronized(urlCache) { urlCache[key] = url }
        return url
    }

    override fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?): String {
        val effectiveWidth = if (performanceMode) PERF_MAX_WIDTH
        else ImageUrlProvider.DEFAULT_MAX_WIDTH
        val key = "c_$itemId|$imageIndex|${tag ?: ""}"
        synchronized(urlCache) { urlCache[key] }?.let { return it }
        val url = playbackRepository.getChapterImageUrl(itemId, imageIndex, tag, maxWidth = effectiveWidth)
        if (url.isNotEmpty()) synchronized(urlCache) { urlCache[key] = url }
        return url
    }

    private companion object {
        const val PERF_MAX_WIDTH = 300
        const val URL_CACHE_MAX_ENTRIES = 512
    }
}
