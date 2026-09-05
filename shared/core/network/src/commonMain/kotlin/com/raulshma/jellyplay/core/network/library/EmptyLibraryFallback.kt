package com.raulshma.jellyplay.core.network.library

/**
 * The empty-library fallback ladder shared by both library clients: when the
 * primary /Items query returns nothing for an unfiltered browse (no search
 * term), try /Items/Latest before declaring the library empty, and memoise
 * libraries where BOTH queries returned nothing so a repeat visit pays a
 * single request instead of the doubled round trip.
 *
 * The memo itself stays a per-client port (the two constructor lambdas)
 * because the thread-safety regimes genuinely differ: the JVM client runs
 * inside the engine's Dispatchers.IO block and synchronizes its access-order
 * LRU; the single-threaded wasm event loop needs no lock but emulates
 * access-order by remove+reinsert. The DECISION ladder — skip the doubled
 * request for known-empty libraries, coerce the fallback limit, remember only
 * genuinely-empty libraries — lives here, once.
 */
internal class EmptyLibraryFallback<Item>(
    /** Memo probe: true when the library is known to fall back to nothing. */
    private val isKnownEmpty: (String) -> Boolean,
    /** Memo write: the primary query AND the fallback both returned nothing. */
    private val rememberEmpty: (String) -> Unit,
    /** The /Items/Latest transport call each client performs. */
    private val fetchLatest: suspend (parentId: String, limit: Int) -> List<Item>,
) {
    /**
     * Raw items for a getMediaItems response: [primaryItems] when non-empty
     * (or when the query is not an unfiltered browse of one folder), else the
     * fallback ladder. Bounded-LRU note: once the library gains content the
     * primary query returns items and the fallback is never reached, so the
     * memo cannot serve a stale non-empty state.
     */
    suspend fun resolve(
        primaryItems: List<Item>,
        parentId: String?,
        searchTerm: String?,
        limit: Int,
    ): List<Item> {
        if (primaryItems.isNotEmpty() || parentId == null || !searchTerm.isNullOrBlank()) return primaryItems
        if (isKnownEmpty(parentId)) return emptyList()
        val fallback = runCatching { fetchLatest(parentId, if (limit > 0) limit else 50) }
            .getOrNull() ?: emptyList()
        if (fallback.isEmpty()) rememberEmpty(parentId)
        return fallback
    }
}

/**
 * totalRecordCount derivation when the empty-library fallback served the
 * items: the server's count describes the (empty) primary query, so the
 * fallback batch size is the honest total; otherwise the server count stands.
 */
internal fun emptyFallbackTotalCount(primaryCount: Int, resolvedCount: Int, serverTotal: Int): Int =
    if (primaryCount == 0 && resolvedCount > 0) resolvedCount else serverTotal
