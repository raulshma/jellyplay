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
 * Koin-owned (conveyor move from `:feature:player:audio` — the
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

    // ── Controller slices (the VideoEffectsController / SleepTimerController
    //    player-video pattern, applied to the audio player) ──────────────────
    // The effects setters' apply→persist choreography and the sleep-timer
    // workflow live in the two controllers below; the VM funs are one-line
    // delegates. The [updateEffects]/[updateState] lambdas are the
    // SettingsProjector-style seam over this VM's uiState slices — the mirror
    // collectors above keep flowing manager state INTO uiState; the seam is
    // only the write path (and it never feeds persistence: the controllers
    // read the manager's StateFlow values, not this mirror).

    /** Owns the effects setters' apply→mirror→persist choreography + play() seeding. */
    internal val effects = AudioEffectsController(
        scope = scope,
        effectsManager = effectsManager,
        engine = engine,
        audioStore = audioStore,
        audioEffectsStore = audioEffectsStore,
        updateEffects = { transform -> _uiState.update { it.copy(effects = transform(it.effects)) } },
    )

    /** Owns the sleep-timer starts/cancel/expiry + store writes + slice updates. */
    internal val sleepTimer = AudioSleepTimerController(
        scope = scope,
        sleepTimerManager = sleepTimerManager,
        audioStore = audioStore,
        engine = engine,
        updateState = { transform -> _uiState.update { it.copy(sleepTimer = transform(it.sleepTimer)) } },
    )

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
            val (audio, fx) = combine(audioStore.audio, audioEffectsStore.audioEffects) { a, e -> a to e }.first()
            if (audio.audioDefaultSpeed != 1.0f) {
                engine.changePlaybackSpeed(audio.audioDefaultSpeed)
            }
            nightModeVolume = audio.audioNightModeVolume
            nightModeGain = audio.audioNightModeGain
            skipPreviousThresholdMs = audio.audioSkipPreviousThresholdMs
            engine.setSkipPreviousThreshold(audio.audioSkipPreviousThresholdMs)
            // The prefs→effects field list lives in ONE place: the controller's
            // seeding entry (apply-only — the values came FROM the stores).
            effects.seedForPlayback(audio, fx)
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

    // Effects setters: one-line delegates — the apply→mirror→persist
    // choreography lives on [effects] (AudioEffectsController).

    fun toggleDialogueBoost() = effects.toggleDialogueBoost()

    fun setDialogueBoostStrength(strength: EffectStrength) = effects.setDialogueBoostStrength(strength)

    fun toggleNightMode() = effects.toggleNightMode()

    fun setNightModeStrength(strength: EffectStrength) = effects.setNightModeStrength(strength)

    fun setReplayGainMode(mode: AudioNormalizationMode) = effects.setReplayGainMode(mode)

    fun setReplayGainPreAmpDb(db: Float) = effects.setReplayGainPreAmpDb(db)

    fun toggleEqualizer() = effects.toggleEqualizer()

    fun setEqualizerBand(bandIndex: Int, levelDb: Int) = effects.setEqualizerBand(bandIndex, levelDb)

    fun resetEqualizer() = effects.resetEqualizer()

    fun setEqualizerPreset(preset: EqualizerPreset) = effects.setEqualizerPreset(preset)

    fun toggleBassBoost() = effects.toggleBassBoost()

    fun setBassBoostStrength(strength: EffectStrength) = effects.setBassBoostStrength(strength)

    fun toggleVirtualizer() = effects.toggleVirtualizer()

    fun setVirtualizerStrength(strength: Int) = effects.setVirtualizerStrength(strength)

    fun setReverbPreset(preset: ReverbPreset) = effects.setReverbPreset(preset)

    fun setLrBalance(balance: Float) = effects.setLrBalance(balance)

    fun setPitchSemitones(semitones: Float) = effects.setPitchSemitones(semitones)

    fun setAutoEqByGenre(enabled: Boolean) = effects.setAutoEqByGenre(enabled)

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

    fun updateCrossfadeDuration(ms: Long) = effects.updateCrossfadeDuration(ms)

    fun updateGaplessPlayback(enabled: Boolean) = effects.updateGaplessPlayback(enabled)

    // Sleep timer: delegates onto [sleepTimer] (AudioSleepTimerController),
    // which owns the store writes, the expiry callback (explicit pause, in ONE
    // place), and the synchronous uiState slice updates. The flow collectors in
    // init keep mirroring manager/prefs state into the same slice.

    fun startSleepTimer(durationMs: Long) = sleepTimer.startSleepTimer(durationMs)

    fun startSleepTimerEndOfEpisode() = sleepTimer.startSleepTimerEndOfEpisode()

    fun cancelSleepTimer() = sleepTimer.cancelSleepTimer()

    fun triggerSleepTimerEndOfEpisode() = sleepTimer.triggerSleepTimerEndOfEpisode()

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
