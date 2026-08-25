package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.data.util.PhotoFolderPrefetcher
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the photo-folder child-URL cache for the home screen: the
 * folder-id → child-image-URLs map the photo rows read to open a folder's
 * children without a per-card round-trip. Exclusive state — the only map in
 * the home feature — so it lives behind this small interface instead of the
 * VM (SearchViewModel-style re-exposure: the VM delegates
 * `photoFolderChildUrls` and the prefetch call straight through, and the UI
 * call sites are unchanged).
 *
 * `prefetch` is incremental: ids already cached are skipped via the
 * `alreadyFetched` hand-off, new results are merged, and the map is evicted
 * oldest-first beyond [PHOTO_FOLDER_CACHE_CAP] so a long-lived VM cannot
 * accumulate stale entries across library changes.
 */
internal class PhotoFolderChildUrlsStore(
    /** The VM's scope: prefetch jobs must die with the VM. */
    private val scope: CoroutineScope,
    private val prefetcher: PhotoFolderPrefetcher,
) {

    companion object {
        /**
         * Cap on cached photo-folder child-URL entries. Photo folders are a
         * fixed, small set per server, but the map is append-only and a
         * long-lived VM could accumulate stale entries across library changes.
         * Evict oldest entries beyond this cap.
         */
        private const val PHOTO_FOLDER_CACHE_CAP = 50
    }

    private val _childUrls = MutableStateFlow<Map<String, List<String>>>(emptyMap())

    /** Cached child-URL lists keyed by photo-folder item id. */
    val childUrls: StateFlow<Map<String, List<String>>> = _childUrls.asStateFlow()

    /**
     * Fetches child URLs for any [items] not already cached (fire-and-forget
     * on [scope]) and merges the results into [childUrls]. Safe to call on
     * every recomposition — already-cached folders are skipped before any
     * network work.
     */
    fun prefetch(items: List<MediaItem>) {
        scope.launch {
            val current = _childUrls.value
            val results = prefetcher.prefetch(items, alreadyFetched = current.keys)
            if (results.isNotEmpty()) {
                // Merge then evict the oldest entries beyond the cap so the
                // map stays bounded for the VM's lifetime.
                val merged = _childUrls.value + results
                _childUrls.value =
                    if (merged.size <= PHOTO_FOLDER_CACHE_CAP) merged
                    else merged.entries.drop(merged.size - PHOTO_FOLDER_CACHE_CAP).associate { it.key to it.value }
            }
        }
    }
}
