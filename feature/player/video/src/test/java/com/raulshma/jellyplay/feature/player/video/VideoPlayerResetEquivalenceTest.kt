package com.raulshma.jellyplay.feature.player.video

import android.content.Context
import com.raulshma.jellyplay.core.data.cast.CastManager
import com.raulshma.jellyplay.core.data.network.NetworkMonitor
import com.raulshma.jellyplay.core.data.playback.AdaptiveBitrateManager
import com.raulshma.jellyplay.core.data.playback.PlaybackSessionManager
import com.raulshma.jellyplay.core.data.playback.PlayerLifecycleManager
import com.raulshma.jellyplay.core.data.playback.SleepTimerManager
import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.remote.ActivePlayerController
import com.raulshma.jellyplay.core.data.repository.DownloadRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.OfflineRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.data.repository.ItemPlaybackPreferenceRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayPlaybackCore
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregateStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.AudioNormalizationMode
import com.raulshma.jellyplay.core.model.ChannelMixMode
import com.raulshma.jellyplay.core.model.DecoderMode
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.model.ReverbPreset
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.ui.feedback.UserMessageBus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Golden characterization test for the item-switch reset semantics.
 * Drives one scripted session mutating every migrated slice (sleep
 * timer, audio effects, dialogue boost, subtitle search, A/B repeat, SyncPlay
 * group join) plus residual probes (subtitle style persists; playback speed /
 * stats reset), triggers the item-switch path (`initialize` with a new item id,
 * which routes through `releaseInternals()`), and snapshots the outcome
 * field-by-field against the golden below.
 *
 * **Intended divergence — exactly one:** the A/B
 * repeat window now RESETS on item switch. Before, the reset ritual
 * wiped the UiState mirror of the controller's `AbRepeatState` but never reset
 * the controller's own copy, so after an episode switch the loop monitor could
 * seek the *next* episode back to the *previous* episode's A point, and one tap
 * on the toggle resurrected the stale points (the divergence bug the change
 * fixes; see `AbRepeatControllerTest.resetForItem clears points and disarms`).
 *
 * A second, benign nuance: `audioDelayMs` previously dropped to 0 for the
 * instant between the reset and the new engine binding (it was never
 * whitelisted); with ownership it now carries across that instant and is
 * re-seeded from the persisted preference when the engine binds (the
 * engineFlow collector's `seedFromPreferences`), converging to the same value.
 * With no engine bound in this harness, the golden pins the carried value.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerResetEquivalenceTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: VideoPlayerViewModel
    private lateinit var mediaRepository: MediaRepository
    private lateinit var aggregateStore: VideoPlayerAggregateStore
    private lateinit var syncPlayManager: SyncPlayManager
    private lateinit var playbackRepository: PlaybackRepository

    /** The residual reset whitelist — the only UiState fields carried across an item switch. */
    private val residualWhitelist = setOf(
        "preferredPlayerType", "seekDurationMs", "defaultOrientation", "controlsTimeoutMs",
        "gesturesEnabled", "defaultSpeed", "swipeSeekMaxMs", "rememberBrightness",
        "brightnessLevel", "segmentBehaviors", "videoEpisodeBrowserEnabled",
        "showPlaybackMetadata", "showClock", "showTimeRemaining", "tvZoomModePercent",
        "keepScreenOnDuringVideo", "subtitleStyle",
    )

    /**
     * Fields the *load* coroutine legitimately re-populates after the reset
     * (the loading screen lifts in the `finally` even when the detail fetch
     * fails; the session's play-method string is re-resolved during load and
     * the autoplay-next pref default differs from the constructor default), so
     * "reset to default" does not hold for them at snapshot time.
     */
    private val loadRepopulated = setOf("isInitializing", "playMethod", "videoAutoplayNext")

    /**
     * Fields the reset re-sets to explicit (non-default) values: the per-item
     * dialogue boost zeroes so it can't bleed into the next item before the
     * resolver re-applies the per-item rule (strength NONE, not the MODERATE
     * constructor default).
     */
    private val explicitlyReset = mapOf(
        "dialogueBoostEnabled" to false,
        "dialogueBoostStrength" to EffectStrength.NONE,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        val context = mockk<Context>(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        playbackRepository = mockk(relaxed = true)
        val imageUrlProvider = mockk<ImageUrlProvider>(relaxed = true)
        val downloadRepository = mockk<DownloadRepository>(relaxed = true)
        val offlineRepository = mockk<OfflineRepository>(relaxed = true)
        val offlinePlaybackFacade = mockk<com.raulshma.jellyplay.core.data.repository.OfflinePlaybackFacade>(relaxed = true)
        val playbackSourceResolver = mockk<com.raulshma.jellyplay.core.data.playback.PlaybackSourceResolver>(relaxed = true)
        val itemPlaybackPreferenceRepository = mockk<ItemPlaybackPreferenceRepository>(relaxed = true)
        // Resolve to "no stored preference": a relaxed mock would return a
        // mocked ItemPlaybackPreference whose enum props clobber the dialogue
        // boost under test.
        coEvery { itemPlaybackPreferenceRepository.get(any(), any()) } returns null
        aggregateStore = mockk(relaxed = true)
        val engineStore = mockk<PlayerEngineStore>(relaxed = true)
        val subtitleStore = mockk<SubtitleLanguageStore>(relaxed = true)
        val securityStore = mockk<SecurityStore>(relaxed = true)
        val syncPlayCastStore = mockk<SyncPlayCastStore>(relaxed = true)
        val playbackStore = mockk<PlaybackStore>(relaxed = true)
        val audioStore = mockk<AudioStore>(relaxed = true)
        val audioEffectsStore = mockk<AudioEffectsStore>(relaxed = true)
        val videoPlayerStore = mockk<VideoPlayerStore>(relaxed = true)
        val downloadsStore = mockk<DownloadsStore>(relaxed = true)
        val appearanceStore = mockk<AppearanceStore>(relaxed = true)
        val networkOfflineStore = mockk<NetworkOfflineStore>(relaxed = true)
        val sessionManager = mockk<PlaybackSessionManager>(relaxed = true)
        val castManager = mockk<CastManager>(relaxed = true)
        val jellyfinRemotePlayCastStrategy = mockk<com.raulshma.jellyplay.core.data.cast.remote.JellyfinRemotePlayCastStrategy>(relaxed = true).apply {
            // Relaxed mock's StateFlow<Boolean>.value returns an Object that
            // won't cast to Boolean — stub the "Play On" routing check away.
            every { isConnected } returns MutableStateFlow(false)
        }
        syncPlayManager = mockk(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>(relaxed = true)
        val adaptiveBitrateManager = mockk<AdaptiveBitrateManager>(relaxed = true)
        val networkMonitor = mockk<NetworkMonitor>(relaxed = true).apply {
            every { isMetered } returns MutableStateFlow(false)
        }
        val activePlayerController = mockk<ActivePlayerController>(relaxed = true)
        val playerLifecycleManager = PlayerLifecycleManager(playbackStore)
        val pipController = com.raulshma.jellyplay.core.data.playback.PipController()
        val videoMiniPlayerState = mockk<VideoMiniPlayerState>(relaxed = true)
        val sleepTimerManager = mockk<SleepTimerManager>(relaxed = true)

        // EXTERNAL preferred player short-circuits engine creation (same harness
        // trick as VideoPlayerViewModelTest.initialize_seedsResumePosition...) so
        // the item-switch path is deterministic with no engine bound.
        val externalAggregate = VideoPlayerAggregate(
            playback = com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice(
                preferredPlayer = PlayerType.EXTERNAL,
            )
        )
        every { aggregateStore.aggregate } returns MutableStateFlow(externalAggregate)
        every { aggregateStore.aggregateRaw } returns flowOf(externalAggregate)
        every { engineStore.playerEngine } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice()
        )
        every { subtitleStore.subtitle } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice()
        )
        every { securityStore.security } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.security.SecuritySlice()
        )
        every { syncPlayCastStore.syncPlayCast } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice()
        )
        every { playbackStore.playback } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice()
        )
        every { audioStore.audio } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.audio.AudioSlice()
        )
        every { audioEffectsStore.audioEffects } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice()
        )
        every { videoPlayerStore.videoPlayer } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice()
        )
        every { downloadsStore.downloads } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice()
        )
        every { appearanceStore.appearance } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice()
        )
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(
            com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice()
        )
        every { sleepTimerManager.remainingMs } returns MutableStateFlow(0L)
        val playbackCore = mockk<SyncPlayPlaybackCore>(relaxed = true)
        every { syncPlayManager.playbackCore } returns playbackCore
        every { syncPlayManager.isInSyncPlaySession } returns false
        every { syncPlayManager.currentGroup } returns null
        syncPlayManager.stubEmptyEvents()

        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            MediaDetail(
                item = MediaItem(id = "item-1", name = "Item 1", mediaType = MediaType.MOVIE),
            )
        )
        // No detail for item-2: the load coroutine then skips applyMediaDetail,
        // so the snapshot captures exactly the post-releaseInternals state.
        coEvery { mediaRepository.getMediaDetail("item-2") } returns Result.failure(RuntimeException("no detail"))

        viewModel = VideoPlayerViewModel(
            context = context,
            mediaRepository = mediaRepository,
            playbackRepository = playbackRepository,
            subtitleProviderRepository = mockk(relaxed = true),
            streamingSubtitleStore = noOpStreamingSubtitleStore(),
            imageUrlProvider = imageUrlProvider,
            downloadRepository = downloadRepository,
            offlineRepository = offlineRepository,
            offlinePlaybackFacade = offlinePlaybackFacade,
            playbackSourceResolver = playbackSourceResolver,
            episodeCatalogue = mockk(relaxed = true),
            itemPlaybackPreferenceRepository = itemPlaybackPreferenceRepository,
            aggregateStore = aggregateStore,
            engineStore = engineStore,
            subtitleStore = subtitleStore,
            securityStore = securityStore,
            syncPlayCastStore = syncPlayCastStore,
            playbackStore = playbackStore,
            audioStore = audioStore,
            audioEffectsStore = audioEffectsStore,
            videoPlayerStore = videoPlayerStore,
            downloadsStore = downloadsStore,
            appearanceStore = appearanceStore,
            networkOfflineStore = networkOfflineStore,
            sessionManager = sessionManager,
            castManager = castManager,
            jellyfinRemotePlayCastStrategy = jellyfinRemotePlayCastStrategy,
            syncPlayManager = syncPlayManager,
            okHttpClient = okHttpClient,
            adaptiveBitrateManager = adaptiveBitrateManager,
            networkMonitor = networkMonitor,
            activePlayerController = activePlayerController,
            playerLifecycleManager = playerLifecycleManager,
            pipController = pipController,
            videoMiniPlayerState = videoMiniPlayerState,
            sleepTimerManager = sleepTimerManager,
            userMessageBus = UserMessageBus(),
            playerEngineFactory = com.raulshma.jellyplay.feature.player.video.engine.PlayerEngineFactory(
                context,
                okHttpClient,
                mockk<com.raulshma.jellyplay.feature.player.video.subtitle.FontProvider>(relaxed = true),
            ),
            fontProvider = mockk(relaxed = true),
            savedStateHandle = androidx.lifecycle.SavedStateHandle(),
            subtitlePreviewRepository = mockk(relaxed = true),
            userDataMutator = mockk(relaxed = true),
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun driveSessionMutations() {
        // ── Sleep slice: running timed timer + last-used duration ──
        viewModel.startSleepTimer(15_000L)

        // ── Audio-effects slice: every user effect to a non-default value ──
        viewModel.toggleNightMode()
        viewModel.setNightModeStrength(EffectStrength.HIGH)
        viewModel.setDecoderMode(DecoderMode.SW_ONLY)
        viewModel.setAudioPassthrough(true)
        viewModel.setAudioNormalizationMode(AudioNormalizationMode.TRACK)
        viewModel.setChannelMixMode(ChannelMixMode.SURROUND_UPMIX)
        viewModel.toggleBassBoost()
        viewModel.setBassBoostStrength(EffectStrength.HIGH)
        viewModel.toggleVirtualizer()
        viewModel.setVirtualizerStrength(750)
        viewModel.setReverbPreset(ReverbPreset.LARGE_HALL)
        viewModel.setAudioDelay(250L)

        // ── Dialogue boost (residual UiState, per-item resolver-driven) ──
        viewModel.setDialogueBoostStrength(EffectStrength.HIGH)

        // ── Subtitle-workflow slice: a completed search + remote list ──
        coEvery { playbackRepository.getRemoteSubtitles("item-1") } returns Result.success(
            listOf(RemoteSubtitleInfo(id = "s1", name = "English"))
        )
        viewModel.loadRemoteSubtitles()
        coEvery { playbackRepository.searchRemoteSubtitles("item-1", "eng") } returns Result.success(
            listOf(RemoteSubtitleInfo(id = "os1", name = "OpenSub en"))
        )
        viewModel.searchRemoteSubtitles("eng")

        // ── A/B repeat: armed window ──
        viewModel.setAbRepeatEnabled(true)
        viewModel.seekTo(1_000L)
        viewModel.setAbRepeatPointA()
        viewModel.seekTo(5_000L)
        viewModel.setAbRepeatPointB()

        // ── SyncPlay group display: joined group ──
        viewModel.joinSyncPlay("group-1")

        // ── Residual probes: whitelisted (persists) vs not (resets) ──
        viewModel.setSubtitleStyle(SubtitleStyle(fontSize = 40)) // whitelisted
        viewModel.setPlaybackSpeed(2.0f)                          // not whitelisted
        viewModel.toggleVideoStats()                              // not whitelisted
    }

    @Test
    fun itemSwitch_goldenSnapshot() {
        viewModel.initialize("item-1", null, 0L)
        driveSessionMutations()

        val before = viewModel.uiState.value

        // Sanity: the session mutations actually landed.
        assertTrue(before.dialogueBoostEnabled)
        assertEquals(EffectStrength.HIGH, before.dialogueBoostStrength)
        assertEquals(2.0f, before.playbackSpeed, 0.001f)
        assertTrue(before.showVideoStats)
        assertEquals(40, before.subtitleStyle.fontSize)
        assertTrue(viewModel.sleepTimerState.value.sleepTimerActive)
        assertTrue(viewModel.effectsState.value.nightModeEnabled)
        assertEquals(250L, viewModel.effectsState.value.audioDelayMs)
        assertTrue(viewModel.subtitleState.value.hasSearchedSubtitles)
        assertEquals(1, viewModel.subtitleState.value.remoteSubtitles.size)
        assertTrue(viewModel.abRepeatState.value.isActive)
        assertTrue(viewModel.syncPlayState.value.isInSyncPlaySession)
        assertEquals("group-1", viewModel.syncPlayState.value.syncPlayGroupName)

        // ── The item switch (routes through releaseInternals) ──
        viewModel.initialize("item-2", null, 0L)

        // ── Golden: controller-owned slices ──

        // Sleep timer deliberately PERSISTS (former whitelist lines 3107-3109).
        assertEquals(
            com.raulshma.jellyplay.feature.player.video.state.SleepTimerState(
                sleepTimerActive = true,
                sleepTimerEndOfEpisode = false,
                sleepTimerLastUsedDurationMs = 15_000L,
            ),
            viewModel.sleepTimerState.value,
        )

        // User audio effects PERSIST (former whitelist lines 3094-3106 died;
        // persistence is the default). audioDelayMs nuance: see class KDoc.
        val effects = viewModel.effectsState.value
        assertTrue(effects.nightModeEnabled)
        assertEquals(EffectStrength.HIGH, effects.nightModeStrength)
        assertEquals(DecoderMode.SW_ONLY, effects.decoderMode)
        assertTrue(effects.audioPassthrough)
        assertEquals(AudioNormalizationMode.TRACK, effects.audioNormalizationMode)
        assertTrue(effects.audioNormalizationEnabled)
        assertEquals(ChannelMixMode.SURROUND_UPMIX, effects.channelMixMode)
        assertTrue(effects.channelMixEnabled)
        assertTrue(effects.bassBoostEnabled)
        assertEquals(EffectStrength.HIGH, effects.bassBoostStrength)
        assertTrue(effects.virtualizerEnabled)
        assertEquals(750, effects.virtualizerStrength)
        assertEquals(ReverbPreset.LARGE_HALL, effects.reverbPreset)
        assertEquals(250L, effects.audioDelayMs)

        // Subtitle workflow RESETS (never whitelisted).
        assertEquals(
            com.raulshma.jellyplay.feature.player.video.state.SubtitleState(),
            viewModel.subtitleState.value,
        )

        // Track state RESETS (never whitelisted).
        assertEquals(
            com.raulshma.jellyplay.feature.player.video.state.TrackState(),
            viewModel.trackState.value,
        )

        // SyncPlay group display RESETS (never whitelisted) — the mirror into
        // the residual UiState follows the bridge's state.
        assertEquals(
            com.raulshma.jellyplay.feature.player.video.state.SyncPlayUiState(),
            viewModel.syncPlayState.value,
        )
        assertFalse(viewModel.uiState.value.isInSyncPlaySession)

        // THE ONE INTENDED DIVERGENCE (bug fix): the A/B repeat
        // window resets on item switch. Before, the reset wiped only the
        // UiState mirror while the controller kept its stale points — the loop
        // monitor could seek the next episode back to the previous episode's A
        // point, and one tap resurrected them. Now the single home is cleared.
        assertEquals(AbRepeatState(), viewModel.abRepeatState.value)

        // Dialogue boost (residual, per-item resolver-driven) resets so it
        // can't bleed into the next item before the resolver re-applies.
        assertFalse(viewModel.uiState.value.dialogueBoostEnabled)
        assertEquals(EffectStrength.NONE, viewModel.uiState.value.dialogueBoostStrength)

        // Residual probes.
        assertEquals(40, viewModel.uiState.value.subtitleStyle.fontSize) // whitelisted → persists
        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed, 0.001f) // not whitelisted → resets
        assertFalse(viewModel.uiState.value.showVideoStats)              // not whitelisted → resets

        // ── Golden: residual UiState partition (field-by-field) ──
        assertResidualPartition(before)
    }

    /**
     * The whitelist-equivalence proof: every declared UiState field either
     * carries its pre-switch value ([residualWhitelist]) or resets to the
     * default (modulo [loadRepopulated]). Adding a field to UiState or moving
     * one between homes without updating this partition fails here — the diff
     * IS the review artifact.
     *
     * Java reflection (not kotlin-reflect, which is not a test dependency):
     * a data class's constructor parameters are its declared backing fields.
     */
    private fun assertResidualPartition(before: VideoPlayerUiState) {
        val after = viewModel.uiState.value
        val defaults = VideoPlayerUiState()
        val fields = VideoPlayerUiState::class.java.declaredFields
            .filter { !it.name.startsWith("$") && !java.lang.reflect.Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
        assertTrue("expected a substantial field set", fields.size > 50)

        for (field in fields) {
            val name = field.name
            val afterVal = field.get(after)
            val beforeVal = field.get(before)
            val defaultVal = field.get(defaults)
            when {
                name in residualWhitelist ->
                    assertEquals("whitelisted field $name must persist", beforeVal, afterVal)
                name in loadRepopulated -> Unit // re-populated by the load, not the reset
                name in explicitlyReset ->
                    assertEquals("explicitly re-set field $name", explicitlyReset[name], afterVal)
                else ->
                    assertEquals("non-whitelisted field $name must reset to default", defaultVal, afterVal)
            }
        }
    }

    /**
     * The A/B-repeat bug fix, VM-level: after an item switch the window is gone
     * AND cannot be resurrected by a toggle tap. (The "no seekTo past the old
     * B" half is pinned with a mock engine in
     * `AbRepeatControllerTest.resetForItem clears points and disarms` — this
     * harness binds no engine.)
     */
    @Test
    fun itemSwitch_stopsAbRepeatLoop_andTapCannotResurrect() {
        viewModel.initialize("item-1", null, 0L)
        viewModel.setAbRepeatEnabled(true)
        viewModel.seekTo(1_000L)
        viewModel.setAbRepeatPointA()
        viewModel.seekTo(5_000L)
        viewModel.setAbRepeatPointB()
        assertTrue(viewModel.abRepeatState.value.isActive)

        viewModel.initialize("item-2", null, 0L)

        // Window cleared…
        assertNull(viewModel.abRepeatState.value.aMs)
        assertNull(viewModel.abRepeatState.value.bMs)
        assertFalse(viewModel.abRepeatState.value.isActive)

        // …and a single toggle tap does NOT resurrect the previous episode's
        // points (previous behaviour: the stale mirror came back alive).
        viewModel.setAbRepeatEnabled(true)
        assertNull(viewModel.abRepeatState.value.aMs)
        assertNull(viewModel.abRepeatState.value.bMs)
        assertFalse(viewModel.abRepeatState.value.isActive)
    }
}
