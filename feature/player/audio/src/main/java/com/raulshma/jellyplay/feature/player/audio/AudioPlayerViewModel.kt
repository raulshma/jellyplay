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
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val audioPlaybackManager: AudioPlaybackManager,
    private val preferencesStore: UserPreferencesStore,
    private val mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository,
    private val downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository,
    private val playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository,
    private val sleepTimerManager: SleepTimerManager,
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

    var crossfadeDurationMs by mutableLongStateOf(0L)
        private set

    var queue by mutableStateOf<List<AudioQueueItem>>(emptyList())
        private set
    var currentIndex by mutableIntStateOf(-1)
        private set

    var nightModeEnabled by mutableStateOf(false)
        private set

    var dialogueBoostEnabled by mutableStateOf(false)
        private set

    private var _dialogueBoostStrength = mutableStateOf(EffectStrength.MODERATE)
    val dialogueBoostStrength: EffectStrength by _dialogueBoostStrength

    private var _nightModeStrength = mutableStateOf(EffectStrength.MODERATE)
    val nightModeStrength: EffectStrength by _nightModeStrength

    var equalizerEnabled by mutableStateOf(false)
        private set

    var equalizerSettings by mutableStateOf(com.raulshma.jellyplay.core.model.EqualizerSettings())
        private set

    var equalizerPreset by mutableStateOf(EqualizerPreset.FLAT)
        private set

    var bassBoostEnabled by mutableStateOf(false)
        private set

    private var _bassBoostStrength = mutableStateOf(EffectStrength.MODERATE)
    val bassBoostStrength: EffectStrength by _bassBoostStrength

    var virtualizerEnabled by mutableStateOf(false)
        private set

    var virtualizerStrength by mutableStateOf(500)
        private set

    var reverbPreset by mutableStateOf(ReverbPreset.NONE)
        private set

    var lrBalance by mutableFloatStateOf(0f)
        private set

    var pitchSemitones by mutableFloatStateOf(0f)
        private set

    var autoEqByGenre by mutableStateOf(false)
        private set

    private val _fftData = MutableStateFlow(ByteArray(0))
    val fftData: StateFlow<ByteArray> = _fftData.asStateFlow()

    var normalizationMode by mutableStateOf(AudioNormalizationMode.NONE)
        private set

    var preAmpDb by mutableFloatStateOf(0f)
        private set

    var isFavorite by mutableStateOf(false)
        private set

    var playbackError by mutableStateOf<String?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var lyrics by mutableStateOf<List<com.raulshma.jellyplay.core.model.LyricsLine>>(emptyList())
        private set

    var currentLyricIndex by mutableIntStateOf(-1)
        private set

    var lyricsSource by mutableStateOf(LyricsSource.UNKNOWN)
        private set

    var isFetchingLyrics by mutableStateOf(false)
        private set

    var lyricsSearchResults by mutableStateOf<List<LrcLibTrack>>(emptyList())
        private set

    var isSearchingLyrics by mutableStateOf(false)
        private set

    var karaokeMode by mutableStateOf(false)
        private set

    fun setKaraokeModeEnabled(enabled: Boolean) {
        karaokeMode = enabled
    }

    fun toggleKaraokeMode() {
        karaokeMode = !karaokeMode
    }

    val hasKaraokeLyrics: Boolean
        get() = lyrics.any { it.words.isNotEmpty() }

    var sleepTimerActive by mutableStateOf(false)
        private set
    var sleepTimerEndOfEpisode by mutableStateOf(false)
        private set
    var sleepTimerRemainingMs by mutableLongStateOf(0L)
        private set
    var sleepTimerLastUsedDurationMs by mutableLongStateOf(0L)
        private set

    private val _currentDownloadItem = MutableStateFlow<com.raulshma.jellyplay.core.model.DownloadItem?>(null)
    val currentDownloadItem: StateFlow<com.raulshma.jellyplay.core.model.DownloadItem?> = _currentDownloadItem.asStateFlow()

    private var downloadJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            audioPlaybackManager.currentPlayingItemId.collect { itemId ->
                downloadJob?.cancel()
                if (itemId != null) {
                    downloadJob = viewModelScope.launch {
                        downloadRepository.getDownloadByMediaItemIdFlow(itemId).collect { download ->
                            _currentDownloadItem.value = download
                        }
                    }
                } else {
                    _currentDownloadItem.value = null
                }
            }
        }

        viewModelScope.launch {
            audioPlaybackManager.title.collect { title = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.playbackError.collect { playbackError = it }
        }
        viewModelScope.launch {
            audioPlaybackManager.isLoadingItem.collect { isLoading = it }
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
            combine(
                audioPlaybackManager.isPlaying,
                audioPlaybackManager.currentPosition,
                audioPlaybackManager.duration,
                audioPlaybackManager.speed,
            ) { playing, pos, dur, spd ->
                isPlaying = playing
                currentPosition = pos
                duration = dur
                speed = spd
            }.collect {}
        }
        viewModelScope.launch {
            combine(
                audioPlaybackManager.shuffleMode,
                audioPlaybackManager.repeatMode,
                audioPlaybackManager.queue,
                audioPlaybackManager.currentIndex,
            ) { shuf, rep, q, idx ->
                shuffleMode = shuf
                repeatMode = rep
                queue = q
                currentIndex = idx
            }.collect {}
        }
        viewModelScope.launch {
            audioPlaybackManager.currentPlayingItemId.collect { itemId ->
                if (itemId != null) {
                    mediaRepository.getMediaDetail(itemId)
                        .onSuccess { isFavorite = it.item.isFavorite }
                } else {
                    isFavorite = false
                }
            }
        }
        viewModelScope.launch {
            combine(
                audioPlaybackManager.lyrics,
                audioPlaybackManager.currentLyricIndex,
                audioPlaybackManager.lyricsSource,
                audioPlaybackManager.isFetchingLyrics,
            ) { ly, idx, src, fetching ->
                lyrics = ly
                currentLyricIndex = idx
                lyricsSource = src
                isFetchingLyrics = fetching
            }.collect {}
        }
        viewModelScope.launch {
            combine(
                audioPlaybackManager.nightModeEnabled,
                audioPlaybackManager.dialogueBoostEnabled,
                audioPlaybackManager.equalizerEnabled,
                audioPlaybackManager.equalizerSettings,
                audioPlaybackManager.equalizerPreset,
            ) { night, dialogue, eqEn, eqSet, eqPre ->
                nightModeEnabled = night
                dialogueBoostEnabled = dialogue
                equalizerEnabled = eqEn
                equalizerSettings = eqSet
                equalizerPreset = eqPre
            }.collect {}
        }
        viewModelScope.launch {
            combine(
                audioPlaybackManager.bassBoostEnabled,
                audioPlaybackManager.virtualizerEnabled,
                audioPlaybackManager.virtualizerStrength,
                audioPlaybackManager.reverbPresetState,
            ) { bass, virtEn, virtStr, rev ->
                bassBoostEnabled = bass
                virtualizerEnabled = virtEn
                virtualizerStrength = virtStr
                reverbPreset = rev
            }.collect {}
        }
        viewModelScope.launch {
            combine(
                audioPlaybackManager.lrBalance,
                audioPlaybackManager.pitchSemitones,
                audioPlaybackManager.autoEqByGenre,
                audioPlaybackManager.fftData,
            ) { lr, pitch, autoEq, fft ->
                lrBalance = lr
                pitchSemitones = pitch
                autoEqByGenre = autoEq
                _fftData.value = fft
            }.collect {}
        }
        viewModelScope.launch {
            combine(
                audioPlaybackManager.crossfadeDurationMs,
                audioPlaybackManager.replayGainMode,
                audioPlaybackManager.replayGainPreAmpDb,
            ) { cross, rg, pre ->
                crossfadeDurationMs = cross
                normalizationMode = rg
                preAmpDb = pre
            }.collect {}
        }
        viewModelScope.launch {
            sleepTimerManager.remainingMs.collect { remaining ->
                sleepTimerRemainingMs = remaining
            }
        }
        viewModelScope.launch {
            combine(
                sleepTimerManager.isActive,
                sleepTimerManager.isEndOfEpisodeMode,
            ) { active, endOfEpisode ->
                sleepTimerActive = active
                sleepTimerEndOfEpisode = endOfEpisode
            }.collect {}
        }
        viewModelScope.launch {
            preferencesStore.preferences.collect { prefs ->
                sleepTimerLastUsedDurationMs = prefs.sleepTimerDurationMs
            }
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
            _dialogueBoostStrength.value = prefs.dialogueBoostStrength
            _nightModeStrength.value = prefs.nightModeStrength
            audioPlaybackManager.setDialogueBoostStrength(prefs.dialogueBoostStrength)
            audioPlaybackManager.setNightModeStrength(prefs.nightModeStrength)
            audioPlaybackManager.setCrossfadeDurationMs(prefs.audioCrossfadeDurationMs)
            audioPlaybackManager.setGaplessEnabled(prefs.audioGaplessEnabled)
            audioPlaybackManager.setReplayGainMode(prefs.audioNormalizationMode)
            audioPlaybackManager.setReplayGainPreAmpDb(prefs.replayGainPreAmpDb)
            _bassBoostStrength.value = prefs.bassBoostStrength
            audioPlaybackManager.setBassBoostStrength(prefs.bassBoostStrength)
            audioPlaybackManager.setVirtualizerStrength(prefs.virtualizerStrength)
            audioPlaybackManager.setLrBalance(prefs.lrBalance)
            audioPlaybackManager.setPitchSemitones(prefs.pitchSemitones)
            audioPlaybackManager.setAutoEqByGenre(prefs.autoEqByGenre)
        }

        fetchBlurHash(itemId)
    }

    private val blurHashCache = android.util.LruCache<String, String?>(50)

    private fun fetchBlurHash(itemId: String) {
        if (blurHashCache.get(itemId) != null || blurHashCache[keySentinel(itemId)] != null) {
            albumArtBlurHash = blurHashCache.get(itemId)
            return
        }
        viewModelScope.launch {
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    val hash = detail.item.blurHashes.primary
                    blurHashCache.put(itemId, hash)
                    if (hash == null) blurHashCache.put(keySentinel(itemId), "")
                    albumArtBlurHash = hash
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

    fun toggleDialogueBoost() {
        audioPlaybackManager.toggleDialogueBoost()
        viewModelScope.launch {
            preferencesStore.setDialogueBoostEnabled(dialogueBoostEnabled)
        }
    }

    fun setDialogueBoostStrength(strength: EffectStrength) {
        _dialogueBoostStrength.value = strength
        audioPlaybackManager.setDialogueBoostStrength(strength)
        viewModelScope.launch {
            preferencesStore.setDialogueBoostStrength(strength)
        }
    }

    fun toggleNightMode() {
        audioPlaybackManager.toggleNightMode()
        viewModelScope.launch {
            preferencesStore.setNightModeEnabled(nightModeEnabled)
        }
    }

    fun setNightModeStrength(strength: EffectStrength) {
        _nightModeStrength.value = strength
        audioPlaybackManager.setNightModeStrength(strength)
        viewModelScope.launch {
            preferencesStore.setNightModeStrength(strength)
        }
    }

    fun setReplayGainMode(mode: AudioNormalizationMode) {
        audioPlaybackManager.setReplayGainMode(mode)
        viewModelScope.launch {
            preferencesStore.setAudioNormalizationMode(mode)
        }
    }

    fun setReplayGainPreAmpDb(db: Float) {
        audioPlaybackManager.setReplayGainPreAmpDb(db)
        viewModelScope.launch {
            preferencesStore.setReplayGainPreAmpDb(db)
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
            preferencesStore.setEqualizerPreset(equalizerPreset)
        }
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        audioPlaybackManager.setEqualizerPreset(preset)
        viewModelScope.launch {
            preferencesStore.setEqualizerPreset(preset)
            preferencesStore.setEqualizerSettings(equalizerSettings)
        }
    }

    fun toggleBassBoost() {
        audioPlaybackManager.toggleBassBoost()
        viewModelScope.launch {
            preferencesStore.setBassBoostEnabled(bassBoostEnabled)
        }
    }

    fun setBassBoostStrength(strength: EffectStrength) {
        _bassBoostStrength.value = strength
        audioPlaybackManager.setBassBoostStrength(strength)
        viewModelScope.launch {
            preferencesStore.setBassBoostStrength(strength)
        }
    }

    fun toggleVirtualizer() {
        audioPlaybackManager.toggleVirtualizer()
        viewModelScope.launch {
            preferencesStore.setVirtualizerEnabled(virtualizerEnabled)
        }
    }

    fun applyVirtualizerStrength(strength: Int) {
        audioPlaybackManager.setVirtualizerStrength(strength)
        viewModelScope.launch {
            preferencesStore.setVirtualizerStrength(strength)
        }
    }

    fun applyReverbPreset(preset: ReverbPreset) {
        audioPlaybackManager.setReverbPreset(preset)
        viewModelScope.launch {
            preferencesStore.setReverbPreset(preset)
        }
    }

    fun applyLrBalance(balance: Float) {
        audioPlaybackManager.setLrBalance(balance)
        viewModelScope.launch {
            preferencesStore.setLrBalance(balance)
        }
    }

    fun applyPitchSemitones(semitones: Float) {
        audioPlaybackManager.setPitchSemitones(semitones)
        viewModelScope.launch {
            preferencesStore.setPitchSemitones(semitones)
        }
    }

    fun applyAutoEqByGenre(enabled: Boolean) {
        audioPlaybackManager.setAutoEqByGenre(enabled)
        viewModelScope.launch {
            preferencesStore.setAutoEqByGenre(enabled)
        }
    }

    fun getImageUrl(itemId: String): String =
        audioPlaybackManager.getImageUrl(itemId)

    fun searchLyrics(query: String) {
        isSearchingLyrics = true
        audioPlaybackManager.searchLyrics(query) { result ->
            lyricsSearchResults = result.getOrElse { emptyList() }
            isSearchingLyrics = false
        }
    }

    fun applyLyrics(track: LrcLibTrack) {
        audioPlaybackManager.applyLyrics(track.id)
        lyricsSearchResults = emptyList()
    }

    fun clearLyricsSearch() {
        lyricsSearchResults = emptyList()
    }

    fun updateCrossfadeDuration(ms: Long) {
        audioPlaybackManager.setCrossfadeDurationMs(ms)
        viewModelScope.launch {
            preferencesStore.setCrossfadeDurationMs(ms)
        }
    }

    fun updateGaplessPlayback(enabled: Boolean) {
        audioPlaybackManager.setGaplessEnabled(enabled)
        viewModelScope.launch {
            preferencesStore.setGaplessEnabled(enabled)
        }
    }

    fun startSleepTimer(durationMs: Long) {
        viewModelScope.launch {
            preferencesStore.setSleepTimerDurationMs(durationMs)
            preferencesStore.setSleepTimerEndOfEpisode(false)
        }
        sleepTimerManager.setOnTimerExpired {
            audioPlaybackManager.togglePlayPause()
        }
        sleepTimerManager.start(durationMs)
        sleepTimerActive = true
        sleepTimerEndOfEpisode = false
        sleepTimerLastUsedDurationMs = durationMs
    }

    fun startSleepTimerEndOfEpisode() {
        viewModelScope.launch {
            preferencesStore.setSleepTimerEndOfEpisode(true)
        }
        sleepTimerManager.setOnTimerExpired {
            audioPlaybackManager.togglePlayPause()
        }
        sleepTimerManager.startEndOfEpisode()
        sleepTimerActive = true
        sleepTimerEndOfEpisode = true
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancel()
        sleepTimerActive = false
        sleepTimerEndOfEpisode = false
        sleepTimerRemainingMs = 0
    }

    fun triggerSleepTimerEndOfEpisode() {
        sleepTimerManager.triggerEndOfEpisode()
    }

    fun stopPlayback() {
        audioPlaybackManager.stopAndRelease()
    }

    fun toggleFavorite() {
        val itemId = audioPlaybackManager.currentPlayingItemId.value ?: return
        viewModelScope.launch {
            mediaRepository.toggleFavorite(itemId)
                .onSuccess { isFavorite = it }
        }
    }

    val currentPlayingItemId: String?
        get() = audioPlaybackManager.currentPlayingItemId.value

    private fun keySentinel(id: String) = "§null§$id"

    fun downloadCurrentTrack() {
        val itemId = currentPlayingItemId ?: return
        val existing = _currentDownloadItem.value
        if (existing != null && existing.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) {
            viewModelScope.launch {
                downloadRepository.deleteDownload(existing.id)
            }
            return
        }
        viewModelScope.launch {
            try {
                val detail = mediaRepository.getMediaDetail(itemId).getOrNull() ?: return@launch
                val item = detail.item
                val source = detail.mediaSources.firstOrNull() ?: return@launch
                val streamUrl = playbackRepository.getStreamUrl(itemId, source.id)
                if (streamUrl.isBlank()) return@launch
                val imageUrl = playbackRepository.getImageUrl(itemId, maxWidth = 300)
                val mediaType = com.raulshma.jellyplay.core.model.MediaType.AUDIO.name

                downloadRepository.startDownload(
                    mediaItemId = itemId,
                    name = item.name,
                    mediaType = mediaType,
                    mediaSourceId = source.id,
                    downloadUrl = streamUrl,
                    imageUrl = imageUrl,
                    imageBlurHash = item.blurHashes.primary,
                ).onSuccess { downloadItem ->
                    if (downloadItem.status == com.raulshma.jellyplay.core.model.DownloadStatus.PENDING) {
                        downloadRepository.enqueueDownload(downloadItem.id)
                        try {
                            val backdropUrl = playbackRepository.getBackdropUrl(itemId, maxWidth = 1280)
                            downloadRepository.saveOfflineMediaItem(item, imageUrl, backdropUrl)
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}
