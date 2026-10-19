package com.raulshma.jellyplay.feature.player.video.chrome

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * JVM tests for the chrome module's controller core (the GLOSSARY "Player
 * chrome module"): the auto-hide timer (gate, TV-doubled timeout, re-arm),
 * the play/pause summon policy fold, the BACK hide-or-exit ladder verdicts,
 * the focus-grab arming verdict and the visibility-edge dispatch — all with
 * a fake time source (runTest's scheduler drives the injected scope's
 * delay). The visibility VALUE is a recording get/set pair, exactly the
 * binding the screens hand in.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlayerChromeControllerTest {

    /** Recording binding + the mutable gate inputs, the screens' reader-lambda shape. */
    private class Fixture(
        scope: CoroutineScope,
        isTv: Boolean = false,
        timeoutMs: Long = 5_000L,
    ) {
        var visible = true
        var sheetOpen = false
        var seeking = false
        var overflowOpen = false
        var controlsFocused = false
        var hideOsdOnPause = false
        val edges = mutableListOf<Boolean>()

        val controller: PlayerChromeController = PlayerChromeController(
            scope = scope,
            isVisibleNow = { visible },
            applyVisible = { visible = it },
            isTv = { isTv },
            controlsTimeoutMs = { timeoutMs },
            isSheetOpen = { sheetOpen },
            isSeeking = { seeking },
            isOverflowMenuOpen = { overflowOpen },
            controlsHasFocus = { controlsFocused },
            hideOsdOnPause = { hideOsdOnPause },
            onVisibilityChange = { edges += it },
        )
    }

    // ── visibility + edges ────────────────────────────────────────────────

    @Test
    fun showHideToggle_writeTheBoundValue_andFireOneEdgePerActualFlip() = runTest {
        val f = Fixture(backgroundScope)
        f.controller.hide()
        f.controller.hide() // same-value write: no edge
        assertEquals(false, f.visible)
        assertEquals(listOf(false), f.edges)

        f.controller.show()
        f.controller.toggle()
        assertEquals(false, f.visible)
        assertEquals(listOf(false, true, false), f.edges)
    }

    // ── auto-hide timer ───────────────────────────────────────────────────

    @Test
    fun autoHide_hidesAfterTheGatePasses_andFiresTheHiddenEdge() = runTest {
        val f = Fixture(backgroundScope, timeoutMs = 5_000L)
        f.controller.scheduleAutoHide()

        advanceTimeBy(4_999L)
        runCurrent()
        assertTrue(f.visible, "the timer must not fire early")

        advanceTimeBy(1L)
        runCurrent()
        assertFalse(f.visible)
        assertEquals(listOf(false), f.edges)
    }

    @Test
    fun autoHide_tvDoublesTheTimeout() = runTest {
        val f = Fixture(backgroundScope, isTv = true, timeoutMs = 5_000L)
        f.controller.scheduleAutoHide()

        advanceTimeBy(5_000L)
        runCurrent()
        assertTrue(f.visible, "TV keeps controls twice as long")

        advanceTimeBy(5_000L)
        runCurrent()
        assertFalse(f.visible)
    }

    @Test
    fun autoHide_suppressedGate_inputsNeverArmTheTimer() = runTest {
        val f = Fixture(backgroundScope)
        for (mutate in listOf<(Fixture) -> Unit>(
            { it.seeking = true },
            { it.sheetOpen = true },
            { it.overflowOpen = true },
            // The non-TV focused-chrome suppression is the last input.
            { it.controlsFocused = true },
        )) {
            mutate(f)
            f.controller.scheduleAutoHide()
        }

        advanceTimeBy(60_000L)
        runCurrent()
        assertTrue(f.visible, "no suppressed gate may arm a hide")

        // The first gate term itself: hidden controls never arm a hide.
        val hidden = Fixture(backgroundScope)
        hidden.visible = false
        hidden.controller.scheduleAutoHide()
        advanceTimeBy(60_000L)
        runCurrent()
        assertFalse(hidden.visible)
    }

    @Test
    fun autoHide_reArmReplacesThePendingHide() = runTest {
        val f = Fixture(backgroundScope, timeoutMs = 5_000L)
        f.controller.scheduleAutoHide()
        // An interaction bump inside the window restarts the timer — the
        // screens' effect re-calls scheduleAutoHide on the counter key.
        advanceTimeBy(3_000L)
        f.controller.scheduleAutoHide()
        advanceTimeBy(3_000L)
        runCurrent()
        assertTrue(f.visible, "the re-arm must have replaced the pending hide")

        advanceTimeBy(2_000L)
        runCurrent()
        assertFalse(f.visible)
    }

    @Test
    fun autoHide_sheetOpeningReArmSupersedesThePendingHide() = runTest {
        val f = Fixture(backgroundScope, timeoutMs = 5_000L)
        f.controller.scheduleAutoHide()
        advanceTimeBy(2_000L)
        f.sheetOpen = true
        f.controller.scheduleAutoHide()
        advanceTimeBy(60_000L)
        runCurrent()
        assertTrue(f.visible, "a sheet-open re-arm must have disarmed the hide")
    }

    @Test
    fun summon_reArmsTheTimer_ThenItFires() = runTest {
        val f = Fixture(backgroundScope, timeoutMs = 5_000L)
        f.visible = false
        f.edges.clear()
        f.controller.show()
        assertEquals(listOf(true), f.edges)
        advanceTimeBy(5_000L)
        runCurrent()
        assertFalse(f.visible, "a summon re-arms the hide timer")
    }

    // ── summon policy (shouldSummonControlsOnPause fold) ──────────────────

    @Test
    fun playPauseSummon_pausingWithThePrefOn_staysHidden() = runTest {
        val f = Fixture(backgroundScope)
        f.visible = false
        f.edges.clear()
        f.hideOsdOnPause = true
        f.controller.summonForPlayPause(isPauseDirection = true)
        assertFalse(f.visible)
        assertTrue(f.edges.isEmpty())
    }

    @Test
    fun playPauseSummon_pausingWithThePrefOff_summons_andPlayingAlwaysSummons() = runTest {
        val f = Fixture(backgroundScope)
        f.controller.summonForPlayPause(isPauseDirection = true)
        assertTrue(f.visible)

        f.hideOsdOnPause = true
        f.controller.summonForPlayPause(isPauseDirection = false)
        assertTrue(f.visible, "the option is 'don't summon on pause', not 'never show'")
    }

    // ── BACK ladder verdicts ──────────────────────────────────────────────

    @Test
    fun backVerdict_defaultLadder_sheetHideExit() {
        val f = Fixture(kotlin.coroutines.EmptyCoroutineContext.asScope())
        val controller = f.controller
        f.sheetOpen = true
        assertEquals(PlayerChromeBackVerdict.CloseSheet, controller.backVerdict())
        f.sheetOpen = false
        assertEquals(PlayerChromeBackVerdict.HideControls, controller.backVerdict())
        f.visible = false
        assertEquals(PlayerChromeBackVerdict.Exit, controller.backVerdict())
    }

    @Test
    fun backVerdict_touchSystemLadder_armsHideOnTvOnly() {
        val touch = Fixture(kotlin.coroutines.EmptyCoroutineContext.asScope(), isTv = false)
        assertEquals(PlayerChromeBackVerdict.Exit, touch.controller.backVerdict(hideArm = false))
        val tv = Fixture(kotlin.coroutines.EmptyCoroutineContext.asScope(), isTv = true)
        assertEquals(PlayerChromeBackVerdict.HideControls, tv.controller.backVerdict(hideArm = true))
    }

    @Test
    fun backVerdict_edgeSwipeLadder_summonsWhenHidden_exitsWhenVisible() {
        val f = Fixture(kotlin.coroutines.EmptyCoroutineContext.asScope())
        val verdict = { f.controller.backVerdict(hideArm = false, summonArm = true, sheetArm = false) }
        f.visible = true
        assertEquals(PlayerChromeBackVerdict.Exit, verdict())
        f.visible = false
        assertEquals(PlayerChromeBackVerdict.SummonControls, verdict())
    }

    @Test
    fun backVerdict_executorLadder_ignoresTheSheetArm() {
        val f = Fixture(kotlin.coroutines.EmptyCoroutineContext.asScope())
        f.sheetOpen = true
        assertEquals(
            PlayerChromeBackVerdict.HideControls,
            f.controller.backVerdict(sheetArm = false),
            "the keyboard paths decline while a sheet is open, so the sheet arm is unreachable",
        )
    }

    // ── focus-grab arming ─────────────────────────────────────────────────

    @Test
    fun layerFocus_armedOnlyWhenHiddenAndSheetClosedWithNoClaimants() {
        val f = Fixture(kotlin.coroutines.EmptyCoroutineContext.asScope())
        fun armed(vararg claimants: Boolean) = f.controller.shouldLayerOwnFocus(*claimants)

        f.visible = false
        assertTrue(armed())
        f.visible = true
        assertFalse(armed(), "the play button's grab owns focus while the controls are up")
        f.visible = false
        f.sheetOpen = true
        assertFalse(armed())
        f.sheetOpen = false
        assertTrue(armed())
        assertFalse(armed(true), "a screen claimant (overlay/cast/host screen/banner) disarms the grab")
    }
}

/** A minimal scope for the verdict/arming cases (no timer is armed there). */
private fun kotlin.coroutines.EmptyCoroutineContext.asScope(): CoroutineScope =
    kotlinx.coroutines.CoroutineScope(this)
