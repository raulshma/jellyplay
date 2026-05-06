package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SyncPlayOverlayTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun syncPlayOverlay_visible_showsContent() {
        composeTestRule.setContent {
            MaterialTheme {
                SyncPlayOverlay(
                    isVisible = true,
                    groupName = "Movie Night",
                    participantCount = 3,
                    isSynced = true,
                )
            }
        }
        composeTestRule.onNodeWithText("Synced").assertIsDisplayed()
        composeTestRule.onNodeWithText("Movie Night").assertIsDisplayed()
        composeTestRule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun syncPlayOverlay_hidden_doesNotShowContent() {
        composeTestRule.setContent {
            MaterialTheme {
                SyncPlayOverlay(
                    isVisible = false,
                    groupName = "Movie Night",
                    participantCount = 3,
                    isSynced = true,
                )
            }
        }
        composeTestRule.onNodeWithText("Synced").assertDoesNotExist()
        composeTestRule.onNodeWithText("Movie Night").assertDoesNotExist()
    }

    @Test
    fun syncPlayOverlay_synced_showsSyncedText() {
        composeTestRule.setContent {
            MaterialTheme {
                SyncPlayOverlay(
                    isVisible = true,
                    groupName = "Group",
                    participantCount = 2,
                    isSynced = true,
                )
            }
        }
        composeTestRule.onNodeWithText("Synced").assertIsDisplayed()
        composeTestRule.onNodeWithText("Buffering").assertDoesNotExist()
    }

    @Test
    fun syncPlayOverlay_buffering_showsBufferingText() {
        composeTestRule.setContent {
            MaterialTheme {
                SyncPlayOverlay(
                    isVisible = true,
                    groupName = "Group",
                    participantCount = 2,
                    isSynced = false,
                )
            }
        }
        composeTestRule.onNodeWithText("Buffering").assertIsDisplayed()
        composeTestRule.onNodeWithText("Synced").assertDoesNotExist()
    }

    @Test
    fun syncPlayOverlay_singleParticipant_showsCount() {
        composeTestRule.setContent {
            MaterialTheme {
                SyncPlayOverlay(
                    isVisible = true,
                    groupName = "Solo",
                    participantCount = 1,
                    isSynced = true,
                )
            }
        }
        composeTestRule.onNodeWithText("1").assertIsDisplayed()
    }
}
