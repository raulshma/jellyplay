package com.raulshma.jellyplay.core.data.playback

/**
 * Deep module: the ONE owner of the pure audio-queue policy the two
 * [AudioQueueManager] adapters previously duplicated nearly verbatim —
 * Android's media3 `AudioPlaybackManager` (legacy `:core:data` androidMain)
 * and desktop's mpv `DesktopAudioQueueManager`
 * (apps/desktop), whose class-KDoc parity table was the semantic pin until
 * this extraction (it now points HERE). Every function takes plain inputs
 * (sizes, indices, the Int repeat-mode encoding, positions, timestamps) and
 * returns a decision; NO ExoPlayer, NO mpv, NO Android imports, no side
 * effects — the adapters keep the effects (engine writes, StateFlow
 * publishes, undo-snapshot pushes).
 *
 * Public (not internal) because the desktop adapter lives in another module
 * (`PlaybackVolumePolicy` precedent): cross-module callers are exactly the
 * two queue-manager adapters, nothing else.
 *
 * ## Invariants (each pinned by `AudioQueuePolicyTest`, jvmTest)
 *  - ADVANCE RULE ([nextIndex]): mid-queue → +1; at the tail → 0 under
 *    repeat ALL or ONE, else null (blocked). An empty queue is always null.
 *    Desktop called this rule in THREE places (manual skipToNext, ENDED
 *    auto-advance, next-item prefetch); its ENDED copy additionally guarded
 *    the wrap branch with `q.isNotEmpty()` — the total `queueSize <= 0 →
 *    null` head subsumes that guard, so all three copies reduce to this
 *    one function with zero behavior drift.
 *  - RETREAT RULE ([previousIndex]): mirror image — mid-queue → -1; at the
 *    head → lastIndex under repeat ≥ ALL, else null.
 *  - SKIP-PREVIOUS RESTART ([skipsPreviousRestart]): strictly greater than
 *    the threshold — position == threshold does NOT restart (both adapters
 *    pinned `>`; default [SKIP_PREVIOUS_RESTART_THRESHOLD_MS] = 3 s).
 *  - MOVE REMAP ([planMove]): rejected (null) for out-of-bounds or no-op
 *    moves; the cursor follows a moved current row, shifts by one when a
 *    foreign row crosses it, and is untouched otherwise (a -1 cursor stays
 *    -1 — nothing is playing).
 *  - A→B LOOP ([AbLoopMarkers] + [cycleAbLoop]): nothing → set A → set B
 *    (looping) → clear. Setting A clears a B that would now sit at or
 *    before it; setting B requires A set AND a strictly later position, so
 *    empty/inverted loops are impossible by construction.
 *  - POSITION TICK ([positionTickPlan]): A–B enforcement seeks back to A on
 *    reaching B but still publishes the position that TRIGGERED the loop
 *    (the pre-seek value — the adapters' original order; the next tick
 *    publishes A). Unchanged position/duration publish as null (writing
 *    them back would be a StateFlow-conflated no-op anyway).
 *  - END-OF-STREAM STOP POSITION ([finalStopPositionTicks]): last published
 *    position wins; otherwise the item's full duration stands in (the row
 *    ended); ticks are 100-ns units; total, 0/0 → 0.
 */
object AudioQueuePolicy {

    /** [AudioQueueManager.repeatMode] encoding: 0 = off, 1 = all, 2 = one. */
    const val REPEAT_NONE: Int = 0

    /** Wrap-to-head boundary for both [nextIndex] and [previousIndex]. */
    const val REPEAT_ALL: Int = 1

    /**
     * Repeat-one. Manual/auto advance treats it like [REPEAT_ALL] for
     * wrapping (the engine replays the row itself under media3); the desktop
     * ENDED path short-circuits to an engine replay before consulting
     * [nextIndex].
     */
    const val REPEAT_ONE: Int = 2

    /** Production default for [skipsPreviousRestart]'s threshold. */
    const val SKIP_PREVIOUS_RESTART_THRESHOLD_MS: Long = 3_000L

    /**
     * One accepted `moveQueueItem(from, to)` — the reordered list plus the
     * remapped cursor and the moved row (for the adapters'
     * [QueueUndoEvent.ItemMoved] push, which fires BEFORE the writes).
     */
    data class QueueMovePlan(
        val movedItem: AudioQueueItem,
        val queue: List<AudioQueueItem>,
        val currentIndex: Int,
    )

    /**
     * Pure `moveQueueItem`: bounds/no-op validation, the reorder, and the
     * cursor remap in one decision. Returns null (caller no-ops, no undo
     * snapshot) when either index is out of bounds or from == to.
     *
     * Remap: the cursor follows its own row ([currentIndex] == fromIndex →
     * toIndex); a foreign row crossing it from the left (from < current,
     * to >= current) pushes the cursor down by one; crossing from the right
     * (from > current, to <= current) pushes it up by one; anything else
     * leaves it untouched.
     */
    fun planMove(
        queue: List<AudioQueueItem>,
        currentIndex: Int,
        fromIndex: Int,
        toIndex: Int,
    ): QueueMovePlan? {
        if (fromIndex < 0 || fromIndex >= queue.size) return null
        if (toIndex < 0 || toIndex >= queue.size) return null
        if (fromIndex == toIndex) return null
        val movedItem = queue[fromIndex]
        val reordered = queue.toMutableList().apply {
            removeAt(fromIndex)
            add(toIndex, movedItem)
        }
        val newIndex = when {
            currentIndex == fromIndex -> toIndex
            fromIndex < currentIndex && toIndex >= currentIndex -> currentIndex - 1
            fromIndex > currentIndex && toIndex <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        return QueueMovePlan(movedItem = movedItem, queue = reordered, currentIndex = newIndex)
    }

    /**
     * The advance/wrap rule shared by manual skip-to-next, natural-end
     * auto-advance and next-item prefetch: +1 mid-queue; wrap to 0 at the
     * tail under repeat ALL/ONE; null = blocked (RepeatNone at the tail, or
     * an empty queue — total, so callers need no separate emptiness guard).
     * A null result pushes NO undo snapshot in the adapters.
     */
    fun nextIndex(index: Int, queueSize: Int, repeatMode: Int): Int? = when {
        queueSize <= 0 -> null
        index < queueSize - 1 -> index + 1
        repeatMode >= REPEAT_ALL -> 0
        else -> null
    }

    /**
     * Mirror of [nextIndex] for skip-to-previous: -1 mid-queue; wrap to the
     * last row at the head under repeat ≥ ALL; null = blocked (callers
     * no-op without an undo snapshot). ALWAYS consulted after
     * [skipsPreviousRestart] — the restart branch never moves the cursor.
     */
    fun previousIndex(index: Int, queueSize: Int, repeatMode: Int): Int? = when {
        queueSize <= 0 -> null
        index > 0 -> index - 1
        repeatMode >= REPEAT_ALL -> queueSize - 1
        else -> null
    }

    /**
     * Skip-previous restart decision: true → the caller seeks the CURRENT
     * item to zero and returns (no cursor move, no undo snapshot). Strictly
     * greater-than — a position exactly AT the threshold steps to the
     * previous item instead.
     */
    fun skipsPreviousRestart(
        positionMs: Long,
        thresholdMs: Long = SKIP_PREVIOUS_RESTART_THRESHOLD_MS,
    ): Boolean = positionMs > thresholdMs

    /** The A→B loop markers; both null = loop disabled. */
    data class AbLoopMarkers(
        val startMs: Long? = null,
        val endMs: Long? = null,
    )

    /**
     * Marks A at [positionMs]: A is written unconditionally; an existing B
     * at or before the new A is cleared (a loop may never be empty or
     * inverted).
     */
    fun markAbLoopStart(positionMs: Long, markers: AbLoopMarkers): AbLoopMarkers =
        AbLoopMarkers(
            startMs = positionMs,
            endMs = if (markers.endMs != null && markers.endMs <= positionMs) null else markers.endMs,
        )

    /**
     * Marks B at [positionMs]: a no-op (markers returned unchanged) unless A
     * is set AND [positionMs] is strictly after it — so `B <= A` can never
     * be written. Adapters may assign the returned pair to their flows
     * verbatim; the unchanged case writes back equal values, which
     * StateFlow conflation drops.
     */
    fun markAbLoopEnd(positionMs: Long, markers: AbLoopMarkers): AbLoopMarkers =
        if (markers.startMs == null || positionMs <= markers.startMs) markers
        else AbLoopMarkers(startMs = markers.startMs, endMs = positionMs)

    /**
     * The `cycleAbLoop` state machine: A unset → mark A; A set, B unset →
     * mark B; both set → clear. [positionMs] is the caller's CURRENT
     * playback position (engine position when live, last published
     * otherwise — an adapter concern).
     */
    fun cycleAbLoop(positionMs: Long, markers: AbLoopMarkers): AbLoopMarkers = when {
        markers.startMs == null -> markAbLoopStart(positionMs, markers)
        markers.endMs == null -> markAbLoopEnd(positionMs, markers)
        else -> AbLoopMarkers(null, null)
    }

    /**
     * One active position-tick's decisions. `null` fields mean "leave the
     * corresponding flow at its previous value" (the [CastStateFanout]
     * null-keeps-previous idiom): an unchanged position/duration publishes
     * nothing, and [updateLyricIndex] false skips the lyric-index scan.
     */
    data class PositionTickPlan(
        val seekToMs: Long?,
        val publishPositionMs: Long?,
        val publishDurationMs: Long?,
        val updateLyricIndex: Boolean,
    )

    /**
     * The shared tick body of both adapters' position tickers
     * (engine-poll plumbing stays per-adapter on [EnginePositionTicker]'s
     * contract). DECISIONS ONLY, in the adapters' original order:
     *
     *  1. A–B enforcement: both markers set and [positionMs] has reached B
     *     → [seekToMs] = A (else null). The publish below still carries the
     *     PRE-seek [positionMs] that triggered the loop — intentional, see
     *     the class KDoc.
     *  2. Position publish: [positionMs] itself when it differs from
     *     [lastPublishedPositionMs], else null.
     *  3. Duration publish: [durationMs] coerced to >= 0 (engines report a
     *     sentinel for unknown — both adapters coerced identically) when it
     *     differs from [lastPublishedDurationMs], else null.
     *  4. Lyric index: refresh when [hasLyrics] (the caller re-reads its
     *     position flow, which this plan just brought up to date).
     */
    fun positionTickPlan(
        positionMs: Long,
        durationMs: Long,
        lastPublishedPositionMs: Long,
        lastPublishedDurationMs: Long,
        hasLyrics: Boolean,
        abLoopStartMs: Long?,
        abLoopEndMs: Long?,
    ): PositionTickPlan {
        val seekToMs = if (abLoopEndMs != null && abLoopStartMs != null && positionMs >= abLoopEndMs) {
            abLoopStartMs
        } else {
            null
        }
        val duration = durationMs.coerceAtLeast(0L)
        return PositionTickPlan(
            seekToMs = seekToMs,
            publishPositionMs = if (positionMs != lastPublishedPositionMs) positionMs else null,
            publishDurationMs = if (duration != lastPublishedDurationMs) duration else null,
            updateLyricIndex = hasLyrics,
        )
    }

    /**
     * The end-of-stream stop position every track handoff reports for the
     * PREVIOUS item: the last published position when one exists, else the
     * item's full duration (the engine ended the row, so "no position
     * published" means it played to the end), converted to 100-ns ticks —
     * Jellyfin's `playbackPositionTicks` unit. Total: any input pair yields a
     * non-negative result; the 0/0 pair (nothing ever published) reports 0.
     *
     * Was a verbatim `if (position > 0) position * 10_000 else duration *
     * 10_000` three-peat: Android's natural-transition site, Android's
     * crossfade site (both `AudioPlaybackManager`), and commonMain's
     * `AudioQueueStateCore.transitionTo` (which pins the shared shape).
     */
    fun finalStopPositionTicks(positionMs: Long, durationMs: Long): Long =
        if (positionMs > 0) positionMs * 10_000 else durationMs * 10_000
}
