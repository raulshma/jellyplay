package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals

/** Pins the shared engine→server duration fallback ladder. */
class EngineDurationFallbackTest {

    @Test
    fun positiveEngineDuration_wins() {
        assertEquals(123_456L, resolveDurationMs(123_456L, 999_999L))
    }

    @Test
    fun nonPositiveEngineDuration_fallsBackToServer() {
        assertEquals(999_999L, resolveDurationMs(0L, 999_999L))
        assertEquals(999_999L, resolveDurationMs(-5L, 999_999L))
        // Media3's C.TIME_UNSET sentinel is negative — same ladder, no special case.
        assertEquals(999_999L, resolveDurationMs(Long.MIN_VALUE, 999_999L))
    }
}
