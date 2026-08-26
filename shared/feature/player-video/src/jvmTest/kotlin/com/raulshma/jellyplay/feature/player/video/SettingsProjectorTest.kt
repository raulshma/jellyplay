package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.model.SubtitleStyle
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

class SettingsProjectorTest {

    private var uiState = VideoPlayerUiState()
    private lateinit var projector: SettingsProjector

    @BeforeTest
    fun setUp() {
        uiState = VideoPlayerUiState()
        projector = SettingsProjector(
            getUiState = { uiState },
            updateUiState = { transform -> uiState = transform(uiState) },
            getItemId = { "item-123" },
            getMediaStreams = { emptyList() },
        )
    }

    @Test
    fun project_updatesFieldsFromUserPreferences() {
        val agg = VideoPlayerAggregate(
            playback = PlaybackSlice(
                keepScreenOnDuringVideo = true,
                autoPlayCountdownSec = 10,
            ),
            videoPlayer = VideoPlayerSlice(
                videoShowPlaybackMetadata = true,
                showClockInPlayer = true,
                showTimeRemaining = true,
                tvZoomModePercent = 100f,
                videoPassOutProtectionHours = 4,
            ),
            security = SecuritySlice(
                usePinForPlayerLock = true,
                pinHash = "hashed_pin",
            ),
        )

        projector.project(agg)

        assertTrue(uiState.uiPrefs.showPlaybackMetadata)
        assertTrue(uiState.uiPrefs.showClock)
        assertTrue(uiState.uiPrefs.showTimeRemaining)
        assertEquals(100f, uiState.videoFx.tvZoomModePercent, 0.001f)
        assertTrue(uiState.uiPrefs.keepScreenOnDuringVideo)
        assertEquals(4, uiState.uiPrefs.passOutProtectionHours)
        assertEquals(10, uiState.autoplay.autoPlayCountdownSec)
        assertTrue(uiState.uiPrefs.usePinForPlayerLock)
        assertTrue(uiState.uiPrefs.hasPin)
    }

    // Note: the sleep-timer last-used duration and the Subtitle Manager's
    // default search language were formerly projected here too; their homes
    // moved to SleepTimerController / SubtitleManager and the
    // ViewModel's aggregate collector seeds them — see
    // SleepTimerControllerTest.`seedLastUsedDurationMs updates only when different`
    // and SubtitleManagerTest.`seedDefaultSearchLanguage updates only when different`.

    @Test
    fun project_returnsTrueWhenSubtitleStyleChanges() {
        val aggInitial = VideoPlayerAggregate()
        projector.project(aggInitial)

        val initialSlice = SubtitleSlice()
        val newStyle = resolveSubtitleStyle(initialSlice, isHdr = false).copy(fontSize = 42)
        val aggUpdated = VideoPlayerAggregate(subtitle = SubtitleSlice(subtitleStyle = newStyle))

        val subtitleStyleChanged = projector.project(aggUpdated)
        assertTrue(subtitleStyleChanged, "Project returns true when subtitle style changes")
        assertEquals(42, uiState.subtitleStyle.fontSize)
    }

    @Test
    fun project_noOpsWhenPreferencesAreUnchanged() {
        // UiState seeds subtitleStyle with SubtitleStyle.DEFAULT (applyCustomStyle=true),
        // so build an aggregate whose stored style resolves to the same value. Under the
        // Override-respecting resolver a default aggregate has the toggle off,
        // which would (correctly) read as a change on first project — not the no-op
        // this test pins.
        val agg = VideoPlayerAggregate(subtitle = SubtitleSlice(subtitleStyle = SubtitleStyle.DEFAULT))
        val changed1 = projector.project(agg)
        assertFalse(changed1)

        val changed2 = projector.project(agg)
        assertFalse(changed2)
    }

    @Test
    fun project_preservesPerItemSubtitleDelayOnSliceReemission() {
        // Regression: writing the per-item delay re-emits the subtitle slice. The
        // projector must NOT then overwrite offsetMs with the global style default
        // (0) — that clobber rebuilt the engine config and reloaded media with the
        // delay removed on every fine-tune. The per-item value is authoritative.
        val styleWithDelay = SubtitleStyle.DEFAULT.copy(offsetMs = 500L)
        uiState = uiState.copy(subtitleStyle = styleWithDelay)
        val agg = VideoPlayerAggregate(
            subtitle = SubtitleSlice(
                subtitleStyle = SubtitleStyle.DEFAULT.copy(offsetMs = 0L),
                subtitleDelayByItem = mapOf("item-123" to 500L),
            ),
        )

        val changed = projector.project(agg)

        assertEquals(500L, uiState.subtitleStyle.offsetMs)
        assertFalse(changed, "Per-item delay re-emission must not signal a style change")
    }

    @Test
    fun project_appliesGlobalDefaultDelayWhenNoPerItemEntry() {
        // With no per-item override, the global "Subtitle sync offset" default
        // should still apply through projection (and signal a change so the engine
        // picks it up).
        uiState = uiState.copy(subtitleStyle = SubtitleStyle.DEFAULT.copy(offsetMs = 0L))
        val agg = VideoPlayerAggregate(
            subtitle = SubtitleSlice(
                subtitleStyle = SubtitleStyle.DEFAULT.copy(offsetMs = 250L),
            ),
        )

        val changed = projector.project(agg)

        assertEquals(250L, uiState.subtitleStyle.offsetMs)
        assertTrue(changed)
    }
}
