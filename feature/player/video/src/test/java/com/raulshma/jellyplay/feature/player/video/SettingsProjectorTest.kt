package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.SubtitleStyle
import com.raulshma.jellyplay.core.model.UserPreferences
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
        val prefs = UserPreferences(
            sleepTimerDurationMs = 20_000L,
            videoShowPlaybackMetadata = true,
            showClockInPlayer = true,
            showTimeRemaining = true,
            tvZoomModePercent = 100f,
            keepScreenOnDuringVideo = true,
            videoPassOutProtectionHours = 4,
            autoPlayCountdownSec = 10,
            usePinForPlayerLock = true,
            pinHash = "hashed_pin",
            preferredSubtitleLanguage = "spa",
        )

        projector.project(prefs)

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
        val prefsInitial = UserPreferences()
        projector.project(prefsInitial)

        val newStyle = prefsInitial.resolvedSubtitleStyle(isHdr = false).copy(fontSize = 42)
        val prefsUpdated = prefsInitial.copy(subtitleStyle = newStyle)

        val subtitleStyleChanged = projector.project(prefsUpdated)
        assertTrue("Project returns true when subtitle style changes", subtitleStyleChanged)
        assertEquals(42, uiState.subtitleStyle.fontSize)
    }

    @Test
    fun project_noOpsWhenPreferencesAreUnchanged() {
        // UiState seeds subtitleStyle with SubtitleStyle.DEFAULT (applyCustomStyle=true),
        // so build prefs whose stored style resolves to the same value. Under the
        // Override-respecting resolver a default UserPreferences() has the toggle off,
        // which would (correctly) read as a change on first project — not the no-op
        // this test pins.
        val prefs = UserPreferences(subtitleStyle = SubtitleStyle.DEFAULT)
        val changed1 = projector.project(prefs)
        assertFalse(changed1)

        val changed2 = projector.project(prefs)
        assertFalse(changed2)
    }
}
