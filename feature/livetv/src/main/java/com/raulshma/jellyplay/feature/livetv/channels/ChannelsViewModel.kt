package com.raulshma.jellyplay.feature.livetv.channels

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.player.video.VideoMiniPlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    videoMiniPlayerState: VideoMiniPlayerState,
) : JellyPlayViewModel() {

    private val _channels = composeState<List<LiveTvChannel>>(emptyList())
    val channels: List<LiveTvChannel> get() = _channels.value

    private val _isLoading = composeState(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _error = composeState<String?>(null)
    val error: String? get() = _error.value

    private val _nowPlayingChannelId = MutableStateFlow<String?>(null)
    val nowPlayingChannelId: StateFlow<String?> = _nowPlayingChannelId.asStateFlow()

    init {
        loadChannels()
        // Mirror the active mini-player item id so the channel row can show a
        // "now playing" indicator without exposing player internals to the UI.
        launch {
            videoMiniPlayerState.itemId.collect { itemId ->
                _nowPlayingChannelId.value = itemId
            }
        }
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

    fun setNowPlayingChannelId(channelId: String?) {
        _nowPlayingChannelId.value = channelId
    }

    fun getImageUrl(itemId: String, imageTag: String?): String {
        return if (imageTag != null) {
            playbackRepository.getImageUrl(itemId, maxWidth = 400)
        } else ""
    }
}
