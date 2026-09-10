package com.raulshma.jellyplay

import com.raulshma.jellyplay.core.data.playback.PipAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM test table for [PipActionSet] — the remote-action decision tables that
 * lived inline in PlayerActivity with nothing pinning their sync. Pins:
 *
 *  - the action-set fork table over all four `(isPlaying, hasNext)` cells —
 *    the fixed skip-back → play/pause → skip-forward spine, next only when
 *    `hasNext`;
 *  - the play/pause icon+title fork (icon AND title swap together, and the
 *    playing/paused picks differ — a swapped pair would show a pause icon
 *    labelled "Play");
 *  - the id codec round-trip for EVERY [PipAction] (encode and decode are
 *    separate tables in the source; this is the pin that keeps them in sync);
 *  - unknown ids decode to null (the receiver's default-extra arm);
 *  - the wire protocol constants, verbatim (PendingIntents and broadcasts
 *    reference them across app updates).
 */
class PipActionSetTest {

    private fun actions(isPlaying: Boolean, hasNext: Boolean): List<PipAction> =
        PipActionSet.actionSpecs(isPlaying = isPlaying, hasNext = hasNext).map { it.action }

    // ── Action-set fork table ───────────────────────────────────────────────

    @Test
    fun `paused without next renders the three-action spine`() {
        assertEquals(
            listOf(PipAction.SKIP_BACKWARD, PipAction.PLAY, PipAction.SKIP_FORWARD),
            actions(isPlaying = false, hasNext = false),
        )
    }

    @Test
    fun `playing without next swaps play for pause in place`() {
        assertEquals(
            listOf(PipAction.SKIP_BACKWARD, PipAction.PAUSE, PipAction.SKIP_FORWARD),
            actions(isPlaying = true, hasNext = false),
        )
    }

    @Test
    fun `paused with next appends the gated tail`() {
        assertEquals(
            listOf(PipAction.SKIP_BACKWARD, PipAction.PLAY, PipAction.SKIP_FORWARD, PipAction.NEXT),
            actions(isPlaying = false, hasNext = true),
        )
    }

    @Test
    fun `playing with next appends the gated tail after pause`() {
        assertEquals(
            listOf(PipAction.SKIP_BACKWARD, PipAction.PAUSE, PipAction.SKIP_FORWARD, PipAction.NEXT),
            actions(isPlaying = true, hasNext = true),
        )
    }

    // ── Play/pause icon + title fork ────────────────────────────────────────

    private fun transportSpec(action: PipAction): PipActionSet.ActionSpec =
        PipActionSet.actionSpecs(isPlaying = action == PipAction.PAUSE, hasNext = false)
            .first { it.action == action }

    @Test
    fun `playing picks the pause icon with the pause title`() {
        val spec = transportSpec(PipAction.PAUSE)
        assertEquals(android.R.drawable.ic_media_pause, spec.iconRes)
        assertEquals(R.string.media_pause, spec.titleRes)
    }

    @Test
    fun `paused picks the play icon with the play title`() {
        val spec = transportSpec(PipAction.PLAY)
        assertEquals(android.R.drawable.ic_media_play, spec.iconRes)
        assertEquals(R.string.media_play, spec.titleRes)
    }

    @Test
    fun `the play-pause fork swaps BOTH icon and title`() {
        val paused = transportSpec(PipAction.PLAY)
        val playing = transportSpec(PipAction.PAUSE)
        assertNotEquals(paused.iconRes, playing.iconRes)
        assertNotEquals(paused.titleRes, playing.titleRes)
    }

    @Test
    fun `skip back and skip forward carry distinct icons`() {
        val skipBack = PipActionSet.actionSpecs(isPlaying = false, hasNext = false)
            .first { it.action == PipAction.SKIP_BACKWARD }
        val skipForward = transportSpec(PipAction.SKIP_FORWARD)
        assertNotEquals(skipBack.iconRes, skipForward.iconRes)
    }

    // ── Id codec round-trip ─────────────────────────────────────────────────

    @Test
    fun `every action round-trips through the id codec`() {
        for (action in PipAction.entries) {
            assertEquals(action, PipActionSet.actionForId(PipActionSet.idFor(action)))
        }
    }

    @Test
    fun `every rendered spec's id decodes back to its own action`() {
        for (isPlaying in listOf(false, true)) {
            for (hasNext in listOf(false, true)) {
                for (spec in PipActionSet.actionSpecs(isPlaying = isPlaying, hasNext = hasNext)) {
                    assertEquals(spec.action, PipActionSet.actionForId(PipActionSet.idFor(spec.action)))
                }
            }
        }
    }

    @Test
    fun `id table values are the historical wire ids`() {
        assertEquals(1, PipActionSet.PIP_ACTION_PLAY)
        assertEquals(2, PipActionSet.PIP_ACTION_PAUSE)
        assertEquals(3, PipActionSet.PIP_ACTION_SKIP_FORWARD)
        assertEquals(4, PipActionSet.PIP_ACTION_SKIP_BACK)
        assertEquals(5, PipActionSet.PIP_ACTION_NEXT)
    }

    @Test
    fun `wire ids are distinct`() {
        val ids = PipAction.entries.map { PipActionSet.idFor(it) }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `unknown ids decode to null`() {
        assertNull(PipActionSet.actionForId(-1))
        assertNull(PipActionSet.actionForId(0))
        assertNull(PipActionSet.actionForId(6))
        assertNull(PipActionSet.actionForId(Int.MIN_VALUE))
        assertNull(PipActionSet.actionForId(Int.MAX_VALUE))
    }

    // ── Wire protocol constants ─────────────────────────────────────────────

    @Test
    fun `broadcast protocol strings are unchanged`() {
        assertEquals("com.raulshma.jellyplay.PIP_ACTION", PipActionSet.PIP_ACTION_BROADCAST)
        assertEquals("pip_action_id", PipActionSet.PIP_ACTION_EXTRA)
    }
}
