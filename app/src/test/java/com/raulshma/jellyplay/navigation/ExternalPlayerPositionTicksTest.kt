package com.raulshma.jellyplay.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [externalPlayerPositionTicks] — the external-player ActivityResult
 * position parse that used to live inline in JellyPlayApp's launcher
 * callback: "position"/"positionMs" alias, Number coercion, `>= 0` gate,
 * ms→ticks ×10_000, everything else -1 (no resume credit).
 */
class ExternalPlayerPositionTicksTest {

    @Test
    fun `missing extras parse to -1`() {
        assertEquals(-1L, externalPlayerPositionTicks(position = null, positionMs = null))
    }

    @Test
    fun `position extra converts ms to ticks`() {
        assertEquals(50_000L, externalPlayerPositionTicks(position = 5, positionMs = null))
        assertEquals(50_000_000L, externalPlayerPositionTicks(position = 5_000L, positionMs = null))
    }

    @Test
    fun `positionMs alias is consulted when position is absent`() {
        // 1_234_567_890 ms × 10_000 = 12_345_678_900_000 ticks.
        assertEquals(
            12_345_678_900_000L,
            externalPlayerPositionTicks(position = null, positionMs = 1_234_567_890L),
        )
        // A present-but-null "position" falls through to the alias too — the
        // same elvis the inline `get("position") ?: get("positionMs")` had.
        assertEquals(
            50_000_000L,
            externalPlayerPositionTicks(position = null, positionMs = 5_000L),
        )
    }

    @Test
    fun `position wins over the alias when both are present`() {
        assertEquals(50_000L, externalPlayerPositionTicks(position = 5, positionMs = 9_999L))
    }

    @Test
    fun `number subtypes coerce through toLong — fractional values truncate`() {
        // Float and Double subtypes of the extra bundle.
        assertEquals(900_000L, externalPlayerPositionTicks(position = 90.5f, positionMs = null))
        assertEquals(900_000L, externalPlayerPositionTicks(position = 90.5, positionMs = null))
        // Short/Byte ride Number too.
        assertEquals(70_000L, externalPlayerPositionTicks(position = 7.toShort(), positionMs = null))
    }

    @Test
    fun `zero is a valid position — start from the beginning`() {
        assertEquals(0L, externalPlayerPositionTicks(position = 0, positionMs = null))
        assertEquals(0L, externalPlayerPositionTicks(position = 0L, positionMs = null))
    }

    @Test
    fun `negative positions are rejected`() {
        assertEquals(-1L, externalPlayerPositionTicks(position = -1, positionMs = null))
        assertEquals(-1L, externalPlayerPositionTicks(position = -100L, positionMs = null))
    }

    @Test
    fun `non-numeric junk parses as no position`() {
        assertEquals(-1L, externalPlayerPositionTicks(position = "not-a-number", positionMs = null))
        assertEquals(-1L, externalPlayerPositionTicks(position = true, positionMs = null))
    }
}
