package com.raulshma.jellyplay.feature.livetv.recordings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.model.PendingConfirmation
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.livetv.LiveTvLoad

@Immutable
data class RecordingsUiState(
    val recordings: List<LiveTvRecording> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    /** Delete-confirmation machine behind [pendingDelete]. */
    val deleteConfirmation: PendingConfirmation<LiveTvRecording> = PendingConfirmation(),
    val isDeleting: Boolean = false,
) {
    /** Recording awaiting a delete confirmation, if any. Null hides the dialog. */
    val pendingDelete: LiveTvRecording?
        get() = deleteConfirmation.item
}

/**
 * Recordings tab — mirrors jellyfin-web `livetvrecordings.js`: fetches the
 * latest recordings list. (Recording folders are intentionally omitted —
 * Jellyfin's web client no longer exposes them.)
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
            LiveTvLoad.load(
                start = { _uiState.update { it.copy(isLoading = true, error = null) } },
                fetch = { mediaRepository.getRecordings(limit = LATEST_LIMIT) },
                onSuccess = { recordings ->
                    _uiState.update { s -> s.copy(recordings = recordings, isLoading = false) }
                },
                onFailure = { e ->
                    // The legacy ladder settled unconditionally with
                    // getOrDefault(emptyList()) — a failure still clears the
                    // previous list, it does not preserve it.
                    _uiState.update { s -> s.copy(recordings = emptyList(), error = e.message, isLoading = false) }
                },
            )
        }
    }

    fun getImageUrl(itemId: String, imageTag: String?): String =
        imageUrlProvider.getImageUrlOrNull(itemId, imageTag)

    // ── Delete / cancel affordance ──────────────────────────────────────────

    /** Opens the confirm dialog for deleting [recording] (and cancelling its series timer if set). */
    fun showDeleteDialog(recording: LiveTvRecording) {
        _uiState.update { it.copy(deleteConfirmation = it.deleteConfirmation.hold(recording)) }
    }

    /** Dismiss fold: the machine's in-flight guard, fed the site's [RecordingsUiState.isDeleting] flag. */
    fun dismissDeleteDialog() {
        _uiState.update { it.copy(deleteConfirmation = it.deleteConfirmation.dismiss(it.isDeleting)) }
    }

    /**
     * Deletes the recording pending confirmation. If it has a [LiveTvRecording.seriesTimerId]
     * the series timer is cancelled first so future episodes aren't recorded,
     * then the recorded item itself is deleted.
     *
     * Confirm never clears — the settle arm is success-only: explicit
     * [PendingConfirmation.clear] where the reload triggers; failure keeps
     * the dialog open with the error. The [PendingConfirmation.confirm] gate
     * refuses a second tap while [RecordingsUiState.isDeleting] is raised.
     */
    fun deleteRecording() {
        val state = _uiState.value
        val recording = state.deleteConfirmation.confirm(inFlight = state.isDeleting) ?: return
        launch {
            _uiState.update { it.copy(isDeleting = true) }
            // Cancel the series timer (best-effort) if one is attached.
            recording.seriesTimerId?.let { mediaRepository.cancelSeriesTimer(it) }
            val result = mediaRepository.deleteRecording(recording.id)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isDeleting = false,
                        deleteConfirmation = it.deleteConfirmation.clear(),
                        error = null,
                    )
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
