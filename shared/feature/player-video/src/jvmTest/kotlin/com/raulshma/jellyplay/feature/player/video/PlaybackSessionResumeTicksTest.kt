package com.raulshma.jellyplay.feature.player.video

import kotlin.test.assertEquals
import kotlin.test.Test

/**
 * Pins [resolveResumeTicks] — the pure resume-position resolver behind
 * `PlaybackSession.resolveStartTicksAfterProcessDeath`. The function encodes
 * three invariants that must not drift:
 *
 * 1. **No persisted position → entry point.** `savedPosMs <= 0` (empty store
 *    or a corrupt write) can never move the start position.
 * 2. **Stale position → entry point.** A position persisted with a timestamp
 *    older than the 60-minute staleness window is ignored (the user may have
 *    auto-advanced past it); a MISSING timestamp (`persistedAtMs == 0`) is
 *    treated as fresh so a normal resume-from-background keeps working.
 * 3. **Only advance forward.** A valid saved position resumes at
 *    `savedPosMs * 10_000` (ms → Jellyfin's 100ns ticks) but never below the
 *    deliberate entry point — auto-advance may have moved the user forward of
 *    the route's original ticks, and rewinding would jump back unexpectedly.
 */
class PlaybackSessionResumeTicksTest {

    private val entryPointTicks = 30L * 60L * 1000L * 10_000L // 30 min, ms→ticks

    @Test
    fun noPersistedPosition_keepsEntryPoint() {
        val now = 1_000_000_000L

        // Empty store: nothing persisted.
        assertEquals(
            entryPointTicks,
            resolveResumeTicks(savedPosMs = 0L, persistedAtMs = now, nowMs = now, entryPointTicks = entryPointTicks),
        )
        // A non-positive position is equally unusable.
        assertEquals(
            entryPointTicks,
            resolveResumeTicks(savedPosMs = -1L, persistedAtMs = now, nowMs = now, entryPointTicks = entryPointTicks),
        )
    }

    @Test
    fun stalePositionWithTimestamp_keepsEntryPoint() {
        val now = 1_000_000_000L
        val stalePersistedAt = now - (60L * 60L * 1000L + 1L) // one ms past the 60-minute window

        assertEquals(
            entryPointTicks,
            resolveResumeTicks(
                savedPosMs = 90L * 60L * 1000L,
                persistedAtMs = stalePersistedAt,
                nowMs = now,
                entryPointTicks = entryPointTicks,
            ),
        )
    }

    @Test
    fun positionAtExactThreshold_isStillFresh() {
        val now = 1_000_000_000L
        val persistedAt = now - 60L * 60L * 1000L // exactly the threshold: NOT stale (> comparison)

        // Fresh enough to resume: 45 min saved (past the 30-min entry point).
        assertEquals(
            45L * 60L * 1000L * 10_000L,
            resolveResumeTicks(
                savedPosMs = 45L * 60L * 1000L,
                persistedAtMs = persistedAt,
                nowMs = now,
                entryPointTicks = entryPointTicks,
            ),
        )
    }

    @Test
    fun missingTimestamp_isTreatedAsFresh() {
        val now = 1_000_000_000_000L

        // persistedAtMs == 0 (positions persisted before the timestamp field
        // existed, or a non-process-death re-entry): the staleness guard must
        // not trigger, however large `now` is.
        assertEquals(
            90L * 60L * 1000L * 10_000L,
            resolveResumeTicks(
                savedPosMs = 90L * 60L * 1000L,
                persistedAtMs = 0L,
                nowMs = now,
                entryPointTicks = entryPointTicks,
            ),
        )
    }

    @Test
    fun freshSavedPositionBeyondEntry_resumesAtSavedTicks() {
        val now = 1_000_000_000L

        // 90 min saved (past the 30-min entry point): 5 400 000 ms →
        // 54 000 000 000 ticks (ms × 10 000).
        assertEquals(
            90L * 60L * 1000L * 10_000L,
            resolveResumeTicks(
                savedPosMs = 90L * 60L * 1000L,
                persistedAtMs = now - 1000L,
                nowMs = now,
                entryPointTicks = entryPointTicks,
            ),
        )
    }

    @Test
    fun savedPositionBelowEntryPoint_keepsEntryPoint_neverRewinds() {
        val now = 1_000_000_000L

        // Saved 10 min is BEHIND the 30-min entry point (auto-advance moved the
        // user forward): keep the entry point rather than jumping back.
        assertEquals(
            entryPointTicks,
            resolveResumeTicks(
                savedPosMs = 10L * 60L * 1000L,
                persistedAtMs = now - 1000L,
                nowMs = now,
                entryPointTicks = entryPointTicks,
            ),
        )
    }

    @Test
    fun savedPositionExactlyAtEntryPoint_keepsEntryPoint() {
        val now = 1_000_000_000L

        // Strictly-forward-only: equal ticks resolve to the entry point.
        assertEquals(
            entryPointTicks,
            resolveResumeTicks(
                savedPosMs = 30L * 60L * 1000L, // == entry point in ticks
                persistedAtMs = now - 1000L,
                nowMs = now,
                entryPointTicks = entryPointTicks,
            ),
        )
    }

    @Test
    fun customStaleThreshold_isHonored() {
        val now = 1_000_000_000L
        val persistedAt = now - 5L * 60L * 1000L // 5 minutes old

        // A tighter 1-minute threshold makes the position stale.
        assertEquals(
            entryPointTicks,
            resolveResumeTicks(
                savedPosMs = 90L * 60L * 1000L,
                persistedAtMs = persistedAt,
                nowMs = now,
                entryPointTicks = entryPointTicks,
                staleThresholdMs = 60L * 1000L,
            ),
        )
        // The default 60-minute threshold keeps it fresh.
        assertEquals(
            90L * 60L * 1000L * 10_000L,
            resolveResumeTicks(
                savedPosMs = 90L * 60L * 1000L,
                persistedAtMs = persistedAt,
                nowMs = now,
                entryPointTicks = entryPointTicks,
            ),
        )
    }
}
