package com.raulshma.jellyplay.core.data.search

import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem
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
 * Immutable snapshot of one side-search round, as rendered by the search
 * screen's Seerr + "On-device" companion rows.
 *
 * @property query the query this state answers.
 * @property seerr top Seerr matches; empty when the Seerr gate is closed or
 * the round failed — [seerrError] tells the two apart.
 * @property seerrError true when a gated Seerr round failed and the screen
 * should render its retry row; false for a closed gate or a clean empty
 * result.
 * @property offline the on-device matches for the same query.
 */
data class MediaSideSearchState(
    val query: String,
    val seerr: List<SeerrSearchItem>,
    val seerrError: Boolean,
    val offline: List<OfflineMediaItem>,
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
     * Local network status. While an offline mode is active the round queries
     * the offline library instead (see [runOfflinePreviewSearch]) — the server
     * is unreachable and would silently answer nothing. Never throws.
     */
    fun preview(
        queries: Flow<String>,
        limit: Int = PREVIEW_LIMIT,
        seerrLimit: Int = PREVIEW_LIMIT,
    ): Flow<MediaSearchPreviewState>

    /**
     * The search screen's side rows: gated Seerr companion results + the
     * on-device library scan for one query. Driven by the caller's (already
     * debounced) query flow — every emission runs one cancel-and-replace
     * round, so dedupe and retry re-kicks are the caller's concern. Emits a
     * cleared state when a round starts (the side rows never linger on the
     * previous query's results) and the cleared state for a blank query
     * without touching any source. Seerr rides [isSeerrSearchAvailable] — the
     * same gate the home preview uses — and caps at `seerrLimit` results
     * (`seerrLimit` caps the Seerr half only; the offline half is
     * independently capped at [SIDE_SEARCH_LIMIT], matching the pre-fold
     * behavior where both were 10). A failed gated round surfaces as
     * `seerrError = true` where the preview swallows silently, so the screen
     * can render its retry row. The offline scan keeps the preview's plain
     * swallow: best-effort, its failure renders as an empty row. Never throws.
     */
    fun sideSearch(
        queries: Flow<String>,
        seerrLimit: Int = SIDE_SEARCH_LIMIT,
    ): Flow<MediaSideSearchState>

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

        /** The search screen's side-row cap — the Seerr take and the offline
         * scan limit share it (both shipped at 10). */
        const val SIDE_SEARCH_LIMIT: Int = 10

        /** The search screen's shipping value — the more keystroke-heavy surface. */
        const val DEFAULT_DEBOUNCE_MS: Long = 300
    }
}
