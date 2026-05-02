package com.raulshma.jellyplay.feature.player.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set

    fun play(itemId: String) {
        viewModelScope.launch {
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    title = detail.item.name
                    artist = detail.item.albumArtist ?: detail.item.artistItems.firstOrNull()?.name ?: ""
                }
        }
    }

    fun togglePlayPause() {}

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)
}
