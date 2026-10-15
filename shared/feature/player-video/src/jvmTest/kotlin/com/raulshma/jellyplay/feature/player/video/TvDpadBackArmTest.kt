package com.raulshma.jellyplay.feature.player.video

import androidx.compose.ui.input.key.KeyEventType
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.PlayerInputMap
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The BACK arm of [tvDpadKeyConfig] must NOT act on BACK's default
 * (BACK_OR_HIDE): the system back pipeline owns it. The arm set used to hide
 * the controls on the BACK KeyDown — and on API 33+ (manifest predictive
 * back) the framework replayed the same physical press through the
 * predictive-back callback on the KeyUp's Pre-IME stage, where the screen's
 * JellyPlayBackHandler ladder read the controls the arm had just hidden,
 * fell to its exit branch, and finished the player: one press hid AND
 * exited. The default now falls through unconsumed so the ladder's single
 * decision stands (PlayerActivity additionally suppresses the legacy
 * onBackPressed path on API 33+). A REBOUND action still routes to the
 * executor — the system ladder cannot know the rebinding.
 */
class TvDpadBackArmTest {

    private val seekState = DpadSeekState(
        acceleration = DpadSeekAcceleration.Default,
        getBaseStepMs = { 10_000L },
        getCurrentPositionMs = { 0L },
        getDurationMs = { 100_000L },
        onCommit = {},
    )

    private class Probe {
        var hiddenByArm = false
        var executed: PlayerAction? = null
    }

    /** Recording chrome + dispatcher over [backBinding]'s map row. */
    private fun fixtures(
        probe: Probe,
        map: PlayerInputMap,
    ): Pair<PlayerInputDispatcher, PlayerChromeController> {
        val inputs = object {
            var visible = true
        }
        val chrome = PlayerChromeController(
            scope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext),
            isVisibleNow = { inputs.visible },
            applyVisible = { inputs.visible = it; if (!it) probe.hiddenByArm = true },
            isTv = { true },
            controlsTimeoutMs = { 5_000L },
            isSheetOpen = { false },
        )
        val dispatcher = PlayerInputDispatcher(
            getMap = { map },
            executeAction = { action ->
                probe.executed = action
                true
            },
        )
        return dispatcher to chrome
    }

    private fun fixture(
        backBinding: PlayerAction?,
    ): Pair<(DpadKeyEvent) -> Boolean, Probe> {
        val probe = Probe()
        val map = if (backBinding == null) {
            // Unbound BACK control: a disabled row reads as OFF.
            PlayerInputDefaults.defaultMap()
                .withBindingEnabled(PlayerInputDefaults.ID_DPAD_BACK, enabled = false)
        } else {
            PlayerInputDefaults.defaultMap()
                .withBindingAction(PlayerInputDefaults.ID_DPAD_BACK, backBinding)
        }
        val (dispatcher, chrome) = fixtures(probe, map)
        val handler = dpadKeyHandlerHandle(
            tvDpadKeyConfig(
                dispatcher = dispatcher,
                chrome = chrome,
                seekState = seekState,
                isPlaying = { true },
                doTogglePlayPause = {},
                doSeekBack = {},
                doSeekForward = {},
                performConfirmHaptic = {},
            ),
        )
        return handler to probe
    }

    private fun backKey(type: KeyEventType) = DpadKeyEvent(
        action = DpadAction.Back,
        type = type,
        repeatCount = 0,
    )

    @Test
    fun `default BACK_OR_HIDE binding falls through on both edges`() {
        val (handler, probe) = fixture(backBinding = PlayerAction.BACK_OR_HIDE)

        assertFalse(handler(backKey(KeyEventType.KeyDown)), "the arm must leave BACK to the system pipeline")
        assertFalse(handler(backKey(KeyEventType.KeyUp)))
        assertFalse(probe.hiddenByArm, "the arm must not hide the controls")
        assertNull(probe.executed)
    }

    @Test
    fun `unbound BACK control falls through too`() {
        val (handler, probe) = fixture(backBinding = null)

        assertFalse(handler(backKey(KeyEventType.KeyDown)))
        assertFalse(probe.hiddenByArm)
        assertNull(probe.executed)
    }

    @Test
    fun `a rebound BACK control still routes to the executor`() {
        var hideCalls = 0
        val probe = Probe()
        val map = PlayerInputDefaults.defaultMap()
            .withBindingAction(PlayerInputDefaults.ID_DPAD_BACK, PlayerAction.TOGGLE_PLAY_PAUSE)
        val (dispatcher, chrome) = fixtures(probe, map)
        val handler = dpadKeyHandlerHandle(
            tvDpadKeyConfig(
                dispatcher = dispatcher,
                chrome = chrome,
                seekState = seekState,
                isPlaying = { true },
                doTogglePlayPause = {},
                doSeekBack = {},
                doSeekForward = {},
                performConfirmHaptic = {},
            ),
        )

        assertTrue(handler(backKey(KeyEventType.KeyDown)), "a rebound action must consume the key")
        assertEquals(PlayerAction.TOGGLE_PLAY_PAUSE, probe.executed)
        assertEquals(0, hideCalls)
        assertFalse(handler(backKey(KeyEventType.KeyUp)), "rebound acts on KeyDown only")
    }
}
