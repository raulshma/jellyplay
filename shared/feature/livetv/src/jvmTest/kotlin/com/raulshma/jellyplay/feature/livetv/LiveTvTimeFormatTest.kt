package com.raulshma.jellyplay.feature.livetv

import com.raulshma.jellyplay.core.model.LiveTvProgram
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Coverage for the Live-TV feature's one time module: the canonical lenient
 * ISO parse ([toInstantOrNull]), the shared "h:mm a"/date-label formatters
 * (channel-detail program timeline + recording schedule rows), and the
 * unified airing/progress folds ([isAiringAt] / [liveProgressFraction]).
 *
 * The locale is pinned to US for the run because DateTimeFormatter.ofPattern
 * resolves the am/pm text from the *default* FORMAT locale — without the pin,
 * a non-English machine (or CI runner) would render "14:30 nachm."-style
 * output and fail the exact-string assertions.
 */
class LiveTvTimeFormatTest {

    private lateinit var previousLocale: Locale

    /** Fixed reference instant for the airing/progress folds (no clock reads). */
    private val now: Instant = Instant.parse("2026-06-22T15:00:00Z")

    @BeforeTest
    fun setUp() {
        previousLocale = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
    }

    @AfterTest
    fun tearDown() {
        Locale.setDefault(Locale.Category.FORMAT, previousLocale)
    }

    private fun program(startIso: String?, endIso: String?) = LiveTvProgram(
        id = "p",
        name = "Program",
        channelId = "c",
        startDate = startIso,
        endDate = endIso,
    )

    // ── formatLiveTvTime ─────────────────────────────────────────────────────

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

    // ── formatLiveTvDateLabel (the former ScheduleViewModel.parseDateLabel) ──

    @Test
    fun date_label_formats_iso_offset_input_and_drops_unparseable_input() {
        // Same parse ladder as formatLiveTvTime; null (not raw) on failure so
        // the schedule's groupByDate drops the timer.
        assertEquals("Mon, Jun 22", formatLiveTvDateLabel("2026-06-22T14:30:00-05:00"))
        assertEquals("Mon, Jun 22", formatLiveTvDateLabel("2026-06-22T09:15:00Z"))
        assertNull(formatLiveTvDateLabel("not-a-date"))
        assertNull(formatLiveTvDateLabel(null))
        assertNull(formatLiveTvDateLabel(""))
    }

    // ── toInstantOrNull (the canonical lenient ladder) ───────────────────────

    @Test
    fun toInstantOrNull_parses_Z_and_explicit_offsets() {
        assertEquals(Instant.parse("2026-06-22T15:30:00Z"), "2026-06-22T15:30:00Z".toInstantOrNull())
        assertEquals(Instant.parse("2026-06-22T13:30:00Z"), "2026-06-22T15:30:00+02:00".toInstantOrNull())
    }

    @Test
    fun toInstantOrNull_parses_offset_less_strings_as_utc() {
        // The exact offset-less forms the former strict `Instant.parse` sites
        // (channel detail) returned null for — the C10 declared fix rides on
        // this UTC fallback.
        assertEquals(
            LocalDateTime.parse("2026-06-22T15:30:00").toInstant(ZoneOffset.UTC),
            "2026-06-22T15:30:00".toInstantOrNull(),
        )
        assertEquals(
            LocalDateTime.parse("2026-01-01T00:05:00").toInstant(ZoneOffset.UTC),
            "2026-01-01T00:05:00".toInstantOrNull(),
        )
    }

    @Test
    fun toInstantOrNull_returns_null_for_invalid_input() {
        assertNull("not-a-date".toInstantOrNull())
        assertNull("".toInstantOrNull())
    }

    // ── isAiringAt ───────────────────────────────────────────────────────────

    @Test
    fun isAiringAt_is_true_inside_the_half_open_window_and_false_past_or_future() {
        val start = now.minusSeconds(600)
        val end = now.plusSeconds(600)
        // airing
        assertTrue(isAiringAt(start = start, end = end, now = now))
        // past: now at/after end
        assertFalse(isAiringAt(start = start, end = end, now = end))
        assertFalse(isAiringAt(start = start, end = end, now = end.plusSeconds(300)))
        // future: now strictly before start
        assertFalse(isAiringAt(start = start, end = end, now = start.minusSeconds(300)))
    }

    @Test
    fun isAiringAt_window_is_start_inclusive_end_exclusive() {
        val end = now.plusSeconds(600)
        assertTrue(isAiringAt(start = now, end = end, now = now))
        assertFalse(isAiringAt(start = now.minusSeconds(600), end = now, now = now))
    }

    @Test
    fun isAiringAt_treats_null_bounds_as_unconstrained() {
        // Missing/unparseable timestamp = lenient "no constraint on that side"
        // (the former ChannelDetailViewModel semantics).
        assertTrue(isAiringAt(start = null, end = null, now = now))
        assertTrue(isAiringAt(start = null, end = now.plusSeconds(60), now = now))
        assertTrue(isAiringAt(start = now.minusSeconds(60), end = null, now = now))
        assertFalse(isAiringAt(start = now.plusSeconds(60), end = null, now = now))
    }

    @Test
    fun isAiringAt_program_parses_offset_less_dates_via_the_lenient_ladder() {
        // 14:30Z→16:30Z spans now (15:00Z); offset-less strings read as UTC.
        val airing = program(startIso = "2026-06-22T14:30:00", endIso = "2026-06-22T16:30:00")
        assertTrue(isAiringAt(airing, now))
        val past = program(startIso = "2026-06-22T12:30:00", endIso = "2026-06-22T13:30:00")
        assertFalse(isAiringAt(past, now))
        val future = program(startIso = "2026-06-22T16:30:00", endIso = "2026-06-22T17:30:00")
        assertFalse(isAiringAt(future, now))
    }

    @Test
    fun isAiringAt_program_with_bad_end_date_stays_lenient_not_disqualified() {
        // Unparseable endDate → null end → "not yet ended"; a started program
        // still counts as airing (pinned deliberately).
        val badEnd = program(startIso = "2026-06-22T14:30:00Z", endIso = "not-a-date")
        assertTrue(isAiringAt(badEnd, now))
        // …but a valid future start still disqualifies regardless of the end.
        val notYetStarted = program(startIso = "2026-06-22T16:30:00Z", endIso = "not-a-date")
        assertFalse(isAiringAt(notYetStarted, now))
    }

    // ── liveProgressFraction ─────────────────────────────────────────────────

    @Test
    fun liveProgressFraction_clamps_before_start_to_zero_and_after_end_to_one() {
        val start = Instant.parse("2026-06-22T15:00:00Z")
        val end = Instant.parse("2026-06-22T16:00:00Z")
        // Before start → today's behavior: negative fraction clamped to 0f.
        assertEquals(0f, liveProgressFraction(start = start, end = end, now = start.minusSeconds(300))!!)
        // Mid-point → 0.5.
        assertEquals(0.5f, liveProgressFraction(start = start, end = end, now = start.plusSeconds(1800))!!, 1e-6f)
        // After end → clamped to 1f.
        assertEquals(1f, liveProgressFraction(start = start, end = end, now = end.plusSeconds(300))!!)
    }

    @Test
    fun liveProgressFraction_is_null_for_missing_timestamps_or_non_positive_span() {
        // Missing/garbage program timestamps → null (callers render 0f).
        assertNull(liveProgressFraction(program(startIso = null, endIso = "2026-06-22T16:00:00Z"), now))
        assertNull(liveProgressFraction(program(startIso = "2026-06-22T15:00:00Z", endIso = "not-a-date"), now))
        // Zero / inverted span → null (today's behavior returned 0f).
        val start = Instant.parse("2026-06-22T15:00:00Z")
        assertNull(liveProgressFraction(start = start, end = start, now = now))
        assertNull(liveProgressFraction(start = start, end = start.minusSeconds(60), now = now))
    }

    @Test
    fun liveProgressFraction_program_parses_offset_less_dates_instead_of_zero() {
        // The C10 fix: offset-less strings previously strict-parsed to null and
        // forced 0f; the lenient ladder now yields the real fraction.
        val airing = program(startIso = "2026-06-22T14:30:00", endIso = "2026-06-22T15:30:00")
        assertEquals(0.5f, liveProgressFraction(airing, now)!!, 1e-6f)
    }
}
