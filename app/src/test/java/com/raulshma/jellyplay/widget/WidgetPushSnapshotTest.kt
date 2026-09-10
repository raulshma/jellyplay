package com.raulshma.jellyplay.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the Now Playing widget's render-equality policy, split
 * out of `NowPlayingWidgetUpdater` so the rules are testable without its
 * `Context`, collectors, or binder pushes.
 *
 * Pins:
 *  - position/duration equality is bucketed to whole seconds (the partial
 *    push ticks at 1 Hz anyway) — same second renders equal, a bucket
 *    boundary crossing does not;
 *  - metadata (title/subtitle/artwork/empty-state) changes always differ;
 *  - [WidgetPushSnapshot.sameNonPositionRenderAs] covers exactly what the
 *    position-only partial push cannot re-render — position, duration and
 *    playing-state stay out of it;
 *  - [shouldPushPartialPosition] defers the 1 Hz partial push to the full
 *    metadata push whenever the partial couldn't re-render what moved (or no
 *    full push happened yet), and suppresses redundant renders otherwise.
 */
class WidgetPushSnapshotTest {

    private fun snapshot(
        title: String = "Episode 1",
        subtitle: String? = "Artist",
        isPlaying: Boolean = true,
        positionMs: Long = 65_000L,
        durationMs: Long = 200_000L,
        artUrl: String? = "http://art/1",
        isEmptyState: Boolean = false,
    ) = WidgetPushSnapshot(
        title = title,
        subtitle = subtitle,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = durationMs,
        artUrl = artUrl,
        isEmptyState = isEmptyState,
    )

    // ── positionSecondBucket ──────────────────────────────────────────────

    @Test
    fun `bucketing floors milliseconds to whole seconds`() {
        assertEquals(65L, positionSecondBucket(65_000L))
        assertEquals(65L, positionSecondBucket(65_999L))
        assertEquals(66L, positionSecondBucket(66_000L))
        assertEquals(0L, positionSecondBucket(999L))
    }

    // ── sameRenderAs ──────────────────────────────────────────────────────

    @Test
    fun `positions within the same second render equal`() {
        assertTrue(snapshot(positionMs = 65_123L).sameRenderAs(snapshot(positionMs = 65_987L)))
    }

    @Test
    fun `crossing a second bucket boundary changes the render`() {
        assertFalse(snapshot(positionMs = 65_999L).sameRenderAs(snapshot(positionMs = 66_000L)))
    }

    @Test
    fun `durations are bucketed to whole seconds too`() {
        assertTrue(snapshot(durationMs = 200_123L).sameRenderAs(snapshot(durationMs = 200_987L)))
        assertFalse(snapshot(durationMs = 199_999L).sameRenderAs(snapshot(durationMs = 200_000L)))
    }

    @Test
    fun `any metadata change makes the render differ`() {
        val base = snapshot()

        assertFalse(base.sameRenderAs(base.copy(title = "Episode 2")))
        assertFalse(base.sameRenderAs(base.copy(subtitle = "Other artist")))
        assertFalse(base.sameRenderAs(base.copy(subtitle = null)))
        assertFalse(base.sameRenderAs(base.copy(artUrl = "http://art/2")))
        assertFalse(base.sameRenderAs(base.copy(artUrl = null)))
        assertFalse(base.sameRenderAs(base.copy(isEmptyState = true)))
    }

    @Test
    fun `a playing-state flip changes the full render`() {
        assertFalse(snapshot().sameRenderAs(snapshot(isPlaying = false)))
    }

    @Test
    fun `null subtitle and artwork compare equal`() {
        val a = snapshot(subtitle = null, artUrl = null)
        assertTrue(a.sameRenderAs(a.copy()))
        assertFalse(a.sameRenderAs(a.copy(subtitle = "Artist")))
    }

    // ── sameNonPositionRenderAs ───────────────────────────────────────────

    @Test
    fun `non-position equality ignores position and duration entirely`() {
        val base = snapshot()

        // Not bucketed — ignored outright, so even a bucket boundary crossing
        // is invisible to the partial-push guard.
        assertTrue(base.sameNonPositionRenderAs(base.copy(positionMs = 12_345_678L)))
        assertTrue(base.sameNonPositionRenderAs(base.copy(durationMs = 1L)))
    }

    @Test
    fun `non-position equality ignores the playing state`() {
        // The partial push renders isPlaying into the position label
        // ("Paused ·"); only the transport icon needs the full push.
        assertTrue(snapshot().sameNonPositionRenderAs(snapshot(isPlaying = false)))
    }

    @Test
    fun `non-position equality still sees metadata changes`() {
        val base = snapshot()

        assertFalse(base.sameNonPositionRenderAs(base.copy(title = "Episode 2")))
        assertFalse(base.sameNonPositionRenderAs(base.copy(subtitle = null)))
        assertFalse(base.sameNonPositionRenderAs(base.copy(artUrl = "http://art/2")))
        assertFalse(base.sameNonPositionRenderAs(base.copy(isEmptyState = true)))
    }

    // ── shouldPushPartialPosition (the partial-vs-full deferral rule) ─────

    @Test
    fun `no previous full push defers the partial`() {
        // The partial RemoteViews cannot rebuild title/subtitle/artwork —
        // before any full push there is nothing safe to repaint over.
        assertFalse(shouldPushPartialPosition(lastPushed = null, snapshot = snapshot()))
    }

    @Test
    fun `a non-position render change defers to the full metadata push`() {
        val last = snapshot()
        val moved = snapshot(title = "Episode 2", positionMs = 66_000L)

        assertFalse(shouldPushPartialPosition(last, moved))
    }

    @Test
    fun `an unchanged render suppresses the redundant partial`() {
        val last = snapshot(positionMs = 65_500L)

        assertFalse(shouldPushPartialPosition(last, snapshot(positionMs = 65_900L)))
    }

    @Test
    fun `a pure position tick across the bucket boundary proceeds`() {
        val last = snapshot(positionMs = 65_999L)

        assertTrue(shouldPushPartialPosition(last, snapshot(positionMs = 66_000L)))
    }

    @Test
    fun `a playing-state flip rides the partial push`() {
        // sameNonPositionRenderAs ignores isPlaying while sameRenderAs does
        // not, so the label's "Paused ·" settles via the partial push.
        assertTrue(shouldPushPartialPosition(snapshot(), snapshot(isPlaying = false)))
    }
}
