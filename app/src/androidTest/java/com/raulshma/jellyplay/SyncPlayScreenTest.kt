package com.raulshma.jellyplay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayParticipant
import com.raulshma.jellyplay.feature.syncplay.SyncPlayScreen
import org.junit.Rule
import org.junit.Test

class SyncPlayScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun syncPlayScreen_showsEmptyState() {
        composeTestRule.setContent {
            SyncPlayScreen(onBack = {})
        }

        composeTestRule.onNodeWithText("SyncPlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("No active SyncPlay groups").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create a group to watch together").assertIsDisplayed()
    }

    @Test
    fun syncPlayScreen_showsBackButton() {
        composeTestRule.setContent {
            SyncPlayScreen(onBack = {})
        }

        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun syncPlayScreen_showsCreateFab() {
        composeTestRule.setContent {
            SyncPlayScreen(onBack = {})
        }

        composeTestRule.onNodeWithContentDescription("Create group").assertIsDisplayed()
    }

    @Test
    fun syncPlayScreen_showsCreateDialogOnClick() {
        composeTestRule.setContent {
            SyncPlayScreen(onBack = {})
        }

        composeTestRule.onNodeWithContentDescription("Create group").performClick()
        composeTestRule.onNodeWithText("Create SyncPlay Group").assertIsDisplayed()
    }
}
