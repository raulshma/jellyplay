package com.raulshma.jellyplay.core.data.search

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.data.repository.SearchHistoryRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
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

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@Singleton
class MediaSearchEngineImpl @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val seerrRepository: SeerrRepository,
    private val searchHistoryRepository: SearchHistoryRepository,
    private val serverIdentityStore: ServerIdentityStore,
    private val experimentalStore: ExperimentalStore,
    private val offlineModeManager: OfflineModeManager,
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
     * One preview round: Jellyfin + Seerr in parallel, both failure-swallowing
     * (the preview must never throw), history recorded when Jellyfin matched.
     */
    private suspend fun runPreviewSearch(query: String, limit: Int, seerrLimit: Int): MediaSearchPreviewState =
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
