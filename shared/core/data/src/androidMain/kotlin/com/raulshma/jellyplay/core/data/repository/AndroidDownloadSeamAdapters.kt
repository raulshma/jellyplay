package com.raulshma.jellyplay.core.data.repository

import android.content.Context
import android.util.Log
import coil3.SingletonImageLoader
import coil3.request.ImageRequest

// V3 downloads conveyor: Android actuals for the two notification/cache seams
// the portable DownloadRepositoryImpl (now in :shared:core:data jvmShared)
// calls out through. Bodies are verbatim splits of the two platform blocks the
// repository previously inlined — the notification group summary refresh and
// the Coil image preload — moved behind the shared fun interfaces so Koin can
// own the repository on both platforms.

/**
 * Posts the Android download-notification group summary onto the shared
 * [DownloadProgressNotifier] seam — the ONE port consumer is the
 * [com.raulshma.jellyplay.core.data.worker.DownloadSummarySurface] single
 * (which observes the in-flight count flow and posts on transitions); the
 * body is the exact helper call the repository's refreshDownloadSummary
 * previously inlined. The helper's same-count memo and cancel-at-zero
 * semantics stay behind this port, unchanged.
 */
class DownloadSummaryRefresher(
    private val context: Context,
) : DownloadProgressNotifier {
    override fun refreshSummary(inFlightCount: Int) {
        com.raulshma.jellyplay.core.data.worker.DownloadNotificationHelper
            .refreshSummary(context, inFlightCount)
    }
}

/**
 * Posts the gh-181 Ask-mode suggestion shade summary onto the shared
 * [com.raulshma.jellyplay.core.data.worker.AutoDownloadSuggestionNotifier]
 * seam — the one consumer is [com.raulshma.jellyplay.core.data.worker.AutoDownloadWorker]
 * after a pass records fresh prompts. The helper's permission/channel handling
 * stays behind the port.
 */
class AndroidSuggestionNotifier(
    private val context: Context,
) : com.raulshma.jellyplay.core.data.worker.AutoDownloadSuggestionNotifier {
    override fun notifyNewSuggestions(count: Int) {
        com.raulshma.jellyplay.core.data.worker.DownloadNotificationHelper
            .postSuggestionsNotification(context, count)
    }
}

/**
 * Preloads offline images into Coil's cache (decoded at 384² to match
 * MediaImage's default decode size — decoding at a different size produces a
 * separate memory-cache key, so the display path would re-decode and the
 * larger bitmap would sit stranded until evicted). Adapts the shared
 * [OfflineImagePreloader] seam; the body is the exact Coil block the
 * repository's preloadImageToCache previously inlined (minus the null/blank
 * guard, which stays at the repository call site).
 */
class CoilOfflineImagePreloader(
    private val context: Context,
) : OfflineImagePreloader {
    override fun preload(url: String) {
        try {
            val imageLoader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(url)
                // Match MediaImage's default decode size (384²). Decoding the
                // preload at a different size produces a separate memory-cache
                // key, so the display path would re-decode and the larger
                // bitmap would sit stranded until evicted.
                .size(384, 384)
                .build()
            imageLoader.enqueue(request)
        } catch (e: Exception) {
            Log.d("DownloadRepository", "Failed to preload image to cache", e)
        }
    }
}
