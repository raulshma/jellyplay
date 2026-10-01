package com.raulshma.jellyplay.feature.player.audio

import com.raulshma.jellyplay.core.data.playback.AudioEffectsManager
import com.raulshma.jellyplay.core.data.playback.AudioPlayerEngine
import com.raulshma.jellyplay.core.data.playback.AudioQueueItem
import com.raulshma.jellyplay.core.data.playback.AudioQueueManager
import com.raulshma.jellyplay.core.data.playback.SleepCountdown
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.LyricsLine
import com.raulshma.jellyplay.core.model.LyricsSource
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the ONE projection fold behind AudioPlayerUiState's flow-driven half
 * (the former ~12 hand-synced init collectors):
 *
 * 1. The engine/queue/sleep/prefs → [AudioProjectionState] field mappings,
 * 2. the [AudioProjectionState.appliedTo] fold onto [AudioPlayerUiState]
 *    (including that the imperative fields — isFavorite, blur hash, lyrics
 *    search/karaoke — pass through untouched), and
 * 3. one regression per deleted mirror getter: `dialogueBoostStrength` /
 *    `nightModeStrength` / `bassBoostStrength` were re-exposures of the
 *    effects slice, so their read path is [AudioPlayerViewModel.effectsState]
 *    — pinned here through a VM construction (the deleted getters must stay
 *    deleted; the slice serves the reads).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AudioStateProjectionTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var engine: AudioPlayerEngine
    private lateinit var queueManager: AudioQueueManager
    private lateinit var sleepCountdown: SleepCountdown
    private lateinit var audioStore: AudioStore
    private lateinit var effectsManager: AudioEffectsManager

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        engine = mockk()
        queueManager = mockk()
        sleepCountdown = mockk()
        audioStore = mockk()
        effectsManager = mockk()

        every { engine.title } returns MutableStateFlow("")
        every { engine.artist } returns MutableStateFlow("")
        every { engine.artistId } returns MutableStateFlow(null)
        every { engine.album } returns MutableStateFlow("")
        every { engine.albumArtUrl } returns MutableStateFlow("")
        every { engine.isPlaying } returns MutableStateFlow(false)
        every { engine.duration } returns MutableStateFlow(0L)
        every { engine.speed } returns MutableStateFlow(1.0f)
        every { engine.playbackError } returns MutableStateFlow(null)
        every { engine.isLoadingItem } returns MutableStateFlow(false)
        every { engine.crossfadeDurationMs } returns MutableStateFlow(0L)
        every { engine.lyrics } returns MutableStateFlow(emptyList())
        every { engine.currentLyricIndex } returns MutableStateFlow(-1)
        every { engine.lyricsSource } returns MutableStateFlow(LyricsSource.UNKNOWN)
        every { engine.isFetchingLyrics } returns MutableStateFlow(false)
        every { engine.lyricsOffsetMs } returns MutableStateFlow(200L)

        every { queueManager.queue } returns MutableStateFlow(emptyList())
        every { queueManager.currentIndex } returns MutableStateFlow(-1)
        every { queueManager.shuffleMode } returns MutableStateFlow(false)
        every { queueManager.repeatMode } returns MutableStateFlow(0)

        every { sleepCountdown.sleepTimerRemainingMs } returns MutableStateFlow(0L)
        every { sleepCountdown.isSleepTimerActive } returns MutableStateFlow(false)
        every { sleepCountdown.isEndOfEpisodeMode } returns MutableStateFlow(false)

        every { audioStore.audio } returns MutableStateFlow(AudioSlice())
        stubAudioEffectsReadSurface(effectsManager)
        // The controller's own mirror collectors read the replay-gain pair.
        every { effectsManager.replayGainMode } returns MutableStateFlow(com.raulshma.jellyplay.core.model.AudioNormalizationMode.NONE)
        every { effectsManager.replayGainPreAmpDb } returns MutableStateFlow(0.0f)
        // The strength setters the mirror-getter regressions drive.
        every { effectsManager.setDialogueBoostStrength(any()) } returns Unit
        every { effectsManager.setNightModeStrength(any()) } returns Unit
        every { effectsManager.setBassBoostStrength(any()) } returns Unit
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newProjection() = AudioStateProjection(
        engine = engine,
        queueManager = queueManager,
        sleepCountdown = sleepCountdown,
        audioStore = audioStore,
        scope = kotlinx.coroutines.CoroutineScope(testDispatcher),
    )

    @Test
    fun `engine and queue fields map onto the projection slice`() = runTest(testDispatcher) {
        every { engine.title } returns MutableStateFlow("Song")
        every { engine.artist } returns MutableStateFlow("Artist")
        every { engine.artistId } returns MutableStateFlow("a-1")
        every { engine.album } returns MutableStateFlow("Album")
        every { engine.albumArtUrl } returns MutableStateFlow("http://art")
        every { engine.isPlaying } returns MutableStateFlow(true)
        every { engine.duration } returns MutableStateFlow(180_000L)
        every { engine.speed } returns MutableStateFlow(1.5f)
        every { engine.playbackError } returns MutableStateFlow("decoder")
        every { engine.isLoadingItem } returns MutableStateFlow(true)
        every { engine.crossfadeDurationMs } returns MutableStateFlow(4_000L)
        every { engine.lyrics } returns MutableStateFlow(listOf(LyricsLine(timeMs = 0L, text = "la")))
        every { engine.currentLyricIndex } returns MutableStateFlow(2)
        every { engine.lyricsSource } returns MutableStateFlow(LyricsSource.LRCLIB)
        every { engine.isFetchingLyrics } returns MutableStateFlow(true)
        every { engine.lyricsOffsetMs } returns MutableStateFlow(-300L)
        every { queueManager.queue } returns MutableStateFlow(listOf(AudioQueueItem(id = "t1", name = "T1", artist = "A", album = null, imageUrl = null, mediaSourceId = null)))
        every { queueManager.currentIndex } returns MutableStateFlow(0)
        every { queueManager.shuffleMode } returns MutableStateFlow(true)
        every { queueManager.repeatMode } returns MutableStateFlow(1)

        val projection = newProjection()
        advanceUntilIdle()

        val p = projection.state.value
        assertEquals("Song", p.title)
        assertEquals("decoder", p.playbackError)
        assertEquals(true, p.isLoading)
        assertEquals("Artist", p.artist)
        assertEquals("a-1", p.artistId)
        assertEquals("Album", p.album)
        assertEquals("http://art", p.albumArtUrl)
        assertEquals(true, p.isPlaying)
        assertEquals(180_000L, p.duration)
        assertEquals(1.5f, p.speed)
        assertEquals(4_000L, p.crossfadeDurationMs)
        assertEquals(
            QueueState(
                queue = listOf(AudioQueueItem(id = "t1", name = "T1", artist = "A", album = null, imageUrl = null, mediaSourceId = null)),
                currentIndex = 0,
                shuffleMode = true,
                repeatMode = 1,
            ),
            p.queue,
        )
        assertEquals(listOf(LyricsLine(timeMs = 0L, text = "la")), p.lyrics.lyrics)
        assertEquals(2, p.lyrics.currentLyricIndex)
        assertEquals(LyricsSource.LRCLIB, p.lyrics.lyricsSource)
        assertEquals(true, p.lyrics.isFetchingLyrics)
        assertEquals(-300L, p.lyrics.lyricsOffsetMs)
    }

    @Test
    fun `sleep and prefs fields map onto the projection slice`() = runTest(testDispatcher) {
        every { sleepCountdown.isSleepTimerActive } returns MutableStateFlow(true)
        every { sleepCountdown.isEndOfEpisodeMode } returns MutableStateFlow(true)
        every { audioStore.audio } returns MutableStateFlow(AudioSlice(sleepTimerDurationMs = 900_000L))

        val projection = newProjection()
        advanceUntilIdle()

        val p = projection.state.value
        assertEquals(true, p.sleepTimerActive)
        assertEquals(true, p.sleepTimerEndOfEpisode)
        assertEquals(900_000L, p.sleepTimerLastUsedDurationMs)
    }

    @Test
    fun `appliedTo writes exactly the projection fields and preserves imperative slices`() {
        val projection = AudioProjectionState(
            title = "Song",
            playbackError = "boom",
            isLoading = true,
            artist = "Artist",
            artistId = "a-1",
            album = "Album",
            albumArtUrl = "http://art",
            isPlaying = true,
            duration = 5L,
            speed = 2.0f,
            crossfadeDurationMs = 7L,
            queue = QueueState(queue = emptyList(), currentIndex = 3, shuffleMode = true, repeatMode = 2),
            lyrics = ProjectionLyrics(currentLyricIndex = 4, lyricsOffsetMs = -100L),
            sleepTimerActive = true,
            sleepTimerEndOfEpisode = false,
            sleepTimerLastUsedDurationMs = 60_000L,
        )
        // Imperative fields the flows must never clobber.
        val state = AudioPlayerUiState(
            isFavorite = true,
            albumArtBlurHash = "hash",
            lyrics = LyricsState(searchResults = emptyList(), isSearching = true, karaokeMode = true),
            sleepTimer = SleepTimerState(active = false, endOfEpisode = true, lastUsedDurationMs = 1L),
        )

        val applied = projection.appliedTo(state)

        assertEquals("Song", applied.title)
        assertEquals("boom", applied.playbackError)
        assertEquals(true, applied.isLoading)
        assertEquals("Artist", applied.artist)
        assertEquals("a-1", applied.artistId)
        assertEquals("Album", applied.album)
        assertEquals("http://art", applied.albumArtUrl)
        assertEquals(true, applied.isPlaying)
        assertEquals(5L, applied.duration)
        assertEquals(2.0f, applied.speed)
        assertEquals(7L, applied.crossfadeDurationMs)
        assertEquals(QueueState(queue = emptyList(), currentIndex = 3, shuffleMode = true, repeatMode = 2), applied.queue)
        assertEquals(4, applied.lyrics.currentLyricIndex)
        assertEquals(-100L, applied.lyrics.lyricsOffsetMs)
        assertEquals(true, applied.sleepTimer.active)
        assertEquals(false, applied.sleepTimer.endOfEpisode)
        assertEquals(60_000L, applied.sleepTimer.lastUsedDurationMs)
        // Imperative slices pass through.
        assertEquals(true, applied.isFavorite)
        assertEquals("hash", applied.albumArtBlurHash)
        assertEquals(true, applied.lyrics.isSearching)
        assertEquals(true, applied.lyrics.karaokeMode)
    }

    // ── regressions: the deleted effects-strength mirror getters ──────────
    // `dialogueBoostStrength` / `nightModeStrength` / `bassBoostStrength` were
    // VM getters re-exposing the effects slice; the read path is now (and was
    // then) `effectsState` — pinned here through the controller's command leg.

    @Test
    fun `deleted dialogueBoostStrength getter is served by the effectsState slice`() {
        val viewModel = newViewModel()
        viewModel.onEvent(AudioPlayerUiEvent.SetDialogueBoostStrength(EffectStrength.HIGH))
        assertEquals(EffectStrength.HIGH, viewModel.effectsState.value.dialogueBoostStrength)
    }

    @Test
    fun `deleted nightModeStrength getter is served by the effectsState slice`() {
        val viewModel = newViewModel()
        viewModel.onEvent(AudioPlayerUiEvent.SetNightModeStrength(EffectStrength.LOW))
        assertEquals(EffectStrength.LOW, viewModel.effectsState.value.nightModeStrength)
    }

    @Test
    fun `deleted bassBoostStrength getter is served by the effectsState slice`() {
        val viewModel = newViewModel()
        viewModel.onEvent(AudioPlayerUiEvent.SetBassBoostStrength(EffectStrength.NONE))
        assertEquals(EffectStrength.NONE, viewModel.effectsState.value.bassBoostStrength)
    }

    /**
     * Minimal VM construction over the same stub surface
     * ([AudioPlayerViewModelTest] is the full harness) — the projection and
     * the mirror-getter regressions only need the flows and the effects
     * read-surface.
     */
    private fun newViewModel(): AudioPlayerViewModel {
        val projections = mockk<com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections>(relaxed = true)
        val audioEffectsStore = mockk<com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore>(relaxed = true)
        val mediaRepository = mockk<com.raulshma.jellyplay.core.data.repository.MediaRepository>(relaxed = true)
        val playlistRepository = mockk<com.raulshma.jellyplay.core.data.repository.PlaylistRepository>(relaxed = true)
        val userDataMutator = mockk<com.raulshma.jellyplay.core.data.repository.UserDataMutator>(relaxed = true)
        val downloads = mockk<com.raulshma.jellyplay.core.data.download.TrackDownloadStatusWindow>(relaxed = true)
            .apply { every { isSupported } returns true }
        val trackDownloadActions = mockk<com.raulshma.jellyplay.core.data.download.TrackDownloadActions>(relaxed = true)
        val cast = mockk<AudioPlayerCast>(relaxed = true)
        val audioQueueFacade = mockk<com.raulshma.jellyplay.core.data.playback.AudioQueueFacade>(relaxed = true)

        every { projections.audioPlayerUiPreferences } returns MutableStateFlow(
            com.raulshma.jellyplay.core.model.AudioPlayerUiPreferences(),
        )

        return AudioPlayerViewModel(
            queueManager = queueManager,
            effectsManager = effectsManager,
            engine = engine,
            projections = projections,
            audioStore = audioStore,
            audioEffectsStore = audioEffectsStore,
            mediaRepository = mediaRepository,
            playlistRepository = playlistRepository,
            userDataMutator = userDataMutator,
            downloads = downloads,
            trackDownloadActions = trackDownloadActions,
            sleepCountdown = sleepCountdown,
            cast = cast,
            audioQueueFacade = audioQueueFacade,
        )
    }
}
