package com.raulshma.jellyplay.feature.livetv.recordings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.ui.message.UiMessage
import com.raulshma.jellyplay.core.ui.viewmodel.ConfirmationHost
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto
import com.raulshma.jellyplay.feature.livetv.generated.resources.Res
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_error_delete_recording
import com.raulshma.jellyplay.feature.livetv.generated.resources.livetv_error_load_recordings

@Immutable
data class RecordingsUiState(
    val recordings: List<LiveTvRecording> = emptyList(),
    val isLoading: Boolean = false,
    /** The load/delete failure — resolved to text at render ([UiMessage.asText]). */
    val error: UiMessage? = null,
    val isDeleting: Boolean = false,
)

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

    /**
     * Delete-confirmation host (moved out of [RecordingsUiState]; the
     * machine's writes go through it instead of uiState copies). Settle arm:
     * success-only [ConfirmationHost.clear] where the reload triggers — a
     * failure keeps the dialog open over the error. The in-flight fact is
     * the state's [RecordingsUiState.isDeleting] flag, fed per call, so the
     * confirm gate and the dismiss are both refused while the request runs.
     */
    val deleteConfirmation = ConfirmationHost<LiveTvRecording>()

    init { load() }

    fun load() {
        launch {
            loadInto(
                start = { _uiState.update { it.copy(isLoading = true, error = null) } },
                fetch = { mediaRepository.getRecordings(limit = LATEST_LIMIT) },
                onSuccess = { recordings ->
                    _uiState.update { s -> s.copy(recordings = recordings, isLoading = false) }
                },
                onFailure = { e ->
                    // The legacy ladder settled unconditionally with
                    // getOrDefault(emptyList()) — a failure still clears the
                    // previous list, it does not preserve it.
                    _uiState.update {
                        s -> s.copy(recordings = emptyList(), error = UiMessage.of(e, Res.string.livetv_error_load_recordings), isLoading = false)
                    }
                },
            )
        }
    }

    fun getImageUrl(itemId: String, imageTag: String?): String =
        imageUrlProvider.getImageUrlOrNull(itemId, imageTag)

    // ── Delete / cancel affordance ──────────────────────────────────────────

    /** Opens the confirm dialog for deleting [recording] (and cancelling its series timer if set). */
    fun showDeleteDialog(recording: LiveTvRecording) = deleteConfirmation.show(recording)

    /** Dismiss fold: the host's in-flight guard, fed the site's [RecordingsUiState.isDeleting] flag. */
    fun dismissDeleteDialog() = deleteConfirmation.dismiss(inFlight = _uiState.value.isDeleting)

    /**
     * Deletes the recording pending confirmation. If it has a [LiveTvRecording.seriesTimerId]
     * the series timer is cancelled first so future episodes aren't recorded,
     * then the recorded item itself is deleted.
     *
     * The deferred confirm arm never clears — the settle arm is success-only:
     * explicit [ConfirmationHost.clear] where the reload triggers; failure
     * keeps the dialog open with the error. The gate refuses a second tap
     * while [RecordingsUiState.isDeleting] is raised.
     */
    fun deleteRecording() {
        val recording = deleteConfirmation.confirm(inFlight = _uiState.value.isDeleting) ?: return
        launch {
            _uiState.update { it.copy(isDeleting = true) }
            // Cancel the series timer (best-effort) if one is attached.
            recording.seriesTimerId?.let { mediaRepository.cancelSeriesTimer(it) }
            val result = mediaRepository.deleteRecording(recording.id)
            if (result.isSuccess) {
                _uiState.update { it.copy(isDeleting = false, error = null) }
                deleteConfirmation.clear()
                load()
            } else {
                _uiState.update {
                    it.copy(isDeleting = false, error = UiMessage.of(result, Res.string.livetv_error_delete_recording))
                }
            }
        }
    }

    private companion object {
        const val LATEST_LIMIT = 24
    }
}
