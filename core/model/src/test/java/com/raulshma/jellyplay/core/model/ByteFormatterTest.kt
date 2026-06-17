package com.raulshma.jellyplay.core.model

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ByteFormatterTest {

    @Test
    fun testFormatBytes() {
        // Use Locale.US to ensure uniform decimal formatting (dot instead of comma)
        Locale.setDefault(Locale.US)

        assertEquals("0 B", 0L.formatBytes())
        assertEquals("500 B", 500L.formatBytes())
        assertEquals("1023 B", 1023L.formatBytes())

        assertEquals("1.0 KB", 1024L.formatBytes())
        assertEquals("150.0 KB", (150L * 1024).formatBytes())

        assertEquals("1.0 MB", (1024L * 1024).formatBytes())
        assertEquals("50.5 MB", (50.5 * 1024 * 1024).toLong().formatBytes())

        assertEquals("1.0 GB", (1024L * 1024 * 1024).formatBytes())
        assertEquals("2.5 GB", (2.5 * 1024 * 1024 * 1024).toLong().formatBytes())
    }

    @Test
    fun testFormatSpeed() {
        Locale.setDefault(Locale.US)

        assertEquals("", 0L.formatSpeed())
        assertEquals("", (-5L).formatSpeed())

        assertEquals("500 B/s", 500L.formatSpeed())
        assertEquals("1.0 KB/s", 1024L.formatSpeed())
        assertEquals("100.0 KB/s", (100L * 1024).formatSpeed())

        assertEquals("1.0 MB/s", (1024L * 1024).formatSpeed())
        assertEquals("10.2 MB/s", (10.2 * 1024 * 1024).toLong().formatSpeed())

        assertEquals("1.0 GB/s", (1024L * 1024 * 1024).formatSpeed())
    }

    @Test
    fun testFormatEta() {
        // Edge cases
        assertEquals("", formatEta(0, 0, 100))
        assertEquals("", formatEta(0, -100, 100))
        assertEquals("", formatEta(0, 100, 0))
        assertEquals("", formatEta(0, 100, -5))
        assertEquals("", formatEta(100, 100, 10))
        assertEquals("", formatEta(150, 100, 10))

        // Seconds remaining < 60
        // remaining = 1000 - 500 = 500 bytes. speed = 10 bytes/sec. secondsRemaining = 50.
        assertEquals("50s left", formatEta(500, 1000, 10))

        // Seconds remaining < 3600 (Minutes & Seconds)
        // remaining = 10000 - 4000 = 6000 bytes. speed = 5 bytes/sec. secondsRemaining = 1200 (20 minutes, 0 seconds).
        assertEquals("20m 0s left", formatEta(4000, 10000, 5))
        // secondsRemaining = 125 (2 minutes, 5 seconds)
        assertEquals("2m 5s left", formatEta(0, 125, 1))

        // Seconds remaining >= 3600 (Hours & Minutes)
        // secondsRemaining = 7300 (2 hours, 1 minute, 40 seconds) -> "2h 1m left"
        assertEquals("2h 1m left", formatEta(0, 7300, 1))
    }
}
