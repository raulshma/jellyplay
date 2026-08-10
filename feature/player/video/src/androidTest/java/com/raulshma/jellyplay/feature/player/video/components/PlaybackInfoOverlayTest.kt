package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.raulshma.jellyplay.core.model.MediaSource
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import org.junit.Rule
import org.junit.Test

class PlaybackInfoOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mediaSource = MediaSource(
        id = "1",
        name = "video.mkv",
        container = "mkv",
        size = 1_073_741_824L,
        bitrate = 8_000_000L,
    )

    private val mediaStreams = listOf(
        MediaStream(
            index = 0,
            type = StreamType.VIDEO,
            codec = "hevc",
            width = 3840,
            height = 2160,
            bitRate = 6_000_000L,
            realFrameRate = 23.976f,
            videoRange = "HDR10",
        ),
        MediaStream(
            index = 1,
            type = StreamType.AUDIO,
            codec = "dts",
            language = "eng",
            title = "Surround",
            channels = 6,
        ),
        MediaStream(
            index = 2,
            type = StreamType.SUBTITLE,
            language = "eng",
            title = "English",
            isDefault = true,
        ),
    )

    @Test
    fun playbackInfoOverlay_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource,
                    mediaStreams = mediaStreams,
                    playMethod = "Direct Play",
                )
            }
        }
        composeTestRule.onNodeWithText("Playback Info").assertIsDisplayed()
    }

    @Test
    fun playbackInfoOverlay_displaysMediaSourceSection() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource,
                    mediaStreams = mediaStreams,
                    playMethod = "Direct Play",
                )
            }
        }
        composeTestRule.onNodeWithText("Media Source").assertIsDisplayed()
        composeTestRule.onNodeWithText("mkv").assertIsDisplayed()
        composeTestRule.onNodeWithText("Direct Play").assertIsDisplayed()
    }

    @Test
    fun playbackInfoOverlay_displaysVideoSection() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource,
                    mediaStreams = mediaStreams,
                    playMethod = "Direct Play",
                )
            }
        }
        composeTestRule.onNodeWithText("Video").assertIsDisplayed()
        composeTestRule.onNodeWithText("HEVC").assertIsDisplayed()
        composeTestRule.onNodeWithText("3840x2160").assertIsDisplayed()
    }

    @Test
    fun playbackInfoOverlay_displaysFrameRate() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource,
                    mediaStreams = mediaStreams,
                    playMethod = "Direct Play",
                )
            }
        }
        composeTestRule.onNodeWithText("23.98 fps").assertIsDisplayed()
    }

    @Test
    fun playbackInfoOverlay_displaysHdrType() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource,
                    mediaStreams = mediaStreams,
                    playMethod = "Direct Play",
                    hdrType = "HDR10",
                )
            }
        }
        composeTestRule.onNodeWithText("HDR10").assertIsDisplayed()
    }

    @Test
    fun playbackInfoOverlay_displaysAudioSection() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource,
                    mediaStreams = mediaStreams,
                    playMethod = "Direct Play",
                )
            }
        }
        composeTestRule.onNodeWithText("Audio").assertIsDisplayed()
        composeTestRule.onNodeWithText("DTS").assertIsDisplayed()
        composeTestRule.onNodeWithText("6ch").assertIsDisplayed()
    }

    @Test
    fun playbackInfoOverlay_displaysSubtitleSection() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource,
                    mediaStreams = mediaStreams,
                    playMethod = "Direct Play",
                )
            }
        }
        composeTestRule.onNodeWithText("Subtitles").assertIsDisplayed()
        composeTestRule.onNodeWithText("Default").assertIsDisplayed()
    }

    @Test
    fun playbackInfoOverlay_nullMediaSource_showsUnknown() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = null,
                    mediaStreams = emptyList(),
                    playMethod = "Transcode",
                )
            }
        }
        composeTestRule.onNodeWithText("Media Source").assertIsDisplayed()
    }

    @Test
    fun playbackInfoOverlay_noStreams_showsOnlyPlaybackInfoTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource,
                    mediaStreams = emptyList(),
                    playMethod = "Direct Play",
                )
            }
        }
        composeTestRule.onNodeWithText("Playback Info").assertIsDisplayed()
        composeTestRule.onNodeWithText("Video").assertDoesNotExist()
        composeTestRule.onNodeWithText("Audio").assertDoesNotExist()
    }

    @Test
    fun playbackInfoOverlay_displaysFileSize() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource.copy(size = 1_073_741_824L),
                    mediaStreams = emptyList(),
                    playMethod = "Direct Play",
                )
            }
        }
        composeTestRule.onNodeWithText("1.00 GB").assertIsDisplayed()
    }

    @Test
    fun playbackInfoOverlay_sdrVideo_noHdrRow() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource,
                    mediaStreams = mediaStreams,
                    playMethod = "Direct Play",
                    hdrType = null,
                )
            }
        }
        composeTestRule.onNodeWithText("HDR10").assertDoesNotExist()
    }

    @Test
    fun playbackInfoOverlay_nonZeroSubtitleDelay_showsSubtitleDelayRow() {
        composeTestRule.setContent {
            MaterialTheme {
                PlaybackInfoOverlay(
                    mediaSource = mediaSource,
                    mediaStreams = mediaStreams,
                    playMethod = "Direct Play",
                    subtitleDelayMs = 1500L,
                )
            }
        }
        composeTestRule.onNodeWithText("Subtitle Delay").assertIsDisplayed()
        composeTestRule.onNodeWithText("+1.5s").assertIsDisplayed()
    }
}
