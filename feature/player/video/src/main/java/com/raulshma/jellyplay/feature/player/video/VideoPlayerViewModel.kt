package com.raulshma.jellyplay.feature.player.video

import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.media3.exoplayer.ExoPlayer
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) : ViewModel() {

    private var _exoPlayer by mutableStateOf<ExoPlayer?>(null)
    val exoPlayer get() = _exoPlayer
    private var _streamUrl by mutableStateOf<String?>(null)
    val streamUrl get() = _streamUrl
    private var _title by mutableStateOf("")
    val title get() = _title
    private var _isPlaying by mutableStateOf(false)
    val isPlaying get() = _isPlaying

    private var progressJob: Job? = null
    private var playSessionId: String = java.util.UUID.randomUUID().toString()
    private var currentItemId: String? = null

    fun initialize(itemId: String, mediaSourceId: String?, startPositionTicks: Long) {
        currentItemId = itemId
        _exoPlayer = ExoPlayer.Builder(context).build()

        viewModelScope.launch {
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    _title = detail.item.name
                    val source = if (mediaSourceId != null) {
                        detail.mediaSources.find { it.id == mediaSourceId }
                    } else {
                        detail.mediaSources.firstOrNull()
                    }
                    val url = playbackRepository.getStreamUrl(
                        itemId,
                        source?.id ?: "",
                        startPositionTicks,
                    )
                    _streamUrl = url

                    playbackRepository.reportPlaybackStart(
                        com.raulshma.jellyplay.core.model.PlaybackStartInfo(
                            itemId = itemId,
                            sessionId = playSessionId,
                            mediaSourceId = source?.id,
                        )
                    )

                    startProgressReporting()
                }
                .onFailure {
                    _title = "Error loading media"
                }
        }
    }

    private fun startProgressReporting() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(10_000)
                val player = _exoPlayer ?: continue
                val itemId = currentItemId ?: continue
                playbackRepository.reportPlaybackProgress(
                    com.raulshma.jellyplay.core.model.PlaybackProgress(
                        itemId = itemId,
                        sessionId = playSessionId,
                        positionTicks = player.currentPosition * 10_000,
                        isPaused = !player.isPlaying,
                    )
                )
            }
        }
    }

    fun release() {
        viewModelScope.launch {
            val player = _exoPlayer ?: return@launch
            val itemId = currentItemId ?: return@launch
            playbackRepository.reportPlaybackStopped(
                itemId = itemId,
                sessionId = playSessionId,
                positionTicks = player.currentPosition * 10_000,
            )
        }
        progressJob?.cancel()
        _exoPlayer?.release()
        _exoPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
