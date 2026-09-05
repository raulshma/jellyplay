package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
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
import com.raulshma.jellyplay.core.model.lruMapOf
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
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

/**
 * Koin-owned (wave 7A conveyor move from `:feature:player:audio` — the
 * HiltViewModel/@Inject annotations were stripped; see di/PlayerAudioKoin
 * Module.kt). The former concrete [com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager]
 * ctor dep is split across the two shared playback contracts
 * ([AudioQueueManager], [AudioEffectsManager] — the legacy Hilt single
 * implements both) plus the module-local [AudioPlayerEngine] /
 * [AudioPlayerCast] seams over the Hilt-owned Android impls.
 */
class AudioPlayerViewModel(
    private val queueManager: AudioQueueManager,
    private val effectsManager: AudioEffectsManager,
    private val engine: AudioPlayerEngine,
    private val cast: AudioPlayerCast,
    private val projections: PreferenceProjections,
    private val audioStore: AudioStore,
    private val audioEffectsStore: com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore,
    private val mediaRepository: com.raulshma.jellyplay.core.data.repository.MediaRepository,
    private val playlistRepository: com.raulshma.jellyplay.core.data.repository.PlaylistRepository,
    private val userDataMutator: com.raulshma.jellyplay.core.data.repository.UserDataMutator,
    private val downloadRepository: com.raulshma.jellyplay.core.data.repository.DownloadRepository,
    private val downloadIntake: com.raulshma.jellyplay.core.data.download.DownloadIntake,
    private val sleepTimerManager: SleepTimerManager,
) : JellyPlayViewModel() {

    /** Exposed so the audio top bar can render a shared [com.raulshma.jellyplay.feature.player.audio.components.CastButton]. */
    val castController: AudioPlayerCast = cast

    init {
        // The cast controller is a ref-counted app-wide singleton shared with
        // the video player and the Home "Play On" entry. Acquire for this VM's
        // lifetime.
        cast.acquireConsumer()
    }

    override fun onCleared() {
        super.onCleared()
        cast.releaseConsumer()
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
            queueManager.currentPlayingItemId.collect { itemId ->
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
            engine.title.collect { value ->
                _uiState.update { it.copy(title = value) }
            }
        }
        launch {
            engine.playbackError.collect { value ->
                _uiState.update { it.copy(playbackError = value) }
            }
        }
        launch {
            engine.isLoadingItem.collect { value ->
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
                engine.artist,
                engine.artistId,
                engine.album,
                engine.albumArtUrl,
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
                engine.isPlaying,
                engine.duration,
                engine.speed,
            ) { playing, dur, spd ->
                _uiState.update { it.copy(isPlaying = playing, duration = dur, speed = spd) }
            }.collect {}
        }
        // Position is high-frequency; keep it in its own state holder (not in uiState).
        launch {
            engine.currentPosition.collect { currentPosition = it }
        }
        launch {
            combine(
                queueManager.shuffleMode,
                queueManager.repeatMode,
                queueManager.queue,
                queueManager.currentIndex,
            ) { shuf, rep, q, idx ->
                _uiState.update {
                    it.copy(queue = QueueState(queue = q, currentIndex = idx, shuffleMode = shuf, repeatMode = rep))
                }
            }.collect {}
        }
        launch {
            queueManager.currentPlayingItemId.collect { itemId ->
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
                engine.lyrics,
                engine.currentLyricIndex,
                engine.lyricsSource,
                engine.isFetchingLyrics,
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
            engine.lyricsOffsetMs.collect { value ->
                _uiState.update { it.copy(lyrics = it.lyrics.copy(lyricsOffsetMs = value)) }
            }
        }
        launch {
            combine(
                effectsManager.nightModeEnabled,
                effectsManager.dialogueBoostEnabled,
                effectsManager.equalizerEnabled,
                effectsManager.equalizerSettings,
                effectsManager.equalizerPreset,
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
                effectsManager.bassBoostEnabled,
                effectsManager.virtualizerEnabled,
                effectsManager.virtualizerStrength,
                effectsManager.reverbPresetState,
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
                effectsManager.lrBalance,
                effectsManager.pitchSemitones,
                effectsManager.autoEqByGenre,
            ) { lr, pitch, autoEq ->
                _uiState.update {
                    it.copy(effects = it.effects.copy(lrBalance = lr, pitchSemitones = pitch, autoEqByGenre = autoEq))
                }
            }.collect {}
        }
        launch {
            combine(
                engine.crossfadeDurationMs,
                effectsManager.replayGainMode,
                effectsManager.replayGainPreAmpDb,
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
        engine.play(itemId)

        launch {
            val (audio, effects) = combine(audioStore.audio, audioEffectsStore.audioEffects) { a, e -> a to e }.first()
            if (audio.audioDefaultSpeed != 1.0f) {
                engine.changePlaybackSpeed(audio.audioDefaultSpeed)
            }
            nightModeVolume = audio.audioNightModeVolume
            nightModeGain = audio.audioNightModeGain
            skipPreviousThresholdMs = audio.audioSkipPreviousThresholdMs
            effectsManager.setNightModeParams(audio.audioNightModeVolume, audio.audioNightModeGain)
            engine.setSkipPreviousThreshold(audio.audioSkipPreviousThresholdMs)
            effectsManager.setDialogueBoostStrength(effects.dialogueBoostStrength)
            effectsManager.setNightModeStrength(effects.nightModeStrength)
            engine.setCrossfadeDurationMs(audio.audioCrossfadeDurationMs)
            engine.setGaplessEnabled(audio.audioGaplessEnabled)
            effectsManager.setReplayGainMode(audio.audioNormalizationMode)
            effectsManager.setReplayGainPreAmpDb(audio.replayGainPreAmpDb)
            effectsManager.setBassBoostStrength(effects.bassBoostStrength)
            effectsManager.setVirtualizerStrength(effects.virtualizerStrength)
            effectsManager.setLrBalance(effects.lrBalance)
            effectsManager.setPitchSemitones(effects.pitchSemitones)
            effectsManager.setAutoEqByGenre(effects.autoEqByGenre)
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

    private val blurHashCache = lruMapOf<String, String?>(50)

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

    fun removeFromQueue(index: Int) {
        queueManager.removeFromQueue(index)
    }

    /** One-shot stream of destructive queue ops the UI can offer to undo. */
    val undoEvents: kotlinx.coroutines.flow.SharedFlow<com.raulshma.jellyplay.core.data.playback.QueueUndoEvent>
        get() = engine.undoEvents

    /** Restores the queue to before the most recent destructive op, if any. */
    fun undoLastQueueOperation(): Boolean =
        engine.undoLastQueueOperation()

    /** A→B loop markers (null = unset). */
    val abLoopStartMs: StateFlow<Long?> get() = engine.abLoopStartMs
    val abLoopEndMs: StateFlow<Long?> get() = engine.abLoopEndMs

    /** Cycles A→B loop: set A → set B → clear. */
    fun cycleAbLoop() = engine.cycleAbLoop()

    fun skipToNext() {
        queueManager.skipToNext()
    }

    fun skipToPrevious() {
        queueManager.skipToPrevious()
    }

    fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
    }

    fun togglePlayPause() {
        engine.togglePlayPause()
    }

    // ------------------------------------------------------------------
    // Cast / "Play On" — fling the current track to another Jellyfin session.
    // Mirrors VideoPlayerViewModel.castToDevice(); for audio there are no
    // subtitle/quality variants to carry, so the cast options are empty.
    // ------------------------------------------------------------------

    fun castToDevice() {
        val itemId = queueManager.currentPlayingItemId.value ?: return
        val positionMs = currentPosition
        // MediaItem/CastMediaOptions construction lives app-side in the
        // AudioPlayerCast interop adapter (media3 types can't cross commonMain).
        cast.loadMedia(itemId = itemId, startPositionMs = positionMs)
        engine.pause()
    }

    fun castPlay() = cast.play()
    fun castPause() = cast.pause()
    fun castSeekTo(positionMs: Long) = cast.seekTo(positionMs)
    fun setCastVolume(volume: Float) = cast.setVolume(volume)
    fun onCastDisconnected() {
        // No local teardown needed — the singleton owns the session lifecycle.
    }

    fun changePlaybackSpeed(value: Float) {
        engine.changePlaybackSpeed(value)
    }

    fun toggleShuffle() {
        queueManager.toggleShuffle()
    }

    fun cycleRepeatMode() {
        queueManager.cycleRepeatMode()
    }

    fun playFromQueue(index: Int) {
        queueManager.playFromQueue(index)
    }

    fun toggleDialogueBoost() {
        effectsManager.toggleDialogueBoost()
        launch {
            audioEffectsStore.setDialogueBoostEnabled(_uiState.value.effects.dialogueBoostEnabled)
        }
    }

    fun setDialogueBoostStrength(strength: EffectStrength) {
        effectsManager.setDialogueBoostStrength(strength)
        _uiState.update {
            it.copy(effects = it.effects.copy(dialogueBoostStrength = strength))
        }
        launch {
            audioEffectsStore.setDialogueBoostStrength(strength)
        }
    }

    fun toggleNightMode() {
        effectsManager.toggleNightMode()
        launch {
            audioEffectsStore.setNightModeEnabled(_uiState.value.effects.nightModeEnabled)
        }
    }

    fun setNightModeStrength(strength: EffectStrength) {
        effectsManager.setNightModeStrength(strength)
        _uiState.update {
            it.copy(effects = it.effects.copy(nightModeStrength = strength))
        }
        launch {
            audioEffectsStore.setNightModeStrength(strength)
        }
    }

    fun setReplayGainMode(mode: AudioNormalizationMode) {
        effectsManager.setReplayGainMode(mode)
        launch {
            audioStore.setAudioNormalizationMode(mode)
        }
    }

    fun setReplayGainPreAmpDb(db: Float) {
        effectsManager.setReplayGainPreAmpDb(db)
        launch {
            audioStore.setReplayGainPreAmpDb(db)
        }
    }

    fun toggleEqualizer() {
        effectsManager.toggleEqualizer()
        launch {
            audioEffectsStore.setEqualizerEnabled(_uiState.value.effects.equalizerEnabled)
        }
    }

    fun setEqualizerBand(bandIndex: Int, levelDb: Int) {
        effectsManager.setEqualizerBand(bandIndex, levelDb)
        launch {
            audioEffectsStore.setEqualizerSettings(_uiState.value.effects.equalizerSettings)
        }
    }

    fun resetEqualizer() {
        effectsManager.resetEqualizer()
        launch {
            audioEffectsStore.setEqualizerSettings(_uiState.value.effects.equalizerSettings)
            audioEffectsStore.setEqualizerPreset(_uiState.value.effects.equalizerPreset)
        }
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        effectsManager.setEqualizerPreset(preset)
        launch {
            audioEffectsStore.setEqualizerPreset(preset)
            audioEffectsStore.setEqualizerSettings(_uiState.value.effects.equalizerSettings)
        }
    }

    fun toggleBassBoost() {
        effectsManager.toggleBassBoost()
        launch {
            audioEffectsStore.setBassBoostEnabled(_uiState.value.effects.bassBoostEnabled)
        }
    }

    fun setBassBoostStrength(strength: EffectStrength) {
        effectsManager.setBassBoostStrength(strength)
        _uiState.update {
            it.copy(effects = it.effects.copy(bassBoostStrength = strength))
        }
        launch {
            audioEffectsStore.setBassBoostStrength(strength)
        }
    }

    fun toggleVirtualizer() {
        effectsManager.toggleVirtualizer()
        launch {
            audioEffectsStore.setVirtualizerEnabled(_uiState.value.effects.virtualizerEnabled)
        }
    }

    fun applyVirtualizerStrength(strength: Int) {
        effectsManager.setVirtualizerStrength(strength)
        launch {
            audioEffectsStore.setVirtualizerStrength(strength)
        }
    }

    fun applyReverbPreset(preset: ReverbPreset) {
        effectsManager.setReverbPreset(preset)
        launch {
            audioEffectsStore.setReverbPreset(preset)
        }
    }

    fun applyLrBalance(balance: Float) {
        effectsManager.setLrBalance(balance)
        launch {
            audioEffectsStore.setLrBalance(balance)
        }
    }

    fun applyPitchSemitones(semitones: Float) {
        effectsManager.setPitchSemitones(semitones)
        launch {
            audioEffectsStore.setPitchSemitones(semitones)
        }
    }

    fun applyAutoEqByGenre(enabled: Boolean) {
        effectsManager.setAutoEqByGenre(enabled)
        launch {
            audioEffectsStore.setAutoEqByGenre(enabled)
        }
    }

    fun getImageUrl(itemId: String): String =
        engine.getImageUrl(itemId)

    fun searchLyrics(query: String) {
        _uiState.update { it.copy(lyrics = it.lyrics.copy(isSearching = true)) }
        engine.searchLyrics(query) { result ->
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
        engine.applyLyrics(track.id)
        _uiState.update { it.copy(lyrics = it.lyrics.copy(searchResults = emptyList())) }
    }

    fun clearLyricsSearch() {
        _uiState.update { it.copy(lyrics = it.lyrics.copy(searchResults = emptyList())) }
    }

    fun setLyricsOffset(offsetMs: Long) {
        engine.setLyricsOffset(offsetMs)
    }

    fun updateCrossfadeDuration(ms: Long) {
        engine.setCrossfadeDurationMs(ms)
        launch {
            audioStore.setAudioCrossfadeDurationMs(ms)
        }
    }

    fun updateGaplessPlayback(enabled: Boolean) {
        engine.setGaplessEnabled(enabled)
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
            engine.pause()
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
            engine.pause()
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
        engine.stopAndRelease()
    }

    fun toggleFavorite() {
        val itemId = queueManager.currentPlayingItemId.value ?: return
        launch {
            // Silent mode (no containers — the player exposes a scalar, not a
            // list); the resolved target drives the scalar flip.
            userDataMutator.setFavorite(itemId)
                .onSuccess { applied ->
                    applied.favorite?.let { fav -> _uiState.update { it.copy(isFavorite = fav) } }
                }
        }
    }

    val currentPlayingItemId: String?
        get() = queueManager.currentPlayingItemId.value

    // ── Add to playlist ─────────────────────────────────────────────────────

    /** Opens the playlist picker and loads the user's editable playlists. */
    fun openPlaylistPicker() {
        if (currentPlayingItemId == null) return
        _uiState.update { it.copy(showPlaylistPicker = true, isLoadingPlaylists = true) }
        launch {
            playlistRepository.getPlaylists(limit = 100)
                .onSuccess { all ->
                    val editable = all.filter { it.canEdit }
                    _uiState.update {
                        it.copy(playlists = editable, isLoadingPlaylists = false)
                    }
                }
                .onFailure {
                    _uiState.update { it.copy(isLoadingPlaylists = false) }
                }
        }
    }

    fun dismissPlaylistPicker() {
        if (!_uiState.value.isAddingToPlaylist) {
            _uiState.update { it.copy(showPlaylistPicker = false, playlists = emptyList(), playlistMessage = null) }
        }
    }

    /** Adds the current track to [playlist]; clears the message after a beat. */
    fun addToPlaylist(playlist: com.raulshma.jellyplay.core.model.Playlist) {
        val itemId = currentPlayingItemId ?: return
        _uiState.update { it.copy(isAddingToPlaylist = true) }
        launch {
            playlistRepository.addItemsToPlaylist(playlist.id, listOf(itemId))
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            isAddingToPlaylist = false,
                            showPlaylistPicker = false,
                            playlists = emptyList(),
                            playlistMessage = playlist.name,
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(isAddingToPlaylist = false, playlistMessage = err.message)
                    }
                }
        }
    }

    fun clearPlaylistMessage() {
        _uiState.update { it.copy(playlistMessage = null) }
    }

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
