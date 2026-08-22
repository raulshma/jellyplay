package com.raulshma.jellyplay.core.data.search

import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import kotlinx.coroutines.flow.Flow

/**
 * Immutable snapshot of one preview-search round, as rendered by the home
 * search bar (and reusable by any future preview surface).
 *
 * @property query the query this state answers (blank for the empty state).
 * @property jellyfin top Jellyfin matches.
 * @property seerr top Seerr matches; empty when the Seerr gate is closed or it
 * failed — the gate is exposed separately by
 * [MediaSearchEngine.isSeerrSearchAvailable] for surfaces that render a
 * dedicated error row.
 * @property isSearching true while the round-trips are in flight.
 */
data class MediaSearchPreviewState(
    val query: String,
    val jellyfin: List<MediaItem>,
    val seerr: List<SeerrSearchItem>,
    val isSearching: Boolean,
)

/**
 * The single search kernel behind every search entry point:
 * debounced preview fetch + Seerr gating + history policy. Previously the
 * home bar and the search screen each hand-rolled this choreography with two
 * debounce constants and two history policies.
 *
 * Mirrors the `SeerrRequestStateHolder` precedent: a stateful coordinator in
 * `core/data` shared by HomeViewModel and SearchViewModel so behavior fixes
 * land once. The engine never throws and never hard-codes a dispatcher —
 * `preview` runs on the caller's context (VM scope in production, the test
 * scheduler under `runTest`).
 */
interface MediaSearchEngine {
    /** Single debounce constant for every search entry point. */
    val debounceMs: Long

    /**
     * Debounced, cancel-and-replace preview search driven by the caller's raw
     * query flow. Emits a `isSearching = true` state when a round starts, the
     * completed state when both branches resolve, and the empty state for a
     * blank query (without touching the network). Jellyfin top-`limit` items +
     * Seerr results (top `seerrLimit`) only when connected & enabled & not on
     * Local network status. Never throws.
     */
    fun preview(
        queries: Flow<String>,
        limit: Int = PREVIEW_LIMIT,
        seerrLimit: Int = PREVIEW_LIMIT,
    ): Flow<MediaSearchPreviewState>

    /**
     * True when the Seerr companion should be consulted for search: connected,
     * search enabled, and not on a `NetworkStatus.Local` connection (Seerr is
     * internet-facing; on Local status the reachability assumption fails).
     * Never throws.
     */
    suspend fun isSeerrSearchAvailable(): Boolean

    /** Result-gated history save: no-op unless [jellyfinHadResults], the query
     * is ≥ 2 non-blank chars, and the hide-history preference is off. */
    suspend fun recordHistory(query: String, jellyfinHadResults: Boolean)

    /** Recent history for the active user; empty when signed out or hidden. */
    fun recentHistory(): Flow<List<SearchHistoryItem>>

    /** Deletes a single history row (undo is the caller's concern). */
    suspend fun deleteHistoryItem(id: Long)

    /** Clears the active user's history (undo is the caller's concern). */
    suspend fun clearHistory()

    companion object {
        const val PREVIEW_LIMIT: Int = 8

        /** The search screen's shipping value — the more keystroke-heavy surface. */
        const val DEFAULT_DEBOUNCE_MS: Long = 300
    }
}
