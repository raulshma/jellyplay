package com.raulshma.jellyplay.feature.livetv.channels

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.livetv.LiveTvLoad
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@Immutable
data class ChannelsUiState(
    val channels: List<LiveTvChannel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class ChannelsViewModel(
    private val mediaRepository: LiveTvRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    videoMiniPlayerState: VideoMiniPlayerState,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(ChannelsUiState())
    val uiState: StateFlow<ChannelsUiState> = _uiState.flow

    private val _nowPlayingChannelId = MutableStateFlow<String?>(null)
    val nowPlayingChannelId: StateFlow<String?> = _nowPlayingChannelId.asStateFlow()

    val favoriteChannelIds: StateFlow<Set<String>> = appRuntimeStateStore.state
        .map { it.favoriteChannels }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    init {
        loadChannels()
        launch {
            videoMiniPlayerState.itemId.collect { itemId ->
                _nowPlayingChannelId.value = itemId
            }
        }
    }

    fun loadChannels() {
        launch {
            LiveTvLoad.load(
                start = { _uiState.update { it.copy(isLoading = true, error = null) } },
                fetch = { mediaRepository.getLiveTvChannels(limit = 100) },
                onSuccess = { channels ->
                    val favorites = appRuntimeStateStore.state.value.favoriteChannels
                    // Stable partition, not an O(n log n) sort: favorites lead
                    // in server order, then the rest — identical output to the
                    // old (stable) sortedByDescending { it.id in favorites }.
                    val sorted = if (favorites.isEmpty()) {
                        channels
                    } else {
                        channels.partition { it.id in favorites }.let { (fav, rest) -> fav + rest }
                    }
                    _uiState.update { it.copy(channels = sorted, isLoading = false) }
                },
                onFailure = { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } },
            )
        }
    }

    fun toggleFavorite(channelId: String) {
        launch {
            val current = appRuntimeStateStore.state.value.favoriteChannels
            val updated = if (channelId in current) current - channelId else current + channelId
            appRuntimeStateStore.setFavoriteChannels(updated)
            val favorites = updated
            _uiState.update { state ->
                // Same stable partition as loadChannels — identical output to
                // the old stable sort, O(n) instead of O(n log n).
                val sorted = if (favorites.isEmpty()) {
                    state.channels
                } else {
                    state.channels.partition { it.id in favorites }.let { (fav, rest) -> fav + rest }
                }
                state.copy(channels = sorted)
            }
        }
    }

    fun setNowPlayingChannelId(channelId: String?) {
        _nowPlayingChannelId.value = channelId
    }

    fun getImageUrl(itemId: String, imageTag: String?): String =
        imageUrlProvider.getImageUrlOrNull(itemId, imageTag)
}
