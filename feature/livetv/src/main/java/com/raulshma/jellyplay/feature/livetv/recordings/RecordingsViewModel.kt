package com.raulshma.jellyplay.feature.livetv.recordings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.LiveTvRecording
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Immutable
data class RecordingsUiState(
    val recordings: List<LiveTvRecording> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * Recordings tab — mirrors jellyfin-web `livetvrecordings.js`: fetches the
 * latest recordings list, gated by the same 5-minute full-render throttle the
 * web app applies. (Recording folders are intentionally omitted — Jellyfin's
 * web client no longer exposes them.)
 */
@HiltViewModel
class RecordingsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
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

    private companion object {
        const val LATEST_LIMIT = 24
    }
}
