package com.raulshma.jellyplay.feature.livetv.channels

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : JellyPlayViewModel() {

    private val _channels = composeState<List<LiveTvChannel>>(emptyList())
    val channels: List<LiveTvChannel> get() = _channels.value

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    init {
        loadChannels()
    }

    fun loadChannels() {
        launch {
            _isLoading.value = true
            _error.value = null
            mediaRepository.getLiveTvChannels(limit = 100)
                .onSuccess { _channels.value = it }
                .onFailure { _error.value = it.message }
            _isLoading.value = false
        }
    }

    fun getImageUrl(itemId: String, imageTag: String?): String {
        return if (imageTag != null) {
            playbackRepository.getImageUrl(itemId, maxWidth = 400)
        } else ""
    }
}
