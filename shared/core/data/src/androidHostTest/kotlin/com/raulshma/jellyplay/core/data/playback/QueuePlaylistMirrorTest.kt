package com.raulshma.jellyplay.core.data.playback

import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.cancel

/**
 * Mutable playlist window the stub [ExoPlayer] projects. Top-level (not a
 * nested member of the test class) so [stubPlayer] needs no outer receiver.
 */
private class MirrorStubPlaylist {
    val items = mutableListOf<MediaItem>()
    var index = 0
    var lastSetMediaItemsPositionMs = -1L
    var rebuilt = 0
}

/**
 * Direct pin for the [QueuePlaylistMirror] — the windowed queue→playlist
 * mirror extracted from [AudioPlaybackManager] (the play-path pre-warm, the
 * transition window slide, the crossfade re-mirror and the rebuild write).
 * Runs over a recording stub [ExoPlayer] (the QueueSemantics suite's
 * playlist-window pattern, slimmed) and a fake item-builder — no real media3
 * engine, no repository machinery. The choreography hops IO → Robolectric's
 * main looper, so every async write is awaited through a poll-drain.
 */
private fun stubPlayer(playlist: MirrorStubPlaylist): ExoPlayer {
    val player = mockk<ExoPlayer>(relaxed = true)
    every { player.mediaItemCount } answers { playlist.items.size }
    every { player.getMediaItemAt(any()) } answers { playlist.items[arg<Int>(0)] }
    every { player.currentMediaItemIndex } answers { playlist.index }
    every { player.addMediaItems(any<List<MediaItem>>()) } answers {
        playlist.items += arg<List<MediaItem>>(0)
    }
    every { player.addMediaItems(any<Int>(), any<List<MediaItem>>()) } answers {
        playlist.items.addAll(firstArg<Int>(), arg<List<MediaItem>>(1))
    }
    every { player.setMediaItems(any<List<MediaItem>>(), any(), any()) } answers {
        playlist.items.clear()
        playlist.items += arg<List<MediaItem>>(0)
        playlist.index = arg<Int>(1)
        playlist.lastSetMediaItemsPositionMs = arg<Long>(2)
        playlist.rebuilt += 1
    }
    return player
}

/**
 * Direct pin for the [QueuePlaylistMirror] — the windowed queue→playlist
 * mirror extracted from [AudioPlaybackManager] (the play-path pre-warm, the
 * transition window slide, the crossfade re-mirror and the rebuild write).
 * Runs over a recording stub [ExoPlayer] (the QueueSemantics suite's
 * playlist-window pattern, slimmed) and a fake item-builder — no real media3
 * engine, no repository machinery. The choreography hops IO → Robolectric's
 * main looper, so every async write is awaited through a poll-drain.
 */
@OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class QueuePlaylistMirrorTest {

    /**
     * The mirror under test over a recording fixture: the queue/cursor/player
     * cells the mirror reads are plain vars the tests mutate to simulate the
     * chassis, the item-builder records every row it is asked to resolve
     * (with an optional gate to park a build mid-flight), and every chassis
     * cursor write is recorded.
     */
    private class Fixture {
        val playlist = MirrorStubPlaylist()
        val player: ExoPlayer = stubPlayer(playlist)
        var queue: List<AudioQueueItem> = emptyList()
        var cursor: Int = 0

        /** Every row id the item-builder was asked to resolve, in order. */
        val builderRequests = CopyOnWriteArrayList<String>()

        /** When set, every build parks until it is completed (build parking). */
        @Volatile
        var gate: CompletableDeferred<Unit>? = null

        val cursorWrites = CopyOnWriteArrayList<Int>()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

        val mirror = QueuePlaylistMirror(
            scope = scope,
            playerProvider = { player },
            queueProvider = { queue },
            cursorProvider = { cursor },
            writeCursor = { cursorWrites += it },
            buildItem = { item ->
                builderRequests.add(item.id)
                gate?.await()
                MediaItem.Builder().setMediaId(item.id).build()
            },
        )

        fun item(id: String) = AudioQueueItem(
            id = id,
            name = "Track $id",
            artist = "Artist of $id",
            album = "Album $id",
            imageUrl = "art://$id",
            mediaSourceId = "ms-$id",
            durationMs = 180_000L,
            normalizationGain = null,
        )

        fun rows(count: Int) = (0 until count).map { item("row$it") }

        /**
         * Seeds the player playlist the way the play path's
         * `setMediaItem(clickedMediaItem, ...)` (and the crossfade's swapped-in
         * secondary) leaves it before the mirror's writes run: just the row at
         * queue index [at].
         */
        fun seedRow(at: Int) {
            playlist.items.clear()
            playlist.items += MediaItem.Builder().setMediaId(queue[at].id).build()
            playlist.index = 0
        }

        fun close() {
            scope.cancel()
        }
    }

    /** Drains the IO→Main pipeline until [condition] holds, then asserts it. */
    private fun awaitMain(condition: () -> Boolean, message: String) {
        val deadline = System.currentTimeMillis() + 5_000L
        while (!condition() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(message, condition())
    }

    // ── the invariant predicate ─────────────────────────────────────────────

    @Test
    fun mirrorsQueuePinsTheIndexAlignedPrefixInvariant() {
        val f = Fixture()
        try {
            f.queue = f.rows(4)

            // Nothing mirrored yet — the empty prefix is trivially aligned.
            assertTrue(f.mirror.mirrorsQueue(f.player))

            // A partial prefix of the queue's FIRST rows still mirrors
            // (rows past the lookahead are simply not mirrored yet).
            f.playlist.items += listOf("row0", "row1").map { MediaItem.Builder().setMediaId(it).build() }
            assertTrue("a queue-aligned prefix mirrors", f.mirror.mirrorsQueue(f.player))

            // One divergent id breaks the equality the crossfader and the
            // window seek ride.
            f.playlist.items[1] = MediaItem.Builder().setMediaId("other").build()
            assertFalse("a divergent row breaks the mirror", f.mirror.mirrorsQueue(f.player))

            // A playlist LONGER than the queue cannot be a prefix of it.
            f.queue = f.rows(1)
            assertFalse("an overlong playlist cannot be a queue prefix", f.mirror.mirrorsQueue(f.player))
        } finally {
            f.close()
        }
    }

    // ── the play-path pre-warm: window math + prepend reconciliation ───────

    @Test
    fun prewarmMirrorsOnlyTheLookaheadWindowPrefixAroundTheCursor() {
        val f = Fixture()
        try {
            f.queue = f.rows(60)
            f.cursor = 2
            f.seedRow(at = 2)

            f.mirror.prewarm(f.player)
            awaitMain(
                { f.playlist.items.size == 2 + 1 + QueuePlaylistMirror.PREWARM_LOOKAHEAD_ITEMS },
                "the windowed pre-warm mirrors the cursor + lookahead prefix",
            )

            // Prefix [0, cursor + 1 + lookahead): the full prefix below the
            // cursor (the prepend) plus the bounded lookahead ahead of it —
            // rows past the window are never mirrored.
            assertEquals(
                f.queue.subList(0, 15).map { it.id },
                f.playlist.items.map { it.mediaId },
            )
            // The prepend reconciliation re-publishes the chassis cursor.
            assertEquals(listOf(2), f.cursorWrites)
        } finally {
            f.close()
        }
    }

    @Test
    fun prewarmClampsToTheQueueTailAndSkipsTheCursorWriteWithoutAPrefix() {
        val f = Fixture()
        try {
            f.queue = f.rows(4)
            f.cursor = 0
            f.seedRow(at = 0)

            f.mirror.prewarm(f.player)
            awaitMain({ f.playlist.items.size == 4 }, "a short queue clamps the window to its tail")

            assertEquals(f.queue.map { it.id }, f.playlist.items.map { it.mediaId })
            assertTrue(
                "nothing prepended → no cursor reconciliation write",
                f.cursorWrites.isEmpty(),
            )
        } finally {
            f.close()
        }
    }

    @Test
    fun prewarmShortCircuitsCachedRowsOnTheSecondWindow() {
        val f = Fixture()
        try {
            f.queue = f.rows(60)
            f.cursor = 2
            f.seedRow(at = 2)
            f.mirror.prewarm(f.player)
            awaitMain({ f.playlist.items.size == 15 }, "the first window mirrors")
            val firstRoundRequests = f.builderRequests.toList()

            // Fresh load (the play path's setMediaItem replaced the playlist);
            // the mirror's LRU survives across loads — the second window only
            // resolves the rows it has never built: row15 entering the
            // lookahead, and row2 — round one's seeded clicked row, which the
            // mirror never builds — now below the cursor.
            f.cursor = 3
            f.seedRow(at = 3)
            f.mirror.prewarm(f.player)
            awaitMain({ f.playlist.items.size == 16 }, "the second window mirrors")

            assertTrue(
                "cached rows never re-reach the builder (got: "
                    + f.builderRequests.subList(firstRoundRequests.size, f.builderRequests.size) + ")",
                f.builderRequests.size - firstRoundRequests.size == 2,
            )
            assertEquals(
                "cached rows never re-reach the builder",
                setOf("row2", "row15"),
                f.builderRequests.subList(firstRoundRequests.size, f.builderRequests.size).toSet(),
            )
        } finally {
            f.close()
        }
    }

    @Test
    fun prewarmRestartsAndSupersedesAnInFlightBuild() {
        val f = Fixture()
        try {
            f.queue = f.rows(60)
            f.cursor = 2
            f.seedRow(at = 2)

            val parkedGate = CompletableDeferred<Unit>()
            f.gate = parkedGate
            f.mirror.prewarm(f.player)
            assertTrue("the play-path build owns the loading guard", f.mirror.isLoading)

            // The restart: the second prewarm cancels the parked build and
            // mirrors the (unchanged) window on its own build.
            f.gate = CompletableDeferred<Unit>().apply { complete(Unit) }
            f.mirror.prewarm(f.player)
            parkedGate.complete(Unit) // release the cancelled build's park
            awaitMain({ !f.mirror.isLoading }, "the superseding build clears the loading guard")

            assertEquals(
                "the window lands exactly once",
                f.queue.subList(0, 15).map { it.id },
                f.playlist.items.map { it.mediaId },
            )
        } finally {
            f.close()
        }
    }

    // ── the crossfade re-mirror variant ─────────────────────────────────────

    @Test
    fun prewarmAroundMirrorsAroundTheGivenCursorWithoutOwningTheGuards() {
        val f = Fixture()
        try {
            f.queue = f.rows(6)
            // The chassis cursor is irrelevant to this variant: the crossfaded
            // row's index is handed in (the handoff committed it already) and
            // the secondary engine arrives with that row loaded (the swap).
            f.cursor = 0
            f.seedRow(at = 4)
            f.mirror.prewarmAround(f.player, cursor = 4)
            awaitMain({ f.playlist.items.size == 6 }, "the window clamps to the queue tail")

            assertEquals(f.queue.map { it.id }, f.playlist.items.map { it.mediaId })
            assertTrue(
                "the crossfade variant never writes the chassis cursor (the handoff owns it)",
                f.cursorWrites.isEmpty(),
            )
            assertFalse("the crossfade variant takes no job guard", f.mirror.isLoading)
        } finally {
            f.close()
        }
    }

    @Test
    fun prewarmAroundASingleRowQueueIsANoOp() {
        val f = Fixture()
        try {
            f.queue = f.rows(1)
            f.mirror.prewarmAround(f.player, cursor = 0)
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()

            assertTrue("a single row has no lookahead to mirror", f.playlist.items.isEmpty())
            assertTrue(f.builderRequests.isEmpty())
        } finally {
            f.close()
        }
    }

    // ── the transition window slide + its staleness guards ──────────────────

    @Test
    fun extendSlidesTheWindowForwardAndIsASecondCallNoOp() {
        val f = Fixture()
        try {
            f.queue = f.rows(60)
            f.cursor = 2
            f.seedRow(at = 2)
            f.mirror.prewarm(f.player)
            awaitMain({ f.playlist.items.size == 15 }, "seed window")

            // The transition moved the cursor; the slide mirrors exactly the
            // row(s) that entered the lookahead.
            f.cursor = 3
            val requestsBefore = f.builderRequests.size
            f.mirror.extend()
            awaitMain({ f.playlist.items.size == 16 }, "the slide appends only the entered row")

            assertEquals(f.queue.subList(0, 16).map { it.id }, f.playlist.items.map { it.mediaId })
            assertEquals(
                listOf("row15"),
                f.builderRequests.subList(requestsBefore, f.builderRequests.size),
            )

            // A second slide with nothing past the frontier builds nothing.
            val requestsAfterSlide = f.builderRequests.size
            f.mirror.extend()
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals("windowEnd <= frontier → no-op", requestsAfterSlide, f.builderRequests.size)
        } finally {
            f.close()
        }
    }

    @Test
    fun extendSkipsWhenTheMirrorInvariantIsBroken() {
        val f = Fixture()
        try {
            f.queue = f.rows(60)
            f.cursor = 2
            f.seedRow(at = 2)
            f.mirror.prewarm(f.player)
            awaitMain({ f.playlist.items.size == 15 }, "seed window")

            // A playlist whose rows no longer align with the queue (e.g. a
            // stale remove) forfeits the append — extending under it would
            // interleave out of queue order.
            f.playlist.items[0] = MediaItem.Builder().setMediaId("diverged").build()
            val requestsBefore = f.builderRequests.size
            f.cursor = 3
            f.mirror.extend()
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()

            assertEquals("a broken mirror never extends", requestsBefore, f.builderRequests.size)
            assertEquals("the player is untouched", "diverged", f.playlist.items[0].mediaId)
        } finally {
            f.close()
        }
    }

    @Test
    fun extendWithoutAnEngineIsANoOp() {
        val f = Fixture()
        try {
            val playerless = QueuePlaylistMirror(
                scope = f.scope,
                playerProvider = { null },
                queueProvider = { f.queue },
                cursorProvider = { f.cursor },
                writeCursor = { f.cursorWrites += it },
                buildItem = { item ->
                    f.builderRequests.add(item.id)
                    MediaItem.Builder().setMediaId(item.id).build()
                },
            )
            f.queue = f.rows(30)
            f.cursor = 2
            playerless.extend()
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(f.builderRequests.isEmpty())
        } finally {
            f.close()
        }
    }

    // ── the rebuild write ───────────────────────────────────────────────────

    @Test
    fun rebuildReplacesThePlaylistAtTheTargetIndexWithTheWriteTimePosition() {
        val f = Fixture()
        try {
            f.queue = f.rows(60)
            f.mirror.rebuild(
                items = f.queue,
                targetIndex = 20,
                positionMs = { 12_345L },
            )
            awaitMain({ f.playlist.rebuilt > 0 }, "the rebuild writes the player playlist")

            // The same prefix-window shape the pre-warm maintains: rows past
            // the lookahead ahead of the target stay unmirrored.
            assertEquals(f.queue.subList(0, 33).map { it.id }, f.playlist.items.map { it.mediaId })
            assertEquals(20, f.playlist.index)
            assertEquals(12_345L, f.playlist.lastSetMediaItemsPositionMs)
        } finally {
            f.close()
        }
    }

    @Test
    fun rebuildCoercesAnOutOfRangeTargetIntoTheBuiltListsBounds() {
        val f = Fixture()
        try {
            f.queue = f.rows(5)
            f.mirror.rebuild(
                items = f.queue,
                targetIndex = 99,
                positionMs = { 0L },
            )
            awaitMain({ f.playlist.rebuilt > 0 }, "the rebuild writes the player playlist")

            assertEquals(f.queue.map { it.id }, f.playlist.items.map { it.mediaId })
            assertEquals("a partial build must not crash the write", 4, f.playlist.index)
        } finally {
            f.close()
        }
    }

    @Test
    fun rebuildOfAnEmptyQueueIsANoOp() {
        val f = Fixture()
        try {
            f.mirror.rebuild(items = emptyList(), targetIndex = 0, positionMs = { 0L })
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(50)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, f.playlist.rebuilt)
        } finally {
            f.close()
        }
    }
}
