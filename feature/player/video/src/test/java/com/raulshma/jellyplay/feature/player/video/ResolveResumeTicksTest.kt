package com.raulshma.jellyplay.feature.player.video

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [resolveResumeTicks] — the pure resume-position resolver that
 * backs the VideoPlayerViewModel's process-death restore. Covers the staleness
 * guard (issue #104) and the "only advance forward" rule.
 *
 * Ticks ↔ ms conversion is ×10_000 (Jellyfin ticks are 100ns; ms × 10_000).
 */
class ResolveResumeTicksTest {

    private val staleThresholdMs = 60L * 60L * 1000L // 1h, matches production default
    private val now = 1_700_000_000_000L

    /** ms → ticks helper to keep assertions readable. */
    private fun ticks(ms: Long): Long = ms * 10_000L

    @Test
    fun `no persisted position keeps the entry point`() {
        assertEquals(
            123_456_700L,
            resolveResumeTicks(
                savedPosMs = 0L,
                persistedAtMs = now,
                nowMs = now,
                entryPointTicks = 123_456_700L,
            ),
        )
    }

    @Test
    fun `negative persisted position keeps the entry point`() {
        assertEquals(
            500L,
            resolveResumeTicks(savedPosMs = -100L, persistedAtMs = now, nowMs = now, entryPointTicks = 500L),
        )
    }

    @Test
    fun `fresh persisted position beyond entry point resumes there`() {
        // savedPosMs = 5_000ms (→ 50_000_000 ticks); entry = 10_000_000 ticks.
        assertEquals(
            ticks(5_000L),
            resolveResumeTicks(savedPosMs = 5_000L, persistedAtMs = now, nowMs = now, entryPointTicks = ticks(1_000L)),
        )
    }

    @Test
    fun `persisted position below entry point is clamped to entry point`() {
        // savedPosMs = 1_000ms (→ 10_000_000 ticks); entry = 50_000_000 ticks → keep entry (no rewind).
        assertEquals(
            ticks(5_000L),
            resolveResumeTicks(savedPosMs = 1_000L, persistedAtMs = now, nowMs = now, entryPointTicks = ticks(5_000L)),
        )
    }

    @Test
    fun `position persisted just under the threshold resumes there`() {
        val persistedAt = now - (staleThresholdMs - 1_000L) // 59 min ago
        assertEquals(
            ticks(5_000L),
            resolveResumeTicks(
                savedPosMs = 5_000L,
                persistedAtMs = persistedAt,
                nowMs = now,
                entryPointTicks = ticks(1_000L),
                staleThresholdMs = staleThresholdMs,
            ),
        )
    }

    @Test
    fun `position older than the threshold is rejected for entry point`() {
        val persistedAt = now - (staleThresholdMs + 1_000L) // 1h 1min ago
        assertEquals(
            ticks(1_000L),
            resolveResumeTicks(
                savedPosMs = 5_000L, // would resume at ticks(5_000) if fresh
                persistedAtMs = persistedAt,
                nowMs = now,
                entryPointTicks = ticks(1_000L),
                staleThresholdMs = staleThresholdMs,
            ),
        )
    }

    @Test
    fun `missing timestamp (zero) is treated as fresh and resumes there`() {
        // Back-compat: positions persisted before the timestamp field existed.
        assertEquals(
            ticks(5_000L),
            resolveResumeTicks(savedPosMs = 5_000L, persistedAtMs = 0L, nowMs = now, entryPointTicks = ticks(1_000L)),
        )
    }
}
