@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
 * TV twin of [PlayerKeyboardFocusGrabUiTest] for [PlayerTvFocusGrabEffect]
 * The surface Box's D-pad tier only receives keys while its
 * subtree holds Compose focus, and on Android there is no topmost key-input
 * node above the player for the null-focus fallback to land on — so a lost
 * focus after the controls auto-hide silenced EVERY remote key while Back
 * (OnBackPressedDispatcher, focus-free) survived.
 *
 * Same miniature topology as the keyboard test — the real VideoPlayerScreen
 * needs its whole Koin VM graph — with the TV seam's one behavioral
 * difference: the effect has NO platform gate (the `isTv` gating happens at
 * the screen's call site, folded into `layerComposed` together with the
 * controls-hidden and no-overlay-claims conditions), so a state flip of
 * `layerComposed` stands in for the controls-hide edge (test 4).
 */
class PlayerTvFocusGrabUiTest {

    private companion object {
        const val PLAYER_TAG = "player"
        const val THIEF_TAG = "focus-thief"
    }

    @Test
    fun `dpad key reaches the player box once the grab effect runs`() = runComposeUiTest {
        var selectHandled = false
        var playerHoldsFocus by mutableStateOf(false)
        setContent {
            Box(Modifier.fillMaxSize().onPreviewKeyEvent { false }) {
                val requester = remember { FocusRequester() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(requester)
                        // Mirrors the screen's TV branch: the observer sits
                        // BEFORE the focus target it observes and feeds the
                        // re-assert watch with the subtree's live hasFocus.
                        .onFocusChanged { playerHoldsFocus = it.hasFocus }
                        .focusable()
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                                selectHandled = true
                                true
                            } else {
                                false
                            }
                        }
                        .testTag(PLAYER_TAG),
                )
                PlayerTvFocusGrabEffect(
                    focusRequester = requester,
                    layerComposed = true,
                    targetHoldsFocus = playerHoldsFocus,
                )
            }
        }
        waitForIdle()
        assertFalse(selectHandled, "D-pad UP must not be handled before the grab effect ran")
        assertTrue(playerHoldsFocus, "grab effect must leave focus on the TV layer")

        onNodeWithTag(PLAYER_TAG).performKeyInput {
            keyDown(Key.DirectionUp)
            keyUp(Key.DirectionUp)
        }
        waitForIdle()
        assertTrue(selectHandled, "grabbed layer must receive D-pad UP through the focus system")
    }

    @Test
    fun `disarmed seam must not grab`() = runComposeUiTest {
        var selectHandled = false
        var playerHoldsFocus by mutableStateOf(false)
        setContent {
            Box(Modifier.fillMaxSize().onPreviewKeyEvent { false }) {
                val requester = remember { FocusRequester() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(requester)
                        .onFocusChanged { playerHoldsFocus = it.hasFocus }
                        .focusable()
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                                selectHandled = true
                                true
                            } else {
                                false
                            }
                        }
                        .testTag(PLAYER_TAG),
                )
                // layerComposed = false: controls visible / sheet open / overlay
                // claiming focus — the effect is present but must not grab.
                PlayerTvFocusGrabEffect(
                    focusRequester = requester,
                    layerComposed = false,
                    targetHoldsFocus = playerHoldsFocus,
                )
            }
        }
        waitForIdle()
        assertFalse(selectHandled)
        assertFalse(playerHoldsFocus, "disarmed seam must not grab")
    }

    @Test
    fun `re-assert recovers focus when an outside node steals it after the grab`() =
        runComposeUiTest {
            // The post-hide reality: after the controls auto-hid, focus
            // could be taken by something outside the layer (the exiting play
            // button's disposal, an overlay, an engine-surface shuffle) with no
            // re-grab — every subsequent key died. A sibling focusable steals
            // focus after the grab; the targetHoldsFocus edge must recover.
            var selectHandled = false
            var playerHoldsFocus by mutableStateOf(false)
            setContent {
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { false }) {
                    val requester = remember { FocusRequester() }
                    val thiefRequester = remember { FocusRequester() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(thiefRequester)
                            .focusable()
                            .testTag(THIEF_TAG),
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(requester)
                            .onFocusChanged { playerHoldsFocus = it.hasFocus }
                            .focusable()
                            .onKeyEvent {
                                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionUp) {
                                    selectHandled = true
                                    true
                                } else {
                                    false
                                }
                            }
                            .testTag(PLAYER_TAG),
                    )
                    PlayerTvFocusGrabEffect(
                        focusRequester = requester,
                        layerComposed = true,
                        targetHoldsFocus = playerHoldsFocus,
                    )
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        repeat(5) { androidx.compose.runtime.withFrameNanos { } }
                        thiefRequester.requestFocus()
                    }
                }
            }
            waitForIdle()
            assertTrue(playerHoldsFocus, "initial grab must land before the theft")

            waitUntil(timeoutMillis = 5_000) { playerHoldsFocus } // re-assert must recover within its 3-frame retry
            assertTrue(playerHoldsFocus, "re-assert must recover focus stolen by an outside node")

            onNodeWithTag(PLAYER_TAG).performKeyInput {
                keyDown(Key.DirectionUp)
                keyUp(Key.DirectionUp)
            }
            waitForIdle()
            assertTrue(selectHandled, "recovered layer must receive D-pad UP through the focus system")
        }

    @Test
    fun `layerComposed flipping true - the controls-hide edge - grabs the layer`() =
        runComposeUiTest {
            // The TV seam's gate folds the controls-visible state into
            // layerComposed at the call site: while the controls are visible the
            // play button owns focus (grab disarmed); when they auto-hide the
            // edge must grab the layer with its 3-frame retry.
            var layerComposed by mutableStateOf(false)
            var playerHoldsFocus by mutableStateOf(false)
            setContent {
                Box(Modifier.fillMaxSize().onPreviewKeyEvent { false }) {
                    val requester = remember { FocusRequester() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(requester)
                            .onFocusChanged { playerHoldsFocus = it.hasFocus }
                            .focusable()
                            .testTag(PLAYER_TAG),
                    )
                    PlayerTvFocusGrabEffect(
                        focusRequester = requester,
                        layerComposed = layerComposed,
                        targetHoldsFocus = playerHoldsFocus,
                    )
                }
            }
            waitForIdle()
            assertFalse(playerHoldsFocus, "controls-visible phase: no grab")

            layerComposed = true // the auto-hide edge
            waitUntil(timeoutMillis = 5_000) { playerHoldsFocus }
            assertTrue(playerHoldsFocus, "the hide-edge grab must land within its 3-frame retry")
        }
}
