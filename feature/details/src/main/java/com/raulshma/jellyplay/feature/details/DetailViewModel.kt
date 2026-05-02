package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@Stable
class DetailState {
    var detail by mutableStateOf<MediaDetail?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    fun setDetail(detail: MediaDetail?) { this.detail = detail }
    fun setLoading(loading: Boolean) { isLoading = loading }
    fun setError(error: String?) { this.error = error }
}

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    private val _state = DetailState()
    val detail get() = _state.detail
    val isLoading get() = _state.isLoading
    val error get() = _state.error

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            _state.setLoading(true)
            _state.setError(null)
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { _state.setDetail(it) }
                .onFailure { _state.setError(it.message ?: "Failed to load details") }
            _state.setLoading(false)
        }
    }

    fun toggleFavorite() {
        val itemId = _state.detail?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.toggleFavorite(itemId)
                .onSuccess { loadItem(itemId) }
        }
    }

    fun markPlayed() {
        val itemId = _state.detail?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.markPlayed(itemId)
            loadItem(itemId)
        }
    }

    fun markUnplayed() {
        val itemId = _state.detail?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.markUnplayed(itemId)
            loadItem(itemId)
        }
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    fun getBackdropUrl(itemId: String): String =
        playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)
}
