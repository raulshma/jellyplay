package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests [TtlCacheGate] — the ONE TTL freshness decision (the former
 * feature/home gate + core:network's ported twin, folded into core:model
 * beside [TtlCache]).
 */
class TtlCacheGateTest {

    @Test
    fun `never-fetched gate says fetch`() {
        val gate = TtlCacheGate(ttlMs = 60_000L)
        assertTrue(gate.shouldFetch(now = 1_000L))
    }

    @Test
    fun `fresh gate skips fetch until ttl elapses`() {
        val gate = TtlCacheGate(ttlMs = 60_000L)
        gate.markFetched(now = 10_000L)
        assertFalse(gate.shouldFetch(now = 69_999L))
        assertTrue(gate.shouldFetch(now = 70_000L))
    }

    @Test
    fun `invalidate forces the next fetch`() {
        val gate = TtlCacheGate(ttlMs = 60_000L)
        gate.markFetched(now = 10_000L)
        gate.invalidate()
        assertTrue(gate.shouldFetch(now = 11_000L))
    }

    @Test
    fun `markFetched after invalidate restores freshness`() {
        val gate = TtlCacheGate(ttlMs = 60_000L)
        gate.invalidate()
        gate.markFetched(now = 20_000L)
        assertFalse(gate.shouldFetch(now = 30_000L))
    }

    @Test
    fun `zero ttl always fetches after the first stamp`() {
        val gate = TtlCacheGate(ttlMs = 0L)
        gate.markFetched(now = 5_000L)
        assertTrue(gate.shouldFetch(now = 5_000L))
    }

    @Test
    fun `injected clock drives the default reads when now is omitted`() {
        var tick = 0L
        val gate = TtlCacheGate(ttlMs = 60_000L, clock = { tick })
        assertTrue(gate.shouldFetch())
        gate.markFetched()
        tick = 59_999L
        assertFalse(gate.shouldFetch())
        tick = 60_000L
        assertTrue(gate.shouldFetch())
    }
}
