package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the ONE storage-byte table ([toStorageBytesValue] + [formatBytes]) that
 * the four formerly-drifted formatters (admin Logs, PhotoViewer,
 * DetailDownloadDialog, ArrQueue) now read instead of declaring their own
 * divisor/rounding conventions: band boundaries on the ÷1024 ladder, the
 * deliberately-no-TB-band policy (terabyte counts stay in the GB arm), and
 * negative/zero handling (any value below 1024 — including negatives — stays
 * in the bytes band verbatim).
 */
class FormatStorageBytesTest {

    // ── Band boundaries: B → KB → MB → GB (÷1024 ladder) ────────────────────

    @Test
    fun `storage bytes table across band boundaries`() {
        assertEquals("0 B", 0L.formatBytes())
        assertEquals("1 B", 1L.formatBytes())
        assertEquals("1023 B", 1023L.formatBytes())
        assertEquals("1.0 KB", 1024L.formatBytes())
        assertEquals("1.5 KB", (1024L + 512).formatBytes())
        assertEquals("1023.5 KB", (1024L * 1024 - 512).formatBytes())
        assertEquals("1.0 MB", (1024L * 1024).formatBytes())
        assertEquals("1.5 MB", ((1024L + 512) * 1024).formatBytes())
        assertEquals("1.0 GB", (1024L * 1024 * 1024).formatBytes())
        assertEquals("1.5 GB", ((1024L + 512) * 1024 * 1024).formatBytes())
    }

    @Test
    fun `terabyte counts stay in the gb band`() {
        // Declared policy: no TB band — 1 TB renders as 1024.0 GB (same as the
        // pre-fold house formatters; adding a TB arm would be a product call).
        assertEquals("1024.0 GB", (1024L * 1024 * 1024 * 1024).formatBytes())
        assertEquals("2048.0 GB", (2L * 1024 * 1024 * 1024 * 1024).formatBytes())
    }

    @Test
    fun `zero and negative counts stay in the bytes band verbatim`() {
        // Any value below 1024 — negatives included — never leaves the bytes
        // arm; no sign-flipping, no clamping, no empty string.
        assertEquals("0 B", 0L.formatBytes())
        assertEquals("-1 B", (-1L).formatBytes())
        assertEquals("-500 B", (-500L).formatBytes())
        assertEquals("-1024 B", (-1024L).formatBytes())
        assertEquals("-2048 B", (-2048L).formatBytes())
    }

    // ── The parts half (localized wrappers feed off this) ───────────────────

    @Test
    fun `storage bytes parts carry band and scaled value`() {
        // The localized wrappers (DetailDownloadDialog) keep their per-unit
        // string resources and feed them these number+unit parts — the divisor
        // logic must stay here and only here.
        assertEquals(StorageBytesValue(500.0, StorageBytesUnit.B), 500L.toStorageBytesValue())
        assertEquals(StorageBytesValue(1.5, StorageBytesUnit.KB), 1536L.toStorageBytesValue())
        assertEquals(StorageBytesValue(1.5, StorageBytesUnit.MB), (1536L * 1024).toStorageBytesValue())
        assertEquals(StorageBytesValue(1.5, StorageBytesUnit.GB), (1536L * 1024 * 1024).toStorageBytesValue())
    }

    @Test
    fun `parts and string formatting agree on the band`() {
        // The same input must never pick different bands through the two
        // halves — that agreement IS the one-table invariant.
        for (bytes in listOf(0L, 1L, 1023L, 1024L, 1536L, 1024L * 1024, 1024L * 1024 * 1024, -1L, -1024L)) {
            val (value, unit) = bytes.toStorageBytesValue()
            val number = if (unit == StorageBytesUnit.B) value.toLong().toString() else {
                val tenths = kotlin.math.round(value * 10.0).toLong()
                "${tenths / 10}.${tenths % 10}"
            }
            assertEquals("$number ${unit.suffix}", bytes.formatBytes())
        }
    }

    // ── The house "%.Nf" replacements (string-resource call sites feed these) ──

    @Test
    fun `format one decimal matches house convention`() {
        // compose-resources' stringResource only substitutes %n$s/%n$d, so
        // callers pre-format floats with formatFixed — pin its rendering:
        // always one digit after the '.', '.' separator, half-rounded.
        assertEquals("5.2", formatFixed(5.234, 1))
        assertEquals("0.0", formatFixed(0.0, 1))
        assertEquals("1.0", formatFixed(1.0, 1))
        assertEquals("0.3", formatFixed(0.25, 1))
        assertEquals("1024.0", formatFixed(1024.0, 1))
        assertEquals("0.1", formatFixed(0.05, 1))
    }

    @Test
    fun `format two decimals pads the fraction`() {
        // The playback-speed slider needs the second digit — including the
        // zero-padded ones ("1.05", not "1.5").
        assertEquals("0.25", formatFixed(0.25, 2))
        assertEquals("1.00", formatFixed(1.0, 2))
        assertEquals("1.05", formatFixed(1.05, 2))
        assertEquals("2.00", formatFixed(2.0, 2))
        assertEquals("0.30", formatFixed(0.299, 2))
    }
}
