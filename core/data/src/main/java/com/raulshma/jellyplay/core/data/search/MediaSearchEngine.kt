package com.raulshma.jellyplay.core.data.search

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.toMediaItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

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

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class MediaSearchEngineImpl @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val serverIdentityStore: ServerIdentityStore,
    private val experimentalStore: ExperimentalStore,
    private val offlineModeManager: OfflineModeManager,
    private val offlineRepository: OfflineRepository,
) : MediaSearchEngine {

    override val debounceMs: Long = MediaSearchEngine.DEFAULT_DEBOUNCE_MS

    override fun preview(
        queries: Flow<String>,
        limit: Int,
        seerrLimit: Int,
    ): Flow<MediaSearchPreviewState> =
        queries
            .debounce(debounceMs)
            .distinctUntilChanged()
            .flatMapLatest { query ->
                if (query.isBlank()) {
                    flowOf(MediaSearchPreviewState(query, emptyList(), emptyList(), isSearching = false))
                } else {
                    flow {
                        emit(MediaSearchPreviewState(query, emptyList(), emptyList(), isSearching = true))
                        emit(runPreviewSearch(query, limit, seerrLimit))
                    }
                }
            }

    /**
     * The preview contract's failure policy: rethrow cancellation, swallow
     * every other failure as `null` — preview search must never throw.
     */
    private suspend fun <T> swallowErrors(block: suspend () -> T): T? =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    /**
     * One preview round, branched once on the offline predicate: the offline
     * library while an offline mode is active, the server pair otherwise. Both
     * branches share the failure policy (swallow everything but cancellation)
     * and the history policy (a round with matches records the query).
     */
    private suspend fun runPreviewSearch(query: String, limit: Int, seerrLimit: Int): MediaSearchPreviewState =
        if (offlineModeManager.isOffline) {
            runOfflinePreviewSearch(query, limit)
        } else {
            runOnlinePreviewSearch(query, limit, seerrLimit)
        }

    /**
     * One online preview round: Jellyfin + Seerr in parallel, both
     * failure-swallowing (the preview must never throw), history recorded when
     * Jellyfin matched.
     */
    private suspend fun runOnlinePreviewSearch(query: String, limit: Int, seerrLimit: Int): MediaSearchPreviewState =
        swallowErrors {
            coroutineScope {
                val jellyfinDeferred = async {
                    swallowErrors { mediaRepository.search(query, limit = limit).getOrNull()?.items }
                        ?: emptyList()
                }
                val seerrDeferred = async {
                    if (!isSeerrSearchAvailable()) {
                        emptyList()
                    } else {
                        swallowErrors { seerrRepository.search(query).getOrNull()?.results?.take(seerrLimit) }
                            ?: emptyList()
                    }
                }
                val jellyfinItems = jellyfinDeferred.await()
                recordHistory(query, jellyfinHadResults = jellyfinItems.isNotEmpty())
                MediaSearchPreviewState(
                    query = query,
                    jellyfin = jellyfinItems,
                    seerr = seerrDeferred.await(),
                    isSearching = false,
                )
            }
        } ?: MediaSearchPreviewState(query, emptyList(), emptyList(), isSearching = false)

    /**
     * One offline preview round: the downloaded library via
     * [OfflineRepository.searchOffline], mapped through
     * [com.raulshma.jellyplay.core.model.toMediaItem] so results render in the
     * Jellyfin slot unchanged (cards, clicks and the detail tree are shared).
     * Seerr is skipped by construction — it is internet-facing. Offline
     * matches record history like server matches, so a query the user repeats
     * after reconnecting is still one tap away.
     */
    private suspend fun runOfflinePreviewSearch(query: String, limit: Int): MediaSearchPreviewState {
        val items = swallowErrors {
            offlineRepository.searchOffline(query, limit).map { it.toMediaItem() }
        } ?: emptyList()
        // The history write is failure-isolated from the results: a failed
        // save is swallowed (the never-throws contract) and the matches
        // above still land. The online round shares the swallow but not the
        // isolation — its outer block drops the whole round on a failed save.
        swallowErrors { recordHistory(query, jellyfinHadResults = items.isNotEmpty()) }
        return MediaSearchPreviewState(
            query = query,
            jellyfin = items,
            seerr = emptyList(),
            isSearching = false,
        )
    }

    override suspend fun isSeerrSearchAvailable(): Boolean =
        swallowErrors {
            if (offlineModeManager.networkStatus.value == NetworkStatus.Local) {
                false
            } else {
                seerrRepository.isConnected().first() && seerrRepository.isSearchEnabled().first()
            }
        } ?: false

    override suspend fun recordHistory(query: String, jellyfinHadResults: Boolean) {
        if (!jellyfinHadResults) return
        if (query.trim().length < 2) return
        // Skip persistence entirely when the user has hidden search history —
        // avoids surfacing past queries the moment they re-enable the setting.
        if (hideSearchHistory()) return
        val userId = serverIdentityStore.activeUserId.first() ?: return
        searchHistoryRepository.saveQuery(query, userId)
    }

    override fun recentHistory(): Flow<List<SearchHistoryItem>> =
        combine(
            serverIdentityStore.activeUserId.flatMapLatest { userId ->
                if (userId != null) searchHistoryRepository.getRecent(userId)
                else flowOf(emptyList())
            },
            // Respect the user's "hide search history" preference: when enabled
            // we expose an empty list while still keeping the underlying
            // history intact for when they re-enable.
            experimentalStore.experimental.map { it.hideSearchHistory }.distinctUntilChanged(),
        ) { history, hide ->
            if (hide) emptyList() else history
        }

    override suspend fun deleteHistoryItem(id: Long) {
        searchHistoryRepository.deleteById(id)
    }

    override suspend fun clearHistory() {
        val userId = serverIdentityStore.activeUserId.first() ?: return
        searchHistoryRepository.clearAll(userId)
    }

    private suspend fun hideSearchHistory(): Boolean =
        experimentalStore.experimental.first().hideSearchHistory
}
