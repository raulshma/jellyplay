package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @Test
    fun playbackMetadataRow_showsResolutionAndCodec() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackMetadataRow(
                    playMethod = "Direct Play",
                    isDirectPlayForced = false,
                    hdrType = "dolbyvision",
                    mediaStreams = listOf(
                        com.raulshma.jellyplay.core.model.MediaStream(
                            index = 0,
                            type = com.raulshma.jellyplay.core.model.StreamType.VIDEO,
                            width = 3840,
                            height = 2160,
                            codec = "hevc",
                        ),
                    ),
                    videoStats = PlaybackMetadataSnapshot(),
                    audioTracks = emptyList(),
                )
            }
        }
        composeTestRule.onNodeWithText("4K").assertIsDisplayed()
        composeTestRule.onNodeWithText("HEVC").assertIsDisplayed()
        composeTestRule.onNodeWithText("Dolby Vision").assertIsDisplayed()
    }

    @Test
    fun playbackMetadataRow_showsMeteredBadge() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackMetadataRow(
                    playMethod = "Transcode",
                    isDirectPlayForced = false,
                    hdrType = null,
                    mediaStreams = emptyList(),
                    videoStats = PlaybackMetadataSnapshot(),
                    audioTracks = emptyList(),
                    isConnectionMetered = true,
                )
            }
        }
        composeTestRule.onNodeWithText("Transcode").assertIsDisplayed()
        composeTestRule.onNodeWithText("Metered").assertIsDisplayed()
    }

    @Test
    fun playbackMetadataRow_playMethodClickProvided_chipIsClickable() {
        var clicks = 0
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackMetadataRow(
                    playMethod = "Direct Play",
                    isDirectPlayForced = false,
                    hdrType = null,
                    mediaStreams = emptyList(),
                    videoStats = PlaybackMetadataSnapshot(),
                    audioTracks = emptyList(),
                    onPlayMethodClick = { clicks++ },
                )
            }
        }
        composeTestRule.onNodeWithText("Direct Play").assertHasClickAction()
        composeTestRule.onNodeWithText("Direct Play").performClick()
        org.junit.Assert.assertEquals(1, clicks)
    }

    @Test
    fun playbackMetadataRow_playMethodClickNull_chipIsNotClickable() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackMetadataRow(
                    playMethod = "Direct Play",
                    isDirectPlayForced = false,
                    hdrType = null,
                    mediaStreams = emptyList(),
                    videoStats = PlaybackMetadataSnapshot(),
                    audioTracks = emptyList(),
                )
            }
        }
        composeTestRule.onNodeWithText("Direct Play").assertHasNoClickAction()
    }
}

