package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.model.GestureIndicatorSide
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaSegmentType
import com.raulshma.jellyplay.core.model.OrientationMode
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.RefreshRateMode
import com.raulshma.jellyplay.core.model.SegmentBehavior
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.feature.player.video.engine.AspectRatio
import com.raulshma.jellyplay.feature.player.video.state.AutoplayState
import com.raulshma.jellyplay.feature.player.video.state.EpisodeBrowserState
import com.raulshma.jellyplay.feature.player.video.state.GesturePrefsState
import com.raulshma.jellyplay.feature.player.video.state.PlayerUiPrefsState
import com.raulshma.jellyplay.feature.player.video.state.SegmentState
import com.raulshma.jellyplay.feature.player.video.state.VideoFxState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the [PlayerPrefsSeed] aggregate → uiState mapping: which pref feeds
 * which seeded uiState leaf at session-load time. Extracted from
 * [SessionLoadPipeline]'s inline `onPrefsProjected { copy(...) }`; these tests
 * make the 30-leaf inventory explicit so a leaf cannot silently move, drop, or
 * change default without this file noticing (the change-time counterpart of
 * the same vocabulary lives in [SettingsProjector]).
 *
 * [baseState] deliberately differs from the defaults for every seeded leaf and
 * carries sentinel values on leaves the seed must NOT touch
 * (`isHoldSpeedActive`, `autoplayCancelled`, `detectedAspectRatio`,
 * `segments`, `isLoadingEpisodes`, title/position/subtitleStyle…), so
 * "exactly the expected leaves changed" is checkable per test.
 */
class PlayerPrefsSeedTest {

    // ── Fixtures ─────────────────────────────────────────────────────────────

    /**
     * A receiver whose seeded leaves all differ from what
     * [representativeAgg] should project onto it, plus sentinels on
     * unseeded leaves.
     */
    private fun baseState() = VideoPlayerUiState(
        title = "Persisted Title",
        currentPosition = 42L,
        subtitleStyle = SubtitleStyle.DEFAULT.copy(offsetMs = 123L),
        preferredPlayerType = PlayerType.EXTERNAL,
        uiPrefs = PlayerUiPrefsState(
            defaultOrientation = OrientationMode.SENSOR,
            controlsTimeoutMs = 1_000L,
            passOutProtectionHours = 2,
            trickplayEnabled = true,
            trickplayOnSeekGesture = true,
            showPlaybackMetadata = true,
            showClock = false,
            showTimeRemaining = false,
            keepScreenOnDuringVideo = true,
            streamingQuality = StreamingQuality.LOW_360P,
            adaptiveBitrateEnabled = false,
            playbackMode = PlaybackMode.FORCE_TRANSCODE,
        ),
        gestures = GesturePrefsState(
            gesturesEnabled = true,
            holdSpeedEnabled = true,
            holdSpeedMultiplier = 2.0f,
            isHoldSpeedActive = true,
            defaultSpeed = 0.5f,
            swipeSeekMaxMs = 90_000L,
            seekDurationMs = 5_000L,
            rememberBrightness = true,
            brightnessLevel = 0.1f,
            gestureIndicatorSide = GestureIndicatorSide.OPPOSITE,
            frameRateMatching = false,
            refreshRateMode = RefreshRateMode.OFF,
        ),
        segmentState = SegmentState(
            segments = listOf(
                MediaSegment(
                    id = "seg-1",
                    itemId = "item-1",
                    type = MediaSegmentType.INTRO,
                    startTicks = 0L,
                    endTicks = 10_000L,
                ),
            ),
            segmentBehaviors = mapOf(
                MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON,
                MediaSegmentType.OUTRO to SegmentBehavior.SHOW_BUTTON,
                MediaSegmentType.COMMERCIAL to SegmentBehavior.IGNORE,
            ),
        ),
        videoFx = VideoFxState(
            aspectRatio = AspectRatio.FIT,
            detectedAspectRatio = AspectRatio.RATIO_4_3,
            tvZoomModePercent = 10f,
        ),
        autoplay = AutoplayState(
            videoAutoplayNext = true,
            autoPlayCountdownSec = 5,
            autoplayCancelled = true,
        ),
        episodes = EpisodeBrowserState(
            videoEpisodeBrowserEnabled = true,
            isLoadingEpisodes = true,
        ),
    )

    /**
     * A non-default aggregate covering every seeded pref. The segment-behavior
     * base map mirrors [baseState]'s so the auto-skip layering is observable
     * per type.
     */
    private fun representativeAgg(
        autoSkipIntro: Boolean = true,
        autoSkipOutro: Boolean = true,
    ) = VideoPlayerAggregate(
        playback = PlaybackSlice(
            preferredPlayer = PlayerType.MPV,
            streamingQuality = StreamingQuality.UHD_4K,
            playbackMode = PlaybackMode.FORCE_DIRECT_PLAY,
            keepScreenOnDuringVideo = false,
            frameRateMatching = true,
            refreshRateMode = RefreshRateMode.FRAME_RATE_AND_RESOLUTION,
            autoPlayCountdownSec = 30,
        ),
        videoPlayer = VideoPlayerSlice(
            videoDefaultOrientation = OrientationMode.LOCKED_PORTRAIT,
            videoControlsTimeoutMs = 7_500L,
            videoPassOutProtectionHours = 6,
            trickplayEnabled = false,
            trickplayOnSeekGesture = false,
            videoShowPlaybackMetadata = false,
            showClockInPlayer = true,
            showTimeRemaining = true,
            videoGesturesEnabled = false,
            videoHoldSpeedEnabled = false,
            videoHoldSpeedMultiplier = 3.0f,
            videoDefaultSpeed = 1.25f,
            videoSwipeSeekMaxMs = 60_000L,
            videoSeekDurationMs = 15_000L,
            videoRememberBrightness = false,
            videoBrightnessLevel = 0.8f,
            videoGestureIndicatorSide = GestureIndicatorSide.SAME,
            videoEpisodeBrowserEnabled = false,
            videoAutoplayNext = false,
            videoAutoSkipIntro = autoSkipIntro,
            videoAutoSkipOutro = autoSkipOutro,
            tvZoomModePercent = 25f,
            videoDefaultAspectRatio = "16:9",
            segmentBehaviors = mapOf(
                MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON,
                MediaSegmentType.OUTRO to SegmentBehavior.SHOW_BUTTON,
                MediaSegmentType.COMMERCIAL to SegmentBehavior.IGNORE,
            ),
        ),
    )

    private fun seed(
        agg: VideoPlayerAggregate,
        adaptiveBitrateEnabled: Boolean = true,
    ): (VideoPlayerUiState) -> VideoPlayerUiState =
        PlayerPrefsSeed.seededProjection(agg, adaptiveBitrateEnabled)

    // ── Full-leaf inventory ──────────────────────────────────────────────────

    @Test
    fun representativeAggregate_seedsAllThirtyLeaves_exactly() {
        val base = baseState()
        val seeded = seed(representativeAgg())(base)

        // root (1)
        assertEquals(PlayerType.MPV, seeded.preferredPlayerType)

        // uiPrefs (12)
        assertEquals(OrientationMode.LOCKED_PORTRAIT, seeded.uiPrefs.defaultOrientation)
        assertEquals(7_500L, seeded.uiPrefs.controlsTimeoutMs)
        assertEquals(6, seeded.uiPrefs.passOutProtectionHours)
        assertFalse(seeded.uiPrefs.trickplayEnabled)
        assertFalse(seeded.uiPrefs.trickplayOnSeekGesture)
        assertFalse(seeded.uiPrefs.showPlaybackMetadata)
        assertTrue(seeded.uiPrefs.showClock)
        assertTrue(seeded.uiPrefs.showTimeRemaining)
        assertFalse(seeded.uiPrefs.keepScreenOnDuringVideo)
        assertEquals(StreamingQuality.UHD_4K, seeded.uiPrefs.streamingQuality)
        assertTrue(seeded.uiPrefs.adaptiveBitrateEnabled)
        assertEquals(PlaybackMode.FORCE_DIRECT_PLAY, seeded.uiPrefs.playbackMode)

        // gestures (11)
        val g = seeded.gestures
        assertFalse(g.gesturesEnabled)
        assertFalse(g.holdSpeedEnabled)
        assertEquals(3.0f, g.holdSpeedMultiplier, 0.0001f)
        assertEquals(1.25f, g.defaultSpeed, 0.0001f)
        assertEquals(60_000L, g.swipeSeekMaxMs)
        assertEquals(15_000L, g.seekDurationMs)
        assertFalse(g.rememberBrightness)
        assertEquals(0.8f, g.brightnessLevel, 0.0001f)
        assertEquals(GestureIndicatorSide.SAME, g.gestureIndicatorSide)
        assertTrue(g.frameRateMatching)
        assertEquals(RefreshRateMode.FRAME_RATE_AND_RESOLUTION, g.refreshRateMode)

        // videoFx (2)
        assertEquals(AspectRatio.RATIO_16_9, seeded.videoFx.aspectRatio)
        assertEquals(25f, seeded.videoFx.tvZoomModePercent, 0.0001f)

        // segmentState (1)
        assertEquals(
            mapOf(
                MediaSegmentType.INTRO to SegmentBehavior.AUTO_SKIP,
                MediaSegmentType.OUTRO to SegmentBehavior.AUTO_SKIP,
                MediaSegmentType.COMMERCIAL to SegmentBehavior.IGNORE,
            ),
            seeded.segmentState.segmentBehaviors,
        )

        // episodes (1)
        assertFalse(seeded.episodes.videoEpisodeBrowserEnabled)

        // autoplay (2)
        assertFalse(seeded.autoplay.videoAutoplayNext)
        assertEquals(30, seeded.autoplay.autoPlayCountdownSec)

        // ── NOT seeded: session state + controller-owned leaves survive. ──
        assertEquals("Persisted Title", seeded.title)
        assertEquals(42L, seeded.currentPosition)
        assertEquals(123L, seeded.subtitleStyle.offsetMs)
        assertTrue(seeded.gestures.isHoldSpeedActive, "hold-speed activity is session state")
        assertTrue(seeded.autoplay.autoplayCancelled, "the user's cancellation is not a pref")
        assertEquals(AspectRatio.RATIO_4_3, seeded.videoFx.detectedAspectRatio)
        assertEquals(base.segmentState.segments, seeded.segmentState.segments)
        assertTrue(seeded.episodes.isLoadingEpisodes)
        // PIN leaves are projected by SettingsProjector only, not by the seed.
        assertFalse(seeded.uiPrefs.usePinForPlayerLock)
        assertFalse(seeded.uiPrefs.hasPin)
        assertNull(seeded.uiPrefs.trickplayInfo)
        assertFalse(seeded.uiPrefs.showVideoStats)
    }

    // ── Aspect-ratio string parsing ──────────────────────────────────────────

    @Test
    fun aspectRatioRawString_mapsOntoEngineEnum() {
        fun seededRatio(raw: String): AspectRatio {
            val agg = VideoPlayerAggregate(
                videoPlayer = VideoPlayerSlice(videoDefaultAspectRatio = raw),
            )
            return seed(agg)(baseState()).videoFx.aspectRatio
        }

        assertEquals(AspectRatio.FIT, seededRatio("FIT"))
        assertEquals(AspectRatio.FILL, seededRatio("FILL"))
        assertEquals(AspectRatio.CROP, seededRatio("CROP"))
        assertEquals(AspectRatio.RATIO_16_9, seededRatio("16:9"))
        assertEquals(AspectRatio.RATIO_4_3, seededRatio("4:3"))
        assertEquals(AspectRatio.RATIO_21_9, seededRatio("21:9"))
        // AUTO and anything unknown degrade to AUTO — never a wrong fixed ratio.
        assertEquals(AspectRatio.AUTO, seededRatio("AUTO"))
        assertEquals(AspectRatio.AUTO, seededRatio("SURROUND"))
        assertEquals(AspectRatio.AUTO, seededRatio(""))
    }

    // ── Segment auto-skip layering ───────────────────────────────────────────

    @Test
    fun autoSkipFlags_layerOntoConfiguredBaseBehaviors() {
        fun behaviors(intro: Boolean, outro: Boolean) =
            seed(representativeAgg(autoSkipIntro = intro, autoSkipOutro = outro))(
                baseState(),
            ).segmentState.segmentBehaviors

        // Both off: the configured base map passes through verbatim.
        assertEquals(
            mapOf(
                MediaSegmentType.INTRO to SegmentBehavior.SHOW_BUTTON,
                MediaSegmentType.OUTRO to SegmentBehavior.SHOW_BUTTON,
                MediaSegmentType.COMMERCIAL to SegmentBehavior.IGNORE,
            ),
            behaviors(intro = false, outro = false),
        )
        // Intro only: INTRO forced to AUTO_SKIP, OUTRO keeps its base behavior.
        assertEquals(
            SegmentBehavior.AUTO_SKIP,
            behaviors(intro = true, outro = false)[MediaSegmentType.INTRO],
        )
        assertEquals(
            SegmentBehavior.SHOW_BUTTON,
            behaviors(intro = true, outro = false)[MediaSegmentType.OUTRO],
        )
        // Outro only: OUTRO forced, INTRO keeps its base behavior.
        assertEquals(
            SegmentBehavior.SHOW_BUTTON,
            behaviors(intro = false, outro = true)[MediaSegmentType.INTRO],
        )
        assertEquals(
            SegmentBehavior.AUTO_SKIP,
            behaviors(intro = false, outro = true)[MediaSegmentType.OUTRO],
        )
        // Both on: forced, and the non-intro/outro entry is never touched.
        assertEquals(
            SegmentBehavior.IGNORE,
            behaviors(intro = true, outro = true)[MediaSegmentType.COMMERCIAL],
        )
    }

    // ── Gestures / autoplay / mode combinations ─────────────────────────────

    @Test
    fun gesturesOff_autoplayOff_seedTheDisabledLeaves() {
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(
                playbackMode = PlaybackMode.FORCE_DIRECT_PLAY,
                streamingQuality = StreamingQuality.SD_480P,
            ),
            videoPlayer = VideoPlayerSlice(
                videoGesturesEnabled = false,
                videoHoldSpeedEnabled = false,
                videoAutoplayNext = false,
            ),
        )

        val seeded = seed(agg)(baseState())

        assertFalse(seeded.gestures.gesturesEnabled)
        assertFalse(seeded.gestures.holdSpeedEnabled)
        assertFalse(seeded.autoplay.videoAutoplayNext)
        assertEquals(PlaybackMode.FORCE_DIRECT_PLAY, seeded.uiPrefs.playbackMode)
        assertEquals(StreamingQuality.SD_480P, seeded.uiPrefs.streamingQuality)
    }

    @Test
    fun gesturesOn_autoplayOn_seedOverAnAllOffReceiver() {
        val base = baseState()
        val offReceiver = base.copy(
            gestures = base.gestures.copy(gesturesEnabled = false, holdSpeedEnabled = false),
            autoplay = base.autoplay.copy(videoAutoplayNext = false),
        )
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(playbackMode = PlaybackMode.AUTO),
            videoPlayer = VideoPlayerSlice(
                videoGesturesEnabled = true,
                videoHoldSpeedEnabled = true,
                videoAutoplayNext = true,
            ),
        )

        val seeded = seed(agg)(offReceiver)

        assertTrue(seeded.gestures.gesturesEnabled)
        assertTrue(seeded.gestures.holdSpeedEnabled)
        assertTrue(seeded.autoplay.videoAutoplayNext)
        assertEquals(PlaybackMode.AUTO, seeded.uiPrefs.playbackMode)
    }

    @Test
    fun countdownSec_comesFromPlaybackSlice_notVideoPlayerSlice() {
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(autoPlayCountdownSec = 45),
            videoPlayer = VideoPlayerSlice(videoAutoplayNext = false),
        )

        val seeded = seed(agg)(baseState())

        assertEquals(45, seeded.autoplay.autoPlayCountdownSec)
        assertFalse(seeded.autoplay.videoAutoplayNext)
    }

    // ── The injected network flag ────────────────────────────────────────────

    @Test
    fun adaptiveBitrateParameter_feedsTheUiPrefsLeaf() {
        val agg = VideoPlayerAggregate()

        assertTrue(seed(agg, adaptiveBitrateEnabled = true)(baseState()).uiPrefs.adaptiveBitrateEnabled)
        assertFalse(seed(agg, adaptiveBitrateEnabled = false)(baseState()).uiPrefs.adaptiveBitrateEnabled)
    }

    // ── Purity ───────────────────────────────────────────────────────────────

    @Test
    fun transform_isRebuildable_andIdempotent_overItsOwnResult() {
        val agg = representativeAgg()

        val once = seed(agg)(baseState())
        val twice = seed(agg)(once)

        assertEquals(once, twice, "the seed reads only prefs + seeded leaves, so re-applying is a no-op")

        // A freshly built projection from the same aggregate is equal — the
        // seed holds no hidden session state between builds.
        assertEquals(once, seed(agg)(baseState()))
    }
}
