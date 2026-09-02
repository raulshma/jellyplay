package com.raulshma.jellyplay.core.model

import kotlin.test.assertEquals
import kotlin.test.Test

class ByteFormatterTest {

    @Test
    fun testFormatBytes() {
        // ByteFormatter is a pure-Kotlin replacement always emitting '.' as the
        // decimal separator, so no Locale pinning is needed on any target.

        assertEquals(
0L.formatBytes(),
"0 B",
)
        assertEquals(
500L.formatBytes(),
"500 B",
)
        assertEquals(
1023L.formatBytes(),
"1023 B",
)

        assertEquals(
1024L.formatBytes(),
"1.0 KB",
)
        assertEquals(
(150L * 1024).formatBytes(),
"150.0 KB",
)

        assertEquals(
(1024L * 1024).formatBytes(),
"1.0 MB",
)
        assertEquals(
(50.5 * 1024 * 1024).toLong().formatBytes(),
"50.5 MB",
)

        assertEquals(
(1024L * 1024 * 1024).formatBytes(),
"1.0 GB",
)
        assertEquals(
(2.5 * 1024 * 1024 * 1024).toLong().formatBytes(),
"2.5 GB",
)
    }

    @Test
    fun testFormatSpeed() {

        assertEquals(
0L.formatSpeed(),
"",
)
        assertEquals(
(-5L).formatSpeed(),
"",
)

        assertEquals(
500L.formatSpeed(),
"500 B/s",
)
        assertEquals(
1024L.formatSpeed(),
"1.0 KB/s",
)
        assertEquals(
(100L * 1024).formatSpeed(),
"100.0 KB/s",
)

        assertEquals(
(1024L * 1024).formatSpeed(),
"1.0 MB/s",
)
        assertEquals(
(10.2 * 1024 * 1024).toLong().formatSpeed(),
"10.2 MB/s",
)

        assertEquals(
(1024L * 1024 * 1024).formatSpeed(),
"1.0 GB/s",
)
    }

    @Test
    fun testFormatEta() {
        // Edge cases
        assertEquals(
formatEta(0, 0, 100),
"",
)
        assertEquals(
formatEta(0, -100, 100),
"",
)
        assertEquals(
formatEta(0, 100, 0),
"",
)
        assertEquals(
formatEta(0, 100, -5),
"",
)
        assertEquals(
formatEta(100, 100, 10),
"",
)
        assertEquals(
formatEta(150, 100, 10),
"",
)

        // Seconds remaining < 60
        // remaining = 1000 - 500 = 500 bytes. speed = 10 bytes/sec. secondsRemaining = 50.
        assertEquals(
formatEta(500, 1000, 10),
"50s left",
)

        // Seconds remaining < 3600 (Minutes & Seconds)
        // remaining = 10000 - 4000 = 6000 bytes. speed = 5 bytes/sec. secondsRemaining = 1200 (20 minutes, 0 seconds).
        assertEquals(
formatEta(4000, 10000, 5),
"20m 0s left",
)
        // secondsRemaining = 125 (2 minutes, 5 seconds)
        assertEquals(
formatEta(0, 125, 1),
"2m 5s left",
)

        // Seconds remaining >= 3600 (Hours & Minutes)
        // secondsRemaining = 7300 (2 hours, 1 minute, 40 seconds) -> "2h 1m left"
        assertEquals(
formatEta(0, 7300, 1),
"2h 1m left",
)
    }
}
