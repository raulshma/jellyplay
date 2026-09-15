package com.raulshma.jellyplay.core.data.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure decision tests for [AudioQueuePolicy] — the extracted queue policy
 * the Android media3 `AudioPlaybackManager` and the desktop
 * `DesktopAudioQueueManager` previously duplicated nearly verbatim (the
 * desktop's class-KDoc parity table now points HERE as the pin). What is
 * pinned is exactly the shape of each decision:
 *
 *  - the advance/wrap rule per repeat mode (null = blocked — the adapters'
 *    no-op paths, which push NO undo snapshot);
 *  - the retreat mirror incl. the wrap-to-last head case;
 *  - the skip-previous restart threshold on BOTH sides of 3 s — strictly
 *    greater-than, so position == threshold steps to the previous item;
 *  - the move plan: bounds/no-op rejection, the reorder, and every cursor
 *    remap edge (current follows its row, ±1 on a foreign crossing,
 *    untouched otherwise, -1 cursor stays -1);
 *  - the A→B loop transitions: the full cycle, the set-A clears-at/before-B
 *    guard, the set-B strictly-later guard;
 *  - the position-tick plan: A–B enforcement target + the PRE-seek publish
 *    order, publish dedup (null = leave the flow alone) and the duration
 *    coercion.
 */
class AudioQueuePolicyTest {

    private fun item(id: String) = AudioQueueItem(
        id = id,
        name = "Track $id",
        artist = "Artist $id",
        album = null,
        imageUrl = null,
        mediaSourceId = null,
    )

    private fun queue(vararg ids: String) = ids.map(::item)

    // ── nextIndex (advance/wrap rule) ─────────────────────────────────────

    @Test
    fun `nextIndex advances mid-queue under every repeat mode`() {
        for (mode in 0..2) {
            assertEquals(2, AudioQueuePolicy.nextIndex(index = 1, queueSize = 4, repeatMode = mode), "mode $mode")
        }
    }

    @Test
    fun `nextIndex wraps to head at the tail under repeat ALL and ONE`() {
        assertEquals(0, AudioQueuePolicy.nextIndex(3, 4, AudioQueuePolicy.REPEAT_ALL))
        assertEquals(0, AudioQueuePolicy.nextIndex(3, 4, AudioQueuePolicy.REPEAT_ONE))
    }

    @Test
    fun `nextIndex at the tail under RepeatNone is blocked`() {
        // null = the adapters' no-op path: no cursor write, no undo event.
        assertNull(AudioQueuePolicy.nextIndex(3, 4, AudioQueuePolicy.REPEAT_NONE))
        // Single-row queue: index 0 IS the tail.
        assertNull(AudioQueuePolicy.nextIndex(0, 1, AudioQueuePolicy.REPEAT_NONE))
    }

    @Test
    fun `nextIndex on an empty queue is blocked under every repeat mode`() {
        // Totality guard subsuming desktop's former `q.isNotEmpty()` wrap
        // condition in its ENDED auto-advance copy: an empty queue must take
        // the end-of-queue path even under repeat.
        for (mode in 0..2) {
            assertNull(AudioQueuePolicy.nextIndex(-1, 0, mode), "mode $mode")
        }
    }

    @Test
    fun `nextIndex coerces nothing but is total for stray indices`() {
        // An unset cursor (-1) on a live queue behaves like "before the
        // head": step to 0 — identical to the pre-extraction inline rule.
        assertEquals(0, AudioQueuePolicy.nextIndex(-1, 3, AudioQueuePolicy.REPEAT_NONE))
        assertEquals(0, AudioQueuePolicy.nextIndex(-1, 3, AudioQueuePolicy.REPEAT_ALL))
    }

    // ── previousIndex (retreat rule) ──────────────────────────────────────

    @Test
    fun `previousIndex retreats mid-queue under every repeat mode`() {
        for (mode in 0..2) {
            assertEquals(1, AudioQueuePolicy.previousIndex(2, 4, mode), "mode $mode")
        }
    }

    @Test
    fun `previousIndex wraps to the last row at the head under repeat ALL and ONE`() {
        assertEquals(3, AudioQueuePolicy.previousIndex(0, 4, AudioQueuePolicy.REPEAT_ALL))
        assertEquals(3, AudioQueuePolicy.previousIndex(0, 4, AudioQueuePolicy.REPEAT_ONE))
    }

    @Test
    fun `previousIndex at the head under RepeatNone is blocked and empty queues always are`() {
        assertNull(AudioQueuePolicy.previousIndex(0, 4, AudioQueuePolicy.REPEAT_NONE))
        assertNull(AudioQueuePolicy.previousIndex(0, 1, AudioQueuePolicy.REPEAT_NONE))
        for (mode in 0..2) {
            assertNull(AudioQueuePolicy.previousIndex(-1, 0, mode), "mode $mode")
        }
    }

    // ── skip-previous restart threshold ───────────────────────────────────

    @Test
    fun `skip-previous restarts only strictly past the 3 s default threshold`() {
        // Both sides of the boundary: == threshold does NOT restart (both
        // adapters pinned `>`), one ms past it does.
        assertFalse(AudioQueuePolicy.skipsPreviousRestart(0L))
        assertFalse(AudioQueuePolicy.skipsPreviousRestart(2_999L))
        assertFalse(AudioQueuePolicy.skipsPreviousRestart(3_000L))
        assertTrue(AudioQueuePolicy.skipsPreviousRestart(3_001L))
        assertTrue(AudioQueuePolicy.skipsPreviousRestart(Long.MAX_VALUE))
    }

    @Test
    fun `skip-previous threshold is injectable for the managers' setSkipPreviousThreshold seam`() {
        val custom = 10_000L
        assertFalse(AudioQueuePolicy.skipsPreviousRestart(10_000L, custom))
        assertTrue(AudioQueuePolicy.skipsPreviousRestart(10_001L, custom))
    }

    // ── planMove (reorder + cursor remap) ─────────────────────────────────

    @Test
    fun `planMove rejects out-of-bounds and no-op moves`() {
        val q = queue("a", "b", "c")
        assertNull(AudioQueuePolicy.planMove(q, 0, fromIndex = -1, toIndex = 0))
        assertNull(AudioQueuePolicy.planMove(q, 0, fromIndex = 3, toIndex = 0))
        assertNull(AudioQueuePolicy.planMove(q, 0, fromIndex = 0, toIndex = -1))
        assertNull(AudioQueuePolicy.planMove(q, 0, fromIndex = 0, toIndex = 3))
        assertNull(AudioQueuePolicy.planMove(q, 1, fromIndex = 1, toIndex = 1), "from == to is a no-op")
    }

    @Test
    fun `planMove reorders the list and carries the moved row for the undo event`() {
        val q = queue("a", "b", "c", "d")
        val plan = AudioQueuePolicy.planMove(q, currentIndex = 3, fromIndex = 0, toIndex = 1)!!

        assertEquals(listOf("b", "a", "c", "d"), plan.queue.map { it.id })
        assertEquals(item("a"), plan.movedItem, "the ItemMoved undo payload is the pre-move row")
        assertEquals(3, plan.currentIndex, "foreign move left of the cursor leaves it untouched")
    }

    @Test
    fun `planMove cursor follows the moved current row in both directions`() {
        val q = queue("a", "b", "c", "d")
        // Current row 3 moved to the head: cursor follows to 0.
        AudioQueuePolicy.planMove(q, 3, 3, 0).let {
            assertEquals(0, it!!.currentIndex)
            assertEquals(item("d"), it.movedItem)
        }
        // Current row 0 moved to the tail: cursor follows to 3.
        assertEquals(3, AudioQueuePolicy.planMove(q, 0, 0, 3)!!.currentIndex)
    }

    @Test
    fun `planMove shifts the cursor by one on foreign crossings in both directions`() {
        val q = queue("a", "b", "c", "d")
        // from < current && to >= current: a left row jumps past the cursor
        // → cursor steps down.
        assertEquals(2, AudioQueuePolicy.planMove(q, 3, 0, 3)!!.currentIndex)
        // from > current && to <= current: a right row lands at/before the
        // cursor → cursor steps up.
        assertEquals(1, AudioQueuePolicy.planMove(q, 0, 3, 0)!!.currentIndex)
        assertEquals(2, AudioQueuePolicy.planMove(q, 1, 3, 1)!!.currentIndex)
        // Foreign move entirely on one side: untouched (both orders).
        assertEquals(1, AudioQueuePolicy.planMove(q, 1, 2, 3)!!.currentIndex)
        assertEquals(1, AudioQueuePolicy.planMove(q, 1, 3, 2)!!.currentIndex)
        // Crossing edge — to == current from the left also steps down.
        assertEquals(0, AudioQueuePolicy.planMove(q, 1, 0, 1)!!.currentIndex)
    }

    @Test
    fun `planMove keeps an unset cursor at -1`() {
        // Nothing playing: a -1 cursor never remaps (the pre-extraction
        // else-arm — every from > -1 with to >= 0 falls through to it).
        val q = queue("a", "b", "c")
        assertEquals(-1, AudioQueuePolicy.planMove(q, -1, 0, 2)!!.currentIndex)
        assertEquals(-1, AudioQueuePolicy.planMove(q, -1, 2, 0)!!.currentIndex)
    }

    // ── A→B loop transitions ──────────────────────────────────────────────

    @Test
    fun `abLoop cycle walks nothing - A - B - clear`() {
        val markers = AudioQueuePolicy.AbLoopMarkers()

        val withA = AudioQueuePolicy.cycleAbLoop(positionMs = 10_000L, markers = markers)
        assertEquals(10_000L, withA.startMs)
        assertNull(withA.endMs)

        val withB = AudioQueuePolicy.cycleAbLoop(positionMs = 15_000L, markers = withA)
        assertEquals(10_000L, withB.startMs)
        assertEquals(15_000L, withB.endMs)

        val cleared = AudioQueuePolicy.cycleAbLoop(positionMs = 17_000L, markers = withB)
        assertNull(cleared.startMs)
        assertNull(cleared.endMs)
    }

    @Test
    fun `markAbLoopStart clears a B that would sit at or before the new A`() {
        val markers = AudioQueuePolicy.AbLoopMarkers(startMs = 5_000L, endMs = 12_000L)

        // B strictly after the new A survives.
        AudioQueuePolicy.markAbLoopStart(8_000L, markers).let {
            assertEquals(8_000L, it.startMs)
            assertEquals(12_000L, it.endMs)
        }
        // B AT the new A is cleared (empty loop impossible)…
        assertNull(AudioQueuePolicy.markAbLoopStart(12_000L, markers).endMs)
        // …and so is a B before it (inverted loop impossible).
        assertNull(AudioQueuePolicy.markAbLoopStart(20_000L, markers).endMs)
    }

    @Test
    fun `markAbLoopEnd is a no-op unless A is set and the position is strictly later`() {
        // No A set: markers returned unchanged (the adapters write the pair
        // back; StateFlow conflation drops the equal values).
        val empty = AudioQueuePolicy.AbLoopMarkers()
        assertEquals(empty, AudioQueuePolicy.markAbLoopEnd(9_000L, empty))

        val withA = AudioQueuePolicy.AbLoopMarkers(startMs = 10_000L)
        assertEquals(withA, AudioQueuePolicy.markAbLoopEnd(10_000L, withA), "B == A is rejected")
        assertEquals(withA, AudioQueuePolicy.markAbLoopEnd(9_000L, withA), "B before A is rejected")
        assertEquals(
            AudioQueuePolicy.AbLoopMarkers(10_000L, 10_001L),
            AudioQueuePolicy.markAbLoopEnd(10_001L, withA),
            "strictly later B is written",
        )
    }

    // ── positionTickPlan ──────────────────────────────────────────────────

    @Test
    fun `tick plan seeks back to A on reaching B but still publishes the triggering position`() {
        // The adapters' original order: the PRE-seek position (the one that
        // reached B) is what publishes this tick; the next tick publishes A.
        val plan = AudioQueuePolicy.positionTickPlan(
            positionMs = 30_000L,
            durationMs = 60_000L,
            lastPublishedPositionMs = 29_800L,
            lastPublishedDurationMs = 60_000L,
            hasLyrics = false,
            abLoopStartMs = 12_000L,
            abLoopEndMs = 30_000L,
        )
        assertEquals(12_000L, plan.seekToMs)
        assertEquals(30_000L, plan.publishPositionMs)
    }

    @Test
    fun `tick plan enforces the loop only when both markers are set`() {
        val args = listOf<Long?>(12_000L, null)
        for (start in args) {
            for (end in args) {
                if (start != null && end != null) continue
                val plan = AudioQueuePolicy.positionTickPlan(
                    positionMs = 30_000L, durationMs = 0L,
                    lastPublishedPositionMs = 0L, lastPublishedDurationMs = 0L,
                    hasLyrics = false, abLoopStartMs = start, abLoopEndMs = end,
                )
                assertNull(plan.seekToMs, "start=$start end=$end must not seek")
            }
        }
        // Position strictly BEFORE B does not seek either.
        assertNull(
            AudioQueuePolicy.positionTickPlan(
                29_999L, 0L, 0L, 0L, false, 12_000L, 30_000L,
            ).seekToMs,
        )
    }

    @Test
    fun `tick plan publishes only changed values and coerces the duration`() {
        // Unchanged position/duration → null (leave the flows alone).
        AudioQueuePolicy.positionTickPlan(
            positionMs = 5_000L, durationMs = 60_000L,
            lastPublishedPositionMs = 5_000L, lastPublishedDurationMs = 60_000L,
            hasLyrics = true, abLoopStartMs = null, abLoopEndMs = null,
        ).let {
            assertNull(it.publishPositionMs)
            assertNull(it.publishDurationMs)
            assertNull(it.seekToMs)
            assertTrue(it.updateLyricIndex, "lyrics present → index refresh, independent of publishes")
        }

        // Engine "unknown duration" sentinels coerce to 0 (both adapters
        // coerced identically pre-extraction). The dedup compares the
        // COERCED value (the adapters' original order), so a stale nonzero
        // last-published duration still publishes the coerced 0 — while a
        // sentinel over an already-0 duration publishes nothing.
        AudioQueuePolicy.positionTickPlan(
            positionMs = 5_000L, durationMs = Long.MIN_VALUE,
            lastPublishedPositionMs = 0L, lastPublishedDurationMs = 60_000L,
            hasLyrics = false, abLoopStartMs = null, abLoopEndMs = null,
        ).let {
            assertEquals(5_000L, it.publishPositionMs)
            assertEquals(0L, it.publishDurationMs)
            assertFalse(it.updateLyricIndex, "no lyrics → no index scan")
        }
        AudioQueuePolicy.positionTickPlan(
            positionMs = 5_000L, durationMs = Long.MIN_VALUE,
            lastPublishedPositionMs = 0L, lastPublishedDurationMs = 0L,
            hasLyrics = false, abLoopStartMs = null, abLoopEndMs = null,
        ).let {
            assertNull(it.publishDurationMs, "coerced-equal duration publishes nothing")
        }
    }
}
