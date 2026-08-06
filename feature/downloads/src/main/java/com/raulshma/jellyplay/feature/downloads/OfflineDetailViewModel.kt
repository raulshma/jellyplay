package com.raulshma.jellyplay.feature.downloads

import androidx.lifecycle.SavedStateHandle
import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OfflineSyncState
import com.raulshma.jellyplay.core.model.ResyncResult
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OfflineDetailViewModel @Inject constructor(
    private val offlineRepository: OfflineRepository,
    private val playbackRepository: PlaybackRepository,
    private val mediaRepository: MediaRepository,
    private val libraryStore: LibraryStore,
    private val editor: PreferencesEditor,
    private val syncManager: OfflineSyncManager,
    private val downloadIntake: DownloadIntake,
    @Suppress("unused") savedStateHandle: SavedStateHandle,
) : JellyPlayViewModel() {

    private val _itemId = MutableStateFlow<String?>(null)

    val item: StateFlow<OfflineMediaItem?> =
        _itemId.flatMapLatest { id -> if (id == null) flowOf(null) else offlineRepository.getOfflineDetail(id) }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * Freshness state for the loaded item. Drives the "update available" badge;
     * updates reactively as the sync manager flips the persisted flags.
     */
    val syncState: StateFlow<OfflineSyncState?> =
        _itemId.flatMapLatest { id ->
            if (id == null) flowOf(null) else offlineRepository.getOfflineSyncState(id)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    /** Resync operation status, for the inline progress affordance. */
    private val _resyncState = MutableStateFlow<ResyncUiState>(ResyncUiState.Idle)
    val resyncState: StateFlow<ResyncUiState> = _resyncState

    /** Children (e.g. album tracks) for the item, with download rows joined. */
    val children: StateFlow<List<OfflineMediaItem>> =
        _itemId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else offlineRepository.getChildren(id)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Seasons for the loaded item's series. Populated only when the item is an
     * episode (i.e. [OfflineMediaItem.seriesId] is set) so the detail screen can
     * render the same seasons/episodes list the online episode detail shows.
     * Empty otherwise.
     */
    val seasons: StateFlow<List<OfflineMediaItem>> =
        item.flatMapLatest { loaded ->
            val seriesId = loaded?.seriesId
            if (seriesId == null) flowOf(emptyList()) else offlineRepository.getSeasonsForSeries(seriesId)
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Episodes keyed by season id for the loaded item's series. Each season's
     * Room flow is subscribed reactively (not snapshotted), so deleting a single
     * episode re-emits that season's flow and the list refreshes on its own —
     * no reload needed. Mirrors OfflineSeriesViewModel.episodes.
     */
    val episodes: StateFlow<Map<String, List<OfflineMediaItem>>> =
        seasons.flatMapLatest { seasonList ->
            if (seasonList.isEmpty()) {
                flowOf(emptyMap())
            } else {
                val perSeason: List<Flow<Pair<String, List<OfflineMediaItem>>>> = seasonList.map { season ->
                    offlineRepository.getEpisodesForSeason(season.id).map { eps -> season.id to eps }
                }
                combine(perSeason) { pairs -> pairs.toMap() }
            }
        }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** Compact vertical episode list preference (shared with the online detail screen). */
    val compactEpisodeList: StateFlow<Boolean> =
        libraryStore.library.map { it.compactEpisodeList }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    fun setCompactEpisodeList(enabled: Boolean) =
        editor.edit { library.setCompactEpisodeList(enabled) }

    /** Drives the screen's data. Called once from a LaunchedEffect(itemId). */
    fun load(itemId: String) {
        if (_itemId.value == itemId) return
        _itemId.value = itemId
    }

    /**
     * Builds the primary-image URL for a cast member. The matching image is
     * preloaded into Coil's cache at download time, so this is a cache hit when
     * offline. Used by the cast row in the offline detail screen.
     */
    fun personImageUrl(personId: String): String =
        playbackRepository.getImageUrl(personId, maxWidth = 200)

    fun delete(onDone: () -> Unit) {
        val id = _itemId.value ?: return
        launch {
            offlineRepository.deleteOfflineItem(id)
            onDone()
        }
    }

    /** Deletes a single episode from the embedded seasons list. */
    fun deleteEpisode(episodeId: String) {
        launch { offlineRepository.deleteOfflineItem(episodeId) }
    }

    /**
     * Marks every downloaded episode in [seasonId] (and the season row itself)
     * as watched. Routes through [MediaRepository.markPlayed] so the change is
     * applied to the local offline DB AND enqueued into the playback outbox for
     * server sync on reconnect (or pushed immediately when online) — mirroring
     * the online season-mark path. The batch UPDATE flows back through the
     * reactive [seasons]/[episodes] queries so the UI refreshes on its own.
     */
    fun markSeasonPlayed(seasonId: String) {
        launch { mediaRepository.markPlayed(seasonId) }
    }

    /**
     * Marks every downloaded episode in [seasonId] (and the season row itself)
     * as unwatched, clearing position/percentage. See [markSeasonPlayed]; uses
     * [MediaRepository.markUnplayed] for the same offline-aware sync behavior.
     */
    fun markSeasonUnplayed(seasonId: String) {
        launch { mediaRepository.markUnplayed(seasonId) }
    }

    /**
     * TTL-gated server freshness check. Safe to call on every screen entry —
     * the manager no-ops (network-wise) when within the per-item TTL or when
     * offline, so this is cheap. The resulting flag re-emits via [syncState].
     */
    fun checkForUpdates() {
        val id = _itemId.value ?: return
        launch { syncManager.checkForUpdates(id) }
    }

    /**
     * Re-syncs the loaded item's metadata and changed images. Surfaces progress
     * via [resyncState]; the badge clears reactively once the baseline updates.
     * Does NOT re-download the media file even if [OfflineSyncState.mediaFileChanged].
     */
    fun resync() {
        val id = _itemId.value ?: return
        if (_resyncState.value is ResyncUiState.Working) return
        launch {
            _resyncState.value = ResyncUiState.Working
            val result = syncManager.resyncItem(id)
            _resyncState.value = if (result.succeeded) {
                ResyncUiState.Done(result)
            } else {
                ResyncUiState.Error(result.steps.lastOrNull { !it.success }?.message ?: "Resync failed")
            }
        }
    }

    fun clearResyncState() {
        if (_resyncState.value !is ResyncUiState.Working) _resyncState.value = ResyncUiState.Idle
    }

    /**
     * Re-downloads the media file when the server's MediaSource changed (a
     * metadata/images resync can't fix that). Fetches fresh detail, removes the
     * stale offline item (clearing its file + row + stale flags), then routes
     * through [DownloadIntake.start] — the same single-item path the online
     * detail screen uses — so the new file + fresh baseline land together.
     * Surfaces progress via [resyncState] (reuses the Working/Done/Error states).
     */
    fun redownloadMedia() {
        val id = _itemId.value ?: return
        if (_resyncState.value is ResyncUiState.Working) return
        launch {
            _resyncState.value = ResyncUiState.Working
            _resyncState.value = try {
                mediaRepository.invalidateDetailCache(id)
                val detail = mediaRepository.getMediaDetail(id).getOrNull()
                if (detail == null) {
                    ResyncUiState.Error("Couldn't load latest details")
                } else {
                    offlineRepository.deleteOfflineItem(id)
                    val result = downloadIntake.start(detail)
                    if (result.downloadItem != null) ResyncUiState.Done(
                        com.raulshma.jellyplay.core.model.ResyncResult(
                            itemId = id,
                            steps = emptyList(),
                            mediaFileChanged = false,
                        ),
                    ) else ResyncUiState.Error(result.error ?: "Re-download failed")
                }
            } catch (e: Exception) {
                ResyncUiState.Error(e.message ?: "Re-download failed")
            }
        }
    }
}

/** UI-facing resync status for the offline detail screen. */
sealed interface ResyncUiState {
    data object Idle : ResyncUiState
    data object Working : ResyncUiState
    data class Done(val result: ResyncResult) : ResyncUiState
    data class Error(val message: String) : ResyncUiState
}
