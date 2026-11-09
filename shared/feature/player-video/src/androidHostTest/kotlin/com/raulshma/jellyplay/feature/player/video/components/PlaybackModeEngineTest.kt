package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
// assertDoesNotExist() is a member of SemanticsNodeInteraction — no import
// (the former top-level extension import stopped resolving under the current
// compose-ui-test and broke the whole androidHostTest lane).
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.model.PlaybackMode
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.StreamingQuality
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackModeEngineTest {
    @get:Rule val composeTestRule = createComposeRule()

    private fun setContent(
        currentMode: PlaybackMode = PlaybackMode.AUTO,
        qualityAvailable: Boolean = true,
        currentEngineName: String? = null,
        availablePlayers: List<PlayerType> = emptyList(),
        onPlayerSelect: (PlayerType) -> Unit = {},
        onQualitySelect: (StreamingQuality) -> Unit = {},
        onDismiss: () -> Unit = {},
        isTv: Boolean = false,
        performanceAvailable: Boolean = false,
    ) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalTvMode provides isTv) {
                MaterialTheme {
                    PlaybackModeSheet(
                        currentMode = currentMode,
                        currentPlayMethod = "Transcode",
                        onSelect = {},
                        onDismiss = onDismiss,
                        currentQuality = StreamingQuality.HD_720P,
                        adaptiveBitrateEnabled = true,
                        onQualitySelect = onQualitySelect,
                        onToggleAdaptiveBitrate = {},
                        qualityAvailable = qualityAvailable,
                        currentEngineName = currentEngineName,
                        availablePlayers = availablePlayers,
                        onPlayerSelect = onPlayerSelect,
                        performanceAvailable = performanceAvailable,
                        performanceLevel = if (performanceAvailable) PerformanceLevel.BALANCED else null,
                    )
                }
            }
        }
    }

    @Test
    fun unavailableQuality_rendersTheHelpTextInsteadOfChips() {
        setContent(qualityAvailable = false)
        composeTestRule.onNodeWithText("720p").assertDoesNotExist()
    }

    @Test
    fun transcodeQuality_dispatchesWithoutClosingTheOptions() {
        var quality: StreamingQuality? = null
        var dismissed = false
        setContent(onQualitySelect = { quality = it }, onDismiss = { dismissed = true })
        composeTestRule.onNodeWithText("720p").performScrollTo().performClick()
        assertEquals(StreamingQuality.HD_720P, quality)
        assertFalse(dismissed)
    }

    @Test
    fun selectingAnotherEngine_dispatchesTheChoiceAndKeepsOptionsOpen() {
        var selected: PlayerType? = null
        var dismissed = false
        setContent(
            currentEngineName = "ExoPlayer",
            availablePlayers = listOf(PlayerType.MPV),
            onPlayerSelect = { selected = it },
            onDismiss = { dismissed = true },
        )
        composeTestRule.onNodeWithText("mpv").performScrollTo().performClick()
        assertEquals(PlayerType.MPV, selected)
        assertFalse(dismissed)
    }

    @Test
    fun deliveryModeSelect_keepsTheSheetOpen() {
        var dismissed = false
        setContent(onDismiss = { dismissed = true })
        composeTestRule.onNodeWithText("Force Direct Play").performScrollTo().performClick()
        assertFalse(dismissed)
    }

    /**
     * Regression for the TV crash when opening the play-method badge: the TV
     * body used to keep several LazyColumns inside the scaffold's
     * `verticalScroll` column, which measures them with infinite max height
     * ("Vertically scrollable component was measured with an infinity maximum
     * height constraints"). The TV body is now one bounded LazyColumn.
     */
    @Test
    fun tvMode_opensWithEverySectionInOneBoundedLazyList() {
        setContent(
            currentEngineName = "ExoPlayer",
            availablePlayers = listOf(PlayerType.MPV),
            isTv = true,
            performanceAvailable = true,
        )

        composeTestRule.onNodeWithText("Delivery").assertExists()
    }
}
