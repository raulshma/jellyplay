package com.raulshma.jellyplay.core.ui.tv

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the focus-scroll slowdown connection used on TV lazy lists:
 *
 *  - USER-INPUT deltas are scaled per axis by the configured fractions —
 *    `onPreScroll` "steals" `1 - fraction` of each delta (default y = 0.6);
 *  - every other scroll source (side effect / programmatic) passes through
 *    untouched as [Offset.Zero] — the connection must only slow real drags;
 *  - a zero-delay axis forwards that axis fully.
 */
class DelayedNestedScrollConnectionTest {

    @Test
    fun userInput_scaledByConfiguredFractions() {
        val connection = DelayedNestedScrollConnection(xDelay = 0.25f, yDelay = 0.6f)

        val consumed = connection.onPreScroll(
            available = Offset(100f, 100f),
            source = NestedScrollSource.UserInput,
        )

        assertEquals(25f, consumed.x)
        // 0.6f is not exact in binary — allow float rounding.
        assertEquals(60f, consumed.y, 1e-3f)
    }

    @Test
    fun defaultConstructor_stealsHalfPlusOfVerticalOnly() {
        val connection = DelayedNestedScrollConnection()

        val consumed = connection.onPreScroll(
            available = Offset(80f, 40f),
            source = NestedScrollSource.UserInput,
        )

        assertEquals(0f, consumed.x, "default xDelay is 0 — horizontal must pass untouched")
        assertEquals(24f, consumed.y, 1e-3f)
    }

    @Test
    fun nonUserInputSources_consumeNothing() {
        val connection = DelayedNestedScrollConnection(xDelay = 0.9f, yDelay = 0.9f)

        val consumed = connection.onPreScroll(
            available = Offset(100f, 100f),
            source = NestedScrollSource.SideEffect,
        )

        assertEquals(Offset.Zero, consumed)
    }

    @Test
    fun fullDelay_zeroDelayAxes_forwardAndBlockExactly() {
        val connection = DelayedNestedScrollConnection(xDelay = 1f, yDelay = 0f)

        val consumed = connection.onPreScroll(
            available = Offset(-50f, -50f),
            source = NestedScrollSource.UserInput,
        )

        assertEquals(-50f, consumed.x, "1f delay consumes the whole axis")
        // -50f * 0f is -0.0f — compare with tolerance so signed zero passes.
        assertEquals(0f, consumed.y, 1e-6f, "0f delay consumes nothing")
        assertTrue(consumed != Offset(-50f, -50f))
    }
}
