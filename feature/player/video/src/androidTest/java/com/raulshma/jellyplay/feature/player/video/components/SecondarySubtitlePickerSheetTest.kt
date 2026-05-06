package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.StreamType
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SecondarySubtitlePickerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val mediaStreams = listOf(
        MediaStream(index = 0, type = StreamType.VIDEO, codec = "h264"),
        MediaStream(index = 1, type = StreamType.AUDIO, codec = "aac", language = "eng"),
        MediaStream(index = 2, type = StreamType.SUBTITLE, codec = "srt", language = "eng", displayTitle = "English"),
        MediaStream(index = 3, type = StreamType.SUBTITLE, codec = "ass", language = "spa", displayTitle = "Spanish"),
    )

    @Test
    fun secondarySubtitleSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                SecondarySubtitlePickerSheet(
                    mediaStreams = mediaStreams,
                    currentSecondary = null,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Secondary Subtitle").assertIsDisplayed()
    }

    @Test
    fun secondarySubtitleSheet_displaysOffOption() {
        composeTestRule.setContent {
            MaterialTheme {
                SecondarySubtitlePickerSheet(
                    mediaStreams = mediaStreams,
                    currentSecondary = null,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Off").assertIsDisplayed()
    }

    @Test
    fun secondarySubtitleSheet_offSelected_showsCheckmark() {
        composeTestRule.setContent {
            MaterialTheme {
                SecondarySubtitlePickerSheet(
                    mediaStreams = mediaStreams,
                    currentSecondary = null,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("\u2713").assertIsDisplayed()
    }

    @Test
    fun secondarySubtitleSheet_displaysSubtitleTracksOnly() {
        composeTestRule.setContent {
            MaterialTheme {
                SecondarySubtitlePickerSheet(
                    mediaStreams = mediaStreams,
                    currentSecondary = null,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spanish").assertIsDisplayed()
    }

    @Test
    fun secondarySubtitleSheet_displaysCodec() {
        composeTestRule.setContent {
            MaterialTheme {
                SecondarySubtitlePickerSheet(
                    mediaStreams = mediaStreams,
                    currentSecondary = null,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("SRT").assertIsDisplayed()
        composeTestRule.onNodeWithText("ASS").assertIsDisplayed()
    }

    @Test
    fun secondarySubtitleSheet_trackSelected_showsCheckmark() {
        val selected = mediaStreams[2]
        composeTestRule.setContent {
            MaterialTheme {
                SecondarySubtitlePickerSheet(
                    mediaStreams = mediaStreams,
                    currentSecondary = selected,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("\u2713").assertIsDisplayed()
    }

    @Test
    fun secondarySubtitleSheet_clickOff_callsOnSelectWithNull() {
        var result: MediaStream? = MediaStream(index = -1, type = StreamType.SUBTITLE)
        composeTestRule.setContent {
            MaterialTheme {
                SecondarySubtitlePickerSheet(
                    mediaStreams = mediaStreams,
                    currentSecondary = mediaStreams[2],
                    onSelect = { result = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Off").performClick()
        assertNull(result)
    }

    @Test
    fun secondarySubtitleSheet_clickTrack_callsOnSelect() {
        var result: MediaStream? = null
        composeTestRule.setContent {
            MaterialTheme {
                SecondarySubtitlePickerSheet(
                    mediaStreams = mediaStreams,
                    currentSecondary = null,
                    onSelect = { result = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Spanish").performClick()
        assertTrue(result?.index == 3)
    }

    @Test
    fun secondarySubtitleSheet_noSubtitleStreams_showsOnlyOff() {
        val noSubs = listOf(
            MediaStream(index = 0, type = StreamType.VIDEO),
            MediaStream(index = 1, type = StreamType.AUDIO),
        )
        composeTestRule.setContent {
            MaterialTheme {
                SecondarySubtitlePickerSheet(
                    mediaStreams = noSubs,
                    currentSecondary = null,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Off").assertIsDisplayed()
        composeTestRule.onNodeWithText("English").assertDoesNotExist()
    }
}
