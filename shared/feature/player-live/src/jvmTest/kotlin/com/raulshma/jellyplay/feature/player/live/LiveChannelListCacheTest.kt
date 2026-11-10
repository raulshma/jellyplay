package com.raulshma.jellyplay.feature.player.live

import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.feature.player.live.data.LiveChannelListCache
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame

/**
 * Pins [LiveChannelListCache]'s three policies — the TTL window, the
 * (server, token) scope isolation and the identity of the memoized list —
 * over an injectable monotonic clock so the expiry assertions are exact.
 */
class LiveChannelListCacheTest {

    private var nowMs = 0L

    private val cache = LiveChannelListCache(clock = { nowMs })

    private fun channels(count: Int) = (0 until count).map { LiveTvChannel(id = "ch-$it", name = "Channel $it") }

    @Test
    fun `a memoized list is served within the ttl window`() {
        val list = channels(2)
        cache.put("srv::tok", list)

        nowMs += LiveChannelListCache.TTL_MS - 1

        assertSame(list, cache.get("srv::tok"))
    }

    @Test
    fun `an entry at and past the ttl boundary is a miss`() {
        cache.put("srv::tok", channels(2))

        nowMs += LiveChannelListCache.TTL_MS

        assertNull(cache.get("srv::tok"), "TTL expiry is inclusive of the boundary")
    }

    @Test
    fun `one scope never sees another scope's list`() {
        val userA = channels(2)
        cache.put("srv::tok-a", userA)

        assertNull(cache.get("srv::tok-b"), "no cross-scope read, ever")
        cache.put("srv::tok-b", channels(1))

        // The single-entry memo holds only the CURRENT scope: a server/user
        // switch drops the previous list outright (bounded to one list in
        // memory) — the isolation guarantee is "never cross-served", not
        // "multi-scope".
        assertNull(cache.get("srv::tok-a"))
        assertEquals(1, cache.get("srv::tok-b")?.size)
    }

    @Test
    fun `an uncached scope reads as a miss`() {
        assertNull(cache.get("never-put"))
        assertEquals(null, cache.get(""))
    }
}
