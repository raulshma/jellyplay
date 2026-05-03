package com.raulshma.jellyplay.feature.livetv.channels

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.LiveTvChannel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    var channels by mutableStateOf<List<LiveTvChannel>>(emptyList())
        private set

    var isLoading by mutableStateOf(false)
        private set

    var error by mutableStateOf<String?>(null)
        private set

    init {
        loadChannels()
    }

    fun loadChannels() {
        viewModelScope.launch {
            isLoading = true
            error = null
            mediaRepository.getLiveTvChannels(limit = 100)
                .onSuccess { channels = it }
                .onFailure { error = it.message }
            isLoading = false
        }
    }

    fun getImageUrl(itemId: String, imageTag: String?): String {
        return if (imageTag != null) {
            playbackRepository.getImageUrl(itemId, maxWidth = 400)
        } else ""
    }
}
