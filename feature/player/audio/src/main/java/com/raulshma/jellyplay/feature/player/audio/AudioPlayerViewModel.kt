package com.raulshma.jellyplay.feature.player.audio

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.PlaybackProgress
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import com.raulshma.jellyplay.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AudioQueueItem(
    val id: String,
    val name: String,
    val artist: String,
    val album: String?,
    val imageUrl: String?,
    val mediaSourceId: String?,
    val durationMs: Long = 0L,
)

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val preferencesStore: UserPreferencesStore,
) : ViewModel() {

    val preferences = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    private var exoPlayer by mutableStateOf<ExoPlayer?>(null)
    private var mediaSession by mutableStateOf<MediaSession?>(null)

    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var album by mutableStateOf("")
        private set
    var albumArtUrl by mutableStateOf("")
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var currentPosition by mutableLongStateOf(0L)
        private set
    var duration by mutableLongStateOf(0L)
        private set
    var speed by mutableFloatStateOf(1.0f)
        private set
    var shuffleMode by mutableStateOf(false)
        private set
    var repeatMode by mutableIntStateOf(0)
        private set

    var queue by mutableStateOf<List<AudioQueueItem>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set

    private var progressJob: Job? = null
    private var positionJob: Job? = null
    private var playSessionId: String = UUID.randomUUID().toString()
    private var currentItemId: String? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            this@AudioPlayerViewModel.isPlaying = isPlaying
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                onTrackEnded()
            }
        }
    }

    fun play(itemId: String) {
        val player = exoPlayer ?: createPlayer()

        viewModelScope.launch {
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    currentItemId = itemId
                    title = detail.item.name
                    artist = detail.item.albumArtist
                        ?: detail.item.artistItems.firstOrNull()?.name
                        ?: ""
                    album = detail.item.album ?: ""
                    albumArtUrl = playbackRepository.getImageUrl(itemId, maxWidth = 600)

                    val source = detail.mediaSources.firstOrNull()
                    val url = playbackRepository.getStreamUrl(
                        itemId,
                        source?.id ?: "",
                    )

                    val mediaItem = androidx.media3.common.MediaItem.fromUri(url)
                    player.setMediaItem(mediaItem)
                    player.prepare()

                    val resumeTicks = detail.item.playbackPositionTicks ?: 0L
                    if (resumeTicks > 0) {
                        player.seekTo(resumeTicks / 10_000)
                    }
                    player.playWhenReady = true

                    if (currentIndex < 0 || queue.getOrNull(currentIndex)?.id != itemId) {
                        val queueItem = AudioQueueItem(
                            id = itemId,
                            name = title,
                            artist = artist,
                            album = album,
                            imageUrl = albumArtUrl,
                            mediaSourceId = source?.id,
                            durationMs = detail.item.runTimeTicks?.let { it / 10_000 } ?: 0L,
                        )
                        queue = queue + queueItem
                        currentIndex = queue.lastIndex
                    }

                    playbackRepository.reportPlaybackStart(
                        PlaybackStartInfo(
                            itemId = itemId,
                            sessionId = playSessionId,
                            mediaSourceId = source?.id,
                        )
                    )

                    startPositionTracking()
                    startProgressReporting()
                }
        }
    }

    fun playQueue(items: List<AudioQueueItem>, startIndex: Int = 0) {
        queue = items
        currentIndex = startIndex
        val item = items.getOrNull(startIndex) ?: return
        play(item.id)
    }

    fun addToQueue(item: AudioQueueItem) {
        queue = queue + item
    }

    fun removeFromQueue(index: Int) {
        if (index < 0 || index >= queue.size) return
        val wasPlaying = index == currentIndex
        queue = queue.toMutableList().apply { removeAt(index) }.toList()
        if (wasPlaying) {
            if (queue.isNotEmpty()) {
                currentIndex = currentIndex.coerceAtMost(queue.lastIndex)
            } else {
                currentIndex = -1
            }
        } else if (index < currentIndex) {
            currentIndex--
        }
    }

    fun skipToNext() {
        if (queue.isEmpty()) return
        val next = when {
            shuffleMode -> queue.indices.filter { it != currentIndex }.randomOrNull() ?: return
            currentIndex < queue.lastIndex -> currentIndex + 1
            repeatMode >= 1 -> 0
            else -> return
        }
        currentIndex = next
        play(queue[next].id)
    }

    fun skipToPrevious() {
        if (queue.isEmpty()) return
        val player = exoPlayer ?: return
        if (player.currentPosition > 3000) {
            player.seekTo(0)
            return
        }
        val prev = when {
            shuffleMode -> queue.indices.filter { it != currentIndex }.randomOrNull() ?: return
            currentIndex > 0 -> currentIndex - 1
            repeatMode >= 1 -> queue.lastIndex
            else -> return
        }
        currentIndex = prev
        play(queue[prev].id)
    }

    fun seekTo(positionMs: Long) {
        exoPlayer?.seekTo(positionMs)
    }

    fun togglePlayPause() {
        val player = exoPlayer ?: return
        if (player.isPlaying) player.pause() else player.play()
    }

    fun changePlaybackSpeed(value: Float) {
        speed = value
        exoPlayer?.setPlaybackSpeed(value)
    }

    fun toggleShuffle() {
        shuffleMode = !shuffleMode
    }

    fun cycleRepeatMode() {
        repeatMode = (repeatMode + 1) % 3
    }

    fun playFromQueue(index: Int) {
        if (index < 0 || index >= queue.size) return
        currentIndex = index
        play(queue[index].id)
    }

    fun getImageUrl(itemId: String): String =
        playbackRepository.getImageUrl(itemId, maxWidth = 400)

    private fun onTrackEnded() {
        when {
            repeatMode == 2 -> {
                exoPlayer?.seekTo(0)
                exoPlayer?.play()
            }
            currentIndex < queue.lastIndex || repeatMode >= 1 -> skipToNext()
            else -> {
                isPlaying = false
            }
        }
    }

    private fun createPlayer(): ExoPlayer {
        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val player = ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player.addListener(playerListener)

        exoPlayer = player
        mediaSession = MediaSession.Builder(context, player).build()
        return player
    }

    private fun startPositionTracking() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (true) {
                exoPlayer?.let { player ->
                    currentPosition = player.currentPosition
                    duration = player.duration.coerceAtLeast(0L)
                }
                delay(250)
            }
        }
    }

    private fun startProgressReporting() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                delay(10_000)
                val player = exoPlayer ?: continue
                val itemId = currentItemId ?: continue
                playbackRepository.reportPlaybackProgress(
                    PlaybackProgress(
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
            val player = exoPlayer ?: return@launch
            val itemId = currentItemId ?: return@launch
            playbackRepository.reportPlaybackStopped(
                itemId = itemId,
                sessionId = playSessionId,
                positionTicks = player.currentPosition * 10_000,
            )
        }
        progressJob?.cancel()
        positionJob?.cancel()
        exoPlayer?.removeListener(playerListener)
        mediaSession?.release()
        mediaSession = null
        exoPlayer?.release()
        exoPlayer = null
    }

    override fun onCleared() {
        super.onCleared()
        progressJob?.cancel()
        positionJob?.cancel()
        exoPlayer?.removeListener(playerListener)
        mediaSession?.release()
        exoPlayer?.release()
        exoPlayer = null
    }
}
