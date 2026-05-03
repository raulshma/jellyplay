package com.raulshma.jellyplay.feature.music.playlists

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.PlaylistItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistDetailViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    var items by mutableStateOf<List<PlaylistItem>>(emptyList())
        private set

    var playlistName by mutableStateOf("")
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    fun load(playlistId: String) {
        viewModelScope.launch {
            isLoading = true
            error = null
            mediaRepository.getPlaylistItems(playlistId, limit = 200)
                .onSuccess {
                    items = it
                    if (it.isNotEmpty()) {
                        // Try to infer playlist name from parent if available; otherwise keep previous
                    }
                }
                .onFailure { error = it.message }
            isLoading = false
        }
    }
}
