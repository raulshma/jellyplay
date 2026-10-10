package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.model.EffectStrength
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlin.test.Test

/**
 * The VideoPlayerViewModel behavior surface — the pre-KMP
 * `feature/player/video` tree's suite reinstated against the KMP VM: the
 * command funs became [VideoPlayerUiEvent]s on the [VideoPlayerViewModel.onEvent]
 * funnel (the ownership ratchet), the stores bundled into [PlayerStores], the
 * platform seams collapsed behind [VideoPlayerPlatform]. Construction lives
 * in [VideoPlayerViewModelTestHarness]; the EXTERNAL preferred-player
 * short-circuit keeps every load path engine-free, exactly as the original
 * harness did.
 *
 * Descoped from the original suite (recorded, not silently dropped):
 *  - the reflection pokes into `playbackSession.getReportPositionMs` and the
 *    `resolveOfflineResumeTicks` pass-through pins — that member moved INTO
 *    the session/resolver and is pinned there now
 *    (PlaybackSessionResumeTicksTest / PlaybackSourceTest);
 *  - the next-episode single-flight latch tests (#146) re-land against
 *    [EpisodeContinuationControllerTest]'s controller-level pinning; the
 *    latch's happy path is re-pinned here once at the VM level.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VideoPlayerViewModelTest : VideoPlayerViewModelHarness() {

    // ── Seek → display position ─────────────────────────────────────────────

    @Test
    fun seekTo_updatesCurrentPosition() {
        viewModel.onEvent(VideoPlayerUiEvent.SeekTo(5_000L))
        assertEquals(5_000L, viewModel.currentPositionMs.value)
    }

    @Test
    fun seekTo_multipleCalls_keepsLatestPosition() {
        viewModel.onEvent(VideoPlayerUiEvent.SeekTo(1_000L))
        viewModel.onEvent(VideoPlayerUiEvent.SeekTo(2_000L))
        viewModel.onEvent(VideoPlayerUiEvent.SeekTo(3_000L))
        assertEquals(3_000L, viewModel.currentPositionMs.value)
    }

    // ── Resume seeding (#122 regression: seed before the first engine tick) ─

    @Test
    fun initialize_seedsResumePositionAndDurationBeforeEngineTick() {
        setExternalPlayer()
        val resumeTicks = 10_290_000_000L // 17:09, the reporter's example
        val runtimeTicks = 54_000_000_000L // 90 min total runtime
        // The pipeline seeds the playhead from the RESOLVED ticks; explicit
        // nonzero ticks pass through the resolver unchanged (its real rule,
        // pinned by PlaybackSourceTest).
        coEvery {
            playbackSourceResolver.resolveStartPositionTicks("item-1", resumeTicks)
        } returns resumeTicks
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            itemDetail("item-1", "Test Movie", runtimeTicks)
        )

        viewModel.onEvent(VideoPlayerUiEvent.Initialize("item-1", startPositionTicks = resumeTicks))

        // Position is seeded synchronously at load — before any engine tick.
        assertEquals(resumeTicks / 10_000, viewModel.currentPositionMs.value)
        // Duration is seeded from the resolved item's runTimeTicks once loaded.
        assertEquals(runtimeTicks / 10_000, viewModel.durationMs.value)
        // The loading screen lifts once the load completes, so the seek bar's
        // first paint (with both values seeded) is the resume fraction.
        assertFalse(viewModel.uiState.value.isInitializing)
    }

    @Test
    fun initialize_zeroRequestTicksWithOfflineResume_seedsResolvedPlayhead() {
        setExternalPlayer()
        val storedTicks = 5L * 60L * 1000L * 10_000L // 5 min resume in the offline mirror
        coEvery { playbackSourceResolver.resolveStartPositionTicks("item-1", 0L) } returns storedTicks
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            itemDetail("item-1", "Test Movie", 54_000_000_000L)
        )

        viewModel.onEvent(VideoPlayerUiEvent.Initialize("item-1"))

        assertEquals(storedTicks / 10_000, viewModel.currentPositionMs.value)
        assertFalse(viewModel.uiState.value.isInitializing)
    }

    // ── Playback speed (incl. the hold-to-speed gesture) ────────────────────

    @Test
    fun setPlaybackSpeed_updatesState() {
        viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackSpeed(1.5f))
        assertEquals(1.5f, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun setPlaybackSpeed_zeroOrNegative_stillSetsState() {
        viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackSpeed(0f))
        assertEquals(0f, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun startHoldSpeed_activatesAndStoresHoldMultiplier() {
        viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackSpeed(1.0f))
        viewModel.onEvent(VideoPlayerUiEvent.StartHoldSpeed)
        assertTrue(viewModel.uiState.value.gestures.isHoldSpeedActive)
        assertEquals(viewModel.uiState.value.gestures.holdSpeedMultiplier, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun startHoldSpeed_whenAlreadyActive_isIdempotent() {
        viewModel.onEvent(VideoPlayerUiEvent.StartHoldSpeed)
        val first = viewModel.uiState.value.playbackSpeed
        viewModel.onEvent(VideoPlayerUiEvent.StartHoldSpeed)
        assertTrue(viewModel.uiState.value.gestures.isHoldSpeedActive)
        assertEquals(first, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun stopHoldSpeed_restoresPreviousSpeed() {
        viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackSpeed(1.25f))
        viewModel.onEvent(VideoPlayerUiEvent.StartHoldSpeed)
        assertTrue(viewModel.uiState.value.gestures.isHoldSpeedActive)

        viewModel.onEvent(VideoPlayerUiEvent.StopHoldSpeed)
        assertFalse(viewModel.uiState.value.gestures.isHoldSpeedActive)
        assertEquals(1.25f, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    @Test
    fun stopHoldSpeed_whenNotActive_isNoOp() {
        viewModel.onEvent(VideoPlayerUiEvent.StopHoldSpeed)
        assertFalse(viewModel.uiState.value.gestures.isHoldSpeedActive)
    }

    // ── Aspect ratio ────────────────────────────────────────────────────────

    @Test
    fun setAspectRatio_updatesStateExplicitRatio() {
        viewModel.onEvent(VideoPlayerUiEvent.SetAspectRatio(AspectRatio.RATIO_21_9))
        assertEquals(AspectRatio.RATIO_21_9, viewModel.uiState.value.videoFx.aspectRatio)
    }

    @Test
    fun setAspectRatio_off_doesNotMutateDetected() {
        viewModel.onEvent(VideoPlayerUiEvent.SetAspectRatio(AspectRatio.FIT))
        assertEquals(AspectRatio.FIT, viewModel.uiState.value.videoFx.aspectRatio)
    }

    // ── Dialogue boost ──────────────────────────────────────────────────────

    @Test
    fun toggleDialogueBoost_flipsEnabled() {
        val before = viewModel.uiState.value.dialogueBoostEnabled
        viewModel.onEvent(VideoPlayerUiEvent.ToggleDialogueBoost)
        assertEquals(!before, viewModel.uiState.value.dialogueBoostEnabled)
        viewModel.onEvent(VideoPlayerUiEvent.ToggleDialogueBoost)
        assertEquals(before, viewModel.uiState.value.dialogueBoostEnabled)
    }

    @Test
    fun setDialogueBoostStrength_updatesState() {
        viewModel.onEvent(VideoPlayerUiEvent.SetDialogueBoostStrength(EffectStrength.HIGH))
        assertEquals(EffectStrength.HIGH, viewModel.uiState.value.dialogueBoostStrength)
    }

    // ── Audio effects + playback mode + stats overlays ──────────────────────

    @Test
    fun toggleNightMode_flipsEnabled() {
        val before = viewModel.playbackSession.effects.state.value.nightModeEnabled
        viewModel.playbackSession.effects.toggleNightMode()
        assertEquals(!before, viewModel.playbackSession.effects.state.value.nightModeEnabled)
    }

    @Test
    fun setNightModeStrength_updatesState() {
        viewModel.playbackSession.effects.setNightModeStrength(EffectStrength.HIGH)
        assertEquals(EffectStrength.HIGH, viewModel.playbackSession.effects.state.value.nightModeStrength)
    }

    @Test
    fun setPlaybackMode_updatesState() {
        val target = if (viewModel.uiState.value.uiPrefs.playbackMode == PlaybackMode.FORCE_TRANSCODE)
            PlaybackMode.FORCE_DIRECT_PLAY else PlaybackMode.FORCE_TRANSCODE
        viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackMode(target))
        assertEquals(target, viewModel.uiState.value.uiPrefs.playbackMode)
    }

    @Test
    fun toggleVideoStats_flipsEnabled() {
        val before = viewModel.uiState.value.uiPrefs.showVideoStats
        viewModel.onEvent(VideoPlayerUiEvent.ToggleVideoStats)
        assertEquals(!before, viewModel.uiState.value.uiPrefs.showVideoStats)
    }

    // ── Release ─────────────────────────────────────────────────────────────

    @Test
    fun release_resetsUiState() {
        viewModel.onEvent(VideoPlayerUiEvent.SetPlaybackSpeed(2.0f))
        viewModel.onEvent(VideoPlayerUiEvent.SeekTo(9_000L))

        viewModel.release()

        assertTrue(viewModel.uiState.value.title.isEmpty())
        assertEquals(1.0f, viewModel.uiState.value.playbackSpeed, 0.001f)
    }

    // ── Next-episode single-flight latch (#146, VM level once) ──────────────

    /** Loads "item-1" (a series episode) via the EXTERNAL fast path so the
     *  VM has a mediaDetail with series/season ids and a bound currentItemId. */
    private fun loadCurrentEpisodeExternally() {
        setExternalPlayer()
        coEvery { playbackSourceResolver.resolveStartPositionTicks("item-1", 0L) } returns 0L
        coEvery { mediaRepository.getMediaDetail("item-1") } returns Result.success(
            MediaDetail(
                item = MediaItem(
                    id = "item-1",
                    name = "Episode One",
                    mediaType = com.raulshma.jellyplay.core.model.MediaType.EPISODE,
                    seriesId = "series-1",
                    seasonId = "season-1",
                ),
            )
        )
        viewModel.onEvent(VideoPlayerUiEvent.Initialize("item-1"))
    }

    @Test
    fun playNextEpisode_whileLoadInFlight_ignoresRetaps() {
        loadCurrentEpisodeExternally()
        // The resolution hangs (the offline dead-air case from #146): the
        // request never completes, so the latch must stay held and every
        // re-tap during the window must be swallowed, not queued.
        var resolutionCalls = 0
        val hang = CompletableDeferred<Result<List<MediaItem>>>()
        coEvery { episodeCatalogue.loadSeasonEpisodes(any(), any(), any()) } coAnswers {
            resolutionCalls++
            hang.await()
        }

        viewModel.onEvent(VideoPlayerUiEvent.PlayNextEpisode)
        assertEquals(1, resolutionCalls)
        viewModel.onEvent(VideoPlayerUiEvent.PlayNextEpisode)
        viewModel.onEvent(VideoPlayerUiEvent.PlayNextEpisode)

        // Retaps must not spawn duplicate resolutions…
        assertEquals(1, resolutionCalls)
        // …nor reach mark-played / initialize behind them.
        coVerify(exactly = 0) { userDataMutator.setPlayed(any(), any()) }
        assertTrue(viewModel.isNextEpisodeLoading.value)
    }

    @Test
    fun playNextEpisode_whenResolutionFails_clearsLatch() {
        loadCurrentEpisodeExternally()
        coEvery { episodeCatalogue.loadSeasonEpisodes(any(), any(), any()) } returns
            Result.failure(java.io.IOException("dead air"))

        viewModel.onEvent(VideoPlayerUiEvent.PlayNextEpisode)

        assertFalse(viewModel.isNextEpisodeLoading.value)
    }

    @Test
    fun playNextEpisode_happyPath_latchHeldUntilSessionSettles_thenReleases() {
        loadCurrentEpisodeExternally()
        coEvery { episodeCatalogue.loadSeasonEpisodes(any(), any(), any()) } returns
            Result.success(
                listOf(
                    MediaItem(id = "item-1", name = "Episode One", mediaType = com.raulshma.jellyplay.core.model.MediaType.EPISODE),
                    MediaItem(id = "item-2", name = "Episode Two", mediaType = com.raulshma.jellyplay.core.model.MediaType.EPISODE),
                )
            )
        // Park the pipeline's playhead seed — the last stage before loadMedia.
        // Resolution has finished and initialize has returned, but the session
        // has not moved yet: this is the window the latch exists for.
        val seedGate = CompletableDeferred<Long>()
        coEvery {
            playbackSourceResolver.resolveStartPositionTicks("item-2", 0L)
        } coAnswers { seedGate.await() }
        coEvery { mediaRepository.getMediaDetail("item-2") } returns Result.success(
            itemDetail("item-2", "Episode Two")
        )

        viewModel.onEvent(VideoPlayerUiEvent.PlayNextEpisode)

        // Held while the next load is still unsettled — not released merely
        // because resolution + initialize returned.
        assertTrue(viewModel.isNextEpisodeLoading.value)

        // Settle the session onto item-2 (loadMedia flips currentItemId)…
        seedGate.complete(0L)
        testDispatcher.scheduler.runCurrent()

        // …and only then does the latch release. The current item was also
        // marked played on the way, exactly once.
        assertFalse(viewModel.isNextEpisodeLoading.value)
        coVerify(exactly = 1) {
            userDataMutator.setPlayed("item-1", played = true)
        }
    }
}
