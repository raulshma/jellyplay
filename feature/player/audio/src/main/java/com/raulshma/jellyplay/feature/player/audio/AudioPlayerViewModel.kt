package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val audioPlaybackManager: AudioPlaybackManager,
    private val preferencesStore: UserPreferencesStore,
    private val mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository,
    private val downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository,
    private val playbackRepository: com.raulshma.jellyplay.core.data.repository.PlaybackRepository,
    private val sleepTimerManager: SleepTimerManager,
) : JellyPlayViewModel() {

    val preferences = preferencesStore.preferences
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), UserPreferences())

    var title by composeState("")
        private set
    var artist by composeState("")
        private set
    var album by composeState("")
        private set
    var albumArtUrl by composeState("")
        private set
    var albumArtBlurHash by composeState<String?>(null)
        private set
    var isPlaying by composeState(false)
        private set
    var currentPosition by composeLongState(0L)
        private set
    var duration by composeLongState(0L)
        private set
    var speed by composeFloatState(1.0f)
        private set
    var shuffleMode by composeState(false)
        private set
    var repeatMode by composeIntState(0)
        private set

    var nightModeVolume by composeFloatState(0.4f)
        private set
    var nightModeGain by composeState(1200)
        private set
    var skipPreviousThresholdMs by composeLongState(3_000L)
        private set

    var crossfadeDurationMs by composeLongState(0L)
        private set

    var queue by composeState<List<AudioQueueItem>>(emptyList())
        private set
    var currentIndex by composeIntState(-1)
        private set

    var nightModeEnabled by composeState(false)
        private set

    var dialogueBoostEnabled by composeState(false)
        private set

    private var _dialogueBoostStrength = composeState(EffectStrength.MODERATE)
    val dialogueBoostStrength: EffectStrength by _dialogueBoostStrength

    private var _nightModeStrength = composeState(EffectStrength.MODERATE)
    val nightModeStrength: EffectStrength by _nightModeStrength

    var equalizerEnabled by composeState(false)
        private set

    var equalizerSettings by composeState(com.raulshma.jellyplay.core.model.EqualizerSettings())
        private set

    var equalizerPreset by composeState(EqualizerPreset.FLAT)
        private set

    var bassBoostEnabled by composeState(false)
        private set

    private var _bassBoostStrength = composeState(EffectStrength.MODERATE)
    val bassBoostStrength: EffectStrength by _bassBoostStrength

    var virtualizerEnabled by composeState(false)
        private set

    var virtualizerStrength by composeState(500)
        private set

    var reverbPreset by composeState(ReverbPreset.NONE)
        private set

    var lrBalance by composeFloatState(0f)
        private set

    var pitchSemitones by composeFloatState(0f)
        private set

    var autoEqByGenre by composeState(false)
        private set

    private val _fftData = stateFlow(ByteArray(0))
    val fftData: StateFlow<ByteArray> = _fftData.flow

    var normalizationMode by composeState(AudioNormalizationMode.NONE)
        private set

    var preAmpDb by composeFloatState(0f)
        private set

    var isFavorite by composeState(false)
        private set

    var playbackError by composeState<String?>(null)
        private set

    var isLoading by composeState(false)
        private set

    var lyrics by composeState<List<com.raulshma.jellyplay.core.model.LyricsLine>>(emptyList())
        private set

    var currentLyricIndex by composeIntState(-1)
        private set

    var lyricsSource by composeState(LyricsSource.UNKNOWN)
        private set

    var isFetchingLyrics by composeState(false)
        private set

    var lyricsSearchResults by composeState<List<LrcLibTrack>>(emptyList())
        private set

    var isSearchingLyrics by composeState(false)
        private set

    var karaokeMode by composeState(false)
        private set

    fun setKaraokeModeEnabled(enabled: Boolean) {
        karaokeMode = enabled
    }

    fun toggleKaraokeMode() {
        karaokeMode = !karaokeMode
    }

    val hasKaraokeLyrics: Boolean
        get() = lyrics.any { it.words.isNotEmpty() }

    var sleepTimerActive by composeState(false)
        private set
    var sleepTimerEndOfEpisode by composeState(false)
        private set
    var sleepTimerRemainingMs by composeLongState(0L)
        private set
    var sleepTimerLastUsedDurationMs by composeLongState(0L)
        private set

    private val _currentDownloadItem = stateFlow<com.raulshma.jellyplay.core.model.DownloadItem?>(null)
    val currentDownloadItem: StateFlow<com.raulshma.jellyplay.core.model.DownloadItem?> = _currentDownloadItem.flow

    private var downloadJob: Job? = null

    init {
        launch {
            audioPlaybackManager.currentPlayingItemId.collect { itemId ->
                downloadJob?.cancel()
                if (itemId != null) {
                    downloadJob = launch {
                        downloadRepository.getDownloadByMediaItemIdFlow(itemId).collect { download ->
                            _currentDownloadItem.set(download)
                        }
                    }
                } else {
                    _currentDownloadItem.set(null)
                }
            }
        }

        launch {
            audioPlaybackManager.title.collect { title = it }
        }
        launch {
            audioPlaybackManager.playbackError.collect { playbackError = it }
        }
        launch {
            audioPlaybackManager.isLoadingItem.collect { isLoading = it }
        }
        launch {
            audioPlaybackManager.artist.collect { artist = it }
        }
        launch {
            audioPlaybackManager.album.collect { album = it }
        }
        launch {
            audioPlaybackManager.albumArtUrl.collect { albumArtUrl = it }
        }
        launch {
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
        launch {
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
        launch {
            audioPlaybackManager.currentPlayingItemId.collect { itemId ->
                if (itemId != null) {
                    mediaRepository.getMediaDetail(itemId)
                        .onSuccess { isFavorite = it.item.isFavorite }
                } else {
                    isFavorite = false
                }
            }
        }
        launch {
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
        launch {
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
        launch {
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
        launch {
            combine(
                audioPlaybackManager.lrBalance,
                audioPlaybackManager.pitchSemitones,
                audioPlaybackManager.autoEqByGenre,
                audioPlaybackManager.fftData,
            ) { lr, pitch, autoEq, fft ->
                lrBalance = lr
                pitchSemitones = pitch
                autoEqByGenre = autoEq
                _fftData.set(fft)
            }.collect {}
        }
        launch {
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
        launch {
            sleepTimerManager.remainingMs.collect { remaining ->
                sleepTimerRemainingMs = remaining
            }
        }
        launch {
            combine(
                sleepTimerManager.isActive,
                sleepTimerManager.isEndOfEpisodeMode,
            ) { active, endOfEpisode ->
                sleepTimerActive = active
                sleepTimerEndOfEpisode = endOfEpisode
            }.collect {}
        }
        launch {
            preferencesStore.preferences.collect { prefs ->
                sleepTimerLastUsedDurationMs = prefs.sleepTimerDurationMs
            }
        }
    }

    fun play(itemId: String) {
        audioPlaybackManager.play(itemId)

        launch {
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
        launch {
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    val hash = detail.item.blurHashes.primary
                    if (hash != null) blurHashCache.put(itemId, hash)
                    else blurHashCache.put(keySentinel(itemId), "")
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
        launch {
            preferencesStore.setDialogueBoostEnabled(dialogueBoostEnabled)
        }
    }

    fun setDialogueBoostStrength(strength: EffectStrength) {
        _dialogueBoostStrength.value = strength
        audioPlaybackManager.setDialogueBoostStrength(strength)
        launch {
            preferencesStore.setDialogueBoostStrength(strength)
        }
    }

    fun toggleNightMode() {
        audioPlaybackManager.toggleNightMode()
        launch {
            preferencesStore.setNightModeEnabled(nightModeEnabled)
        }
    }

    fun setNightModeStrength(strength: EffectStrength) {
        _nightModeStrength.value = strength
        audioPlaybackManager.setNightModeStrength(strength)
        launch {
            preferencesStore.setNightModeStrength(strength)
        }
    }

    fun setReplayGainMode(mode: AudioNormalizationMode) {
        audioPlaybackManager.setReplayGainMode(mode)
        launch {
            preferencesStore.setAudioNormalizationMode(mode)
        }
    }

    fun setReplayGainPreAmpDb(db: Float) {
        audioPlaybackManager.setReplayGainPreAmpDb(db)
        launch {
            preferencesStore.setReplayGainPreAmpDb(db)
        }
    }

    fun toggleEqualizer() {
        audioPlaybackManager.toggleEqualizer()
        launch {
            preferencesStore.setEqualizerEnabled(equalizerEnabled)
        }
    }

    fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        audioPlaybackManager.setEqualizerBand(bandIndex, levelDb)
        launch {
            preferencesStore.setEqualizerSettings(equalizerSettings)
        }
    }

    fun resetEqualizer() {
        audioPlaybackManager.resetEqualizer()
        launch {
            preferencesStore.setEqualizerSettings(equalizerSettings)
            preferencesStore.setEqualizerPreset(equalizerPreset)
        }
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        audioPlaybackManager.setEqualizerPreset(preset)
        launch {
            preferencesStore.setEqualizerPreset(preset)
            preferencesStore.setEqualizerSettings(equalizerSettings)
        }
    }

    fun toggleBassBoost() {
        audioPlaybackManager.toggleBassBoost()
        launch {
            preferencesStore.setBassBoostEnabled(bassBoostEnabled)
        }
    }

    fun setBassBoostStrength(strength: EffectStrength) {
        _bassBoostStrength.value = strength
        audioPlaybackManager.setBassBoostStrength(strength)
        launch {
            preferencesStore.setBassBoostStrength(strength)
        }
    }

    fun toggleVirtualizer() {
        audioPlaybackManager.toggleVirtualizer()
        launch {
            preferencesStore.setVirtualizerEnabled(virtualizerEnabled)
        }
    }

    fun applyVirtualizerStrength(strength: Int) {
        audioPlaybackManager.setVirtualizerStrength(strength)
        launch {
            preferencesStore.setVirtualizerStrength(strength)
        }
    }

    fun applyReverbPreset(preset: ReverbPreset) {
        audioPlaybackManager.setReverbPreset(preset)
        launch {
            preferencesStore.setReverbPreset(preset)
        }
    }

    fun applyLrBalance(balance: Float) {
        audioPlaybackManager.setLrBalance(balance)
        launch {
            preferencesStore.setLrBalance(balance)
        }
    }

    fun applyPitchSemitones(semitones: Float) {
        audioPlaybackManager.setPitchSemitones(semitones)
        launch {
            preferencesStore.setPitchSemitones(semitones)
        }
    }

    fun applyAutoEqByGenre(enabled: Boolean) {
        audioPlaybackManager.setAutoEqByGenre(enabled)
        launch {
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
        launch {
            preferencesStore.setCrossfadeDurationMs(ms)
        }
    }

    fun updateGaplessPlayback(enabled: Boolean) {
        audioPlaybackManager.setGaplessEnabled(enabled)
        launch {
            preferencesStore.setGaplessEnabled(enabled)
        }
    }

    fun startSleepTimer(durationMs: Long) {
        launch {
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
        launch {
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
        launch {
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
            launch {
                downloadRepository.deleteDownload(existing.id)
            }
            return
        }
        launch {
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
