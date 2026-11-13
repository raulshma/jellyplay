package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.setContent
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet
import java.awt.EventQueue
import java.awt.Point
import java.awt.Robot
import java.awt.Toolkit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end desktop regression for "sheets are not expanding with scroll":
 * a REAL ComposeWindow + REAL java.awt.Robot wheel events over the sheet's
 * heavyweight ComposeDialog — the exact AWT/skiko path the shipped app uses
 * (the uiTest scene cannot inject wheel input into Dialog windows, so the
 * injected-wheel failures there were infra artifacts, not app behavior).
 *
 * Guarded to interactive display sessions: headless CI skips both cases.
 * Wheel-driven assertions are additionally guarded to platforms whose
 * Compose Desktop wheel pipeline actually scrolls (see
 * [assumeWheelScrollWorks]) — the sheet-expansion assertion runs wheel-free
 * and stays live everywhere the display is interactive.
 */
@OptIn(ExperimentalMaterial3Api::class)
class DesktopSheetWheelRobotTest {

    private companion object {
        // JUnit builds a fresh instance per test method; the probe (a real
        // window + real Robot wheel) must run once per JVM and be shared.
        private val wheelProbeOutcome = AtomicReference<WheelProbeOutcome?>(null)
    }

    private val items = (0 until 100).map { "Item $it" }

    private val warmUpDone = AtomicBoolean(false)
    private val warmUpWindow = AtomicReference<ComposeWindow?>(null)

    /**
     * The JVM's first ComposeWindow is routinely denied the Windows foreground
     * when a background test worker shows it, and Windows routes WM_MOUSEWHEEL
     * to the FOREGROUND window — so whichever wheel case ran first in a fresh
     * JVM lost its wheel events to whatever app actually held focus (observed:
     * first case 0 -> 0, second case scrolls; both pass when run as the second
     * case). Show and dispose a sacrificial window once per JVM so the measured
     * windows are never the process's first activation.
     */
    private fun warmUpFirstWindowOnce() {
        if (!warmUpDone.compareAndSet(false, true)) return
        if (java.awt.GraphicsEnvironment.isHeadless()) return
        EventQueue.invokeLater {
            val warmup = ComposeWindow()
            warmup.isUndecorated = true
            warmup.setSize(120, 80)
            warmup.location = Point(0, 0)
            warmup.setContent { Text("wheel warm-up") }
            warmUpWindow.set(warmup)
            warmup.isVisible = true
            warmup.toFront()
        }
        Thread.sleep(600)
        EventQueue.invokeLater { warmUpWindow.getAndSet(null)?.dispose() }
    }

    private fun assumeInteractiveDisplay() {
        // macOS CI runners report non-headless AWT but have no interactive
        // display session — Robot wheel events never reach the window and
        // both cases fail deterministically (firstVisibleItemIndex 0 -> 0
        // across every retry and runner). Local macOS dev boxes and the
        // ubuntu/windows lanes keep the coverage; skip only headless-style
        // macOS environments... which cannot be told apart from a real mac
        // from inside the JVM, so gate on CI instead: interactive macOS
        // sessions run this suite outside CI.
        val isCi = System.getenv("CI") == "true"
        val isMacos = System.getProperty("os.name").lowercase().contains("mac")
        if (isCi && isMacos) {
            org.junit.Assume.assumeTrue(
                "robot wheel needs an interactive display; macOS CI has none",
                false,
            )
            return
        }
        org.junit.Assume.assumeTrue(
            "robot wheel needs an interactive display",
            !java.awt.GraphicsEnvironment.isHeadless(),
        )
        try {
            val testRobot = Robot()
            testRobot.mouseMove(200, 200)
            Thread.sleep(50)
            val loc = java.awt.MouseInfo.getPointerInfo()?.location
            org.junit.Assume.assumeTrue(
                "robot wheel needs an interactive mouse session (cursor target (200,200), actual $loc)",
                loc != null && Math.abs(loc.x - 200) <= 5 && Math.abs(loc.y - 200) <= 5,
            )
        } catch (_: Throwable) {
            org.junit.Assume.assumeTrue("robot mouse move unavailable", false)
        }
    }

    /**
     * Probes whether the underlying Compose Desktop wheel pipeline actually
     * scrolls on THIS platform — the wheel-driven assertions can only speak
     * for app behavior where the substrate works. The probe is a plain
     * window + [androidx.compose.foundation.verticalScroll] (zero app code,
     * so it can never mask an app-level regression — it only certifies the
     * platform): 80 real Robot wheel notches must move it ≥ 40px, AND ≥ 40
     * of those notches must demonstrably reach the probe window. Both
     * failure signatures measured while writing this gate skip, each for its
     * own reason:
     * - events delivered but content stalls (Linux/X11 + CMP 1.12.2: skiko
     *   converts every notch into a 1px Scroll chunk; Compose's scrollable
     *   consumes the first ~6px and then stops entirely — plain scroll
     *   state, LazyColumn, sheet, wiggle, cadence, all identical);
     * - events NOT deliverable at all even after the same toFront/focus
     *   retry the real cases use (screensaver/lock overlay covering the
     *   display, or an unraisable window).
     * The gate is conservative the other way too: a probe infrastructure
     * failure is treated as working, never as grounds to skip.
     */
    private fun assumeWheelScrollWorks() {
        val outcome = wheelProbeOutcome.updateAndGet { current -> current ?: runWheelProbe() }!!
        org.junit.Assume.assumeTrue(
            "real wheel input cannot drive this suite here (probe: " +
                "${outcome.deliveredEvents} of 80 wheel events reached a Compose window, " +
                "content scrolled ${outcome.scrolledPx}px)",
            outcome.works,
        )
    }

    private fun runWheelProbe(): WheelProbeOutcome {
        val scrollRef = AtomicReference<androidx.compose.foundation.ScrollState?>(null)
        val winRef = AtomicReference<ComposeWindow?>(null)
        var delivered = 0
        val listener = java.awt.event.AWTEventListener { e ->
            // Count only wheel events whose dispatch chain lands on the probe
            // window — a lagging sheet dialog from the previous case says
            // nothing about this one.
            if (e is java.awt.event.MouseWheelEvent) {
                var owner = e.source as? java.awt.Container
                while (owner != null && owner !is java.awt.Window) owner = owner.parent
                if (owner === winRef.get()) delivered++
            }
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, java.awt.AWTEvent.MOUSE_WHEEL_EVENT_MASK)
        EventQueue.invokeLater {
            val probe = ComposeWindow()
            winRef.set(probe)
            probe.setSize(300, 300)
            probe.setLocation(50, 50)
            probe.setContent {
                val st = rememberScrollState()
                scrollRef.set(st)
                Column(Modifier.fillMaxSize().verticalScroll(st)) {
                    repeat(100) { i -> Text("probe item $i", modifier = Modifier.padding(vertical = 20.dp)) }
                }
            }
            probe.isVisible = true
            probe.toFront()
        }
        try {
            Thread.sleep(1_500)
            val probe = winRef.get()
                ?: return WheelProbeOutcome(works = true, deliveredEvents = 0, scrolledPx = 0)
            val robot = Robot()
            val center = Point(probe.locationOnScreen.x + 150, probe.locationOnScreen.y + 150)
            robot.mouseMove(center.x, center.y)
            Thread.sleep(300)
            repeat(40) { robot.mouseWheel(1); Thread.sleep(25) }
            if (delivered == 0) {
                // No wheel reached the probe window: same infra problem the
                // real cases retry around (foreground race, screensaver
                // overlay). Re-assert foreground and wheel once more before
                // concluding the platform can't deliver input.
                EventQueue.invokeAndWait {
                    probe.toFront()
                    probe.requestFocus()
                }
                Thread.sleep(500)
                robot.mouseMove(center.x, center.y)
                Thread.sleep(200)
                repeat(40) { robot.mouseWheel(1); Thread.sleep(25) }
            }
            Thread.sleep(800)
            val scrolled = scrollRef.get()?.value ?: 0
            return when {
                // Input undeliverable even after the retry — the platform
                // cannot host real-wheel assertions (not an app signal).
                delivered < 40 -> WheelProbeOutcome(works = false, deliveredEvents = delivered, scrolledPx = scrolled)
                // Events arrive but the scrollable stalls — the wheel pipeline
                // itself is broken here (the Linux signature).
                scrolled < 40 -> WheelProbeOutcome(works = false, deliveredEvents = delivered, scrolledPx = scrolled)
                else -> WheelProbeOutcome(works = true, deliveredEvents = delivered, scrolledPx = scrolled)
            }
        } catch (_: Throwable) {
            // Probe infrastructure failed — inconclusive, never grounds to skip.
            return WheelProbeOutcome(works = true, deliveredEvents = -1, scrolledPx = 0)
        } finally {
            Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            EventQueue.invokeLater { winRef.get()?.dispose() }
        }
    }

    @Test
    fun `robot wheel scrolls the real dialog sheet content`() =
        robotWheelSheetCase(skipPartiallyExpanded = true) { listState, sheetState, before, after ->
            assertTrue(
                after > before,
                "real robot wheel over the sheet dialog must scroll content " +
                    "(firstVisibleItemIndex $before -> $after)",
            )
        }

    @Test
    fun `desktop sheets open fully expanded and wheel scrolls a default-state caller`() =
        robotWheelSheetCase(
            skipPartiallyExpanded = false,
            preWheelAssert = { listState, _ ->
                // With the TvSafeSheet fix, a default (partial) caller state is
                // substituted by a skip-partial state on desktop, so the tall
                // sheet's list viewport spans most of the window instead of the
                // half-height partial sheet that wheel scroll could never grow.
                // Wheel-free: measurable on every interactive display, which is
                // why it lives ahead of the wheel gate, not beside the wheel
                // assertion below.
                val viewportHeight = listState.layoutInfo.viewportEndOffset -
                    listState.layoutInfo.viewportStartOffset
                assertTrue(
                    viewportHeight > 400,
                    "desktop sheet must open fully expanded (list viewport height=$viewportHeight)",
                )
            },
        ) { _, _, before, after ->
            assertTrue(
                after > before,
                "wheel must scroll the content (firstVisibleItemIndex $before -> $after)",
            )
        }

    private fun robotWheelSheetCase(
        skipPartiallyExpanded: Boolean,
        preWheelAssert: ((LazyListState, SheetState) -> Unit)? = null,
        assert: (LazyListState, SheetState, Int, Int) -> Unit,
    ) {
        assumeInteractiveDisplay()
        warmUpFirstWindowOnce()
        val listRef = AtomicReference<LazyListState?>(null)
        val sheetRef = AtomicReference<SheetState?>(null)
        val windowRef = AtomicReference<ComposeWindow?>(null)
        val laidOut = CountDownLatch(1)

        EventQueue.invokeLater {
            val window = ComposeWindow()
            // App parity: Main.kt opens its Window with undecorated = true
            // (custom DesktopTitleBar) — reproduce that here.
            window.isUndecorated = true
            windowRef.set(window)
            window.setSize(900, 700)
            val screen = window.graphicsConfiguration.bounds
            window.location = Point(
                screen.x + (screen.width - window.width) / 2,
                screen.y + (screen.height - window.height) / 2,
            )
            window.setContent {
                var open by remember { mutableStateOf(true) }
                val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
                val lazyState = rememberLazyListState()
                listRef.set(lazyState)
                sheetRef.set(state)
                if (open) {
                    TvSafeSheet(
                        onDismissRequest = { open = false },
                        sheetState = state,
                    ) {
                        LazyColumn(
                            state = lazyState,
                            modifier = Modifier.fillMaxWidth().testTag("sheet_list"),
                        ) {
                            items(items.size) { i ->
                                Text(
                                    text = items[i],
                                    modifier = Modifier.padding(vertical = 20.dp),
                                )
                            }
                        }
                    }
                }
            }
            window.isVisible = true
            window.toFront()
        }

        try {
            // Wait for the sheet's list to lay out (visible items = the dialog
            // window composed its content). Polling layoutInfo on the EDT.
            val deadline = System.currentTimeMillis() + 15_000
            while (!laidOut.await(100, TimeUnit.MILLISECONDS)) {
                if (System.currentTimeMillis() > deadline) {
                    error("sheet list never laid out in the real window")
                }
                val state = listRef.get() ?: continue
                EventQueue.invokeLater {
                    val info = state.layoutInfo
                    if (info.totalItemsCount > 0 && info.visibleItemsInfo.isNotEmpty()) {
                        laidOut.countDown()
                    }
                }
            }

            val window = windowRef.get() ?: error("window missing")
            val center = Point(
                window.locationOnScreen.x + window.width / 2,
                window.locationOnScreen.y + window.height - 150,
            )
            val listState = listRef.get() ?: error("list state missing")
            val sheetState = sheetRef.get() ?: error("sheet state missing")
            val before = listState.firstVisibleItemIndex

            // Wheel-free assertions stay live even where the wheel probe
            // rejects the platform (below).
            preWheelAssert?.invoke(listState, sheetState)
            assumeWheelScrollWorks()

            val robot = Robot()
            robot.mouseMove(center.x, center.y)
            Thread.sleep(300)
            repeat(40) { robot.mouseWheel(1) }
            Thread.sleep(1_000)

            var after = listState.firstVisibleItemIndex
            if (after == before) {
                // Windows routes WM_MOUSEWHEEL to the foreground window; if the
                // sheet dialog lost the activation race, the wheel above went
                // to another app. Re-assert foreground on the dialog stack and
                // wheel once more before declaring failure — an infra retry,
                // not a second chance at the behavior under test.
                EventQueue.invokeAndWait {
                    window.toFront()
                    window.ownedWindows.filter { it.isVisible }.forEach { owned ->
                        owned.toFront()
                        owned.requestFocus()
                    }
                }
                Thread.sleep(500)
                robot.mouseMove(center.x, center.y)
                Thread.sleep(200)
                repeat(40) { robot.mouseWheel(1) }
                Thread.sleep(1_000)
                after = listState.firstVisibleItemIndex
            }
            assert(listState, sheetState, before, after)
        } finally {
            EventQueue.invokeLater { windowRef.get()?.dispose() }
        }
    }
}

/** [DesktopSheetWheelRobotTest.assumeWheelScrollWorks]'s cached measurement. */
private data class WheelProbeOutcome(val works: Boolean, val deliveredEvents: Int, val scrolledPx: Int)
