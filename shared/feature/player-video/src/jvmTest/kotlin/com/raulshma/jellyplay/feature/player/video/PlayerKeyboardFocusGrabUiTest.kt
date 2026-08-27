@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wave 14A desktop compose UI test for the keyboard-focus grab
 * ([PlayerKeyboardFocusGrabEffect]) — the mechanism behind the wave 13B
 * session-harness SPACE finding, reproduced at miniature scale (the real
 * VideoPlayerScreen needs its whole Koin VM graph, so the test mirrors its
 * focus topology instead of composing the screen):
 *
 *  - an outer `onPreviewKeyEvent { false }` Box stands in for
 *    DesktopNavScaffold's Row — the TOPMOST key-input node in the tree;
 *  - an inner focusable Box with an onKeyEvent SPACE handler stands in for
 *    the player's keyboard layer;
 *  - the layer never grabs focus on its own (the player Box is
 *    `.focusable()`, which does not take focus; the controls' grab is
 *    TV-only), matching the desktop shell where nothing else holds focus.
 *
 * With no focused node, Compose's null-focus fallback dispatches key events
 * only through the chain from the root down to the topmost key-input node
 * (the outer Box) — so an uninjected layer never sees SPACE (the second test
 * pins that bug shape), while the grab effect focuses the layer and SPACE
 * lands (the first test, the wave 14A fix).
 */
class PlayerKeyboardFocusGrabUiTest {

    private companion object {
        const val PLAYER_TAG = "player"
    }

    @Test
    fun `space reaches the player box once the grab effect runs`() = runComposeUiTest {
        var spaceHandled = false
        setContent {
            Box(Modifier.fillMaxSize().onPreviewKeyEvent { false }) {
                val requester = remember { FocusRequester() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(requester)
                        .focusable()
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.Spacebar) {
                                spaceHandled = true
                                true
                            } else {
                                false
                            }
                        }
                        .testTag(PLAYER_TAG),
                )
                PlayerKeyboardFocusGrabEffect(focusRequester = requester, layerComposed = true)
            }
        }
        waitForIdle()
        assertFalse(spaceHandled, "SPACE must not be handled before the grab effect ran")

        onNodeWithTag(PLAYER_TAG).performKeyInput {
            keyDown(Key.Spacebar)
            keyUp(Key.Spacebar)
        }
        waitForIdle()
        assertTrue(spaceHandled, "grabbed layer must receive SPACE through the focus system")
    }

    @Test
    fun `without the grab the null-focus fallback stops at the outer preview node`() =
        runComposeUiTest {
            var spaceHandled = false
            setContent {
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { false }) {
                    val requester = remember { FocusRequester() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(requester)
                            .focusable()
                            .onKeyEvent {
                                if (it.type == KeyEventType.KeyDown && it.key == Key.Spacebar) {
                                    spaceHandled = true
                                    true
                                } else {
                                    false
                                }
                            }
                            .testTag(PLAYER_TAG),
                    )
                    // layerComposed = false: the effect is present but must
                    // not grab (mirrors the sheet-open window of the screen).
                    PlayerKeyboardFocusGrabEffect(focusRequester = requester, layerComposed = false)
                }
            }
            waitForIdle()
            assertFalse(spaceHandled)

            onNodeWithTag(PLAYER_TAG).performKeyInput {
                keyDown(Key.Spacebar)
                keyUp(Key.Spacebar)
            }
            waitForIdle()
            // The wave 13B bug shape: nothing holds focus, so the dispatch
            // never descends past the topmost key-input node (the outer
            // preview Box) and the player layer sees nothing.
            assertFalse(spaceHandled, "unfocused layer must NOT receive SPACE")
        }
}
