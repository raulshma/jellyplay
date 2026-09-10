package com.raulshma.jellyplay.widget

import android.graphics.Bitmap
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for [WidgetPushGate] — the push-decision state behind
 * `NowPlayingWidgetUpdater`, extracted from its loose `lastPushedRender` /
 * `lastItemId` / `lastArtwork` fields so the collector choreography is
 * testable without the updater's Context, collectors or binder pushes.
 *
 * Pins:
 *  - the first metadata decision is a full push, carrying the loaded poster;
 *  - a position tick over an unchanged render is skipped, a pure position
 *    move within the partial window is a partial push (via
 *    [shouldPushPartialPosition]);
 *  - the snapshot re-read AFTER the artwork load is what gets recorded — the
 *    pushed render wins over the metadata trigger that started the load;
 *  - the retained poster survives a failed reload for the same item and is
 *    dropped on an item change;
 *  - reset (presence off / stop) wipes everything, so the next on-cycle
 *    decides exactly like a fresh gate.
 */
class WidgetPushGateTest {

    private val gate = WidgetPushGate()

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

    private fun fullAlbumArt(decision: WidgetPushGate.Decision): Bitmap? =
        (decision as WidgetPushGate.Decision.Full).albumArt

    // ── first push ────────────────────────────────────────────────────────

    @Test
    fun `the first metadata decision is a full push with no poster`() {
        val decision = gate.decideOnMetadata("item-1", loadedArt = null, pushed = snapshot())

        assertTrue(decision is WidgetPushGate.Decision.Full)
        assertNull(fullAlbumArt(decision))
    }

    @Test
    fun `the loaded poster rides the first full push out`() {
        val art = mockk<Bitmap>(relaxed = true)

        val decision = gate.decideOnMetadata("item-1", loadedArt = art, pushed = snapshot())

        assertEquals(art, fullAlbumArt(decision))
    }

    // ── same render vs position-only change (the partial window) ─────────

    @Test
    fun `a position tick over an unchanged render is skipped`() {
        gate.decideOnMetadata("item-1", null, snapshot(positionMs = 65_500L))

        val decision = gate.decideOnPositionTick(snapshot(positionMs = 65_900L))

        assertEquals(WidgetPushGate.Decision.Skip, decision)
    }

    @Test
    fun `a pure position tick across the bucket boundary is a partial push`() {
        gate.decideOnMetadata("item-1", null, snapshot(positionMs = 65_999L))

        val decision = gate.decideOnPositionTick(snapshot(positionMs = 66_000L))

        assertEquals(WidgetPushGate.Decision.Partial, decision)
    }

    @Test
    fun `the partial push records the ticked render - redundant ticks stay skipped`() {
        gate.decideOnMetadata("item-1", null, snapshot(positionMs = 65_999L))
        gate.decideOnPositionTick(snapshot(positionMs = 66_000L))

        val decision = gate.decideOnPositionTick(snapshot(positionMs = 66_400L))

        assertEquals(WidgetPushGate.Decision.Skip, decision)
    }

    @Test
    fun `a metadata move defers the partial even when the position also moved`() {
        gate.decideOnMetadata("item-1", null, snapshot(positionMs = 65_999L))

        val decision = gate.decideOnPositionTick(snapshot(title = "Episode 2", positionMs = 66_000L))

        assertEquals(WidgetPushGate.Decision.Skip, decision)
    }

    // ── artwork-then-reread ordering: the pushed snapshot wins ────────────

    @Test
    fun `the re-read pushed snapshot is recorded - not the metadata trigger`() {
        // The trigger carried "Episode 0", but the manager moved on to
        // "Episode 1" while the poster was loading; the gate is told the
        // post-load re-read.
        val decision = gate.decideOnMetadata(
            triggerItemId = "item-1",
            loadedArt = null,
            pushed = snapshot(title = "Episode 1", positionMs = 65_999L),
        )
        assertTrue(decision is WidgetPushGate.Decision.Full)

        // A later tick compares against what was PUSHED: same title, only
        // the position moved → partial. Had the trigger's "Episode 0" been
        // recorded, this tick would have been suppressed as an unsafe partial.
        assertEquals(
            WidgetPushGate.Decision.Partial,
            gate.decideOnPositionTick(snapshot(title = "Episode 1", positionMs = 66_000L)),
        )
    }

    @Test
    fun `a failed artwork load keeps the previous poster for the same item`() {
        val art = mockk<Bitmap>(relaxed = true)
        gate.decideOnMetadata("item-1", art, snapshot())

        val decision = gate.decideOnMetadata("item-1", null, snapshot(artUrl = "http://art/1"))

        assertEquals(art, fullAlbumArt(decision))
    }

    @Test
    fun `a new item drops the retained poster`() {
        val art = mockk<Bitmap>(relaxed = true)
        gate.decideOnMetadata("item-1", art, snapshot())

        val decision = gate.decideOnMetadata("item-2", null, snapshot(title = "Episode 2"))

        assertNull(fullAlbumArt(decision))
    }

    @Test
    fun `a fresh poster for the same item replaces the retained one`() {
        val art1 = mockk<Bitmap>(relaxed = true)
        val art2 = mockk<Bitmap>(relaxed = true)
        gate.decideOnMetadata("item-1", art1, snapshot())

        val decision = gate.decideOnMetadata("item-1", art2, snapshot())

        assertEquals(art2, fullAlbumArt(decision))
    }

    // ── stop() wipe + presence off/on cycle ───────────────────────────────

    @Test
    fun `reset wipes the pushed render and the retained poster`() {
        val art = mockk<Bitmap>(relaxed = true)
        gate.decideOnMetadata("item-1", art, snapshot(positionMs = 65_999L))

        gate.reset()

        // Position-only requests defer until a full push lands again…
        assertEquals(
            WidgetPushGate.Decision.Skip,
            gate.decideOnPositionTick(snapshot(positionMs = 66_000L)),
        )
        // …and the next metadata decision is a first push, with nothing
        // retained from the previous cycle.
        val decision = gate.decideOnMetadata("item-1", null, snapshot())
        assertTrue(decision is WidgetPushGate.Decision.Full)
        assertNull(fullAlbumArt(decision))
    }

    @Test
    fun `after a presence off-on cycle the gate decides exactly like a fresh one`() {
        // Cycle 1 (presence on): full push, then a partial tick.
        gate.decideOnMetadata("item-1", null, snapshot(positionMs = 65_999L))
        gate.decideOnPositionTick(snapshot(positionMs = 66_000L))

        // Presence off (the updater's stop), then on again.
        gate.reset()
        val regrown = WidgetPushGate()

        // Identical request streams must produce identical decisions on the
        // re-armed gate and a never-used one.
        assertEquals(
            regrown.decideOnPositionTick(snapshot(positionMs = 66_500L)),
            gate.decideOnPositionTick(snapshot(positionMs = 66_500L)),
        )
        val art = mockk<Bitmap>(relaxed = true)
        assertEquals(
            regrown.decideOnMetadata("item-1", art, snapshot()),
            gate.decideOnMetadata("item-1", art, snapshot()),
        )
        assertEquals(
            regrown.decideOnPositionTick(snapshot(positionMs = 67_000L)),
            gate.decideOnPositionTick(snapshot(positionMs = 67_000L)),
        )
    }
}
