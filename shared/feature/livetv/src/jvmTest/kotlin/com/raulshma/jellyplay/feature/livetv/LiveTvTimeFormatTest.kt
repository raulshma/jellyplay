package com.raulshma.jellyplay.feature.livetv

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Coverage for the shared Live-TV "h:mm a" start/end-time formatter
 * (channel-detail program timeline + recording schedule rows).
 *
 * The locale is pinned to US for the run because DateTimeFormatter.ofPattern
 * resolves the am/pm text from the *default* FORMAT locale — without the pin,
 * a non-English machine (or CI runner) would render "14:30 nachm."-style
 * output and fail the exact-string assertions.
 */
class LiveTvTimeFormatTest {

    private lateinit var previousLocale: Locale

    @BeforeTest
    fun setUp() {
        previousLocale = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(Locale.Category.FORMAT, previousLocale)
    }

    @Test
    fun null_and_blank_inputs_map_to_null_so_callers_pick_their_placeholder() {
        assertNull(formatLiveTvTime(null))
        assertNull(formatLiveTvTime(""))
        assertNull(formatLiveTvTime("   "))
    }

    @Test
    fun iso_offset_string_formats_the_wall_clock_as_h_mm_a() {
        assertEquals("2:30 PM", formatLiveTvTime("2026-01-01T14:30:00-05:00"))
        // 'Z' is a valid UTC offset — primary parse path, wall clock in UTC.
        assertEquals("9:15 AM", formatLiveTvTime("2026-01-01T09:15:00Z"))
        assertEquals("12:05 AM", formatLiveTvTime("2026-01-01T00:05:00+02:00"))
    }

    /**
     * Offset-less input falls through BOTH parses to the raw passthrough.
     *
     * This pins a real (and surprising) behavior of the implementation: the
     * naive fallback does `iso.replace("T", " ")` and then LocalDateTime.parse
     * — but ISO_LOCAL_DATE_TIME requires the literal 'T' separator, so the
     * T-to-space replacement defeats its own parser on the JVM (verified on
     * JDK 17: "Text '2026-01-01 12:30:00' could not be parsed at index 10").
     * Net effect: every offset-less timestamp renders raw instead of "12:30".
     * If the fallback is ever fixed, this test is the one to update.
     */
    @Test
    fun offsetless_string_falls_through_to_the_raw_input() {
        assertEquals("2026-01-01 12:30:00", formatLiveTvTime("2026-01-01 12:30:00"))
        assertEquals("2026-01-01T12:30:00", formatLiveTvTime("2026-01-01T12:30:00"))
    }

    @Test
    fun garbage_string_is_returned_raw_so_the_row_still_shows_something() {
        assertEquals("not-a-date", formatLiveTvTime("not-a-date"))
        assertEquals("2026-13-45T99:99:99", formatLiveTvTime("2026-13-45T99:99:99"))
    }
}
