package com.raulshma.jellyplay.feature.player.video

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
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
 */
@OptIn(ExperimentalMaterial3Api::class)
class DesktopSheetWheelRobotTest {

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
        org.junit.Assume.assumeTrue(
            "robot wheel needs an interactive display",
            !java.awt.GraphicsEnvironment.isHeadless(),
        )
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
        robotWheelSheetCase(skipPartiallyExpanded = false) { listState, sheetState, before, after ->
            // With the TvSafeSheet fix, a default (partial) caller state is
            // substituted by a skip-partial state on desktop, so the tall
            // sheet's list viewport spans most of the window instead of the
            // half-height partial sheet that wheel scroll could never grow.
            val viewportHeight = listState.layoutInfo.viewportEndOffset -
                listState.layoutInfo.viewportStartOffset
            assertTrue(
                viewportHeight > 400,
                "desktop sheet must open fully expanded (list viewport height=$viewportHeight)",
            )
            assertTrue(
                after > before,
                "wheel must scroll the content (firstVisibleItemIndex $before -> $after)",
            )
        }

    private fun robotWheelSheetCase(
        skipPartiallyExpanded: Boolean,
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
