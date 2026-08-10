package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.raulshma.jellyplay.feature.player.video.engine.PlaybackMetadataSnapshot
import org.junit.Rule
import org.junit.Test

class PlaybackMetadataRowTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun playbackMetadataRow_zeroSubtitleDelay_hidesSubDelayBadge() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackMetadataRow(
                    playMethod = "Direct Play",
                    isDirectPlayForced = false,
                    hdrType = null,
                    mediaStreams = emptyList(),
                    videoStats = PlaybackMetadataSnapshot(),
                    audioTracks = emptyList(),
                    subtitleDelayMs = 0L,
                )
            }
        }
        composeTestRule.onNodeWithText("Direct Play").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sub Delay +1.5s").assertDoesNotExist()
    }

    @Test
    fun playbackMetadataRow_nonZeroSubtitleDelay_showsSubDelayBadge() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackMetadataRow(
                    playMethod = "Direct Play",
                    isDirectPlayForced = false,
                    hdrType = null,
                    mediaStreams = emptyList(),
                    videoStats = PlaybackMetadataSnapshot(),
                    audioTracks = emptyList(),
                    subtitleDelayMs = 1500L,
                )
            }
        }
        composeTestRule.onNodeWithText("Direct Play").assertIsDisplayed()
        composeTestRule.onNodeWithText("Sub Delay +1.5s").assertIsDisplayed()
    }

    @Test
    fun playbackMetadataRow_negativeSubtitleDelay_showsSubDelayBadge() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackMetadataRow(
                    playMethod = "Direct Play",
                    isDirectPlayForced = false,
                    hdrType = null,
                    mediaStreams = emptyList(),
                    videoStats = PlaybackMetadataSnapshot(),
                    audioTracks = emptyList(),
                    subtitleDelayMs = -500L,
                )
            }
        }
        composeTestRule.onNodeWithText("Sub Delay -0.5s").assertIsDisplayed()
    }
}
