package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val audioPlaybackManager: AudioPlaybackManager,
    private val preferencesStore: UserPreferencesStore,
    private val mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository,
) : ViewModel() {

    val preferences = preferencesStore.preferences
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    var title by mutableStateOf("")
        private set
    var artist by mutableStateOf("")
        private set
    var album by mutableStateOf("")
        private set
    var albumArtUrl by mutableStateOf("")
        private set
    var albumArtBlurHash by mutableStateOf<String?>(null)
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

    var nightModeVolume by mutableFloatStateOf(0.4f)
        private set
    var nightModeGain by mutableStateOf(1200)
        private set
    var skipPreviousThresholdMs by mutableLongStateOf(3_000L)
        private set

    var queue by mutableStateOf<List<AudioQueueItem>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set

    var nightModeEnabled by mutableStateOf(false)
        private set

    var dialogueBoostEnabled by mutableStateOf(false)
        private set

    var equalizerEnabled by mutableStateOf(false)
        private set

    var equalizerSettings by mutableStateOf(com.raulshma.jellyplay.core.model.EqualizerSettings())
        private set

    var lyrics by mutableStateOf<List<com.raulshma.jellyplay.core.model.LyricsLine>>(emptyList())
        private set

    var currentLyricIndex by mutableIntStateOf(-1)
        private set

    init {
        viewModelScope.launch {
            audioPlaybackManager.title.collect { title = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.artist.collect { artist = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.album.collect { album = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.albumArtUrl.collect { albumArtUrl = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.isPlaying.collect { isPlaying = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.currentPosition.collect { currentPosition = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.duration.collect { duration = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.speed.collect { speed = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.shuffleMode.collect { shuffleMode = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.repeatMode.collect { repeatMode = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.queue.collect { queue = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.currentIndex.collect { currentIndex = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.lyrics.collect { lyrics = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.currentLyricIndex.collect { currentLyricIndex = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.nightModeEnabled.collect { nightModeEnabled = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.dialogueBoostEnabled.collect { dialogueBoostEnabled = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.equalizerEnabled.collect { equalizerEnabled = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.equalizerSettings.collect { equalizerSettings = it }
        }
    }

    fun play(itemId: String) {
        audioPlaybackManager.play(itemId)

        viewModelScope.launch {
            val prefs = preferencesStore.preferences.first()
            if (prefs.audioDefaultSpeed != 1.0f) {
                audioPlaybackManager.changePlaybackSpeed(prefs.audioDefaultSpeed)
            }
            nightModeVolume = prefs.audioNightModeVolume
            nightModeGain = prefs.audioNightModeGain
            skipPreviousThresholdMs = prefs.audioSkipPreviousThresholdMs
            audioPlaybackManager.setNightModeParams(prefs.audioNightModeVolume, prefs.audioNightModeGain)
            audioPlaybackManager.setSkipPreviousThreshold(prefs.audioSkipPreviousThresholdMs)
        }

        fetchBlurHash(itemId)
    }

    private fun fetchBlurHash(itemId: String) {
        viewModelScope.launch {
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    albumArtBlurHash = detail.item.blurHashes.primary
                }
        }
    }

    fun playQueue(items: List<AudioQueueItem>, startIndex: Int = 0) {
        audioPlaybackManager.playQueue(items, startIndex)
    }

    fun addToQueue(item: AudioQueueItem) {
        audioPlaybackManager.addToQueue(item)
    }

    fun removeFromQueue(index: Int) {
        audioPlaybackManager.removeFromQueue(index)
    }

    fun skipToNext() {
        audioPlaybackManager.skipToNext()
    }

    fun skipToPrevious() {
        audioPlaybackManager.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        audioPlaybackManager.seekTo(positionMs)
    }

    fun togglePlayPause() {
        audioPlaybackManager.togglePlayPause()
    }

    fun changePlaybackSpeed(value: Float) {
        audioPlaybackManager.changePlaybackSpeed(value)
    }

    fun toggleShuffle() {
        audioPlaybackManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        audioPlaybackManager.cycleRepeatMode()
    }

    fun playFromQueue(index: Int) {
        audioPlaybackManager.playFromQueue(index)
    }

    fun toggleNightMode() {
        audioPlaybackManager.toggleNightMode()
        viewModelScope.launch {
            preferencesStore.setNightModeEnabled(nightModeEnabled)
        }
    }

    fun toggleDialogueBoost() {
        audioPlaybackManager.toggleDialogueBoost()
        viewModelScope.launch {
            preferencesStore.setDialogueBoostEnabled(dialogueBoostEnabled)
        }
    }

    fun toggleEqualizer() {
        audioPlaybackManager.toggleEqualizer()
        viewModelScope.launch {
            preferencesStore.setEqualizerEnabled(equalizerEnabled)
        }
    }

    fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        audioPlaybackManager.setEqualizerBand(bandIndex, levelDb)
        viewModelScope.launch {
            preferencesStore.setEqualizerSettings(equalizerSettings)
        }
    }

    fun resetEqualizer() {
        audioPlaybackManager.resetEqualizer()
        viewModelScope.launch {
            preferencesStore.setEqualizerSettings(equalizerSettings)
        }
    }

    fun getImageUrl(itemId: String): String =
        audioPlaybackManager.getImageUrl(itemId)

    fun stopPlayback() {
        audioPlaybackManager.stopAndRelease()
    }

    val currentPlayingItemId: String?
        get() = audioPlaybackManager.currentPlayingItemId.value
}
