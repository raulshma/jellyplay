package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.model.ChapterInfo
import com.raulshma.jellyplay.feature.player.video.formatDuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChapterPickerSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleChapters = listOf(
        ChapterInfo(name = "Opening", startPositionTicks = 0),
        ChapterInfo(name = "Act 1", startPositionTicks = 600_000_000),
        ChapterInfo(name = "Act 2", startPositionTicks = 1_800_000_000),
        ChapterInfo(name = "Credits", startPositionTicks = 3_600_000_000),
    )

    @Test
    fun chapterPickerSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                ChapterPickerSheet(
                    chapters = sampleChapters,
                    currentPositionMs = 0L,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Chapters").assertIsDisplayed()
    }

    @Test
    fun chapterPickerSheet_displaysAllChapterNames() {
        composeTestRule.setContent {
            MaterialTheme {
                ChapterPickerSheet(
                    chapters = sampleChapters,
                    currentPositionMs = 0L,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Opening").assertIsDisplayed()
        composeTestRule.onNodeWithText("Act 1").assertIsDisplayed()
        composeTestRule.onNodeWithText("Act 2").assertIsDisplayed()
        composeTestRule.onNodeWithText("Credits").assertIsDisplayed()
    }

    @Test
    fun chapterPickerSheet_displaysChapterTimes() {
        composeTestRule.setContent {
            MaterialTheme {
                ChapterPickerSheet(
                    chapters = sampleChapters,
                    currentPositionMs = 0L,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText(formatDuration(60_000L)).assertIsDisplayed()
        composeTestRule.onNodeWithText(formatDuration(180_000L)).assertIsDisplayed()
    }

    @Test
    fun chapterPickerSheet_currentChapter_showsIndicator() {
        composeTestRule.setContent {
            MaterialTheme {
                ChapterPickerSheet(
                    chapters = sampleChapters,
                    currentPositionMs = 90_000L,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("\u25B6").assertIsDisplayed()
    }

    @Test
    fun chapterPickerSheet_clickChapter_callsOnSelect() {
        var selectedTicks: Long? = null
        composeTestRule.setContent {
            MaterialTheme {
                ChapterPickerSheet(
                    chapters = sampleChapters,
                    currentPositionMs = 0L,
                    onSelect = { selectedTicks = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Act 1").performClick()
        assertEquals(600_000_000L, selectedTicks)
    }

    @Test
    fun chapterPickerSheet_lastChapter_isCurrentWhenPositionPast() {
        composeTestRule.setContent {
            MaterialTheme {
                ChapterPickerSheet(
                    chapters = sampleChapters,
                    currentPositionMs = 400_000L,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("\u25B6").assertIsDisplayed()
    }

    @Test
    fun chapterPickerSheet_emptyChapters_showsTitleOnly() {
        composeTestRule.setContent {
            MaterialTheme {
                ChapterPickerSheet(
                    chapters = emptyList(),
                    currentPositionMs = 0L,
                    onSelect = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Chapters").assertIsDisplayed()
    }
}
