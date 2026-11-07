package com.raulshma.jellyplay.feature.player.video.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the two pure halves of the libVLC handle lifecycle, without libVLC
 * (the VlcSlaveRegistrationTest precedent): the cached-handle slot
 * ([VlcHandleCache] — reuse only on an option-identical load, evict-before-
 * replace, an evicted handle never served again), and the serialized retire
 * scheduler ([VlcHandleReleaseQueue] — every scheduled closure destroys the
 * exact object it captured, IN schedule order, even when a newer retire
 * superseded its generation; skipping a superseded closure would leak its
 * handle).
 */
class VlcHandleCachePolicyTest {

    /** Stand-in for the LibVLC handle: identity is all the cache reasons about. */
    private class FakeVlc(val id: Int)

    // ── VlcHandleCache ────────────────────────────────────────────────────

    @Test
    fun `an empty cache never serves a load`() {
        val cache = VlcHandleCache<FakeVlc>()

        assertNull(cache.reusableFor(listOf("--network-caching=3000")))
        assertNull(cache.current)
        assertNull(cache.evict())
    }

    @Test
    fun `the installed handle is reused only for its producing options`() {
        val cache = VlcHandleCache<FakeVlc>()
        val handle = FakeVlc(1)
        val options = listOf("--aout=aaudio", "--network-caching=3000")
        cache.install(handle, options)

        assertSame(handle, cache.reusableFor(listOf("--aout=aaudio", "--network-caching=3000")))
        assertNull(cache.reusableFor(listOf("--network-caching=1500")))
        assertNull(cache.reusableFor(listOf("--aout=aaudio")))
    }

    @Test
    fun `an evicted handle is unreachable and the slot is empty`() {
        val cache = VlcHandleCache<FakeVlc>()
        val handle = FakeVlc(1)
        cache.install(handle, listOf("--aout=aaudio"))

        val retired = cache.evict()

        assertSame(handle, retired)
        assertNull(cache.current)
        assertNull(cache.reusableFor(listOf("--aout=aaudio")))
        // A second evict has nothing to hand out — the engine's retire path
        // schedules each handle exactly once.
        assertNull(cache.evict())
    }

    @Test
    fun `install replaces the key - the new options win the old miss`() {
        val cache = VlcHandleCache<FakeVlc>()
        cache.install(FakeVlc(1), listOf("--network-caching=3000"))
        val replacement = FakeVlc(2)
        cache.install(replacement, listOf("--network-caching=1500"))

        assertSame(replacement, cache.current)
        assertSame(replacement, cache.reusableFor(listOf("--network-caching=1500")))
        assertNull(cache.reusableFor(listOf("--network-caching=3000")))
    }

    @Test
    fun `the cache key is a defensive copy pinned at install time`() {
        val cache = VlcHandleCache<FakeVlc>()
        val mutableKey = mutableListOf("--aout=aaudio")
        val handle = FakeVlc(1)
        cache.install(handle, mutableKey)

        // A caller mutating the list after install must not move the key:
        // the slot outlives the load call that installed it.
        mutableKey.add("--audio-time-stretch")

        assertSame(handle, cache.reusableFor(listOf("--aout=aaudio")))
        assertNull(cache.reusableFor(listOf("--aout=aaudio", "--audio-time-stretch")))
    }

    // ── VlcHandleReleaseQueue ─────────────────────────────────────────────

    /** Recording bench: closures queue in order; destroy/supersede are recorded. */
    private class QueueBench {
        val closures = mutableListOf<() -> Unit>()
        val destroyed = mutableListOf<Int>()
        val superseded = mutableListOf<Pair<Int, Int>>()

        val queue = VlcHandleReleaseQueue<FakeVlc>(
            dispatch = { release -> closures += release },
            destroy = { vlc -> destroyed += vlc.id },
            onSuperseded = { scheduled, current -> superseded += scheduled to current },
        )

        /** Runs the queued closures FIFO — what the serialized release thread does. */
        fun drain() {
            val pending = closures.toList()
            closures.clear()
            pending.forEach { it() }
        }
    }

    @Test
    fun `retire schedules exactly one destroy of the captured handle`() {
        val bench = QueueBench()
        val handle = FakeVlc(7)

        bench.queue.retire(handle)
        assertEquals(1, bench.closures.size)

        bench.drain()
        assertEquals(listOf(7), bench.destroyed)
        assertTrue(bench.superseded.isEmpty())

        // The closure ran once; nothing is queued afterwards.
        bench.drain()
        assertEquals(listOf(7), bench.destroyed)
    }

    @Test
    fun `a superseded retire still destroys its own capture - skip would leak`() {
        val bench = QueueBench()

        // Rapid option flip-flop: two retires scheduled before either runs.
        bench.queue.retire(FakeVlc(1))
        bench.queue.retire(FakeVlc(2))
        assertEquals(2, bench.closures.size)

        bench.drain()

        // BOTH handles are destroyed, in schedule order: the first closure's
        // generation is stale by the time it runs, but its capture is a
        // distinct object the newer retire knows nothing about.
        assertEquals(listOf(1, 2), bench.destroyed)
        assertEquals(listOf(1 to 2), bench.superseded)
    }

    @Test
    fun `a non-superseded retire reports no supersede`() {
        val bench = QueueBench()

        bench.queue.retire(FakeVlc(1))
        bench.drain()
        bench.queue.retire(FakeVlc(2))
        bench.drain()

        assertEquals(listOf(1, 2), bench.destroyed)
        assertTrue(bench.superseded.isEmpty())
    }
}
