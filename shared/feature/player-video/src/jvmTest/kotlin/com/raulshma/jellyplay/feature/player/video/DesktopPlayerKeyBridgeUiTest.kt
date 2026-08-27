@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Wave 14E desktop compose UI test for the deterministic media-key delivery
 * ([DesktopPlayerKeyBridge] + the shell's preview forward). The wave-14D
 * merged-tree live pass proved the focus grab recovers per loss but the AWT
 * flap still leaves focus-less GAPS in which a SPACE died at the shell's
 * Row — so focus-based delivery alone can never be deterministic. The fix:
 * the scaffold preview (which in the REAL window receives EVERY key, with or
 * without a focus owner, through the null-focus fallback — ESC has worked
 * there since wave 13B) offers non-back keys to the player screen's OWN
 * handler through the bridge when Route.VideoPlayer is current.
 *
 * Topology (mirrors [PlayerKeyboardFocusGrabUiTest]): the outer Box's
 * `onPreviewKeyEvent` stands in for DesktopNavScaffold's Row, with the SAME
 * shape the shell uses — forward every non-Escape KeyDown through
 * [DesktopPlayerKeyBridge.deliver] and consume when it returns true. The
 * inner Box stands in for the player's keyboard layer; the installed sink
 * lambda mirrors the screen's (declines when the player layer subtree holds
 * focus, interprets Spacebar otherwise).
 *
 * WHY the tests focus an OUTSIDE node instead of reproducing the true
 * null-focus gap: a probe run of this suite proved compose-ui-test DROPS
 * injected keys when no node holds focus at all (`previewSaw=0 boxSaw=0` —
 * the test scene is not "focused", so its key pipeline never engages; in the
 * real windowed app the null-focus fallback is what delivers to the scaffold
 * Row). The displaced-focus shape below — focus held by an outside node while
 * the player layer has none — is the flap's adjacent reality (the 14D
 * re-assert mid-cycle, or a focus restoration landing elsewhere) and drives
 * the exact same preview → bridge → sink path the real gap relies on. The
 * true no-focus-owner case is exercised by the live pass itself
 * (tools/e2e/desktop-session-pass.sh, the actual gate).
 */
class DesktopPlayerKeyBridgeUiTest {

    private companion object {
        const val PLAYER_TAG = "player"
        const val THIEF_TAG = "focus-thief"
    }

    /** Installs a screen-mirroring sink; returns the flag holder it mutates. */
    private fun installMirrorSink(playerHoldsFocus: () -> Boolean): MutableMap<String, Boolean> {
        val flags = mutableMapOf(
            "sinkHandledSpace" to false,
            "sinkSawEscape" to false,
        )
        installPlayerKeySink { event ->
            when {
                playerHoldsFocus() -> false
                event.key == Key.Escape -> {
                    flags["sinkSawEscape"] = true
                    true
                }
                event.type == KeyEventType.KeyDown && event.key == Key.Spacebar -> {
                    flags["sinkHandledSpace"] = true
                    true
                }
                else -> false
            }
        }
        return flags
    }

    /**
     * The shell's preview stand-in: the exact forward shape of
     * DesktopNavScaffold.onPreviewKeyEvent (wave 14E).
     */
    private fun Modifier.shellPreviewModifier(): Modifier = this.onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) {
            false
        } else if (event.key == Key.Escape) {
            false // back handling stays at the shell
        } else {
            DesktopPlayerKeyBridge.deliver(event)
        }
    }

    @Test
    fun `space reaches the player handler through the bridge while the layer holds no focus`() =
        runComposeUiTest {
            var spaceHandledByFocusChain = false
            var playerHoldsFocus by mutableStateOf(false)
            val flags = installMirrorSink { playerHoldsFocus }
            val before = DesktopPlayerKeyBridge.deliveryCount()
            setContent {
                Box(Modifier.fillMaxSize().shellPreviewModifier()) {
                    // An OUTSIDE node holds focus (the flap reality: the
                    // re-assert is mid-cycle or restoration landed elsewhere);
                    // the player layer has none.
                    val thiefRequester = remember { FocusRequester() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(thiefRequester)
                            .focusable()
                            .testTag(THIEF_TAG),
                    )
                    val requester = remember { FocusRequester() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(requester)
                            .onFocusChanged { playerHoldsFocus = it.hasFocus }
                            .focusable()
                            .onKeyEvent {
                                if (it.type == KeyEventType.KeyDown && it.key == Key.Spacebar) {
                                    spaceHandledByFocusChain = true
                                    true
                                } else {
                                    false
                                }
                            }
                            .testTag(PLAYER_TAG),
                    )
                    LaunchedEffect(Unit) { thiefRequester.requestFocus() }
                }
            }
            waitForIdle()
            assertFalse(playerHoldsFocus, "precondition: the player layer holds no focus")

            onNodeWithTag(PLAYER_TAG).performKeyInput {
                keyDown(Key.Spacebar)
                keyUp(Key.Spacebar)
            }
            waitForIdle()
            assertTrue(
                flags["sinkHandledSpace"] == true,
                "the bridge must forward SPACE to the player handler while the layer is focus-less",
            )
            assertFalse(
                spaceHandledByFocusChain,
                "the player layer's onKeyEvent cannot have handled it (not on the focused path)",
            )
            assertEquals(
                before + 1, // only the KeyDown traverses deliver (KeyUp returns false earlier)
                DesktopPlayerKeyBridge.deliveryCount(),
                "every bridge delivery must move the harness's reach counter",
            )

            // Dispose path: uninstalling the sink stops delivery.
            installPlayerKeySink(null)
            onNodeWithTag(PLAYER_TAG).performKeyInput {
                keyDown(Key.Spacebar)
                keyUp(Key.Spacebar)
            }
            waitForIdle()
            assertEquals(
                before + 1,
                DesktopPlayerKeyBridge.deliveryCount(),
                "an uninstalled sink must not receive (or count) further keys",
            )
        }

    @Test
    fun `focused layer is interpreted once - the sink declines and the focus chain handles`() =
        runComposeUiTest {
            var spaceHandledByFocusChain = false
            var playerHoldsFocus by mutableStateOf(false)
            val flags = installMirrorSink { playerHoldsFocus }
            val before = DesktopPlayerKeyBridge.deliveryCount()
            setContent {
                Box(Modifier.fillMaxSize().shellPreviewModifier()) {
                    val requester = remember { FocusRequester() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(requester)
                            .onFocusChanged { playerHoldsFocus = it.hasFocus }
                            .focusable()
                            .onKeyEvent {
                                if (it.type == KeyEventType.KeyDown && it.key == Key.Spacebar) {
                                    spaceHandledByFocusChain = true
                                    true
                                } else {
                                    false
                                }
                            }
                            .testTag(PLAYER_TAG),
                    )
                    // The grab effect runs, as on the real screen: the layer
                    // ends up focused (the flap recovered BEFORE the key).
                    PlayerKeyboardFocusGrabEffect(
                        focusRequester = requester,
                        layerComposed = true,
                        targetHoldsFocus = playerHoldsFocus,
                    )
                }
            }
            waitForIdle()
            assertTrue(playerHoldsFocus, "precondition: the grab left the layer focused")

            onNodeWithTag(PLAYER_TAG).performKeyInput {
                keyDown(Key.Spacebar)
                keyUp(Key.Spacebar)
            }
            waitForIdle()
            assertTrue(spaceHandledByFocusChain, "the focused chain must handle SPACE")
            assertFalse(
                flags["sinkHandledSpace"] == true,
                "the sink must decline while the layer subtree holds focus (no double handling)",
            )
            assertEquals(
                before,
                DesktopPlayerKeyBridge.deliveryCount(),
                "declined offers are not deliveries: the counter counts only sink-accepted keys",
            )
        }

    @Test
    fun `escape never reaches the bridge sink - the shell keeps back handling`() =
        runComposeUiTest {
            var playerHoldsFocus by mutableStateOf(false)
            val flags = installMirrorSink { playerHoldsFocus }
            val before = DesktopPlayerKeyBridge.deliveryCount()
            setContent {
                Box(Modifier.fillMaxSize().shellPreviewModifier()) {
                    // Any focused node so the test's key pipeline dispatches at
                    // all (see class KDoc — null-focus is not injectable here).
                    val thiefRequester = remember { FocusRequester() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(thiefRequester)
                            .focusable()
                            .testTag(THIEF_TAG),
                    )
                    LaunchedEffect(Unit) { thiefRequester.requestFocus() }
                }
            }
            waitForIdle()
            onNodeWithTag(THIEF_TAG).performKeyInput {
                keyDown(Key.Escape)
                keyUp(Key.Escape)
            }
            waitForIdle()
            assertFalse(
                flags["sinkSawEscape"] == true,
                "ESC must stay with the shell's back handling, never the player sink",
            )
            assertEquals(
                before,
                DesktopPlayerKeyBridge.deliveryCount(),
                "ESC must not be delivered to (or counted by) the bridge",
            )
        }

    @Test
    fun `deliver with no sink installed is a false no-op that does not count`() =
        runComposeUiTest {
            installPlayerKeySink(null)
            val before = DesktopPlayerKeyBridge.deliveryCount()
            setContent {
                Box(Modifier.fillMaxSize().shellPreviewModifier()) {
                    val thiefRequester = remember { FocusRequester() }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .focusRequester(thiefRequester)
                            .focusable()
                            .testTag(THIEF_TAG),
                    )
                    LaunchedEffect(Unit) { thiefRequester.requestFocus() }
                }
            }
            waitForIdle()
            assertFalse(DesktopPlayerKeyBridge.isArmed, "precondition: no sink installed")

            onNodeWithTag(THIEF_TAG).performKeyInput {
                keyDown(Key.Spacebar)
                keyUp(Key.Spacebar)
            }
            waitForIdle()
            assertEquals(
                before,
                DesktopPlayerKeyBridge.deliveryCount(),
                "a missing sink is a false no-op: nothing counted, nothing consumed",
            )
            assertFalse(DesktopPlayerKeyBridge.isArmed)
            // The un-consumed key continued through Compose's normal dispatch
            // exactly as the shell intends (deliver's false falls through).
        }
}
