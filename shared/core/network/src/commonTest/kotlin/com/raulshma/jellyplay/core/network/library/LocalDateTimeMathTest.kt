package com.raulshma.jellyplay.core.network.library

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the pure UTC-millis → local ISO offset formatter backing the wasm
 * NextUp `nextUpDateCutoff` (the java.time-less stand-in for
 * LocalDateTime.now().minusDays(n) + ISO_OFFSET_DATE_TIME).
 */
class LocalDateTimeMathTest {

    @Test
    fun `formats utc midnight with zero offset`() {
        // 1970-01-01T00:00:00Z in UTC.
        assertEquals("1970-01-01T00:00:00+00:00", isoLocalFromUtcMillis(0L, offsetBehindMinutes = 0))
    }

    @Test
    fun `positive offset zones are east of utc`() {
        // 2026-08-24T19:30:05Z; getTimezoneOffset() is west-positive:
        // -120 = UTC+2 (Berlin summer).
        assertEquals(
            "2026-08-24T21:30:05+02:00",
            isoLocalFromUtcMillis(1_787_599_805_000L, offsetBehindMinutes = -120),
        )
    }

    @Test
    fun `negative offset zones are west of utc`() {
        // +300 = UTC-5 (New York).
        assertEquals(
            "2026-08-24T14:30:05-05:00",
            isoLocalFromUtcMillis(1_787_599_805_000L, offsetBehindMinutes = 300),
        )
    }

    @Test
    fun `half-hour offsets format without padding loss`() {
        // +330 west-positive = UTC-5:30 → 19:30:05 - 5:30 = 14:00:05.
        assertEquals(
            "2026-08-24T14:00:05-05:30",
            isoLocalFromUtcMillis(1_787_599_805_000L, offsetBehindMinutes = 330),
        )
    }

    @Test
    fun `leap day rolls correctly`() {
        // 2024-02-29T23:30:00Z → in UTC+2 that is already 2024-03-01.
        assertEquals(
            "2024-03-01T01:30:00+02:00",
            isoLocalFromUtcMillis(1_709_249_400_000L, offsetBehindMinutes = -120),
        )
    }
}
