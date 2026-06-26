package com.raulshma.jellyplay.feature.livetv.channels

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@Immutable
data class ChannelsUiState(
    val channels: List<LiveTvChannel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChannelsViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    videoMiniPlayerState: VideoMiniPlayerState,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState.flow

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
            _uiState.update { it.copy(isLoading = true, error = null) }
            mediaRepository.getLiveTvChannels(limit = 100)
                .onSuccess { channels -> _uiState.update { it.copy(channels = channels, isLoading = false) } }
                .onFailure { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
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
