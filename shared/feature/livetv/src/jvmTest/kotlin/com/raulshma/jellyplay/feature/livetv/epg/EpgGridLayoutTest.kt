package com.raulshma.jellyplay.feature.livetv.epg

import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.LiveTvProgram
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Unit tests for the pure EPG layout helpers in [EpgGridLayout.kt].
 *
 * These cover the data-layer concerns of the grid:
 *  - Channel grouping & ordering
 *  - Windowing (program filtering/clamping)
 *  - Width/offset math (4 dp per minute)
 *  - Time-marker alignment to half-hour boundaries
 *  - Layout is now-independent (live state derived elsewhere)
 *
 * Rendering behaviour is verified via Compose UI tests elsewhere — here we
 * keep the tests fast and deterministic by working directly with the layout
 * data classes.
 */
class EpgGridLayoutTest {

    private val windowStart: Instant = Instant.parse("2026-06-22T14:00:00Z")
    private val windowEnd: Instant = Instant.parse("2026-06-22T18:00:00Z")

    private fun channel(id: String, name: String = id, number: String? = null) =
        LiveTvChannel(id = id, name = name, number = number)

    private fun program(
        id: String,
        channelId: String,
        startIso: String,
        endIso: String,
        name: String = "Program $id",
    ) = LiveTvProgram(
        id = id,
        name = name,
        channelId = channelId,
        startDate = startIso,
        endDate = endIso,
    )

    // ── buildEpgGridData ─────────────────────────────────────────────────────

    @Test
    fun buildEpgGridData_groups_programs_by_channel_and_preserves_channel_order() {
        val channels = listOf(channel("cnn"), channel("espn"), channel("hbo"))
        val programs = listOf(
            program("p1", "espn", "2026-06-22T15:00:00Z", "2026-06-22T16:00:00Z"),
            program("p2", "cnn", "2026-06-22T14:30:00Z", "2026-06-22T15:00:00Z"),
            program("p3", "espn", "2026-06-22T16:00:00Z", "2026-06-22T17:00:00Z"),
        )

        val grid = buildEpgGridData(channels, programs, windowStart, windowEnd)

        assertEquals(3, grid.rows.size)
        assertEquals(listOf("cnn", "espn", "hbo"), grid.rows.map { it.channel.id })
        assertEquals(listOf("p2"), grid.rows[0].timedPrograms.map { it.program.id })
        assertEquals(listOf("p1", "p3"), grid.rows[1].timedPrograms.map { it.program.id })
        assertTrue(grid.rows[2].timedPrograms.isEmpty())
    }

    @Test
    fun buildEpgGridData_filters_programs_entirely_outside_the_window() {
        val channels = listOf(channel("a"))
        val programs = listOf(
            // before window
            program("before", "a", "2026-06-22T12:00:00Z", "2026-06-22T13:00:00Z"),
            // after window
            program("after", "a", "2026-06-22T19:00:00Z", "2026-06-22T20:00:00Z"),
            // inside window
            program("inside", "a", "2026-06-22T15:00:00Z", "2026-06-22T15:30:00Z"),
        )

        val grid = buildEpgGridData(channels, programs, windowStart, windowEnd)

        assertEquals(1, grid.rows.size)
        assertEquals(listOf("inside"), grid.rows[0].timedPrograms.map { it.program.id })
    }

    @Test
    fun buildEpgGridData_keeps_programs_that_partially_overlap_the_window() {
        val channels = listOf(channel("a"))
        val programs = listOf(
            // starts before window, ends inside
            program("overlap-start", "a", "2026-06-22T13:00:00Z", "2026-06-22T15:00:00Z"),
            // starts inside, ends after window
            program("overlap-end", "a", "2026-06-22T17:00:00Z", "2026-06-22T19:00:00Z"),
        )

        val grid = buildEpgGridData(channels, programs, windowStart, windowEnd)

        assertEquals(2, grid.rows[0].timedPrograms.size)
    }

    @Test
    fun buildEpgGridData_sorts_programs_within_a_channel_by_start_time() {
        val channels = listOf(channel("a"))
        val programs = listOf(
            program("late", "a", "2026-06-22T17:00:00Z", "2026-06-22T17:30:00Z"),
            program("early", "a", "2026-06-22T14:00:00Z", "2026-06-22T14:30:00Z"),
            program("mid", "a", "2026-06-22T15:30:00Z", "2026-06-22T16:00:00Z"),
        )

        val grid = buildEpgGridData(channels, programs, windowStart, windowEnd)

        assertEquals(listOf("early", "mid", "late"), grid.rows[0].timedPrograms.map { it.program.id })
    }

    @Test
    fun buildEpgGridData_preserves_empty_channels_as_empty_rows() {
        val channels = listOf(channel("empty"))
        val grid = buildEpgGridData(channels, emptyList(), windowStart, windowEnd)
        assertEquals(1, grid.rows.size)
        assertTrue(grid.rows[0].timedPrograms.isEmpty())
    }

    // ── layoutChannelRow ────────────────────────────────────────────────────

    @Test
    fun layoutChannelRow_computes_start_offset_and_width_from_minutes_at_4_dp_per_minute() {
        val grid = buildEpgGridData(
            channels = listOf(channel("a")),
            programs = listOf(
                // 30-minute program starting 60 minutes into the window
                program("p1", "a", "2026-06-22T15:00:00Z", "2026-06-22T15:30:00Z"),
            ),
            windowStart = windowStart,
            windowEnd = windowEnd,
        )
        val layout = layoutChannelRow(grid.rows.first(), grid)

        assertEquals(1, layout.programLayouts.size)
        val pl = layout.programLayouts.first()
        // 60 minutes offset × 4 dp/min = 240 dp
        assertEquals(240f, pl.startOffsetDp, 0.01f)
        // 30 minutes duration × 4 dp/min = 120 dp
        assertEquals(120f, pl.widthDp, 0.01f)
    }

    @Test
    fun layoutChannelRow_clamps_programs_partially_outside_the_window() {
        val grid = buildEpgGridData(
            channels = listOf(channel("a")),
            programs = listOf(
                // starts before window, ends inside — should be clamped to window start
                program("p1", "a", "2026-06-22T13:00:00Z", "2026-06-22T15:00:00Z"),
            ),
            windowStart = windowStart,
            windowEnd = windowEnd,
        )

        val layout = layoutChannelRow(grid.rows.first(), grid)

        assertEquals(1, layout.programLayouts.size)
        val pl = layout.programLayouts.first()
        // Clamped start = windowStart = 0 dp
        assertEquals(0f, pl.startOffsetDp, 0.01f)
        // Clamped duration = 60 minutes = 240 dp
        assertEquals(240f, pl.widthDp, 0.01f)
    }

    @Test
    fun layoutChannelRow_is_purely_geometric_and_carries_no_live_state() {
        // The "is this program currently live" check is intentionally excluded
        // from the layout so the row geometry stays stable across the 30s
        // now-tick; live status is derived separately by the UI layer. This
        // test pins that contract: ProgramLayout exposes no isCurrent flag.
        val grid = buildEpgGridData(
            channels = listOf(channel("a")),
            programs = listOf(
                program("past", "a", "2026-06-22T14:00:00Z", "2026-06-22T14:30:00Z"),
                program("live", "a", "2026-06-22T14:45:00Z", "2026-06-22T15:15:00Z"),
                program("future", "a", "2026-06-22T16:00:00Z", "2026-06-22T16:30:00Z"),
            ),
            windowStart = windowStart,
            windowEnd = windowEnd,
        )

        val layout = layoutChannelRow(grid.rows.first(), grid)

        // All three programs are laid out regardless of wall-clock "now".
        assertEquals(listOf("past", "live", "future"), layout.programLayouts.map { it.program.id })
    }

    @Test
    fun layoutChannelRow_skips_programs_with_unparseable_timestamps() {
        val grid = buildEpgGridData(
            channels = listOf(channel("a")),
            programs = listOf(
                LiveTvProgram(
                    id = "bad",
                    name = "Bad",
                    channelId = "a",
                    startDate = "not-a-date",
                    endDate = "also-bad",
                ),
            ),
            windowStart = windowStart,
            windowEnd = windowEnd,
        )

        val layout = layoutChannelRow(grid.rows.first(), grid)

        // Unparseable programs should not crash and should be filtered out.
        assertTrue(layout.programLayouts.isEmpty())
    }

    // ── buildTimeMarkers ────────────────────────────────────────────────────

    @Test
    fun buildTimeMarkers_aligns_to_hour_boundaries_inside_the_window() {
        val start = Instant.parse("2026-06-22T14:00:00Z")
        val end = Instant.parse("2026-06-22T16:00:00Z")

        val markers = buildTimeMarkers(start, end)

        // 14:00, 14:30, 15:00, 15:30 — exclusive of 16:00 end
        assertEquals(4, markers.size)
        assertEquals(start, markers.first())
        assertEquals(start.plus(90, ChronoUnit.MINUTES), markers.last())
    }

    @Test
    fun buildTimeMarkers_returns_empty_list_for_an_inverted_window() {
        val start = Instant.parse("2026-06-22T18:00:00Z")
        val end = Instant.parse("2026-06-22T14:00:00Z")
        val markers = buildTimeMarkers(start, end)
        assertTrue(markers.isEmpty())
    }

    // ── offsetDp ────────────────────────────────────────────────────────────

    @Test
    fun offsetDp_scales_minutes_by_4_dp_per_minute() {
        val start = Instant.parse("2026-06-22T14:00:00Z")
        val ts = Instant.parse("2026-06-22T15:30:00Z") // 90 minutes later
        assertEquals(360f, ts.offsetDp(start), 0.01f)
    }

    @Test
    fun offsetDp_is_negative_for_timestamps_before_the_window() {
        val start = Instant.parse("2026-06-22T14:00:00Z")
        val ts = Instant.parse("2026-06-22T13:00:00Z") // 60 minutes earlier
        assertEquals(-240f, ts.offsetDp(start), 0.01f)
    }

    // ── toInstantOrNull ─────────────────────────────────────────────────────

    @Test
    fun toInstantOrNull_parses_ISO_8601_with_offset() {
        val instant = "2026-06-22T15:30:00Z".toInstantOrNull()
        assertNotNull(instant)
        assertEquals(Instant.parse("2026-06-22T15:30:00Z"), instant)
    }

    @Test
    fun toInstantOrNull_parses_ISO_8601_with_explicit_offset() {
        val instant = "2026-06-22T15:30:00+02:00".toInstantOrNull()
        assertNotNull(instant)
        assertEquals(Instant.parse("2026-06-22T13:30:00Z"), instant)
    }

    @Test
    fun toInstantOrNull_parses_LocalDateTime_as_UTC_fallback() {
        val instant = "2026-06-22T15:30:00".toInstantOrNull()
        assertNotNull(instant)
        assertEquals(
            LocalDateTime.parse("2026-06-22T15:30:00").toInstant(ZoneOffset.UTC),
            instant,
        )
    }

    @Test
    fun toInstantOrNull_returns_null_for_invalid_input() {
        assertEquals(null, "not-a-date".toInstantOrNull())
        assertEquals(null, "".toInstantOrNull())
    }

    // ── EpgGridData ─────────────────────────────────────────────────────────

    @Test
    fun EpgGridData_reports_total_width_based_on_4_dp_per_minute_window() {
        val grid = buildEpgGridData(
            channels = listOf(channel("solo")),
            programs = emptyList(),
            windowStart = windowStart,
            windowEnd = windowEnd,
        )
        // 4-hour window × 60 minutes × 4 dp = 960 dp
        assertEquals(960f, grid.totalWidthDp, 0.01f)
        assertEquals(240L, grid.totalMinutes)
        assertFalse(grid.isEmpty)
    }

    @Test
    fun EpgGridData_isEmpty_when_no_channels() {
        val grid = EpgGridData(
            windowStart = windowStart,
            windowEnd = windowEnd,
            rows = emptyList(),
        )
        assertTrue(grid.isEmpty)
    }
}
