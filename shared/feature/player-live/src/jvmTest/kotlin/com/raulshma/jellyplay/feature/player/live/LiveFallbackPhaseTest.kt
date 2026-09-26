package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.feature.player.live.engine.LiveEngineState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pins every arm of the live engine's error/fallback phase machine
 * ([LiveFallbackPhase]) — the four subtleties the androidMain
 * `ExoLiveEngine` used to hand-roll as an enum (before that, two booleans):
 * the per-load reset, the IDLE-gated one-shot fallback, stay-BUFFERING while
 * the fallback re-resolves, and the TERMINAL latch that keeps the follow-up
 * STATE_IDLE from masking ERROR. The engine applies exactly what these
 * decisions declare (MpvEventFoldTest is the precedent for the style).
 */
class LiveFallbackPhaseTest {

    // ─── fresh machine (no error this load) ───────────────────────────────────

    @Test
    fun freshMachine_directErrorWithFallback_invokesFallbackAndHoldsBuffering() {
        val machine = LiveFallbackPhase()

        val decision = machine.onDirectError(fallbackAvailable = true)

        assertSame(LiveFallbackPhase.DirectError.InvokeFallback, decision)
        assertEquals(
            LiveEngineState.BUFFERING,
            decision.stateToPublish,
            "stay BUFFERING while the ViewModel re-resolves so the error overlay does not flash",
        )
    }

    @Test
    fun freshMachine_directErrorWithoutFallback_isTerminalError() {
        // Already on the transcode method: nothing to fall back to.
        val decision = LiveFallbackPhase().onDirectError(fallbackAvailable = false)

        assertSame(LiveFallbackPhase.DirectError.Terminal, decision)
        assertEquals(LiveEngineState.ERROR, decision.stateToPublish)
    }

    @Test
    fun freshMachine_stateChangesPassThroughUnmasked() {
        val machine = LiveFallbackPhase()

        assertEquals(LiveEngineState.BUFFERING, machine.onStateChanged(LiveEngineState.BUFFERING))
        assertEquals(LiveEngineState.READY, machine.onStateChanged(LiveEngineState.READY))
        assertEquals(LiveEngineState.IDLE, machine.onStateChanged(LiveEngineState.IDLE))
    }

    // ─── the IDLE gate (one fallback per load) ────────────────────────────────

    @Test
    fun secondDirectErrorWhileFallingBack_isTerminal() {
        val machine = LiveFallbackPhase()
        machine.onDirectError(fallbackAvailable = true)

        // The original stream errored again mid-re-resolve (or the fallback
        // reload failed before the next load): no second trigger.
        val decision = machine.onDirectError(fallbackAvailable = true)

        assertSame(LiveFallbackPhase.DirectError.Terminal, decision)
        assertEquals(LiveEngineState.ERROR, decision.stateToPublish)
    }

    @Test
    fun directErrorAfterTerminal_staysTerminalWithoutASecondFallback() {
        val machine = LiveFallbackPhase()
        machine.onDirectError(fallbackAvailable = false)

        val decision = machine.onDirectError(fallbackAvailable = true)

        assertSame(LiveFallbackPhase.DirectError.Terminal, decision)
    }

    // ─── the TERMINAL latch (mask the follow-up STATE_IDLE) ──────────────────

    @Test
    fun terminalLatch_masksEveryFollowUpStatePublication() {
        val machine = LiveFallbackPhase()
        machine.onDirectError(fallbackAvailable = false)

        // The follow-up STATE_IDLE after a PlaybackException — and anything
        // else media3 emits before the next load — must not overwrite ERROR.
        assertNull(machine.onStateChanged(LiveEngineState.IDLE))
        assertNull(machine.onStateChanged(LiveEngineState.BUFFERING))
        assertNull(machine.onStateChanged(LiveEngineState.READY))
    }

    @Test
    fun fallbackArm_doesNotLatch_stateStillPassesThrough() {
        val machine = LiveFallbackPhase()
        machine.onDirectError(fallbackAvailable = true)

        // While FALLING_BACK only the terminal arm masks — a state the player
        // emits during the re-resolve is still honest.
        assertEquals(LiveEngineState.BUFFERING, machine.onStateChanged(LiveEngineState.BUFFERING))
    }

    // ─── the per-load reset (reused engine re-fires) ─────────────────────────

    @Test
    fun loadStarted_clearsTheTerminalLatch() {
        val machine = LiveFallbackPhase()
        machine.onDirectError(fallbackAvailable = false)

        machine.onLoadStarted()

        assertEquals(
            LiveEngineState.IDLE,
            machine.onStateChanged(LiveEngineState.IDLE),
            "the ERROR-latch must not survive into the new channel",
        )
    }

    @Test
    fun loadStarted_letsAReusedEngineFireTheFallbackForTheNextChannel() {
        val machine = LiveFallbackPhase()
        machine.onDirectError(fallbackAvailable = true) // fallback fired on the old channel
        machine.onDirectError(fallbackAvailable = true) // retry failed → terminal

        machine.onLoadStarted()

        assertSame(
            LiveFallbackPhase.DirectError.InvokeFallback,
            machine.onDirectError(fallbackAvailable = true),
            "a new load must re-arm the one-shot fallback",
        )
    }

    @Test
    fun loadStarted_midFallback_alsoResetsTheOneShotWindow() {
        val machine = LiveFallbackPhase()
        machine.onDirectError(fallbackAvailable = true)

        machine.onLoadStarted()

        assertSame(
            LiveFallbackPhase.DirectError.InvokeFallback,
            machine.onDirectError(fallbackAvailable = true),
        )
    }
}
