package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.feature.player.video.TrackOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrackPickerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleTracks = listOf(
        TrackOption(index = 0, label = "English", language = "eng", isSelected = true),
        TrackOption(index = 1, label = "Spanish", language = "spa", isSelected = false),
        TrackOption(index = 2, label = "French", language = "fra", isSelected = false),
    )

    @Test
    fun trackPickerSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                TrackPickerSheet(
                    title = "Audio Tracks",
                    tracks = sampleTracks,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Audio Tracks").assertIsDisplayed()
    }

    @Test
    fun trackPickerSheet_displaysAllTracks() {
        composeTestRule.setContent {
            MaterialTheme {
                TrackPickerSheet(
                    title = "Subtitles",
                    tracks = sampleTracks,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spanish").assertIsDisplayed()
        composeTestRule.onNodeWithText("French").assertIsDisplayed()
    }

    @Test
    fun trackPickerSheet_selectedTrack_showsCheckmark() {
        composeTestRule.setContent {
            MaterialTheme {
                TrackPickerSheet(
                    title = "Audio",
                    tracks = sampleTracks,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("\u2713").assertIsDisplayed()
    }

    @Test
    fun trackPickerSheet_clickTrack_callsOnSelect() {
        var selected: TrackOption? = null
        composeTestRule.setContent {
            MaterialTheme {
                TrackPickerSheet(
                    title = "Audio",
                    tracks = sampleTracks,
                    onSelect = { selected = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Spanish").performClick()
        assertEquals("Spanish", selected!!.label)
    }

    @Test
    fun trackPickerSheet_clickTrack_callsOnDismiss() {
        var dismissed = false
        composeTestRule.setContent {
            MaterialTheme {
                TrackPickerSheet(
                    title = "Audio",
                    tracks = sampleTracks,
                    onSelect = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        composeTestRule.onNodeWithText("French").performClick()
        assertTrue(dismissed)
    }

    @Test
    fun trackPickerSheet_emptyTracks_showsTitleOnly() {
        composeTestRule.setContent {
            MaterialTheme {
                TrackPickerSheet(
                    title = "Audio",
                    tracks = emptyList(),
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Audio").assertIsDisplayed()
    }
}
