package com.raulshma.jellyplay.feature.livetv.recordings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel

@Immutable
data class RecordingsUiState(
    val recordings: List<LiveTvRecording> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Recording awaiting a delete confirmation, if any. Null hides the dialog. */
    val pendingDelete: LiveTvRecording? = null,
    val isDeleting: Boolean = false,
)

/**
 * Recordings tab — mirrors jellyfin-web `livetvrecordings.js`: fetches the
 * latest recordings list, gated by the same 5-minute full-render throttle the
 * web app applies. (Recording folders are intentionally omitted — Jellyfin's
 * web client no longer exposes them.)
 */
class RecordingsViewModel(
    private val mediaRepository: LiveTvRepository,
    private val imageUrlProvider: ImageUrlProvider,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(RecordingsUiState())
    val uiState get() = _uiState.flow

    init { load() }

    fun load() {
        launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val recordingsResult = mediaRepository.getRecordings(limit = LATEST_LIMIT)
            recordingsResult.onFailure { _uiState.update { s -> s.copy(error = it.message) } }
            _uiState.update { s ->
                s.copy(
                    recordings = recordingsResult.getOrDefault(emptyList()),
                    isLoading = false,
                )
            }
        }
    }

    fun getImageUrl(itemId: String, imageTag: String?): String =
        if (imageTag != null) imageUrlProvider.getImageUrl(itemId) else ""

    // ── Delete / cancel affordance ──────────────────────────────────────────

    /** Opens the confirm dialog for deleting [recording] (and cancelling its series timer if set). */
    fun showDeleteDialog(recording: LiveTvRecording) {
        _uiState.update { it.copy(pendingDelete = recording) }
    }

    fun dismissDeleteDialog() {
        if (!_uiState.value.isDeleting) {
            _uiState.update { it.copy(pendingDelete = null) }
        }
    }

    /**
     * Deletes the recording pending confirmation. If it has a [LiveTvRecording.seriesTimerId]
     * the series timer is cancelled first so future episodes aren't recorded,
     * then the recorded item itself is deleted.
     */
    fun deleteRecording() {
        val recording = _uiState.value.pendingDelete ?: return
        launch {
            _uiState.update { it.copy(isDeleting = true) }
            // Cancel the series timer (best-effort) if one is attached.
            recording.seriesTimerId?.let { mediaRepository.cancelSeriesTimer(it) }
            val result = mediaRepository.deleteRecording(recording.id)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(isDeleting = false, pendingDelete = null, error = null)
                }
                load()
            } else {
                _uiState.update {
                    it.copy(isDeleting = false, error = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    private companion object {
        const val LATEST_LIMIT = 24
    }
}
