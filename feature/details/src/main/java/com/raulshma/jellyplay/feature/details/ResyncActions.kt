package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.download.DownloadIntake
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.sync.OfflineSyncManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Resync / re-download actions extracted from [DetailViewModel]. Owns the
 * [ResyncUiState] progress affordance surfaced inline on the detail screen; the
 * update-available badge itself clears reactively via the provider's
 * [com.raulshma.jellyplay.core.data.repository.MediaDetailProvider] attachment
 * once the baseline updates.
 */
internal class ResyncActions(
    private val scope: CoroutineScope,
    private val session: StateFlow<DetailSession?>,
    private val offlineSyncManager: OfflineSyncManager,
    private val mediaRepository: MediaRepository,
    private val offlineRepository: OfflineRepository,
    private val downloadIntake: DownloadIntake,
) {
    /**
     * Hilt factory bundling this helper's exclusive collaborator
     * ([OfflineSyncManager]; [DownloadIntake] is shared with
     * [DownloadLifecycleActions.Factory] and resolved here independently) so
     * neither appears in the [DetailViewModel] constructor.
     */
    class Factory @Inject constructor(
        private val offlineSyncManager: OfflineSyncManager,
        private val downloadIntake: DownloadIntake,
    ) {
        fun create(
            scope: CoroutineScope,
            session: StateFlow<DetailSession?>,
            mediaRepository: MediaRepository,
            offlineRepository: OfflineRepository,
        ): ResyncActions = ResyncActions(
            scope = scope,
            session = session,
            offlineSyncManager = offlineSyncManager,
            mediaRepository = mediaRepository,
            offlineRepository = offlineRepository,
            downloadIntake = downloadIntake,
        )
    }

    private val _state = MutableStateFlow<ResyncUiState>(ResyncUiState.Idle)
    val state: StateFlow<ResyncUiState> = _state.asStateFlow()

    /**
     * TTL-gated server freshness check for the current item. Safe to call on
     * every screen entry — [OfflineSyncManager.checkForUpdates] no-ops
     * network-wise when within the per-item TTL (1h) or when offline. The
     * resulting flag re-emits reactively via the provider's sync-state attachment.
     */
    fun checkForUpdates() {
        val id = session.value?.itemId ?: return
        scope.launch { offlineSyncManager.checkForUpdates(id) }
    }

    /**
     * Re-syncs the current item's metadata and changed images from the server.
     * Surfaces progress via [state]; does NOT re-download the media file even if
     * [com.raulshma.jellyplay.core.model.OfflineSyncState.mediaFileChanged].
     */
    fun resync() {
        val id = session.value?.itemId ?: return
        if (_state.value is ResyncUiState.Working) return
        scope.launch {
            _state.value = ResyncUiState.Working
            // Mirror redownloadMedia: a thrown exception must surface as Error,
            // not leave state latched at Working (which would freeze the UI).
            val newState = try {
                val result = offlineSyncManager.resyncItem(id)
                if (result.succeeded) {
                    ResyncUiState.Done(result)
                } else {
                    ResyncUiState.Error(
                        result.steps.lastOrNull { step -> !step.success }?.message ?: "Resync failed",
                    )
                }
            } catch (e: Exception) {
                ResyncUiState.Error(e.message ?: "Resync failed")
            }
            _state.value = newState
        }
    }

    /** Resets [state] to [ResyncUiState.Idle] (no-op while Working). */
    fun clearResyncState() {
        if (_state.value is ResyncUiState.Working) return
        _state.value = ResyncUiState.Idle
    }

    /**
     * Re-downloads the media file when the server's MediaSource changed (a
     * metadata/images resync can't fix that). Fetches fresh detail, removes the
     * stale offline item (clearing its file + row + stale flags), then routes
     * through [DownloadIntake.start] — the same single-item path the online
     * detail screen uses — so the new file + fresh baseline land together.
     *
     * This is the DETAIL re-download path; it must NOT route through
     * [com.raulshma.jellyplay.core.data.repository.ArrRepository.redownloadMedia]
     * (that is the *arr Manage-Series action, unrelated).
     */
    fun redownloadMedia() {
        val id = session.value?.itemId ?: return
        if (_state.value is ResyncUiState.Working) return
        scope.launch {
            _state.value = ResyncUiState.Working
            val newState = try {
                // Force read: fetch fresh detail, bypassing the TTL cache.
                val detail = mediaRepository.getMediaDetail(id, force = true).getOrNull()
                if (detail == null) {
                    ResyncUiState.Error("Couldn't load latest details")
                } else {
                    offlineRepository.deleteOfflineItem(id)
                    val result = downloadIntake.start(detail)
                    if (result.downloadItem != null) {
                        ResyncUiState.Done(com.raulshma.jellyplay.core.model.ResyncResult(id, emptyList(), mediaFileChanged = false))
                    } else {
                        ResyncUiState.Error(result.error ?: "Re-download failed")
                    }
                }
            } catch (e: Exception) {
                ResyncUiState.Error(e.message ?: "Re-download failed")
            }
            _state.value = newState
        }
    }
}
