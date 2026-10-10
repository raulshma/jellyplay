package com.raulshma.jellyplay.core.ui.animation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UiStateAnimatedContentTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private sealed interface BranchKey {
        data object Loading : BranchKey
        data object Error : BranchKey
        data object Content : BranchKey
    }

    @Test
    fun loadingState_rendersLoadingBranch() {
        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                UiStateAnimatedContent(
                    state = JellyPlayViewModel.LoadingState.Loading,
                    loading = { Text("loading-visible") },
                    error = { Text("error-visible") },
                    content = { Text("content-visible") },
                )
            }
        }
        composeTestRule.onNodeWithText("loading-visible").assertIsDisplayed()
        composeTestRule.onNodeWithText("content-visible").assertDoesNotExist()
    }

    @Test
    fun successState_rendersContentBranch() {
        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                UiStateAnimatedContent(
                    state = JellyPlayViewModel.LoadingState.Success("content-visible"),
                    loading = { Text("loading-visible") },
                    error = { Text("error-visible") },
                    content = { value -> Text(value) },
                )
            }
        }
        composeTestRule.onNodeWithText("content-visible").assertIsDisplayed()
        composeTestRule.onNodeWithText("loading-visible").assertDoesNotExist()
    }

    @Test
    fun errorState_rendersErrorBranchWithMessage() {
        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                UiStateAnimatedContent(
                    state = JellyPlayViewModel.LoadingState.Error("the-server-said-no"),
                    loading = { Text("loading-visible") },
                    error = { branch -> Text(branch.message) },
                    content = { Text("content-visible") },
                )
            }
        }
        composeTestRule.onNodeWithText("the-server-said-no").assertIsDisplayed()
    }

    @Test
    fun branchSwap_reachesNewBranchAfterTransition() {
        var branch by mutableStateOf<BranchKey>(BranchKey.Loading)
        composeTestRule.setContent {
            JellyPlayTheme(dynamicColor = false) {
                val state: JellyPlayViewModel.LoadingState<String> = when (branch) {
                    BranchKey.Loading -> JellyPlayViewModel.LoadingState.Loading
                    BranchKey.Error -> JellyPlayViewModel.LoadingState.Error("boom")
                    BranchKey.Content -> JellyPlayViewModel.LoadingState.Success("done-visible")
                }
                UiStateAnimatedContent(
                    state = state,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("loading-visible")
                        }
                    },
                    error = { Text("error-visible") },
                    content = { Text("done-visible") },
                )
            }
        }
        composeTestRule.onNodeWithText("loading-visible").assertIsDisplayed()
        branch = BranchKey.Content
        // AnimatedContent animates the handoff; wait until the new branch's
        // node exists, then assert it settled on screen.
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodes(hasText("done-visible")).fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("done-visible").assertIsDisplayed()
    }
}
