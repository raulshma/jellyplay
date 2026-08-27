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
 *
 * WHY 14A PASSED HERE BUT FAILED THE REAL APP (wave 14D): these miniature
 * topologies have no mpv SwingPanel/HWND surface mounting a second after the
 * grab, so the initial `attempt=1 ok=true` grab was the last focus event and
 * the tests saw a stable focused layer forever. The live session pass proved
 * the real window DROPS that focus ~1–2 s later (surface-mount AWT focus
 * shuffle; player Box hasFocus=false) and nothing re-grabbed — OVERLAY_SPACE
 * failed with spaceReachedPlayer=false. The third test models that reality:
 * an OUTSIDE focusable steals focus after the grab and the seam's
 * re-assert-on-loss (`targetHoldsFocus` edge) must recover it. The `false`
 * parameter in the second test still pins the layerComposed=false disarm.
 */
class PlayerKeyboardFocusGrabUiTest {

    private companion object {
        const val PLAYER_TAG = "player"
        const val THIEF_TAG = "focus-thief"
    }

    @Test
    fun `space reaches the player box once the grab effect runs`() = runComposeUiTest {
        var spaceHandled = false
        var playerHoldsFocus by mutableStateOf(false)
        setContent {
            Box(Modifier.fillMaxSize().onPreviewKeyEvent { false }) {
                val requester = remember { FocusRequester() }
                Box(
                    Modifier
                        .fillMaxSize()
                        .focusRequester(requester)
                        // Mirrors the screen: the seam's re-assert watches the
                        // target's live hasFocus, fed from this observer (which
                        // must sit BEFORE the focus target it observes).
                        .onFocusChanged { playerHoldsFocus = it.hasFocus }
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
                PlayerKeyboardFocusGrabEffect(
                    focusRequester = requester,
                    layerComposed = true,
                    targetHoldsFocus = playerHoldsFocus,
                )
            }
        }
        waitForIdle()
        assertFalse(spaceHandled, "SPACE must not be handled before the grab effect ran")
        assertTrue(playerHoldsFocus, "grab effect must leave focus on the player layer")

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
                    PlayerKeyboardFocusGrabEffect(
                        focusRequester = requester,
                        layerComposed = false,
                        targetHoldsFocus = playerHoldsFocus,
                    )
                }
            }
            waitForIdle()
            assertFalse(spaceHandled)
            assertFalse(playerHoldsFocus, "disarmed seam must not grab")

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

    @Test
    fun `re-assert recovers focus when an outside node steals it after the grab`() =
        runComposeUiTest {
            // The wave 14D live-pass failure shape: the mpv surface mount
            // (an OUTSIDE-of-the-layer focus consumer) drops the player Box's
            // Compose focus ~1–2 s after the initial grab succeeded, and the
            // 14A seam never re-grabbed. Here a sibling focusable steals
            // focus after the grab; the seam's targetHoldsFocus edge must
            // re-assert focus onto the player layer.
            var spaceHandled = false
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
                                if (it.type == KeyEventType.KeyDown && it.key == Key.Spacebar) {
                                    spaceHandled = true
                                    true
                                } else {
                                    false
                                }
                            }
                            .testTag(PLAYER_TAG),
                    )
                    PlayerKeyboardFocusGrabEffect(
                        focusRequester = requester,
                        layerComposed = true,
                        targetHoldsFocus = playerHoldsFocus,
                    )
                    // The theft, frame-clock driven (the surface mounts a
                    // beat after the grab in the real window): after the
                    // initial grab has landed, an outside focusable takes
                    // Compose focus away from the player layer.
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
                keyDown(Key.Spacebar)
                keyUp(Key.Spacebar)
            }
            waitForIdle()
            assertTrue(spaceHandled, "recovered layer must receive SPACE through the focus system")
        }
}
