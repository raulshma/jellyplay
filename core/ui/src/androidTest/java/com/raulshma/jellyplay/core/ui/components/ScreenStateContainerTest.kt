package com.raulshma.jellyplay.core.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ScreenStateContainerTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun loadingState_showsLoadingContent() {
        composeTestRule.setContent {
            ScreenStateContainer(state = ScreenState.LOADING) {
                Text("Content")
            }
        }
        // Loading indicator is a CircularProgressIndicator — assert the content
        // text is NOT present (it's hidden behind the loading state).
        composeTestRule.onNodeWithText("Content").assertDoesNotExist()
    }

    @Test
    fun contentState_showsContent() {
        composeTestRule.setContent {
            ScreenStateContainer(state = ScreenState.CONTENT) {
                Text("Content")
            }
        }
        composeTestRule.onNodeWithText("Content").assertIsDisplayed()
    }

    @Test
    fun transitionsFromLoadingToContent() {
        var state by mutableStateOf(ScreenState.LOADING)
        composeTestRule.setContent {
            ScreenStateContainer(state = state) {
                Text("Content")
            }
        }
        // Initially loading.
        composeTestRule.onNodeWithText("Content").assertDoesNotExist()
        // Switch to content — AnimatedContent crossfades; content becomes visible.
        state = ScreenState.CONTENT
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("Content").assertIsDisplayed()
    }
}
