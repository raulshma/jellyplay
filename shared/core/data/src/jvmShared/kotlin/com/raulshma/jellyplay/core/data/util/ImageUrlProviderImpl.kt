package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.model.lruMapOf
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import java.util.Collections

/**
 * The single JVM-side (android + desktop) implementation of the [ImageUrlProvider]
 * seam (C4 part 2): the android body lived verbatim in the legacy `:core:data`
 * `ImageUrlProviderImpl` and the desktop twin re-implemented the identical
 * policy over a hand-rolled access-order [java.util.LinkedHashMap]; the policy
 * now lives ONCE here, and both platform DI modules
 * ([com.raulshma.jellyplay.core.data.di.androidDataModule] /
 * [com.raulshma.jellyplay.core.data.di.desktopDataModule]) construct this class.
 *
 * Since the image/chapter-image/backdrop URL builders were retired off the
 * [com.raulshma.jellyplay.core.data.repository.PlaybackRepository] surface,
 * this class builds the URLs directly through [LibraryApiClient] — the same
 * client the playback repository delegated to, so the emitted strings are
 * unchanged (imageType "Primary"/"Logo"/"Chapter" + `maxWidth`, empty string
 * when no session).
 *
 * Policy: image URLs are built per visible card per recomposition (poster
 * grids, CW rows, search results). Each build runs UUID parsing + string
 * assembly inside the Jellyfin SDK (imageApi.getItemImageUrl) — not a network
 * call, but non-trivial at O(items × recompositions). The URL string is a pure
 * function of (itemId, effectiveWidth, tag), so it is memoised in a bounded
 * access-order LRU ([lruMapOf]'s JVM actual + a synchronized wrapper — the
 * exact historical construction on both platforms), keyed with the effective
 * width (which embeds the performance-mode decision) so a perf-mode toggle
 * produces a distinct, correct entry rather than serving a stale width.
 *
 * Width policy (the width-taking members, [getImageUrl]/[getBackdropUrl]): a
 * null caller width (original-resolution requests, e.g. the full-screen photo
 * viewer) bypasses BOTH the clamp and the cache; the default-width (no-arg)
 * request rides the performance-mode clamp to [PERF_MAX_WIDTH]; any OTHER
 * explicit width is honored verbatim — the infrastructural callers that
 * migrated off the repository surface (queue artwork, offline preloads,
 * heatmap rows, media-session/cast artwork) pass deliberate per-surface sizes
 * (200/300/600/1280) and never rode the clamp, so clamping only the
 * default-width path keeps every caller's emitted URL identical across the
 * migration. [getLogoUrl]/[getChapterImageUrl] take no caller width at all —
 * one fixed rendering per item — so they always sit on the
 * [PERF_MAX_WIDTH]/[ImageUrlProvider.DEFAULT_MAX_WIDTH] pair and the
 * verbatim rule above never applies to them.
 * Performance mode lowers the width to [PERF_MAX_WIDTH]. Empty client URLs are
 * never cached, so a later login/server change can start producing URLs.
 */
class ImageUrlProviderImpl(
    private val libraryApiClient: LibraryApiClient,
    private val appearanceStore: AppearanceStore,
) : ImageUrlProvider {

    // True when performance mode is on. StateFlow.value is safe to read
    // synchronously on any thread once the flow has been collected; the store
    // seeds it from disk so this is never stale on the main thread.
    private val performanceMode: Boolean get() =
        appearanceStore.appearance.value.performanceMode

    // Bounded access-order LRU, synchronized per call (android.util.LruCache's
    // internal locking / the desktop twin's synchronized-wrapper semantics).
    private val urlCache: MutableMap<String, String> =
        Collections.synchronizedMap(lruMapOf(URL_CACHE_MAX_ENTRIES))

    override fun getImageUrl(itemId: String, maxWidth: Int?): String {
        // Original-resolution requests (null) bypass performance mode: callers
        // like the full-screen photo viewer deliberately ask for the source
        // bitmap, and capping null to a fixed perf width silently broke that contract.
        if (maxWidth == null) {
            return libraryApiClient.getImageUrl(itemId, maxWidth = null)
        }
        // The default-width (UI card) request is the one path performance mode
        // clamps; explicit caller widths are honored verbatim (see the class
        // KDoc) so per-surface sizes keep emitting their exact URLs.
        val effectiveWidth = if (maxWidth == ImageUrlProvider.DEFAULT_MAX_WIDTH && performanceMode) PERF_MAX_WIDTH
        else maxWidth
        val key = "p_$itemId|$effectiveWidth"
        urlCache[key]?.let { return it }
        val url = libraryApiClient.getImageUrl(itemId, maxWidth = effectiveWidth)
        if (url.isNotEmpty()) urlCache[key] = url
        return url
    }

    override fun getBackdropUrl(itemId: String, maxWidth: Int): String {
        val key = "b_$itemId|$maxWidth"
        urlCache[key]?.let { return it }
        val url = libraryApiClient.getBackdropImageUrl(itemId, maxWidth = maxWidth)
        if (url.isNotEmpty()) urlCache[key] = url
        return url
    }

    override fun getLogoUrl(itemId: String): String {
        // Detail-screen title block: one logo per item, same perf-aware width
        // clamp + shared LRU as the poster/backdrop variants.
        val effectiveWidth = if (performanceMode) PERF_MAX_WIDTH
        else ImageUrlProvider.DEFAULT_MAX_WIDTH
        val key = "l_$itemId|$effectiveWidth"
        urlCache[key]?.let { return it }
        val url = libraryApiClient.getImageUrl(itemId, imageType = "Logo", maxWidth = effectiveWidth)
        if (url.isNotEmpty()) urlCache[key] = url
        return url
    }

    override fun getChapterImageUrl(itemId: String, imageIndex: Int, tag: String?): String {
        // Chapter thumbnails are small list-position-keyed images; perf-aware
        // width clamp + shared LRU keep the chapter row cheap to recompose.
        val effectiveWidth = if (performanceMode) PERF_MAX_WIDTH
        else ImageUrlProvider.DEFAULT_MAX_WIDTH
        val key = "c_$itemId|$imageIndex|${tag ?: ""}"
        urlCache[key]?.let { return it }
        val url = libraryApiClient.getImageUrl(
            itemId,
            imageType = "Chapter",
            maxWidth = effectiveWidth,
            imageIndex = imageIndex,
            tag = tag,
        )
        if (url.isNotEmpty()) urlCache[key] = url
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
