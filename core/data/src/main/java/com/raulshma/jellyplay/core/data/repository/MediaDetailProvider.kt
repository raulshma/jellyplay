package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.MediaDetailSnapshot
import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.flow.Flow

/**
 * The single external seam for resolving a media-detail screen. Owns the
 * remote/local source decision, the source-dependent read graph (detail,
 * seasons/episodes, album children, local subtitles), companion-content
 * loading, local asset resolution, capability derivation, and the reactive
 * download/sync attachment. The repository, DAO, file, and network branches
 * remain internal seams of the production adapter.
 *
 * Source policy:
 * - Online (manual mode `ONLINE`) → the server response is the primary detail,
 *   even when a completed download exists. A download may still be *attached*.
 * - `NetworkStatus.Local` still permits the remote attempt (Jellyfin may be
 *   reachable on the LAN).
 * - Manual or auto offline mode with a local row → local projection; the server
 *   is never contacted merely because of a pull-to-refresh.
 * - Remote failure with a local row → in-place local fallback (origin
 *   `LOCAL_REMOTE_FAILURE`); a later connectivity return retries resolution.
 * - No local row and no remote result → [DetailLoadError] (unavailable offline
 *   when in offline mode, otherwise the access/network error).
 *
 * The flow may emit incrementally: a base loaded snapshot first, then a
 * replacement snapshot as remote subordinate work or local Room flows update.
 * It must use structured child coroutines so changing `itemId` cannot write
 * stale sections onto the next item.
 */
interface MediaDetailProvider {
    /**
     * Resolves [itemId] into a stream of [DetailLoadState]s. Cold; the caller
     * (the detail ViewModel) collects for the lifetime of the screen.
     */
    fun observe(itemId: String): Flow<DetailLoadState>

    /**
     * Pull-to-refresh semantics. For a remote primary, invalidates the same
     * caches the detail ViewModel previously invalidated and re-fetches; for a
     * local primary, re-observes local state without a server round-trip and
     * must not violate manual offline mode.
     */
    suspend fun refresh(itemId: String)

    /**
     * Optimistic in-place rewrite of one season's episodes, used by the detail
     * screen's `markSeasonPlayed`/`markSeasonUnplayed` to flip every episode in
     * a season without a server round-trip. [transform] receives the season's
     * current episodes (in the snapshot) and returns the rewritten list.
     *
     * The rewrite is serialized against the session's resolution mutex, then
     * re-emitted as a new-generation [MediaDetailSnapshot] through [observe] —
     * consumers adopt the rewritten content sections via their normal reducer
     * path. After re-emission the series catalogue cache is dropped so the next
     * screen entry refetches the fully-cascaded server state (the existing
     * "no refetch within this screen" contract is preserved: no server round-trip
     * fires for the active session).
     *
     * Replaces the former direct `EpisodeCatalogue.updateSeasonEpisodes` +
     * `invalidateSeries` pair the consumer used to reach for; closing the race
     * window between the two calls is the reason this lives on the provider.
     *
     * No-op (returns without re-emitting) if the [itemId] session is not active
     * or the series snapshot isn't loaded — the server mutation still lands;
     * the next load picks up the post-cascade state.
     */
    suspend fun applyOptimisticSeasonRewrite(
        itemId: String,
        seasonId: String,
        transform: (List<MediaItem>) -> List<MediaItem>,
    )

    /**
     * On-demand per-season expand. Fetches [seasonId]'s episodes via the shared
     * catalogue (serving from the cached series snapshot if present, else fetching
     * the one season), merges the result into the [itemId] session's content as a
     * new-generation [MediaDetailSnapshot] re-emitted through [observe], and
     * returns the episode list for callers (e.g. the download sheet) that need
     * the values synchronously.
     *
     * Replaces the former direct `EpisodeCatalogue.loadSeasonEpisodes` call from
     * the consumer. One method serves both the seasons-section expand and the
     * download sheet's per-season cache populate.
     */
    suspend fun expandSeason(itemId: String, seasonId: String): List<MediaItem>

    /**
     * Canonical playback-order episode ids for [seriesId]. Serves from the
     * loaded session content when present (no I/O); otherwise cold-loads via
     * the shared catalogue. Used by the playlist-expansion path that resolves a
     * series into its episode ids (Jellyfin rejects a bare series id in a
     * playlist).
     *
     * Replaces the former direct `EpisodeCatalogue.loadSeriesEpisodes` fallback
     * the consumer used when its in-memory snapshot was empty (e.g. the playlist
     * picker opened before episodes resolved).
     */
    suspend fun canonicalEpisodeIds(seriesId: String): List<String>

    /**
     * Drop the catalogue cache for [seriesId] so the next screen entry refetches.
     * Defensive re-entry freshness used by the consumer when navigating away from
     * a series. Replaces the former direct `EpisodeCatalogue.invalidateSeries`.
     */
    fun invalidate(seriesId: String)
}

/**
 * Load state emitted by [MediaDetailProvider.observe].
 *
 * Has no "success banner" slot — resync/re-download action results are a
 * distinct concern owned by the ViewModel (`ResyncUiState`), not the load state.
 */
sealed interface DetailLoadState {
    data object Loading : DetailLoadState
    data class Loaded(val snapshot: MediaDetailSnapshot) : DetailLoadState
    data class Error(val error: DetailLoadError) : DetailLoadState
}

/**
 * Classified error for a detail load. Exactly one of [isUnavailableOffline]
 * (offline mode with no local row) or [isAccessDenied] (HTTP 401/403) is
 * expected; otherwise a generic network/load failure.
 */
data class DetailLoadError(
    val message: String,
    val isAccessDenied: Boolean = false,
    val isUnavailableOffline: Boolean = false,
)
