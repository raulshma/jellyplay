package com.raulshma.jellyplay.feature.details

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

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    private val _detail = mutableStateOf<MediaDetail?>(null)
    val detail: androidx.compose.runtime.State<MediaDetail?> get() = _detail
    private val _isLoading = mutableStateOf(false)
    val isLoading: androidx.compose.runtime.State<Boolean> get() = _isLoading
    private val _error = mutableStateOf<String?>(null)
    val error: androidx.compose.runtime.State<String?> get() = _error

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { _detail.value = it }
                .onFailure { _error.value = it.message ?: "Failed to load details" }
            _isLoading.value = false
        }
    }

    fun toggleFavorite() {
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.toggleFavorite(itemId)
                .onSuccess { loadItem(itemId) }
        }
    }

    fun markPlayed() {
        val itemId = _detail.value?.item?.id ?: return
        viewModelScope.launch {
            mediaRepository.markPlayed(itemId)
            loadItem(itemId)
        }
    }

    fun markUnplayed() {
        val itemId = _detail.value?.item?.id ?: return
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
