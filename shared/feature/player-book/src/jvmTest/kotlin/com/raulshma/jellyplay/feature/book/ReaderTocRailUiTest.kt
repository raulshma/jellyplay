@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.raulshma.jellyplay.feature.book

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compose UI regression for the TOC tick rail's reported dead tap: a tap on
 * the rail must fire onJump with the tick under the finger and must NOT fall
 * through to the full-screen tap zone a reader mounts underneath (a
 * fall-through would toggle the chrome on every tick tap). The drag path
 * must extrapolate through the raw unclamped rows and commit on release — a
 * drag upward past the rail's top keeps stepping earlier chapters and lands
 * on the clamped first one. runComposeUiTest injects through the real
 * gesture detectors; no OS window layer involved.
 */
class ReaderTocRailUiTest {

    private val ticks = (0 until 12).map { ReaderTocTick(label = "Chapter $it", href = "ch$it.xhtml") }

    @Test
    fun `tap on the rail jumps to the tick under the finger`() = runComposeUiTest {
        val jumpedTo = AtomicReference<String?>(null)
        var fellThrough = false
        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures { fellThrough = true }
                        },
                ) {
                    ReaderTocRail(
                        ticks = ticks,
                        currentIndex = 5,
                        onJump = { tick -> jumpedTo.set(tick.label) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .testTag("toc_rail"),
                    )
                }
            }
        }
        onNodeWithTag("toc_rail").performTouchInput { click(center) }
        assertEquals(
            "Chapter 5",
            jumpedTo.get(),
            "tap on the rail center (the current tick row) must jump to that entry",
        )
        assertTrue(!fellThrough, "rail tap must not fall through to the reader's tap zones")
    }

    @Test
    fun `drag up the rail commits the tick under the release point`() = runComposeUiTest {
        val jumpedTo = AtomicReference<String?>(null)
        setContent {
            MaterialTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    ReaderTocRail(
                        ticks = ticks,
                        currentIndex = 5,
                        onJump = { tick -> jumpedTo.set(tick.label) },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .testTag("toc_rail"),
                    )
                }
            }
        }
        onNodeWithTag("toc_rail").performTouchInput {
            down(center)
            // A REAL drag is a stream of small moves ending at the target; the
            // release commits the LAST sampled row (row 0 → window entry 3).
            repeat(4) {
                moveBy(Offset(0f, -6f), delayMillis = 20)
            }
            up()
        }
        assertEquals(
            "Chapter 3",
            jumpedTo.get(),
            "drag release on the top tick row (TOC entry 3 of the ±2 window around 5) must commit it",
        )
    }
}
