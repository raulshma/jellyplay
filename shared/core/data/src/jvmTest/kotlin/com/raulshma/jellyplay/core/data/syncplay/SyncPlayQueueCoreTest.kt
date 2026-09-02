package com.raulshma.jellyplay.core.data.syncplay

import com.raulshma.jellyplay.core.model.SyncPlayPlaybackCommand
import com.raulshma.jellyplay.core.model.SyncPlayQueueUpdateData
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode
import io.mockk.every
import io.mockk.mockk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the client-side SyncPlay queue state machine [SyncPlayQueueCore]:
 *
 *  - before any update, every projection reports its documented default
 *    (empty lists, null playing ids, REPEAT_NONE / SORTED, empty playlist map,
 *    no load request, 0 start ticks);
 *  - [SyncPlayQueueCore.updatePlayQueue] projects a [SyncPlayQueueUpdateData]
 *    onto the accessors and builds the playlistItemId→itemId map by index;
 *  - stale updates (older-or-equal `lastUpdateMs`) are rejected, keeping the
 *    current state and cache intact — while an unknown timestamp (0) bypasses
 *    the guard;
 *  - the cached map is rebuilt after each accepted update and after clear().
 */
class SyncPlayQueueCoreTest {

    private val timeSync: TimeSyncManager = mockk()

    private fun core(): SyncPlayQueueCore = SyncPlayQueueCore(timeSync)

    private fun queueUpdate(
        itemIds: List<String>,
        playlistItemIds: List<String>,
        playingItemIndex: Int = 0,
        startPositionTicks: Long = 0L,
        whenMs: Long = 0L,
        lastUpdateMs: Long = 0L,
        repeatMode: SyncPlayRepeatMode = SyncPlayRepeatMode.REPEAT_NONE,
        shuffleMode: SyncPlayShuffleMode = SyncPlayShuffleMode.SORTED,
        reason: String = "NewPlaylist",
    ) = SyncPlayQueueUpdateData(
        playlistItemIds = playlistItemIds,
        itemIds = itemIds,
        playingItemIndex = playingItemIndex,
        playingItemId = itemIds.getOrNull(playingItemIndex) ?: "",
        playingPlaylistItemId = playlistItemIds.getOrNull(playingItemIndex) ?: "",
        startPositionTicks = startPositionTicks,
        isPlaying = false,
        whenMs = whenMs,
        lastUpdateMs = lastUpdateMs,
        repeatMode = repeatMode,
        shuffleMode = shuffleMode,
        reason = reason,
    )

    // ── defaults before any update ──────────────────────────────────────

    @Test
    fun `before any update all projections report their defaults`() {
        val core = core()

        assertNull(core.lastPlayQueueUpdate)
        assertEquals(emptyList(), core.currentPlaylistItemIds)
        assertEquals(emptyList(), core.currentItemIds)
        assertNull(core.playingItemId)
        assertNull(core.playingPlaylistItemId)
        assertEquals(SyncPlayRepeatMode.REPEAT_NONE, core.repeatMode)
        assertEquals(SyncPlayShuffleMode.SORTED, core.shuffleMode)
        assertEquals(emptyMap(), core.playlistItemMap)
        assertFalse(core.shouldLoadItem(currentItemId = null))
        assertFalse(core.shouldLoadItem(currentItemId = "anything"))
        assertEquals(0L, core.getStartPositionTicks(lastPlaybackCommand = null))
    }

    // ── projection from SyncPlayQueueUpdateData ─────────────────────────

    @Test
    fun `updatePlayQueue accepts a fresh queue and projects all fields`() {
        val core = core()
        val update = queueUpdate(
            itemIds = listOf("i1", "i2", "i3"),
            playlistItemIds = listOf("p1", "p2", "p3"),
            playingItemIndex = 1,
            startPositionTicks = 123_000L,
            whenMs = 500L,
            lastUpdateMs = 1_000L,
            repeatMode = SyncPlayRepeatMode.REPEAT_ALL,
            shuffleMode = SyncPlayShuffleMode.SHUFFLE,
            reason = "SetPlayQueue",
        )

        val accepted = core.updatePlayQueue(update)

        assertTrue(accepted)
        assertEquals(update, core.lastPlayQueueUpdate)
        assertEquals(listOf("p1", "p2", "p3"), core.currentPlaylistItemIds)
        assertEquals(listOf("i1", "i2", "i3"), core.currentItemIds)
        assertEquals("i2", core.playingItemId)
        assertEquals("p2", core.playingPlaylistItemId)
        assertEquals(SyncPlayRepeatMode.REPEAT_ALL, core.repeatMode)
        assertEquals(SyncPlayShuffleMode.SHUFFLE, core.shuffleMode)
    }

    @Test
    fun `shouldLoadItem only fires when the playing item differs from the loaded one`() {
        val core = core()
        core.updatePlayQueue(
            queueUpdate(itemIds = listOf("a", "b"), playlistItemIds = listOf("pa", "pb"), playingItemIndex = 1),
        )

        assertFalse(core.shouldLoadItem(currentItemId = "b")) // already on the playing item
        assertTrue(core.shouldLoadItem(currentItemId = "a")) // parked on the wrong item
        assertTrue(core.shouldLoadItem(currentItemId = null)) // nothing loaded yet
    }

    // ── playlistItemMap: build, cache, rebuild ──────────────────────────

    @Test
    fun `playlistItemMap pairs playlist and item ids by index`() {
        val core = core()
        core.updatePlayQueue(
            queueUpdate(
                itemIds = listOf("i1", "i2", "i3"),
                playlistItemIds = listOf("p1", "p2"),
            ),
        )

        // Extra itemIds beyond the playlist pairs are dropped, not crashed on.
        assertEquals(mapOf("p1" to "i1", "p2" to "i2"), core.playlistItemMap)
    }

    @Test
    fun `accepted update rebuilds a stale cached map`() {
        val core = core()
        core.updatePlayQueue(
            queueUpdate(itemIds = listOf("i1"), playlistItemIds = listOf("p1"), lastUpdateMs = 100L),
        )
        assertEquals(mapOf("p1" to "i1"), core.playlistItemMap)

        core.updatePlayQueue(
            queueUpdate(itemIds = listOf("i2"), playlistItemIds = listOf("p2"), lastUpdateMs = 200L),
        )

        assertEquals(mapOf("p2" to "i2"), core.playlistItemMap)
    }

    // ── stale-update rejection ──────────────────────────────────────────

    @Test
    fun `stale update is rejected and the current state survives`() {
        val core = core()
        val current = queueUpdate(
            itemIds = listOf("i1"),
            playlistItemIds = listOf("p1"),
            lastUpdateMs = 1_000L,
        )
        core.updatePlayQueue(current)

        val accepted = core.updatePlayQueue(
            queueUpdate(itemIds = listOf("old"), playlistItemIds = listOf("pp"), lastUpdateMs = 999L),
        )

        assertFalse(accepted)
        assertEquals(current, core.lastPlayQueueUpdate)
        assertEquals(mapOf("p1" to "i1"), core.playlistItemMap)
    }

    @Test
    fun `equal lastUpdateMs is treated as stale`() {
        val core = core()
        val current = queueUpdate(itemIds = listOf("i1"), playlistItemIds = listOf("p1"), lastUpdateMs = 500L)
        core.updatePlayQueue(current)

        assertFalse(core.updatePlayQueue(
            queueUpdate(itemIds = listOf("dup"), playlistItemIds = listOf("px"), lastUpdateMs = 500L),
        ))
        assertEquals(current, core.lastPlayQueueUpdate)
    }

    @Test
    fun `update with unknown lastUpdateMs bypasses the staleness guard`() {
        val core = core()
        core.updatePlayQueue(
            queueUpdate(itemIds = listOf("i1"), playlistItemIds = listOf("p1"), lastUpdateMs = 1_000L),
        )

        // A server that never stamps LastUpdate (0) must not deadlock the queue.
        assertTrue(core.updatePlayQueue(
            queueUpdate(itemIds = listOf("i2"), playlistItemIds = listOf("p2"), lastUpdateMs = 0L),
        ))
        assertEquals("i2", core.playingItemId)
    }

    // ── start-position arbitration ──────────────────────────────────────

    @Test
    fun `start position comes from the update when no newer command arrived`() {
        val core = core()
        core.updatePlayQueue(
            queueUpdate(
                itemIds = listOf("i1"),
                playlistItemIds = listOf("p1"),
                startPositionTicks = 100L,
                whenMs = 1_000L,
                lastUpdateMs = 900L,
            ),
        )
        // remoteNow is 2.1s past the queue's lastUpdateMs (the fallback stamp)
        // → 100 + 2100ms * 10_000 ticks.
        every { timeSync.remoteNow() } returns 3_000L

        assertEquals(100L + (3_000L - 900L) * 10_000L, core.getStartPositionTicks(lastPlaybackCommand = null))
    }

    @Test
    fun `a newer playback command wins over the queue stamp`() {
        val core = core()
        core.updatePlayQueue(
            queueUpdate(
                itemIds = listOf("i1"),
                playlistItemIds = listOf("p1"),
                startPositionTicks = 100L,
                whenMs = 1_000L,
                lastUpdateMs = 900L,
            ),
        )
        val command = SyncPlayPlaybackCommand(
            command = "Seek",
            whenMs = 5_000L, // newer than the queue's whenMs → wins
            positionTicks = 777L,
            playlistItemId = "",
            emittedAtMs = 0L,
        )
        every { timeSync.remoteNow() } returns 6_000L

        assertEquals(777L + 1_000L * 10_000L, core.getStartPositionTicks(lastPlaybackCommand = command))
    }

    @Test
    fun `an older playback command loses to the queue stamp`() {
        val core = core()
        core.updatePlayQueue(
            queueUpdate(
                itemIds = listOf("i1"),
                playlistItemIds = listOf("p1"),
                startPositionTicks = 100L,
                whenMs = 1_000L,
                lastUpdateMs = 900L,
            ),
        )
        val olderCommand = SyncPlayPlaybackCommand(
            command = "Seek",
            whenMs = 500L, // older than the queue's whenMs
            positionTicks = 999L,
            playlistItemId = "",
            emittedAtMs = 0L,
        )
        every { timeSync.remoteNow() } returns 2_000L

        // Fallback extrapolates from update.lastUpdateMs = 900.
        assertEquals(100L + (2_000L - 900L) * 10_000L, core.getStartPositionTicks(lastPlaybackCommand = olderCommand))
    }

    @Test
    fun `estimateCurrentTicks extrapolates elapsed wall time at 10k ticks per ms`() {
        val core = core()
        every { timeSync.remoteNow() } returns 10_000L

        assertEquals(0L + 9_000L * 10_000L, core.estimateCurrentTicks(ticks = 0L, whenMs = 1_000L))
    }

    // ── clear ───────────────────────────────────────────────────────────

    @Test
    fun `clear resets the queue and the cached map back to defaults`() {
        val core = core()
        core.updatePlayQueue(
            queueUpdate(itemIds = listOf("i1"), playlistItemIds = listOf("p1"), lastUpdateMs = 10L),
        )
        assertEquals(mapOf("p1" to "i1"), core.playlistItemMap)

        core.clear()

        assertNull(core.lastPlayQueueUpdate)
        assertEquals(emptyMap(), core.playlistItemMap)
        assertFalse(core.shouldLoadItem(currentItemId = null))
    }
}
