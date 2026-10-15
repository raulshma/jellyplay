package com.raulshma.jellyplay.feature.player.live

import androidx.compose.ui.input.key.KeyEventType
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.ui.tv.input.DpadAction
import com.raulshma.jellyplay.core.ui.tv.input.DpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.input.dpadKeyHandlerHandle
import com.raulshma.jellyplay.feature.player.video.chrome.PlayerChromeController
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The live TV arm set's edge guards ([liveDpadKeyConfig]). The arms at HEAD
 * were unobservable (the handler no-oped without a [com.raulshma.jellyplay.core.ui.LocalTvMode]
 * provider), so the no-guard shape was never a working baseline — with the
 * arms live it is a bug: SELECT toggling on BOTH edges of one press is a net
 * no-op (KeyDown summons the overlay, KeyUp hides it again), and a held key
 * must not re-toggle or walk the back ladder per KeyRepeat.
 */
class LiveDpadKeyConfigTest {

    private class Probe {
        var selectCalls = 0
        var backLadderSteps = mutableListOf<String>()
        var menuCalls = 0
        var channelUps = 0
    }

    private fun fixture(isSheetOpen: Boolean = false, overlayVisible: Boolean = false): Pair<(DpadKeyEvent) -> Boolean, Probe> {
        val probe = Probe()
        val chrome = PlayerChromeController(
            scope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext),
            isVisibleNow = { overlayVisible },
            applyVisible = {},
            isTv = { true },
            controlsTimeoutMs = { 5_000L },
            isSheetOpen = { isSheetOpen },
        )
        val dispatcher = PlayerInputDispatcher(
            getMap = { PlayerInputDefaults.defaultMap() },
            executeAction = { true },
        )
        val handler = dpadKeyHandlerHandle(
            liveDpadKeyConfig(
                isSheetOpen = { isSheetOpen },
                dispatcher = dispatcher,
                onChannelUp = { probe.channelUps++ },
                onChannelDown = {},
                onSelect = {
                    probe.selectCalls++
                    true
                },
                onBack = {
                    probe.backLadderSteps += "back"
                    true
                },
                onMenu = {
                    if (!overlayVisible) {
                        probe.menuCalls++
                        true
                    } else false
                },
            ),
        )
        return handler to probe
    }

    private fun key(action: DpadAction, type: KeyEventType, repeatCount: Int = 0) = DpadKeyEvent(
        action = action,
        type = type,
        repeatCount = repeatCount,
    )

    @Test
    fun `one SELECT press toggles exactly once`() {
        val (handler, probe) = fixture()

        assertTrue(handler(key(DpadAction.Select, KeyEventType.KeyDown)), "KeyDown must consume")
        assertFalse(handler(key(DpadAction.Select, KeyEventType.KeyUp)), "KeyUp must be unconsumed")
        assertEquals(1, probe.selectCalls, "both-edges firing would net a no-op toggle")
    }

    @Test
    fun `held SELECT does not re-toggle on KeyRepeat`() {
        val (handler, probe) = fixture()

        assertTrue(handler(key(DpadAction.Select, KeyEventType.KeyDown)))
        assertFalse(handler(key(DpadAction.Select, KeyEventType.KeyDown, repeatCount = 1)))
        assertEquals(1, probe.selectCalls)
    }

    @Test
    fun `one BACK press walks the ladder exactly once`() {
        val (handler, probe) = fixture()

        assertTrue(handler(key(DpadAction.Back, KeyEventType.KeyDown)), "KeyDown must consume")
        assertFalse(handler(key(DpadAction.Back, KeyEventType.KeyUp)), "KeyUp must not re-run the ladder")
        assertFalse(handler(key(DpadAction.Back, KeyEventType.KeyDown, repeatCount = 1)), "KeyRepeat must not re-run the ladder")
        assertEquals(listOf("back"), probe.backLadderSteps)
    }

    @Test
    fun `first MENU press summons the overlay, repeats and KeyUp stay inert`() {
        val (handler, probe) = fixture()

        assertTrue(handler(key(DpadAction.Menu, KeyEventType.KeyDown)))
        assertFalse(handler(key(DpadAction.Menu, KeyEventType.KeyDown, repeatCount = 1)))
        assertFalse(handler(key(DpadAction.Menu, KeyEventType.KeyUp)))
        assertEquals(1, probe.menuCalls)
    }

    @Test
    fun `MENU with overlay up stays unconsumed`() {
        val (handler, probe) = fixture(overlayVisible = true)

        assertFalse(handler(key(DpadAction.Menu, KeyEventType.KeyDown)))
        assertEquals(0, probe.menuCalls)
    }

    @Test
    fun `channel zap keeps its KeyUp shape`() {
        val (handler, probe) = fixture()

        assertFalse(handler(key(DpadAction.Up, KeyEventType.KeyDown)), "zap arms act on KeyUp, unchanged")
        assertTrue(handler(key(DpadAction.Up, KeyEventType.KeyUp)))
        assertEquals(1, probe.channelUps)
    }

    @Test
    fun `open sheet silences the zap arms`() {
        val (handler, probe) = fixture(isSheetOpen = true)

        assertFalse(handler(key(DpadAction.Up, KeyEventType.KeyUp)))
        assertEquals(0, probe.channelUps)
    }
}
