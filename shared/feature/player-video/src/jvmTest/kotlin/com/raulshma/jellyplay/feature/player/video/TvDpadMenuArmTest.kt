package com.raulshma.jellyplay.feature.player.video

import androidx.compose.ui.input.key.KeyEventType
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.ui.tv.input.DpadAction
import com.raulshma.jellyplay.core.ui.tv.input.DpadKeyEvent
import com.raulshma.jellyplay.core.ui.tv.input.DpadSeekAcceleration
import com.raulshma.jellyplay.core.ui.tv.components.DpadSeekState
import com.raulshma.jellyplay.core.ui.tv.input.dpadKeyHandlerHandle
import com.raulshma.jellyplay.feature.player.video.chrome.PlayerChromeController
import com.raulshma.jellyplay.feature.player.video.state.PlayerInputDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The MENU arm of [tvDpadKeyConfig] (issue #174: "none of Up, Down, Left,
 * Right, OK **or Menu** brings them back"): the first press summons the
 * controls and consumes the key; repeats, KeyUp and an already-visible
 * overlay stay unconsumed. MENU has no bindable control, so the arm carries
 * no binding resolution — the summon is unconditional.
 */
class TvDpadMenuArmTest {

    private class Probe {
        var showCalls = 0
    }

    private fun fixture(showControls: Boolean): Pair<(DpadKeyEvent) -> Boolean, Probe> {
        val probe = Probe()
        val visibleState = object {
            var visible = showControls
        }
        val chrome = PlayerChromeController(
            scope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext),
            isVisibleNow = { visibleState.visible },
            applyVisible = { visibleState.visible = it; if (it) probe.showCalls++ },
            isTv = { true },
            controlsTimeoutMs = { 5_000L },
            isSheetOpen = { false },
        )
        val dispatcher = PlayerInputDispatcher(
            getMap = { PlayerInputDefaults.defaultMap() },
            executeAction = { true },
        )
        val handler = dpadKeyHandlerHandle(
            tvDpadKeyConfig(
                dispatcher = dispatcher,
                chrome = chrome,
                seekState = DpadSeekState(
                    acceleration = DpadSeekAcceleration.Default,
                    getBaseStepMs = { 10_000L },
                    getCurrentPositionMs = { 0L },
                    getDurationMs = { 100_000L },
                    onCommit = {},
                ),
                isPlaying = { true },
                doTogglePlayPause = {},
                doSeekBack = {},
                doSeekForward = {},
                performConfirmHaptic = {},
            ),
        )
        return handler to probe
    }

    private fun menuKey(type: KeyEventType, repeatCount: Int = 0) = DpadKeyEvent(
        action = DpadAction.Menu,
        type = type,
        repeatCount = repeatCount,
    )

    @Test
    fun `first MENU press with hidden controls summons and consumes`() {
        val (handler, probe) = fixture(showControls = false)

        assertTrue(handler(menuKey(KeyEventType.KeyDown)), "the summon must consume the key")
        assertEquals(1, probe.showCalls, "exactly one summon per press")
    }

    @Test
    fun `keyUp does not re-summon`() {
        val (handler, probe) = fixture(showControls = false)

        assertTrue(handler(menuKey(KeyEventType.KeyDown)))
        assertFalse(handler(menuKey(KeyEventType.KeyUp)), "the arm acts on KeyDown only")
        assertEquals(1, probe.showCalls)
    }

    @Test
    fun `keyRepeat does not re-summon`() {
        val (handler, probe) = fixture(showControls = false)

        assertTrue(handler(menuKey(KeyEventType.KeyDown)))
        assertFalse(handler(menuKey(KeyEventType.KeyDown, repeatCount = 1)), "a held MENU must not re-fire")
        assertEquals(1, probe.showCalls)
    }

    @Test
    fun `MENU with controls already up stays inert`() {
        val (handler, probe) = fixture(showControls = true)

        assertFalse(handler(menuKey(KeyEventType.KeyDown)))
        assertEquals(0, probe.showCalls)
    }
}
