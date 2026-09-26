package com.raulshma.jellyplay.feature.player.audio

import androidx.compose.runtime.LongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.AudioPlayerEngine
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.AudioSleepTimerManager
import com.raulshma.jellyplay.core.data.download.TrackDownloadActions
import com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow
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
 * ctor dep is split across the shared playback contracts
 * ([AudioQueueManager], [AudioEffectsManager], [AudioPlayerEngine] — the
 * legacy manager implements all three; the engine contract lives in core/data
 * beside them) plus the module-local [AudioPlayerCast] seam. The track
 * download flip is the shared core:data [TrackDownloadActions] (Koin-bound
 * in di/PlayerAudioKoinModule.kt) — the former inline construction with its
 * own DownloadIntake/MediaRepository pair folded away when the resolve→start
 * leg moved into DownloadIntake.flipTrack.
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
    private val downloads: TrackDownloadStatusWindow,
    private val trackDownloadActions: TrackDownloadActions,
    private val sleepTimerManager: AudioSleepTimerManager,
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
     * Sleep-timer countdown, sourced directly from the AudioSleepTimerManager. Kept OUT
     * of [uiState] (mirroring [currentPosition]) so a 5 s tick — or the 100 ms
     * fade-out burst — does not copy the whole [AudioPlayerUiState] and
     * re-invalidate the screen root. Collected only by the leaf composables
     * that render the countdown (top-bar label, AudioSleepTimerSheet).
     */
    val sleepTimerRemainingMs: StateFlow<Long> = sleepTimerManager.sleepTimerRemainingMs

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
        get() = effects.state.value.dialogueBoostStrength

    /** Mirrors [AudioEffectsState.nightModeStrength] for callers that read it directly. */
    val nightModeStrength: EffectStrength
        get() = effects.state.value.nightModeStrength

    /** Mirrors [AudioEffectsState.bassBoostStrength] for callers that read it directly. */
    val bassBoostStrength: EffectStrength
        get() = effects.state.value.bassBoostStrength

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
    // delegates. Each controller owns its state slice as its own StateFlow
    // (the SettingsProjector-style uiState seams are gone); the VM re-exposes
    // the slices the screen collects and keeps only real orchestration.

    /** Owns the effects setters' apply→mirror→persist choreography + play() seeding. */
    internal val effects = AudioEffectsController(
        scope = scope,
        effectsManager = effectsManager,
        engine = engine,
        audioStore = audioStore,
        audioEffectsStore = audioEffectsStore,
    )

    /** The audio-effects slice — re-exposed from [AudioEffectsController.state]. */
    val effectsState: StateFlow<AudioEffectsState>
        get() = effects.state

    /** Owns the sleep-timer starts/cancel/expiry + store writes + slice updates. */
    internal val sleepTimer = AudioSleepTimerController(
        scope = scope,
        sleepTimerManager = sleepTimerManager,
        audioStore = audioStore,
        engine = engine,
        updateState = { transform -> _uiState.update { it.copy(sleepTimer = transform(it.sleepTimer)) } },
    )

    /**
     * Owns the add-to-playlist picker lifecycle (open guard, editable-playlist
     * fetch, add choreography, success/failure message) as its own
     * StateFlow snapshot — the five uiState fields it used to hand-sync are
     * gone from [AudioPlayerUiState].
     */
    internal val playlistPicker = PlaylistPickerStateHolder(
        scope = scope,
        playlistRepository = playlistRepository,
        currentItemId = { currentPlayingItemId },
    )

    private var downloadJob: Job? = null

    /** At most one favorite-state fetch in flight (the favorite collector below). */
    private var favoriteJob: Job? = null

    private val _currentDownloadItem = stateFlow<com.raulshma.jellyplay.core.model.DownloadItem?>(null)
    val currentDownloadItem: StateFlow<com.raulshma.jellyplay.core.model.DownloadItem?> = _currentDownloadItem.flow

    init {
        launch {
            queueManager.currentPlayingItemId.collect { itemId ->
                downloadJob?.cancel()
                if (itemId != null) {
                    downloadJob = launch {
                        // The single-id window read (the former seam's
                        // trackStatus): rows → the one row, null when none.
                        downloads.downloadsFor(listOf(itemId))
                            .map { rows -> rows.firstOrNull() }
                            .collect { download ->
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
                favoriteJob?.cancel()
                if (itemId != null) {
                    // One fetch in flight, launched off the collect body so a
                    // burst of skips coalesces; itemId is captured per fetch and
                    // the write is dropped if another track took over meanwhile.
                    favoriteJob = launch {
                        mediaRepository.getMediaDetail(itemId)
                            .onSuccess { d ->
                                if (queueManager.currentPlayingItemId.value == itemId) {
                                    _uiState.update { it.copy(isFavorite = d.item.isFavorite) }
                                }
                            }
                    }
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
        // The effects-slice mirror collectors died with the uiState effects
        // field — [AudioEffectsController] mirrors the manager flows into its
        // own state slice now (see its init). Only the crossfade field (a
        // uiState resident, not an effects-slice field) keeps its collector.
        launch {
            engine.crossfadeDurationMs.collect { cross ->
                _uiState.update { it.copy(crossfadeDurationMs = cross) }
            }
        }
        launch {
            combine(
                sleepTimerManager.isSleepTimerActive,
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

    /**
     * The VM's single command surface: every user intent arrives here as an
     * [AudioPlayerUiEvent] (the HomeViewModel precedent). Each arm routes to
     * the former command fun, now private with its body byte-identical — the
     * pure-forwarding delegates onto [effects] / [sleepTimer] /
     * [playlistPicker] keep their one-line shape, and the two funs with
     * internal callers ([toggleKaraokeMode] → [setKaraokeModeEnabled]) stay
     * callable. The VM's other public members are flows, sync getters, and
     * the controller slices — there is no per-action command method to keep
     * in sync with the screen.
     */
    fun onEvent(event: AudioPlayerUiEvent) {
        when (event) {
            is AudioPlayerUiEvent.Play -> play(event.itemId)
            is AudioPlayerUiEvent.RemoveFromQueue -> removeFromQueue(event.index)
            is AudioPlayerUiEvent.UndoLastQueueOperation -> undoLastQueueOperation()
            is AudioPlayerUiEvent.CycleAbLoop -> cycleAbLoop()
            is AudioPlayerUiEvent.SkipToNext -> skipToNext()
            is AudioPlayerUiEvent.SkipToPrevious -> skipToPrevious()
            is AudioPlayerUiEvent.SeekTo -> seekTo(event.positionMs)
            is AudioPlayerUiEvent.TogglePlayPause -> togglePlayPause()
            is AudioPlayerUiEvent.CastToDevice -> castToDevice()
            is AudioPlayerUiEvent.ChangePlaybackSpeed -> changePlaybackSpeed(event.value)
            is AudioPlayerUiEvent.ToggleShuffle -> toggleShuffle()
            is AudioPlayerUiEvent.CycleRepeatMode -> cycleRepeatMode()
            is AudioPlayerUiEvent.PlayFromQueue -> playFromQueue(event.index)
            is AudioPlayerUiEvent.ToggleDialogueBoost -> toggleDialogueBoost()
            is AudioPlayerUiEvent.SetDialogueBoostStrength -> setDialogueBoostStrength(event.strength)
            is AudioPlayerUiEvent.ToggleNightMode -> toggleNightMode()
            is AudioPlayerUiEvent.SetNightModeStrength -> setNightModeStrength(event.strength)
            is AudioPlayerUiEvent.SetReplayGainMode -> setReplayGainMode(event.mode)
            is AudioPlayerUiEvent.ToggleEqualizer -> toggleEqualizer()
            is AudioPlayerUiEvent.SetEqualizerBand -> setEqualizerBand(event.bandIndex, event.levelDb)
            is AudioPlayerUiEvent.ResetEqualizer -> resetEqualizer()
            is AudioPlayerUiEvent.SetEqualizerPreset -> setEqualizerPreset(event.preset)
            is AudioPlayerUiEvent.ToggleBassBoost -> toggleBassBoost()
            is AudioPlayerUiEvent.SetBassBoostStrength -> setBassBoostStrength(event.strength)
            is AudioPlayerUiEvent.ToggleVirtualizer -> toggleVirtualizer()
            is AudioPlayerUiEvent.SetVirtualizerStrength -> setVirtualizerStrength(event.strength)
            is AudioPlayerUiEvent.SetReverbPreset -> setReverbPreset(event.preset)
            is AudioPlayerUiEvent.SetLrBalance -> setLrBalance(event.balance)
            is AudioPlayerUiEvent.SetPitchSemitones -> setPitchSemitones(event.semitones)
            is AudioPlayerUiEvent.SetAutoEqByGenre -> setAutoEqByGenre(event.enabled)
            is AudioPlayerUiEvent.SearchLyrics -> searchLyrics(event.query)
            is AudioPlayerUiEvent.ApplyLyrics -> applyLyrics(event.track)
            is AudioPlayerUiEvent.ClearLyricsSearch -> clearLyricsSearch()
            is AudioPlayerUiEvent.SetLyricsOffset -> setLyricsOffset(event.offsetMs)
            is AudioPlayerUiEvent.StartSleepTimer -> startSleepTimer(event.durationMs)
            is AudioPlayerUiEvent.StartSleepTimerEndOfEpisode -> startSleepTimerEndOfEpisode()
            is AudioPlayerUiEvent.CancelSleepTimer -> cancelSleepTimer()
            is AudioPlayerUiEvent.ToggleFavorite -> toggleFavorite()
            is AudioPlayerUiEvent.OpenPlaylistPicker -> openPlaylistPicker()
            is AudioPlayerUiEvent.DismissPlaylistPicker -> dismissPlaylistPicker()
            is AudioPlayerUiEvent.AddToPlaylist -> addToPlaylist(event.playlist)
            is AudioPlayerUiEvent.SetKaraokeModeEnabled -> setKaraokeModeEnabled(event.enabled)
            is AudioPlayerUiEvent.ToggleKaraokeMode -> toggleKaraokeMode()
            is AudioPlayerUiEvent.SetLyricsVisible -> setLyricsVisible(event.enabled)
            is AudioPlayerUiEvent.DownloadCurrentTrack -> downloadCurrentTrack()
        }
    }

    private fun play(itemId: String) {
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

    private fun removeFromQueue(index: Int) {
        queueManager.removeFromQueue(index)
    }

    /** One-shot stream of destructive queue ops the UI can offer to undo. */
    val undoEvents: kotlinx.coroutines.flow.SharedFlow<com.raulshma.jellyplay.core.data.playback.QueueUndoEvent>
        get() = engine.undoEvents

    /** Restores the queue to before the most recent destructive op, if any. */
    private fun undoLastQueueOperation(): Boolean =
        engine.undoLastQueueOperation()

    /** A→B loop markers (null = unset). */
    val abLoopStartMs: StateFlow<Long?> get() = engine.abLoopStartMs
    val abLoopEndMs: StateFlow<Long?> get() = engine.abLoopEndMs

    /** Cycles A→B loop: set A → set B → clear. */
    private fun cycleAbLoop() = engine.cycleAbLoop()

    private fun skipToNext() {
        queueManager.skipToNext()
    }

    private fun skipToPrevious() {
        queueManager.skipToPrevious()
    }

    private fun seekTo(positionMs: Long) {
        engine.seekTo(positionMs)
    }

    private fun togglePlayPause() {
        engine.togglePlayPause()
    }

    // ------------------------------------------------------------------
    // Cast / "Play On" — fling the current track to another Jellyfin session.
    // Mirrors VideoPlayerViewModel.castToDevice(); for audio there are no
    // subtitle/quality variants to carry, so the cast options are empty.
    // ------------------------------------------------------------------

    private fun castToDevice() {
        val itemId = queueManager.currentPlayingItemId.value ?: return
        val positionMs = currentPosition
        // MediaItem/CastMediaOptions construction lives app-side in the
        // AudioPlayerCast interop adapter (media3 types can't cross commonMain).
        cast.loadMedia(itemId = itemId, startPositionMs = positionMs)
        engine.pause()
    }

    private fun changePlaybackSpeed(value: Float) {
        engine.changePlaybackSpeed(value)
    }

    private fun toggleShuffle() {
        queueManager.toggleShuffle()
    }

    private fun cycleRepeatMode() {
        queueManager.cycleRepeatMode()
    }

    private fun playFromQueue(index: Int) {
        queueManager.playFromQueue(index)
    }

    // Effects setters: one-line delegates — the apply→mirror→persist
    // choreography lives on [effects] (AudioEffectsController).

    private fun toggleDialogueBoost() = effects.toggleDialogueBoost()

    private fun setDialogueBoostStrength(strength: EffectStrength) = effects.setDialogueBoostStrength(strength)

    private fun toggleNightMode() = effects.toggleNightMode()

    private fun setNightModeStrength(strength: EffectStrength) = effects.setNightModeStrength(strength)

    private fun setReplayGainMode(mode: AudioNormalizationMode) = effects.setReplayGainMode(mode)

    private fun toggleEqualizer() = effects.toggleEqualizer()

    private fun setEqualizerBand(bandIndex: Int, levelDb: Int) = effects.setEqualizerBand(bandIndex, levelDb)

    private fun resetEqualizer() = effects.resetEqualizer()

    private fun setEqualizerPreset(preset: EqualizerPreset) = effects.setEqualizerPreset(preset)

    private fun toggleBassBoost() = effects.toggleBassBoost()

    private fun setBassBoostStrength(strength: EffectStrength) = effects.setBassBoostStrength(strength)

    private fun toggleVirtualizer() = effects.toggleVirtualizer()

    private fun setVirtualizerStrength(strength: Int) = effects.setVirtualizerStrength(strength)

    private fun setReverbPreset(preset: ReverbPreset) = effects.setReverbPreset(preset)

    private fun setLrBalance(balance: Float) = effects.setLrBalance(balance)

    private fun setPitchSemitones(semitones: Float) = effects.setPitchSemitones(semitones)

    private fun setAutoEqByGenre(enabled: Boolean) = effects.setAutoEqByGenre(enabled)

    fun getImageUrl(itemId: String): String =
        engine.getImageUrl(itemId)

    private fun searchLyrics(query: String) {
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

    private fun applyLyrics(track: LrcLibTrack) {
        engine.applyLyrics(track.id)
        _uiState.update { it.copy(lyrics = it.lyrics.copy(searchResults = emptyList())) }
    }

    private fun clearLyricsSearch() {
        _uiState.update { it.copy(lyrics = it.lyrics.copy(searchResults = emptyList())) }
    }

    private fun setLyricsOffset(offsetMs: Long) {
        engine.setLyricsOffset(offsetMs)
    }

    // Sleep timer: delegates onto [sleepTimer] (AudioSleepTimerController),
    // which owns the store writes, the expiry callback (explicit pause, in ONE
    // place), and the synchronous uiState slice updates. The flow collectors in
    // init keep mirroring manager/prefs state into the same slice.

    private fun startSleepTimer(durationMs: Long) = sleepTimer.startSleepTimer(durationMs)

    private fun startSleepTimerEndOfEpisode() = sleepTimer.startSleepTimerEndOfEpisode()

    private fun cancelSleepTimer() = sleepTimer.cancelSleepTimer()

    private fun toggleFavorite() {
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
    // One-line forwards: the picker lifecycle (open guard, editable-playlist
    // fetch, add choreography, success/failure message) lives on
    // [playlistPicker] (PlaylistPickerStateHolder); its state is read off
    // playlistPicker.state, not this VM's uiState.

    /** Opens the playlist picker and loads the user's editable playlists. */
    private fun openPlaylistPicker() = playlistPicker.open()

    private fun dismissPlaylistPicker() = playlistPicker.dismiss()

    /** Adds the current track to [playlist]; success posts its name as the message. */
    private fun addToPlaylist(playlist: com.raulshma.jellyplay.core.model.Playlist) = playlistPicker.addTo(playlist)

    private fun setKaraokeModeEnabled(enabled: Boolean) {
        karaokeMode = enabled
        _uiState.update { it.copy(lyrics = it.lyrics.copy(karaokeMode = enabled)) }
    }

    private fun toggleKaraokeMode() {
        setKaraokeModeEnabled(!karaokeMode)
    }

    /** Persists the lyrics overlay visibility so it survives across sessions. */
    private fun setLyricsVisible(enabled: Boolean) {
        launch { audioStore.setAudioLyricsVisible(enabled) }
    }

    private fun keySentinel(id: String) = "§null§$id"

    /** Whether this platform carries a download pipeline; gates the track CTA. */
    val isDownloadSupported: Boolean get() = downloads.isSupported

    // ── Track download flip ────────────────────────────────────────────────
    // The shared core:data TrackDownloadActions choreography (id → intake
    // flipTrack → resolve detail → start) arrives via Koin like the other
    // collaborators — the former hand-rolled inline construction (with its
    // DownloadIntake + MediaRepository pair) is gone: the resolve→start leg
    // exists once, in DownloadIntake.flipTrack. The REMOVE half stays HERE:
    // a COMPLETED download flips the CTA to remove only after the screen's
    // confirm dialog — the module must not swallow that policy.

    private fun downloadCurrentTrack() {
        val itemId = currentPlayingItemId ?: return
        val existing = _currentDownloadItem.value
        if (existing != null && existing.status == com.raulshma.jellyplay.core.model.DownloadStatus.COMPLETED) {
            launch {
                downloads.remove(existing.id)
            }
            return
        }
        trackDownloadActions.flip(itemId)
    }
}
