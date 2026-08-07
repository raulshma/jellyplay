package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.cast.CastMediaOptions
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.AudioPlayerUiPreferences
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.EqualizerPreset
import com.raulshma.jellyplay.core.model.LrcLibTrack
import com.raulshma.jellyplay.core.model.LyricsSource
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class AudioPlayerViewModel @Inject constructor(
    private val audioPlaybackManager: AudioPlaybackManager,
    private val projections: PreferenceProjections,
    private val audioStore: AudioStore,
    private val audioEffectsStore: com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore,
    private val mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository,
    private val downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository,
    private val downloadIntake: com.raulshma.jellyplay.core.data.download.DownloadIntake,
    private val sleepTimerManager: SleepTimerManager,
    private val castManager: CastManager,
) : JellyPlayViewModel() {

    /** Exposed so the audio top bar can render a shared [com.raulshma.jellyplay.feature.player.audio.components.CastButton]. */
    val castManagerField: CastManager = castManager

    init {
        // CastManager is a ref-counted app-wide singleton shared with the video
        // player and the Home "Play On" entry. Acquire for this VM's lifetime.
        castManager.acquireConsumer()
    }

    override fun onCleared() {
        super.onCleared()
        castManager.releaseConsumer()
    }

    /** Lyrics toggle + artwork theme, projected centrally off the store slices. */
    val preferences: StateFlow<AudioPlayerUiPreferences> = projections.audioPlayerUiPreferences

    private val _uiState = MutableStateFlow(AudioPlayerUiState())
    val uiState: StateFlow<AudioPlayerUiState> = _uiState.asStateFlow()

    /**
     * Sleep-timer countdown, sourced directly from SleepTimerManager. Kept OUT
     * of [uiState] (mirroring [currentPosition]) so a 5 s tick — or the 100 ms
     * fade-out burst — does not copy the whole [AudioPlayerUiState] and
     * re-invalidate the screen root. Collected only by the leaf composables
     * that render the countdown (top-bar label, AudioSleepTimerSheet).
     */
    val sleepTimerRemainingMs: StateFlow<Long> = sleepTimerManager.remainingMs

    /**
     * High-frequency playback position, kept OUTSIDE [uiState] so the 250ms tick only
     * recomposes consumers that read position, rather than copying the whole UiState.
     */
    private val currentPositionHolder = composeLongState(0L)
    var currentPosition by currentPositionHolder
        private set

    /**
     * Snapshot-state handle to playback position, meant to be read only inside
     * the leaf composables that render it (seek bar, time labels, karaoke word
     * highlight). Passing this instead of the plain [Long] value keeps the
     * recomposition triggered by the 4 Hz position tick scoped to those leaves
     * rather than invalidating the whole screen body.
     */
    val currentPositionState: LongState get() = currentPositionHolder.asState()

    /** Mirrors [AudioEffectsState.dialogueBoostStrength] for callers that read it directly. */
    val dialogueBoostStrength: EffectStrength
        get() = _uiState.value.effects.dialogueBoostStrength

    /** Mirrors [AudioEffectsState.nightModeStrength] for callers that read it directly. */
    val nightModeStrength: EffectStrength
        get() = _uiState.value.effects.nightModeStrength

    /** Mirrors [AudioEffectsState.bassBoostStrength] for callers that read it directly. */
    val bassBoostStrength: EffectStrength
        get() = _uiState.value.effects.bassBoostStrength

    val hasKaraokeLyrics: Boolean
        get() = _uiState.value.lyrics.hasKaraokeLyrics

    var nightModeVolume by composeFloatState(0.4f)
        private set
    var nightModeGain by composeState(1200)
        private set
    var skipPreviousThresholdMs by composeLongState(3_000L)
        private set

    /** Karaoke toggle — the only lyrics field the UI mutates directly (not engine-driven). */
    var karaokeMode by composeState(false)
        private set

    private var downloadJob: Job? = null

    private val _currentDownloadItem = stateFlow<com.raulshma.jellyplay.core.model.DownloadItem?>(null)
    val currentDownloadItem: StateFlow<com.raulshma.jellyplay.core.model.DownloadItem?> = _currentDownloadItem.flow

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
            audioPlaybackManager.title.collect { value ->
                _uiState.update { it.copy(title = value) }
            }
        }
        launch {
            audioPlaybackManager.playbackError.collect { value ->
                _uiState.update { it.copy(playbackError = value) }
            }
        }
        launch {
            audioPlaybackManager.isLoadingItem.collect { value ->
                _uiState.update { it.copy(isLoading = value) }
            }
        }
        // Group the track-metadata fields that change together on every track
        // transition into a single combine so a transition produces one
        // 95-field uiState copy (rather than 5 separate copies + update
        // attempts). StateFlow conflation means downstream sees the final
        // state either way; this just removes the per-field allocation churn.
        launch {
            combine(
                audioPlaybackManager.artist,
                audioPlaybackManager.artistId,
                audioPlaybackManager.album,
                audioPlaybackManager.albumArtUrl,
            ) { artist, artistId, album, albumArtUrl ->
                _uiState.update {
                    it.copy(
                        artist = artist,
                        artistId = artistId,
                        album = album,
                        albumArtUrl = albumArtUrl,
                    )
                }
            }.collect {}
        }
        launch {
            combine(
                audioPlaybackManager.isPlaying,
                audioPlaybackManager.duration,
                audioPlaybackManager.speed,
            ) { playing, dur, spd ->
                _uiState.update { it.copy(isPlaying = playing, duration = dur, speed = spd) }
            }.collect {}
        }
        // Position is high-frequency; keep it in its own state holder (not in uiState).
        launch {
            audioPlaybackManager.currentPosition.collect { currentPosition = it }
        }
        launch {
            combine(
                audioPlaybackManager.shuffleMode,
                audioPlaybackManager.repeatMode,
                audioPlaybackManager.queue,
                audioPlaybackManager.currentIndex,
            ) { shuf, rep, q, idx ->
                _uiState.update {
                    it.copy(queue = QueueState(queue = q, currentIndex = idx, shuffleMode = shuf, repeatMode = rep))
                }
            }.collect {}
        }
        launch {
            audioPlaybackManager.currentPlayingItemId.collect { itemId ->
                if (itemId != null) {
                    mediaRepository.getMediaDetail(itemId)
                        .onSuccess { d -> _uiState.update { it.copy(isFavorite = d.item.isFavorite) } }
                } else {
                    _uiState.update { it.copy(isFavorite = false) }
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
                _uiState.update {
                    it.copy(
                        lyrics = it.lyrics.copy(
                            lyrics = ly,
                            currentLyricIndex = idx,
                            lyricsSource = src,
                            isFetchingLyrics = fetching,
                        ),
                    )
                }
            }.collect {}
        }
        launch {
            audioPlaybackManager.lyricsOffsetMs.collect { value ->
                _uiState.update { it.copy(lyrics = it.lyrics.copy(lyricsOffsetMs = value)) }
            }
        }
        launch {
            combine(
                audioPlaybackManager.nightModeEnabled,
                audioPlaybackManager.dialogueBoostEnabled,
                audioPlaybackManager.equalizerEnabled,
                audioPlaybackManager.equalizerSettings,
                audioPlaybackManager.equalizerPreset,
            ) { night, dialogue, eqEn, eqSet, eqPre ->
                _uiState.update {
                    it.copy(
                        effects = it.effects.copy(
                            nightModeEnabled = night,
                            dialogueBoostEnabled = dialogue,
                            equalizerEnabled = eqEn,
                            equalizerSettings = eqSet,
                            equalizerPreset = eqPre,
                        ),
                    )
                }
            }.collect {}
        }
        launch {
            combine(
                audioPlaybackManager.bassBoostEnabled,
                audioPlaybackManager.virtualizerEnabled,
                audioPlaybackManager.virtualizerStrength,
                audioPlaybackManager.reverbPresetState,
            ) { bass, virtEn, virtStr, rev ->
                _uiState.update {
                    it.copy(
                        effects = it.effects.copy(
                            bassBoostEnabled = bass,
                            virtualizerEnabled = virtEn,
                            virtualizerStrength = virtStr,
                            reverbPreset = rev,
                        ),
                    )
                }
            }.collect {}
        }
        launch {
            combine(
                audioPlaybackManager.lrBalance,
                audioPlaybackManager.pitchSemitones,
                audioPlaybackManager.autoEqByGenre,
            ) { lr, pitch, autoEq ->
                _uiState.update {
                    it.copy(effects = it.effects.copy(lrBalance = lr, pitchSemitones = pitch, autoEqByGenre = autoEq))
                }
            }.collect {}
        }
        launch {
            combine(
                audioPlaybackManager.crossfadeDurationMs,
                audioPlaybackManager.replayGainMode,
                audioPlaybackManager.replayGainPreAmpDb,
            ) { cross, rg, pre ->
                _uiState.update {
                    it.copy(
                        crossfadeDurationMs = cross,
                        effects = it.effects.copy(normalizationMode = rg, preAmpDb = pre),
                    )
                }
            }.collect {}
        }
        launch {
            combine(
                sleepTimerManager.isActive,
                sleepTimerManager.isEndOfEpisodeMode,
            ) { active, endOfEpisode ->
                _uiState.update { it.copy(sleepTimer = it.sleepTimer.copy(active = active, endOfEpisode = endOfEpisode)) }
            }.collect {}
        }
        launch {
            audioStore.audio.map { it.sleepTimerDurationMs }.collect { durationMs ->
                _uiState.update { it.copy(sleepTimer = it.sleepTimer.copy(lastUsedDurationMs = durationMs)) }
            }
        }
    }

    fun play(itemId: String) {
        audioPlaybackManager.play(itemId)

        launch {
            val (audio, effects) = combine(audioStore.audio, audioEffectsStore.audioEffects) { a, e -> a to e }.first()
            if (audio.audioDefaultSpeed != 1.0f) {
                audioPlaybackManager.changePlaybackSpeed(audio.audioDefaultSpeed)
            }
            nightModeVolume = audio.audioNightModeVolume
            nightModeGain = audio.audioNightModeGain
            skipPreviousThresholdMs = audio.audioSkipPreviousThresholdMs
            audioPlaybackManager.setNightModeParams(audio.audioNightModeVolume, audio.audioNightModeGain)
            audioPlaybackManager.setSkipPreviousThreshold(audio.audioSkipPreviousThresholdMs)
            audioPlaybackManager.setDialogueBoostStrength(effects.dialogueBoostStrength)
            audioPlaybackManager.setNightModeStrength(effects.nightModeStrength)
            audioPlaybackManager.setCrossfadeDurationMs(audio.audioCrossfadeDurationMs)
            audioPlaybackManager.setGaplessEnabled(audio.audioGaplessEnabled)
            audioPlaybackManager.setReplayGainMode(audio.audioNormalizationMode)
            audioPlaybackManager.setReplayGainPreAmpDb(audio.replayGainPreAmpDb)
            audioPlaybackManager.setBassBoostStrength(effects.bassBoostStrength)
            audioPlaybackManager.setVirtualizerStrength(effects.virtualizerStrength)
            audioPlaybackManager.setLrBalance(effects.lrBalance)
            audioPlaybackManager.setPitchSemitones(effects.pitchSemitones)
            audioPlaybackManager.setAutoEqByGenre(effects.autoEqByGenre)
            // Strength fields are not flow-exposed by the manager; seed them into uiState from prefs.
            _uiState.update {
                it.copy(
                    effects = it.effects.copy(
                        dialogueBoostStrength = effects.dialogueBoostStrength,
                        nightModeStrength = effects.nightModeStrength,
                        bassBoostStrength = effects.bassBoostStrength,
                    ),
                )
            }
        }

        fetchBlurHash(itemId)
    }

    private val blurHashCache = android.util.LruCache<String, String?>(50)

    private fun fetchBlurHash(itemId: String) {
        if (blurHashCache.get(itemId) != null || blurHashCache[keySentinel(itemId)] != null) {
            _uiState.update { it.copy(albumArtBlurHash = blurHashCache.get(itemId)) }
            return
        }
        launch {
            mediaRepository.getMediaDetail(itemId)
                .onSuccess { detail ->
                    val hash = detail.item.blurHashes.primary
                    if (hash != null) blurHashCache.put(itemId, hash)
                    else blurHashCache.put(keySentinel(itemId), "")
                    _uiState.update { it.copy(albumArtBlurHash = hash) }
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

    /** One-shot stream of destructive queue ops the UI can offer to undo. */
    val undoEvents: kotlinx.coroutines.flow.SharedFlow<com.raulshma.jellyplay.core.data.playback.QueueUndoEvent>
        get() = audioPlaybackManager.undoEvents

    /** Restores the queue to before the most recent destructive op, if any. */
    fun undoLastQueueOperation(): Boolean =
        audioPlaybackManager.undoLastQueueOperation()

    /** A→B loop markers (null = unset). */
    val abLoopStartMs: StateFlow<Long?> get() = audioPlaybackManager.abLoopStartMs
    val abLoopEndMs: StateFlow<Long?> get() = audioPlaybackManager.abLoopEndMs

    /** Cycles A→B loop: set A → set B → clear. */
    fun cycleAbLoop() = audioPlaybackManager.cycleAbLoop()

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

    // ------------------------------------------------------------------
    // Cast / "Play On" — fling the current track to another Jellyfin session.
    // Mirrors VideoPlayerViewModel.castToDevice(); for audio there are no
    // subtitle/quality variants to carry, so the cast options are empty.
    // ------------------------------------------------------------------

    fun castToDevice() {
        val itemId = audioPlaybackManager.currentPlayingItemId.value ?: return
        val positionMs = currentPosition
        val mediaItem = MediaItem.Builder()
            .setMediaId(itemId)
            .build()
        castManager.loadMedia(
            mediaItem = mediaItem,
            startPositionMs = positionMs,
            listener = object : Player.Listener {},
            options = CastMediaOptions(),
        )
        audioPlaybackManager.pause()
    }

    fun castPlay() = castManager.play()
    fun castPause() = castManager.pause()
    fun castSeekTo(positionMs: Long) = castManager.seekTo(positionMs)
    fun setCastVolume(volume: Float) = castManager.setVolume(volume)
    fun onCastDisconnected() {
        // No local teardown needed — the singleton owns the session lifecycle.
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
            audioEffectsStore.setDialogueBoostEnabled(_uiState.value.effects.dialogueBoostEnabled)
        }
    }

    fun setDialogueBoostStrength(strength: EffectStrength) {
        audioPlaybackManager.setDialogueBoostStrength(strength)
        _uiState.update {
            it.copy(effects = it.effects.copy(dialogueBoostStrength = strength))
        }
        launch {
            audioEffectsStore.setDialogueBoostStrength(strength)
        }
    }

    fun toggleNightMode() {
        audioPlaybackManager.toggleNightMode()
        launch {
            audioEffectsStore.setNightModeEnabled(_uiState.value.effects.nightModeEnabled)
        }
    }

    fun setNightModeStrength(strength: EffectStrength) {
        audioPlaybackManager.setNightModeStrength(strength)
        _uiState.update {
            it.copy(effects = it.effects.copy(nightModeStrength = strength))
        }
        launch {
            audioEffectsStore.setNightModeStrength(strength)
        }
    }

    fun setReplayGainMode(mode: AudioNormalizationMode) {
        audioPlaybackManager.setReplayGainMode(mode)
        launch {
            audioStore.setAudioNormalizationMode(mode)
        }
    }

    fun setReplayGainPreAmpDb(db: Float) {
        audioPlaybackManager.setReplayGainPreAmpDb(db)
        launch {
            audioStore.setReplayGainPreAmpDb(db)
        }
    }

    fun toggleEqualizer() {
        audioPlaybackManager.toggleEqualizer()
        launch {
            audioEffectsStore.setEqualizerEnabled(_uiState.value.effects.equalizerEnabled)
        }
    }

    fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        audioPlaybackManager.setEqualizerBand(bandIndex, levelDb)
        launch {
            audioEffectsStore.setEqualizerSettings(_uiState.value.effects.equalizerSettings)
        }
    }

    fun resetEqualizer() {
        audioPlaybackManager.resetEqualizer()
        launch {
            audioEffectsStore.setEqualizerSettings(_uiState.value.effects.equalizerSettings)
            audioEffectsStore.setEqualizerPreset(_uiState.value.effects.equalizerPreset)
        }
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        audioPlaybackManager.setEqualizerPreset(preset)
        launch {
            audioEffectsStore.setEqualizerPreset(preset)
            audioEffectsStore.setEqualizerSettings(_uiState.value.effects.equalizerSettings)
        }
    }

    fun toggleBassBoost() {
        audioPlaybackManager.toggleBassBoost()
        launch {
            audioEffectsStore.setBassBoostEnabled(_uiState.value.effects.bassBoostEnabled)
        }
    }

    fun setBassBoostStrength(strength: EffectStrength) {
        audioPlaybackManager.setBassBoostStrength(strength)
        _uiState.update {
            it.copy(effects = it.effects.copy(bassBoostStrength = strength))
        }
        launch {
            audioEffectsStore.setBassBoostStrength(strength)
        }
    }

    fun toggleVirtualizer() {
        audioPlaybackManager.toggleVirtualizer()
        launch {
            audioEffectsStore.setVirtualizerEnabled(_uiState.value.effects.virtualizerEnabled)
        }
    }

    fun applyVirtualizerStrength(strength: Int) {
        audioPlaybackManager.setVirtualizerStrength(strength)
        launch {
            audioEffectsStore.setVirtualizerStrength(strength)
        }
    }

    fun applyReverbPreset(preset: ReverbPreset) {
        audioPlaybackManager.setReverbPreset(preset)
        launch {
            audioEffectsStore.setReverbPreset(preset)
        }
    }

    fun applyLrBalance(balance: Float) {
        audioPlaybackManager.setLrBalance(balance)
        launch {
            audioEffectsStore.setLrBalance(balance)
        }
    }

    fun applyPitchSemitones(semitones: Float) {
        audioPlaybackManager.setPitchSemitones(semitones)
        launch {
            audioEffectsStore.setPitchSemitones(semitones)
        }
    }

    fun applyAutoEqByGenre(enabled: Boolean) {
        audioPlaybackManager.setAutoEqByGenre(enabled)
        launch {
            audioEffectsStore.setAutoEqByGenre(enabled)
        }
    }

    fun getImageUrl(itemId: String): String =
        audioPlaybackManager.getImageUrl(itemId)

    fun searchLyrics(query: String) {
        _uiState.update { it.copy(lyrics = it.lyrics.copy(isSearching = true)) }
        audioPlaybackManager.searchLyrics(query) { result ->
            _uiState.update {
                it.copy(
                    lyrics = it.lyrics.copy(
                        searchResults = result.getOrElse { emptyList() },
                        isSearching = false,
                    ),
                )
            }
        }
    }

    fun applyLyrics(track: LrcLibTrack) {
        audioPlaybackManager.applyLyrics(track.id)
        _uiState.update { it.copy(lyrics = it.lyrics.copy(searchResults = emptyList())) }
    }

    fun clearLyricsSearch() {
        _uiState.update { it.copy(lyrics = it.lyrics.copy(searchResults = emptyList())) }
    }

    fun setLyricsOffset(offsetMs: Long) {
        audioPlaybackManager.setLyricsOffset(offsetMs)
    }

    fun updateCrossfadeDuration(ms: Long) {
        audioPlaybackManager.setCrossfadeDurationMs(ms)
        launch {
            audioStore.setAudioCrossfadeDurationMs(ms)
        }
    }

    fun updateGaplessPlayback(enabled: Boolean) {
        audioPlaybackManager.setGaplessEnabled(enabled)
        launch {
            audioStore.setAudioGaplessEnabled(enabled)
        }
    }

    fun startSleepTimer(durationMs: Long) {
        launch {
            audioStore.setSleepTimerDurationMs(durationMs)
            audioStore.setSleepTimerEndOfEpisode(false)
        }
        sleepTimerManager.setOnTimerExpired {
            // Explicit pause rather than togglePlayPause(): if the user paused
            // manually after arming the timer, the toggle would otherwise RESUME
            // playback — the opposite of the timer's intent.
            audioPlaybackManager.pause()
        }
        sleepTimerManager.start(durationMs)
        _uiState.update {
            it.copy(
                sleepTimer = it.sleepTimer.copy(
                    active = true,
                    endOfEpisode = false,
                    lastUsedDurationMs = durationMs,
                ),
            )
        }
    }

    fun startSleepTimerEndOfEpisode() {
        launch {
            audioStore.setSleepTimerEndOfEpisode(true)
        }
        sleepTimerManager.setOnTimerExpired {
            // Explicit pause rather than togglePlayPause(): see startSleepTimer.
            audioPlaybackManager.pause()
        }
        sleepTimerManager.startEndOfEpisode()
        _uiState.update {
            it.copy(sleepTimer = it.sleepTimer.copy(active = true, endOfEpisode = true))
        }
    }

    fun cancelSleepTimer() {
        sleepTimerManager.cancel()
        _uiState.update {
            it.copy(sleepTimer = it.sleepTimer.copy(active = false, endOfEpisode = false))
        }
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
                .onSuccess { fav -> _uiState.update { it.copy(isFavorite = fav) } }
        }
    }

    val currentPlayingItemId: String?
        get() = audioPlaybackManager.currentPlayingItemId.value

    fun setKaraokeModeEnabled(enabled: Boolean) {
        karaokeMode = enabled
        _uiState.update { it.copy(lyrics = it.lyrics.copy(karaokeMode = enabled)) }
    }

    fun toggleKaraokeMode() {
        setKaraokeModeEnabled(!karaokeMode)
    }

    /** Persists the lyrics overlay visibility so it survives across sessions. */
    fun setLyricsVisible(enabled: Boolean) {
        launch { audioStore.setAudioLyricsVisible(enabled) }
    }

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
                // Intake seam owns the full artifact bundle (local poster/backdrop,
                // offline metadata row); previously this path wrote only the
                // remote image URLs, so offline cards fell back to blurHash.
                downloadIntake.start(detail)
            } catch (_: Exception) {}
        }
    }
}
