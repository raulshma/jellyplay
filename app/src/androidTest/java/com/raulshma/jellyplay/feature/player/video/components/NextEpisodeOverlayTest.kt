package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NextEpisodeOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun nextEpisodeOverlay_visible_showsContent() {
        composeTestRule.setContent {
            MaterialTheme {
                NextEpisodeOverlay(
                    isVisible = true,
                    episodeTitle = "The Next Episode",
                    seriesName = "Test Series",
                    seasonNumber = 2,
                    episodeNumber = 5,
                    thumbnailUrl = null,
                    onPlayNext = {},
                    onCancel = {},
                    isPlaying = false,
                )
            }
        }
        composeTestRule.onNodeWithText("Next Episode").assertIsDisplayed()
        composeTestRule.onNodeWithText("The Next Episode").assertIsDisplayed()
        composeTestRule.onNodeWithText("S02E05").assertIsDisplayed()
        composeTestRule.onNodeWithText("Test Series").assertIsDisplayed()
    }

    @Test
    fun nextEpisodeOverlay_hidden_doesNotShowContent() {
        composeTestRule.setContent {
            MaterialTheme {
                NextEpisodeOverlay(
                    isVisible = false,
                    episodeTitle = "The Next Episode",
                    seriesName = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    thumbnailUrl = null,
                    onPlayNext = {},
                    onCancel = {},
                    isPlaying = false,
                )
            }
        }
        composeTestRule.onNodeWithText("Next Episode").assertDoesNotExist()
        composeTestRule.onNodeWithText("The Next Episode").assertDoesNotExist()
    }

    @Test
    fun nextEpisodeOverlay_noSeriesInfo_showsOnlyTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                NextEpisodeOverlay(
                    isVisible = true,
                    episodeTitle = "Episode Title",
                    seriesName = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    thumbnailUrl = null,
                    onPlayNext = {},
                    onCancel = {},
                    isPlaying = false,
                )
            }
        }
        composeTestRule.onNodeWithText("Episode Title").assertIsDisplayed()
        composeTestRule.onNodeWithText("S00E00").assertDoesNotExist()
    }

    @Test
    fun nextEpisodeOverlay_playNextButton_callsOnPlayNext() {
        var played = false
        composeTestRule.setContent {
            MaterialTheme {
                NextEpisodeOverlay(
                    isVisible = true,
                    episodeTitle = "Next",
                    seriesName = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    thumbnailUrl = null,
                    onPlayNext = { played = true },
                    onCancel = {},
                    isPlaying = false,
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Play Next").performClick()
        assertTrue(played)
    }

    @Test
    fun nextEpisodeOverlay_cancelButton_callsOnCancel() {
        var cancelled = false
        composeTestRule.setContent {
            MaterialTheme {
                NextEpisodeOverlay(
                    isVisible = true,
                    episodeTitle = "Next",
                    seriesName = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    thumbnailUrl = null,
                    onPlayNext = {},
                    onCancel = { cancelled = true },
                    isPlaying = false,
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Cancel").performClick()
        assertTrue(cancelled)
    }

    @Test
    fun nextEpisodeOverlay_dismissed_hidesContent() {
        composeTestRule.setContent {
            MaterialTheme {
                NextEpisodeOverlay(
                    isVisible = true,
                    episodeTitle = "Next",
                    seriesName = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    thumbnailUrl = null,
                    onPlayNext = {},
                    onCancel = {},
                    isPlaying = false,
                )
            }
        }
        composeTestRule.onNodeWithContentDescription("Cancel").performClick()
        composeTestRule.onNodeWithText("Next Episode").assertDoesNotExist()
    }

    @Test
    fun nextEpisodeOverlay_seasonEpisodeFormatting() {
        composeTestRule.setContent {
            MaterialTheme {
                NextEpisodeOverlay(
                    isVisible = true,
                    episodeTitle = "Pilot",
                    seriesName = "Show",
                    seasonNumber = 1,
                    episodeNumber = 1,
                    thumbnailUrl = null,
                    onPlayNext = {},
                    onCancel = {},
                    isPlaying = false,
                )
            }
        }
        composeTestRule.onNodeWithText("S01E01").assertIsDisplayed()
    }
}
