package com.raulshma.jellyplay.feature.arrqueue

import com.raulshma.jellyplay.core.model.arr.ArrRelease
import com.raulshma.jellyplay.core.model.arr.ArrReleaseHistoryStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the release sheet's pure reducer ([ReleaseSearchStateMachine]) — the
 * ArrQueuePresentationTest precedent: the machine carries every transition
 * rule, the screen only draws. Covers the search lifecycle (Loading →
 * Results/Empty/CacheMiss/Error, "Search again" keeping the sort), the info
 * expand toggle, and the grab arm (request → confirm → start → success/fail,
 * including FAILED badges outranking GRABBED, which the machine's input map
 * already carries — pinned here so the badge contract stays visible).
 */
class ReleaseSearchStateTest {

    private fun release(
        guid: String,
        score: Int = 0,
        seeders: Int? = 10,
        ageHours: Double? = 1.0,
        approved: Boolean = true,
    ) = ArrRelease(
        guid = guid,
        indexerId = 1,
        title = "Release $guid",
        customFormatScore = score,
        seeders = seeders,
        ageHours = ageHours,
        approved = approved,
    )

    private val results = ReleaseSearchState(
        phase = ReleaseSearchPhase.Results,
        releases = listOf(release("a"), release("b")),
        historyStatuses = mapOf("a" to ArrReleaseHistoryStatus.GRABBED),
    )

    // ── search lifecycle ─────────────────────────────────────────────────────

    @Test
    fun `search started clears per-search state and keeps the sort`() {
        val mid = results.copy(
            sort = ReleaseSort.SEEDERS,
            infoExpandedGuid = "a",
            confirmGrab = release("b"),
            grabError = "boom",
        )

        val started = ReleaseSearchStateMachine.reduce(mid, ReleaseSearchEvent.SearchStarted)

        assertEquals(ReleaseSearchPhase.Loading, started.phase)
        assertTrue(started.releases.isEmpty())
        assertTrue(started.historyStatuses.isEmpty())
        assertNull(started.infoExpandedGuid)
        assertNull(started.confirmGrab)
        assertNull(started.grabError)
        assertFalse(started.grabbing)
        assertEquals(ReleaseSort.SEEDERS, started.sort, "Search again keeps the user's ordering")
    }

    @Test
    fun `search success lands on results or empty by row count`() {
        val ok = ReleaseSearchStateMachine.reduce(
            ReleaseSearchState(),
            ReleaseSearchEvent.SearchSucceeded(listOf(release("a")), mapOf("a" to ArrReleaseHistoryStatus.FAILED)),
        )
        assertEquals(ReleaseSearchPhase.Results, ok.phase)
        assertEquals(mapOf("a" to ArrReleaseHistoryStatus.FAILED), ok.historyStatuses)

        val empty = ReleaseSearchStateMachine.reduce(
            ReleaseSearchState(),
            ReleaseSearchEvent.SearchSucceeded(emptyList(), emptyMap()),
        )
        assertEquals(ReleaseSearchPhase.Empty, empty.phase)
    }

    @Test
    fun `search failure splits cache miss from plain error`() {
        val miss = ReleaseSearchStateMachine.reduce(
            ReleaseSearchState(),
            ReleaseSearchEvent.SearchFailed("cold", cacheMiss = true),
        )
        assertEquals(ReleaseSearchPhase.CacheMiss, miss.phase)

        val plain = ReleaseSearchStateMachine.reduce(
            ReleaseSearchState(),
            ReleaseSearchEvent.SearchFailed("boom"),
        )
        assertEquals(ReleaseSearchPhase.Error("boom"), plain.phase)
    }

    // ── sorting ──────────────────────────────────────────────────────────────

    @Test
    fun `score sort orders by score descending with seeders breaking ties`() {
        val rows = listOf(
            release("low", score = -5),
            release("tie-low-seed", score = 10, seeders = 1),
            release("tie-high-seed", score = 10, seeders = 99),
            release("high", score = 50),
        )
        val sorted = ReleaseSearchStateMachine
            .reduce(results.copy(releases = rows), ReleaseSearchEvent.SortSelected(ReleaseSort.SCORE))
            .sortedReleases

        assertEquals(listOf("high", "tie-high-seed", "tie-low-seed", "low"), sorted.map { it.guid })
    }

    @Test
    fun `seeders sort puts unknowns last`() {
        val rows = listOf(
            release("unknown", seeders = null),
            release("few", seeders = 2),
            release("many", seeders = 80),
        )
        val sorted = ReleaseSearchStateMachine
            .reduce(results.copy(releases = rows), ReleaseSearchEvent.SortSelected(ReleaseSort.SEEDERS))
            .sortedReleases

        assertEquals(listOf("many", "few", "unknown"), sorted.map { it.guid })
    }

    @Test
    fun `age sort shows newest first with unknown ages last`() {
        val rows = listOf(
            release("stale", ageHours = 900.0),
            release("unknown", ageHours = null),
            release("fresh", ageHours = 0.5),
        )
        val sorted = ReleaseSearchStateMachine
            .reduce(results.copy(releases = rows), ReleaseSearchEvent.SortSelected(ReleaseSort.AGE))
            .sortedReleases

        assertEquals(listOf("fresh", "stale", "unknown"), sorted.map { it.guid })
    }

    // ── info expand ──────────────────────────────────────────────────────────

    @Test
    fun `info toggle expands then collapses the same row and switches rows`() {
        val expanded = ReleaseSearchStateMachine.reduce(results, ReleaseSearchEvent.InfoToggled("a"))
        assertEquals("a", expanded.infoExpandedGuid)

        val collapsed = ReleaseSearchStateMachine.reduce(expanded, ReleaseSearchEvent.InfoToggled("a"))
        assertNull(collapsed.infoExpandedGuid)

        val switched = ReleaseSearchStateMachine.reduce(expanded, ReleaseSearchEvent.InfoToggled("b"))
        assertEquals("b", switched.infoExpandedGuid)
    }

    // ── grab arm ─────────────────────────────────────────────────────────────

    @Test
    fun `grab request opens the confirm and clears a stale grab error`() {
        val withError = results.copy(grabError = "boom")
        val requested = ReleaseSearchStateMachine.reduce(
            withError,
            ReleaseSearchEvent.GrabRequested(release("b")),
        )

        assertEquals("b", requested.confirmGrab?.guid)
        assertNull(requested.grabError)
        assertEquals(ReleaseSearchPhase.Results, requested.phase)
    }

    @Test
    fun `grab dismissed closes the confirm without touching the rows`() {
        val pending = results.copy(confirmGrab = release("b"))

        val dismissed = ReleaseSearchStateMachine.reduce(pending, ReleaseSearchEvent.GrabDismissed)

        assertNull(dismissed.confirmGrab)
        assertEquals(2, dismissed.releases.size)
    }

    @Test
    fun `grab started closes the confirm raises the busy flag and clears the error`() {
        val pending = results.copy(
            confirmGrab = release("b"),
            grabError = "boom",
        )

        val started = ReleaseSearchStateMachine.reduce(pending, ReleaseSearchEvent.GrabStarted)

        assertNull(started.confirmGrab)
        assertTrue(started.grabbing)
        assertNull(started.grabError)
    }

    @Test
    fun `grab success removes the row and falls back to empty on the last one`() {
        val grabbed = ReleaseSearchStateMachine.reduce(
            results.copy(grabbing = true),
            ReleaseSearchEvent.GrabSucceeded(release("a")),
        )
        assertFalse(grabbed.grabbing)
        assertEquals(listOf("b"), grabbed.releases.map { it.guid })
        assertEquals(ReleaseSearchPhase.Results, grabbed.phase)

        val lastOne = ReleaseSearchStateMachine.reduce(
            grabbed,
            ReleaseSearchEvent.GrabSucceeded(release("b")),
        )
        assertEquals(ReleaseSearchPhase.Empty, lastOne.phase)
    }

    @Test
    fun `grab failure surfaces the message with the list intact`() {
        val failed = ReleaseSearchStateMachine.reduce(
            results.copy(grabbing = true),
            ReleaseSearchEvent.GrabFailed("server said no"),
        )

        assertFalse(failed.grabbing)
        assertEquals("server said no", failed.grabError)
        assertEquals(ReleaseSearchPhase.Results, failed.phase)
        assertEquals(2, failed.releases.size)
    }

    // ── override predicate ───────────────────────────────────────────────────

    @Test
    fun `needsOverride tracks the approved flag`() {
        assertTrue(!release("ok", approved = true).needsOverride)
        assertTrue(release("bad", approved = false).needsOverride)
    }
}
