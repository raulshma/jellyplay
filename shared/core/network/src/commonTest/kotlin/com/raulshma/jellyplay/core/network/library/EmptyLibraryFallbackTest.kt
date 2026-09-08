package com.raulshma.jellyplay.core.network.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Pins [EmptyLibraryFallback]'s decision ladder (the `LibraryRequestPolicyTest`
 * neighbour for the fallback half): the known-empty memo short-circuit (zero
 * transport calls), the bypasses that never pay the doubled round trip
 * (non-empty primary, no folder, a search term — the "unfiltered browse of one
 * folder" gate), the `limit <= 0 → 50` coercion of the fallback fetch, and the
 * remember-only-genuinely-empty memo write rule (a fallback that served items
 * is never remembered; a failed fallback degrades to empty and IS remembered,
 * so a failing endpoint isn't hammered either). Cancellation from the
 * transport propagates instead of degrading to the empty fallback.
 * [emptyFallbackTotalCount]'s derivation table is pinned alongside.
 */
class EmptyLibraryFallbackTest {

    /**
     * Constructor-lambda fake: a mutable memo plus a scripted `/Items/Latest`
     * transport that records every (parentId, limit) call. The decision ladder
     * under test lives in [EmptyLibraryFallback]; this holder only supplies
     * transport and memory.
     */
    private class FakeFallback {
        val knownEmpty = mutableSetOf<String>()
        val remembered = mutableListOf<String>()
        val fetchCalls = mutableListOf<Pair<String, Int>>()
        var latestResult: List<String> = emptyList()

        fun fallback(): EmptyLibraryFallback<String> = EmptyLibraryFallback(
            isKnownEmpty = { it in knownEmpty },
            rememberEmpty = { remembered += it },
            fetchLatest = { parentId, limit ->
                fetchCalls += parentId to limit
                latestResult
            },
        )
    }

    @Test
    fun `known-empty memo hit short-circuits before any transport call`() = runTest {
        val fake = FakeFallback()
        fake.knownEmpty += "lib-1"

        val resolved = fake.fallback().resolve(
            primaryItems = emptyList(),
            parentId = "lib-1",
            searchTerm = null,
            limit = 20,
        )

        assertEquals(emptyList(), resolved)
        assertEquals(0, fake.fetchCalls.size, "a known-empty library pays zero requests")
    }

    @Test
    fun `non-empty primary result bypasses the ladder untouched`() = runTest {
        val fake = FakeFallback()
        val primary = listOf("a", "b")

        val resolved = fake.fallback().resolve(primary, "lib-1", searchTerm = null, limit = 20)

        assertEquals(primary, resolved)
        assertEquals(0, fake.fetchCalls.size)
        assertEquals(0, fake.remembered.size)
    }

    @Test
    fun `a search term bypasses the fallback even for an empty primary result`() = runTest {
        val fake = FakeFallback()

        val byTerm = fake.fallback().resolve(emptyList(), "lib-1", searchTerm = "query", limit = 20)

        assertEquals(emptyList(), byTerm)
        assertEquals(0, fake.fetchCalls.size, "a search is not an unfiltered browse — no doubled round trip")
    }

    @Test
    fun `a blank search term is an unfiltered browse and pays the fallback`() = runTest {
        val fake = FakeFallback()
        fake.latestResult = listOf("late-1")

        // The gate is isNullOrBlank: a whitespace-only term is indistinguishable
        // from no term, so the ladder runs (same as the null arm below).
        val byBlankTerm = fake.fallback().resolve(emptyList(), "lib-1", searchTerm = "  ", limit = 20)

        assertEquals(listOf("late-1"), byBlankTerm)
        assertEquals(listOf("lib-1" to 20), fake.fetchCalls)
    }

    @Test
    fun `a null parent id bypasses the fallback even for an empty primary result`() = runTest {
        val fake = FakeFallback()

        val resolved = fake.fallback().resolve(emptyList(), parentId = null, searchTerm = null, limit = 20)

        assertEquals(emptyList(), resolved)
        assertEquals(0, fake.fetchCalls.size, "no folder to fall back against")
    }

    @Test
    fun `non-positive limit is coerced to 50 for the fallback fetch`() = runTest {
        val fake = FakeFallback()

        fake.fallback().resolve(emptyList(), "lib-1", searchTerm = null, limit = 0)
        fake.fallback().resolve(emptyList(), "lib-2", searchTerm = null, limit = -3)

        assertEquals(listOf("lib-1" to 50, "lib-2" to 50), fake.fetchCalls)
    }

    @Test
    fun `positive limit passes through to the fallback fetch`() = runTest {
        val fake = FakeFallback()

        fake.fallback().resolve(emptyList(), "lib-1", searchTerm = null, limit = 16)

        assertEquals(listOf("lib-1" to 16), fake.fetchCalls)
    }

    @Test
    fun `double-empty remembers the library, a fallback with items does not`() = runTest {
        val fake = FakeFallback()
        fake.latestResult = emptyList()

        fake.fallback().resolve(emptyList(), "lib-1", searchTerm = null, limit = 20)

        assertEquals(listOf("lib-1"), fake.remembered)

        fake.latestResult = listOf("late-1")
        fake.fallback().resolve(emptyList(), "lib-2", searchTerm = null, limit = 20)

        assertEquals(listOf("lib-1"), fake.remembered, "a successful fallback is never memoised as empty")
    }

    @Test
    fun `failed fallback fetch degrades to empty and is memoised as empty`() = runTest {
        val fake = FakeFallback()
        val failing = EmptyLibraryFallback<String>(
            isKnownEmpty = { false },
            rememberEmpty = { fake.remembered += it },
            fetchLatest = { parentId, limit ->
                fake.fetchCalls += parentId to limit
                throw IllegalStateException("network down")
            },
        )

        val resolved = failing.resolve(emptyList(), "lib-1", searchTerm = null, limit = 20)

        // The failed fetch degrades to the empty fallback — and that emptiness
        // IS remembered, so a repeat visit short-circuits instead of hammering
        // the failing endpoint.
        assertEquals(emptyList(), resolved)
        assertEquals(listOf("lib-1"), fake.remembered)
    }

    @Test
    fun `cancellation from the fallback fetch propagates instead of degrading to empty`() = runTest {
        val cancelled = EmptyLibraryFallback<String>(
            isKnownEmpty = { false },
            rememberEmpty = { error("a cancelling fetch is not an empty one") },
            fetchLatest = { _, _ -> throw CancellationException("cancelled") },
        )

        assertFailsWith<CancellationException> {
            cancelled.resolve(emptyList(), "lib-1", searchTerm = null, limit = 20)
        }
    }

    @Test
    fun `emptyFallbackTotalCount derivation table`() {
        // (primaryCount, resolvedCount, serverTotal) → expected.
        val table = listOf(
            Triple(0, 12, 0) to 12,
            Triple(0, 12, 999) to 12,
            Triple(0, 0, 999) to 999,
            Triple(7, 0, 999) to 999,
            Triple(7, 12, 999) to 999,
        )
        table.forEach { (input, expected) ->
            val (primaryCount, resolvedCount, serverTotal) = input
            assertEquals(
                expected,
                emptyFallbackTotalCount(primaryCount, resolvedCount, serverTotal),
                "primaryCount=$primaryCount resolvedCount=$resolvedCount serverTotal=$serverTotal",
            )
        }
    }
}
