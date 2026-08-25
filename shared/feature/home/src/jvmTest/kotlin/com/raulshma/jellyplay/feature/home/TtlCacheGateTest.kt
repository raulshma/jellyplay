package com.raulshma.jellyplay.feature.home

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.Test

/**
 * Tests [TtlCacheGate] — the TTL freshness decision previously hand-rolled as
 * two mutable fields + an inline arithmetic check on HomeViewModel. Extracted
 * so the cache policy (never-invalidates / refresh-loops-forever bug class) has
 * a direct test.
 */
class TtlCacheGateTest {

    @Test
    fun `unfetched gate always fetches`() {
        val gate = TtlCacheGate(ttlMs = 60_000L)
        assertTrue(gate.shouldFetch(now = 0L))
        assertTrue(gate.shouldFetch(now = 1_000_000L))
    }

    @Test
    fun `within ttl after fetch does not refetch`() {
        val gate = TtlCacheGate(ttlMs = 60_000L)
        gate.markFetched(now = 1_000L)

        assertFalse(gate.shouldFetch(now = 1_000L))
        assertFalse(gate.shouldFetch(now = 30_000L))
        assertFalse(gate.shouldFetch(now = 60_999L))
    }

    @Test
    fun `at or past ttl refetches`() {
        val gate = TtlCacheGate(ttlMs = 60_000L)
        gate.markFetched(now = 1_000L)

        assertTrue(gate.shouldFetch(now = 61_000L))
        assertTrue(gate.shouldFetch(now = 120_000L))
    }

    @Test
    fun `invalidate forces next fetch regardless of age`() {
        val gate = TtlCacheGate(ttlMs = 60_000L)
        gate.markFetched(now = 0L)

        assertFalse(gate.shouldFetch(now = 1_000L))
        gate.invalidate()
        assertTrue(gate.shouldFetch(now = 1_000L))
    }

    @Test
    fun `markFetched clears a pending invalidation`() {
        val gate = TtlCacheGate(ttlMs = 60_000L)
        gate.invalidate()
        gate.markFetched(now = 5_000L)

        assertFalse(gate.shouldFetch(now = 10_000L))
        // Still fresh until ttl elapses.
        assertTrue(gate.shouldFetch(now = 70_000L))
    }

    @Test
    fun `zero ttl always fetches after a fetch`() {
        val gate = TtlCacheGate(ttlMs = 0L)
        gate.markFetched(now = 100L)
        assertTrue(gate.shouldFetch(now = 100L))
    }
}
