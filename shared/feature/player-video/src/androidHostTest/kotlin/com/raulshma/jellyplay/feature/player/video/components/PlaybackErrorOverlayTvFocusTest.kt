package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.requestFocus
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * TV D-pad reachability for [PlaybackErrorOverlay]: the overlay is drawn as an
 * in-window layer (not a real Dialog), so nothing hands it focus — it must grab
 * a primary recovery action itself, and Up/Down must stay inside it instead of
 * escaping to the player surface behind the scrim.
 */
@RunWith(AndroidJUnit4::class)
class PlaybackErrorOverlayTvFocusTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun setOverlay(retryable: Boolean) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalTvMode provides true) {
                MaterialTheme {
                    PlaybackErrorOverlay(
                        errorMessage = "boom",
                        currentPlayerType = PlayerType.MPV,
                        retryable = retryable,
                        onRetry = {},
                        onRetryWithEngine = {},
                        onDismiss = {},
                    )
                }
            }
        }
    }

    @Test
    fun retryableError_focusesRetryOnAppear() {
        setOverlay(retryable = true)

        composeTestRule.onNodeWithText("Retry on mpv").assertIsFocused()
    }

    @Test
    fun fatalError_focusesFirstSwitchEngineActionOnAppear() {
        setOverlay(retryable = false)

        composeTestRule.onNodeWithText("Retry with ExoPlayer").assertIsFocused()
    }

    @Test
    fun dpadDown_movesFocusToTheNextAction() {
        setOverlay(retryable = true)

        composeTestRule.onNodeWithText("Retry on mpv").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Retry with ExoPlayer").assertIsFocused()
    }

    @Test
    fun dpadUpFromFirstAction_wrapsToTheLastAction() {
        setOverlay(retryable = true)

        composeTestRule.onNodeWithText("Retry on mpv").performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Dismiss").assertIsFocused()
    }

    @Test
    fun dpadDownFromLastAction_wrapsToTheFirstAction() {
        setOverlay(retryable = true)

        composeTestRule.onNodeWithText("Dismiss").requestFocus()
        composeTestRule.onNodeWithText("Dismiss").performKeyInput {
            keyDown(Key.DirectionDown)
            keyUp(Key.DirectionDown)
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Retry on mpv").assertIsFocused()
    }
}
