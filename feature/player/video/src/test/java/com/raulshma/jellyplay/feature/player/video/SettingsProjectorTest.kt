package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerAggregate
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.model.SubtitleStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsProjectorTest {

    private var uiState = VideoPlayerUiState()
    private lateinit var projector: SettingsProjector

    @Before
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
            audio = AudioSlice(
                sleepTimerDurationMs = 20_000L,
            ),
            subtitle = SubtitleSlice(
                preferredSubtitleLanguage = "spa",
            ),
            security = SecuritySlice(
                usePinForPlayerLock = true,
                pinHash = "hashed_pin",
            ),
        )

        projector.project(agg)

        assertEquals(20_000L, uiState.sleepTimerLastUsedDurationMs)
        assertTrue(uiState.showPlaybackMetadata)
        assertTrue(uiState.showClock)
        assertTrue(uiState.showTimeRemaining)
        assertEquals(100f, uiState.tvZoomModePercent, 0.001f)
        assertTrue(uiState.keepScreenOnDuringVideo)
        assertEquals(4, uiState.passOutProtectionHours)
        assertEquals(10, uiState.autoPlayCountdownSec)
        assertTrue(uiState.usePinForPlayerLock)
        assertTrue(uiState.hasPin)
        assertEquals("spa", uiState.defaultSearchLanguage)
    }

    @Test
    fun project_returnsTrueWhenSubtitleStyleChanges() {
        val aggInitial = VideoPlayerAggregate()
        projector.project(aggInitial)

        val initialSlice = SubtitleSlice()
        val newStyle = resolveSubtitleStyle(initialSlice, isHdr = false).copy(fontSize = 42)
        val aggUpdated = VideoPlayerAggregate(subtitle = SubtitleSlice(subtitleStyle = newStyle))

        val subtitleStyleChanged = projector.project(aggUpdated)
        assertTrue("Project returns true when subtitle style changes", subtitleStyleChanged)
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
}
