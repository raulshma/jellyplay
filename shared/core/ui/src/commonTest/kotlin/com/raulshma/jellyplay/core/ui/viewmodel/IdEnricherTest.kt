package com.raulshma.jellyplay.core.ui.viewmodel

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Decision-table pins for [IdEnricher], the id-keyed enrichment core the
 * Requests media/arr fan-outs, the Upcoming Calendar's poster enrichment and
 * Next Up Exclusions' sequential hydration route through. The per-site
 * choreography stays pinned by the feature suites
 * (`RequestsViewModelEnrichMergeTest`, `UpcomingCalendarViewModelTest`,
 * `NextUpExcludedViewModelTest`); this suite pins what the MODULE owns: the
 * concurrency bound, fan-out-time skip, the snapshot-atomic merge, the
 * per-item failure swallow (null and thrown alike), and the [IdEnricher.Retry.NEVER]
 * failed-id latch.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class IdEnricherTest {

    // ── concurrency bound ──────────────────────────────────────────────────

    @Test
    fun fan_out_is_bounded_by_the_concurrency_permits() = runTest {
        val gates = (1..5).associateWith { CompletableDeferred<Unit>() }
        // Plain var, not AtomicInteger: the enricher launches only on the
        // scope it's given (runTest's single-threaded TestScope here), so the
        // fetch bodies interleave only at suspension points on one thread —
        // the same discipline the call sites' merge closures rely on.
        var active = 0
        val started = mutableListOf<Int>()
        val merged = mutableListOf<Int>()

        val enricher = IdEnricher<Int, String>(
            scope = this,
            concurrency = 2,
            fetch = { id ->
                started += id
                active++
                try {
                    gates.getValue(id).await()
                    "p$id"
                } finally {
                    active--
                }
            },
            merge = { id, _ -> merged += id },
        )

        enricher.enrich(listOf(1, 2, 3, 4, 5))
        runCurrent() // the first two fetches park on their gates

        assertEquals(2, active, "exactly `concurrency` fetches may run at once")
        assertEquals(listOf(1, 2), started)

        gates.getValue(1).complete(Unit)
        // Idle — not a single runCurrent: the release→re-acquire hop through
        // the semaphore's channel needs the extra scheduler passes to hand the
        // freed permit to id 3's parked acquire before assertions read it.
        advanceUntilIdle()
        assertEquals(2, active)
        // The permit recycles: id 3 started while id 2 is still parked. Active
        // never exceeds `concurrency` — a finishing fetch decrements before
        // its release hands the permit on.
        assertEquals(listOf(1, 2, 3), started)

        gates.getValue(2).complete(Unit)
        gates.getValue(3).complete(Unit)
        gates.getValue(4).complete(Unit)
        gates.getValue(5).complete(Unit)
        advanceUntilIdle()

        assertEquals(0, active)
        assertEquals((1..5).toList(), started)
        assertEquals(setOf(1, 2, 3, 4, 5), merged.toSet())
    }

    // ── skip-cached ────────────────────────────────────────────────────────

    @Test
    fun ids_claimed_by_the_skip_predicate_never_fetch() = runTest {
        val fetched = mutableListOf<Int>()
        val merged = mutableListOf<Int>()
        val enricher = IdEnricher<Int, String>(
            scope = this,
            fetch = { id -> fetched += id; "p$id" },
            merge = { id, _ -> merged += id },
        )

        enricher.enrich(
            ids = listOf(1, 2, 3),
            skip = { it == 2 },
        )
        advanceUntilIdle()

        assertEquals(listOf(1, 3), fetched)
        assertEquals(listOf(1, 3), merged, "skipped ids merge nothing")
    }

    // ── snapshot-atomic merge ──────────────────────────────────────────────

    @Test
    fun out_of_order_completions_both_land_and_each_sees_the_latest_state() = runTest {
        val gates = mapOf(
            1 to CompletableDeferred<Unit>(),
            2 to CompletableDeferred<Unit>(),
        )
        val state = mutableListOf<String>()
        val stateAtMerge = mutableListOf<List<String>>()
        val enricher = IdEnricher<Int, String>(
            scope = this,
            fetch = { id ->
                gates.getValue(id).await()
                "p$id"
            },
            // Read-modify-write of the site's state, as the call sites' merge
            // closures do — the read must see whatever earlier completions wrote.
            merge = { id, payload ->
                stateAtMerge += state.toList()
                state += "$id:$payload"
            },
        )

        enricher.enrich(listOf(1, 2))
        runCurrent()
        gates.getValue(2).complete(Unit)
        runCurrent()
        gates.getValue(1).complete(Unit)
        advanceUntilIdle()

        // Completion 1's merge ran after completion 2's and saw its write.
        assertEquals(listOf(emptyList(), listOf("2:p2")), stateAtMerge)
        assertEquals(listOf("2:p2", "1:p1"), state)
    }

    // ── per-item failure swallow ───────────────────────────────────────────

    @Test
    fun a_thrown_fetch_is_swallowed_and_does_not_disturb_siblings() = runTest {
        val merged = mutableListOf<Int>()
        val settled = mutableListOf<Int>()
        val enricher = IdEnricher<Int, String>(
            scope = this,
            fetch = { id -> if (id == 2) error("boom") else "p$id" },
            merge = { id, _ -> merged += id },
        )

        enricher.enrich(
            ids = listOf(1, 2, 3),
            onSettled = { settled += it },
        )
        advanceUntilIdle()

        assertEquals(listOf(1, 3), merged)
        assertEquals(listOf(1, 2, 3), settled, "every fan-out id settles, merged or swallowed")
    }

    @Test
    fun a_null_payload_merges_nothing() = runTest {
        val merged = mutableListOf<Int>()
        val enricher = IdEnricher<Int, String>(
            scope = this,
            fetch = { id -> if (id == 2) null else "p$id" },
            merge = { id, _ -> merged += id },
        )

        enricher.enrich(listOf(1, 2))
        advanceUntilIdle()

        assertEquals(listOf(1), merged)
    }

    // ── the NEVER failed-id latch ──────────────────────────────────────────

    @Test
    fun never_latch_skips_failed_ids_until_pruned() = runTest {
        var calls = 0
        val merged = mutableSetOf<Int>()
        var failNext = true
        val enricher = IdEnricher<Int, String>(
            scope = this,
            retry = IdEnricher.Retry.NEVER,
            fetch = { id ->
                calls++
                if (id == 2 && failNext) error("flaky") else "p$id"
            },
            merge = { id, _ -> merged += id },
        )

        enricher.enrich(listOf(1, 2))
        advanceUntilIdle()

        assertEquals(setOf(1), merged)
        assertTrue(enricher.hasFailed(2))

        // A second pass (the reconcile-loop shape): the un-latched id may
        // re-fetch; the latched one must not.
        enricher.enrich(listOf(1, 2))
        advanceUntilIdle()
        assertEquals(3, calls, "only the un-latched id re-fetched")

        // Pruning (the id left and came back) releases the latch — and a
        // succeeding fetch clears it.
        enricher.pruneFailures(setOf(1))
        assertFalse(enricher.hasFailed(2))
        failNext = false
        enricher.enrich(listOf(2))
        advanceUntilIdle()
        assertEquals(4, calls)
        assertEquals(setOf(1, 2), merged)
        assertFalse(enricher.hasFailed(2))
    }
}
