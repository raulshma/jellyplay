package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SubtitleDownloadSheetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sampleSubtitles = listOf(
        RemoteSubtitleInfo(
            id = "1",
            name = "English",
            language = "eng",
            format = "srt",
            provider = "OpenSubtitles",
            downloadCount = 1500,
        ),
        RemoteSubtitleInfo(
            id = "2",
            name = "Spanish",
            language = "spa",
            format = "ass",
            provider = "OpenSubtitles",
            downloadCount = 800,
        ),
    )

    @Test
    fun subtitleDownloadSheet_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDownloadSheet(
                    subtitles = sampleSubtitles,
                    isLoading = false,
                    onDownload = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Download Subtitles").assertIsDisplayed()
    }

    @Test
    fun subtitleDownloadSheet_displaysSubtitleNames() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDownloadSheet(
                    subtitles = sampleSubtitles,
                    isLoading = false,
                    onDownload = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("English").assertIsDisplayed()
        composeTestRule.onNodeWithText("Spanish").assertIsDisplayed()
    }

    @Test
    fun subtitleDownloadSheet_displaysDownloadCount() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDownloadSheet(
                    subtitles = sampleSubtitles,
                    isLoading = false,
                    onDownload = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("1500").assertIsDisplayed()
        composeTestRule.onNodeWithText("800").assertIsDisplayed()
    }

    @Test
    fun subtitleDownloadSheet_displaysLanguageAndFormat() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDownloadSheet(
                    subtitles = sampleSubtitles,
                    isLoading = false,
                    onDownload = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("ENG").assertIsDisplayed()
        composeTestRule.onNodeWithText("SRT").assertIsDisplayed()
        composeTestRule.onNodeWithText("OpenSubtitles").assertIsDisplayed()
    }

    @Test
    fun subtitleDownloadSheet_clickSubtitle_callsOnDownload() {
        var downloaded: RemoteSubtitleInfo? = null
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDownloadSheet(
                    subtitles = sampleSubtitles,
                    isLoading = false,
                    onDownload = { downloaded = it },
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Spanish").performClick()
        assertEquals("2", downloaded!!.id)
    }

    @Test
    fun subtitleDownloadSheet_loading_showsProgress() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDownloadSheet(
                    subtitles = emptyList(),
                    isLoading = true,
                    onDownload = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("Download Subtitles").assertIsDisplayed()
    }

    @Test
    fun subtitleDownloadSheet_emptyResults_showsMessage() {
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDownloadSheet(
                    subtitles = emptyList(),
                    isLoading = false,
                    onDownload = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("No remote subtitles available.").assertIsDisplayed()
    }

    @Test
    fun subtitleDownloadSheet_subtitleWithoutName_usesLanguage() {
        val subs = listOf(
            RemoteSubtitleInfo(id = "1", language = "fra"),
        )
        composeTestRule.setContent {
            MaterialTheme {
                SubtitleDownloadSheet(
                    subtitles = subs,
                    isLoading = false,
                    onDownload = {},
                    onDismiss = {},
                )
            }
        }
        composeTestRule.onNodeWithText("fra").assertIsDisplayed()
    }
}
