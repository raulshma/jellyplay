package com.raulshma.jellyplay.feature.livetv

import com.raulshma.jellyplay.core.model.LiveTvProgram
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Coverage for the LiveNowWindow fold — [LiveTvProgramWindow.currentAndNext],
 * the now/next pick player-live's LiveTvPlayerViewModel converged on (its
 * former strict-parse Triple scan, expressed through the canonical
 * [isAiringAt]/[toInstantOrNull] vocabulary). List order is the tie-break;
 * the half-open window boundaries and the lenient-parse legs are pinned here.
 */
class LiveTvProgramWindowTest {

    /** Fixed reference instant — no clock reads (the caller supplies now). */
    private val now: Instant = Instant.parse("2026-06-22T15:00:00Z")

    private fun program(
        id: String,
        startIso: String?,
        endIso: String?,
    ) = LiveTvProgram(
        id = id,
        name = "Program $id",
        channelId = "c",
        startDate = startIso,
        endDate = endIso,
    )

    @Test
    fun empty_list_yields_null_current_and_null_next() {
        assertNull(LiveTvProgramWindow.currentAndNext(emptyList(), now).first)
        assertNull(LiveTvProgramWindow.currentAndNext(emptyList(), now).second)
    }

    @Test
    fun no_current_program_but_one_future_yields_null_current_and_that_future_next() {
        val future = program("p-future", "2026-06-22T16:30:00Z", "2026-06-22T17:30:00Z")
        val (current, next) = LiveTvProgramWindow.currentAndNext(listOf(future), now)
        assertNull(current)
        assertEquals(future, next)
    }

    @Test
    fun current_and_next_are_picked_in_list_order() {
        val past = program("p-past", "2026-06-22T13:00:00Z", "2026-06-22T14:00:00Z")
        val airing = program("p-air", "2026-06-22T14:30:00Z", "2026-06-22T16:30:00Z")
        val future = program("p-next", "2026-06-22T16:30:00Z", "2026-06-22T17:30:00Z")
        val later = program("p-later", "2026-06-22T17:30:00Z", "2026-06-22T18:30:00Z")

        val (current, next) = LiveTvProgramWindow.currentAndNext(
            listOf(past, airing, future, later),
            now,
        )
        assertEquals(airing, current)
        // The FIRST strictly-future start in list order — the earliest-start
        // pick rides the server's ascending order, never a re-sort.
        assertEquals(future, next)
    }

    @Test
    fun start_boundary_is_inclusive_program_starting_exactly_now_is_current() {
        val startingNow = program("p-start", "2026-06-22T15:00:00Z", "2026-06-22T17:00:00Z")
        val (current, _) = LiveTvProgramWindow.currentAndNext(listOf(startingNow), now)
        assertEquals(startingNow, current)
    }

    @Test
    fun end_boundary_is_exclusive_program_ending_exactly_now_is_neither_current_nor_next() {
        val endedNow = program("p-end", "2026-06-22T13:00:00Z", "2026-06-22T15:00:00Z")
        val (current, next) = LiveTvProgramWindow.currentAndNext(listOf(endedNow), now)
        assertNull(current)
        assertNull(next)
    }

    @Test
    fun offsetless_timestamps_parse_through_the_lenient_ladder() {
        // The C10 widening: the former strict `Instant.parse` scan returned
        // null for exactly these forms; the lenient ladder reads them as UTC.
        val airing = program("p-air", "2026-06-22T14:30:00", "2026-06-22T16:30:00")
        val future = program("p-next", "2026-06-22T16:30:00", "2026-06-22T17:30:00")
        val (current, next) = LiveTvProgramWindow.currentAndNext(listOf(airing, future), now)
        assertEquals(airing, current)
        assertEquals(future, next)
    }

    @Test
    fun equal_future_starts_keep_the_first_in_list_order_as_next() {
        val first = program("p-a", "2026-06-22T14:30:00Z", "2026-06-22T16:30:00Z")
        val tieA = program("p-b", "2026-06-22T16:30:00Z", "2026-06-22T17:30:00Z")
        val tieB = program("p-c", "2026-06-22T16:30:00Z", "2026-06-22T17:00:00Z")
        val (_, next) = LiveTvProgramWindow.currentAndNext(listOf(first, tieA, tieB), now)
        assertEquals(tieA, next)
    }

    @Test
    fun next_skips_the_current_program_id() {
        // The current program appears again with a future start (weird server
        // data): the id guard still skips it for the next pick.
        val current = program("p-air", "2026-06-22T14:30:00Z", "2026-06-22T15:30:00Z")
        val rerun = program("p-air", "2026-06-22T16:30:00Z", "2026-06-22T17:30:00Z")
        val (_, next) = LiveTvProgramWindow.currentAndNext(listOf(current, rerun), now.plus(15.seconds))
        assertNull(next)
    }

    @Test
    fun unparseable_bounds_stay_lenient_for_current_and_disqualify_next() {
        // Canonical isAiringAt semantics: a null bound is unconstrained, so a
        // started program with a garbage end is still current (the behavior
        // delta vs the former both-bounds-required player-live scan — pinned).
        val badEnd = program("p-bad-end", "2026-06-22T14:30:00Z", "not-a-date")
        val (current, _) = LiveTvProgramWindow.currentAndNext(listOf(badEnd), now)
        assertEquals(badEnd, current)

        // …and an unparseable start with a live end reads as "already
        // started" — still current, but never eligible as next.
        val badStart = program("p-bad-start", "not-a-date", "2026-06-22T17:30:00Z")
        val (current2, next2) = LiveTvProgramWindow.currentAndNext(listOf(badStart), now)
        assertEquals(badStart, current2)
        assertNull(next2)
    }
}
