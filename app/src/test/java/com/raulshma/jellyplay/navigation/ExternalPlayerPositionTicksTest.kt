package com.raulshma.jellyplay.navigation

import com.raulshma.jellyplay.core.model.ExternalPlayerApp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pins [externalPlaybackOutcome] — the external-player ActivityResult parse
 * that used to be a single "position"/"positionMs" alias fold. The truth
 * table covers every contract arm, routed by the stashed launch's resolved
 * app:
 *
 *  - MPV / mpvKt: `position` 0 = cancelled, >0 = stopped-at, absent =
 *    completed (no position — the reporter marks played explicitly);
 *  - MX Player: `end_by` "playback_completion" = completed, "user" =
 *    stopped-at (`position` ms); an unrecognized `end_by` falls through to
 *    the alias parse;
 *  - VLC: `extra_position`/`extra_duration` ms, equal ⇒ completed;
 *  - chooser arm (null app — unset preference or uninstalled player): the
 *    historical alias parse — a positive value = stopped-at, anything else
 *    = cancelled at the start; a foreign player reporting nothing must NOT
 *    credit completion.
 */
class ExternalPlayerPositionTicksTest {

    private companion object {
        const val START_TICKS = 120_000_000L
    }

    // ── chooser arm: the historical "position"/"positionMs" alias parse ────

    @Test
    fun `chooser arm - missing extras parse as cancelled at the start position`() {
        assertEquals(
            ExternalPlaybackOutcome.Cancelled(START_TICKS),
            externalPlaybackOutcome(resolvedApp = null, startPositionTicks = START_TICKS, extras = emptyMap()),
        )
        assertEquals(
            ExternalPlaybackOutcome.Cancelled(START_TICKS),
            externalPlaybackOutcome(
                resolvedApp = null,
                startPositionTicks = START_TICKS,
                extras = mapOf("something" to 42),
            ),
        )
    }

    @Test
    fun `chooser arm - positive position converts ms to ticks`() {
        assertEquals(
            ExternalPlaybackOutcome.StoppedAt(50_000L),
            externalPlaybackOutcome(null, START_TICKS, mapOf("position" to 5)),
        )
        assertEquals(
            ExternalPlaybackOutcome.StoppedAt(50_000_000_000L),
            externalPlaybackOutcome(null, START_TICKS, mapOf("position" to 5_000_000L)),
        )
    }

    @Test
    fun `chooser arm - positionMs aliases an absent position and fractional values truncate`() {
        assertEquals(
            ExternalPlaybackOutcome.StoppedAt(25_000_000L),
            externalPlaybackOutcome(null, START_TICKS, mapOf("positionMs" to 2_500L)),
        )
        // Number coercion through toLong — fractional truncates.
        assertEquals(
            ExternalPlaybackOutcome.StoppedAt(900_000L),
            externalPlaybackOutcome(null, START_TICKS, mapOf("position" to 90.5f)),
        )
    }

    @Test
    fun `chooser arm - zero, negative and junk positions parse as cancelled`() {
        assertEquals(
            ExternalPlaybackOutcome.Cancelled(START_TICKS),
            externalPlaybackOutcome(null, START_TICKS, mapOf("position" to 0)),
        )
        assertEquals(
            ExternalPlaybackOutcome.Cancelled(START_TICKS),
            externalPlaybackOutcome(null, START_TICKS, mapOf("position" to -100L)),
        )
        assertEquals(
            ExternalPlaybackOutcome.Cancelled(START_TICKS),
            externalPlaybackOutcome(null, START_TICKS, mapOf("position" to "not-a-number")),
        )
    }

    // ── MPV / mpvKt contract ───────────────────────────────────────────────

    @Test
    fun `mpv contract - absent position means completed without a position`() {
        assertEquals(
            ExternalPlaybackOutcome.Completed(0L),
            externalPlaybackOutcome(ExternalPlayerApp.MPV, START_TICKS, emptyMap()),
        )
        assertEquals(
            ExternalPlaybackOutcome.Completed(0L),
            externalPlaybackOutcome(ExternalPlayerApp.MPV_KT, START_TICKS, mapOf("unrelated" to true)),
        )
    }

    @Test
    fun `mpv contract - zero position is a cancellation`() {
        assertEquals(
            ExternalPlaybackOutcome.Cancelled(START_TICKS),
            externalPlaybackOutcome(ExternalPlayerApp.MPV, START_TICKS, mapOf("position" to 0)),
        )
    }

    @Test
    fun `mpv contract - positive position is a stop at that spot in ticks`() {
        assertEquals(
            ExternalPlaybackOutcome.StoppedAt(12_345_000_000L),
            externalPlaybackOutcome(ExternalPlayerApp.MPV, START_TICKS, mapOf("position" to 1_234_500L)),
        )
        assertEquals(
            ExternalPlaybackOutcome.StoppedAt(5_000_000_000_000L),
            externalPlaybackOutcome(ExternalPlayerApp.MPV_KT, START_TICKS, mapOf("position" to 500_000_000L)),
        )
    }

    // ── MX Player contract ─────────────────────────────────────────────────

    @Test
    fun `mx contract - playback_completion is completed at the reported position`() {
        assertEquals(
            ExternalPlaybackOutcome.Completed(9_000_000_000L),
            externalPlaybackOutcome(
                ExternalPlayerApp.MX_PLAYER_FREE,
                START_TICKS,
                mapOf("end_by" to "playback_completion", "position" to 900_000L, "duration" to 900_000L),
            ),
        )
        // The pro flavor routes to the same contract.
        assertEquals(
            ExternalPlaybackOutcome.Completed(9_000_000_000L),
            externalPlaybackOutcome(
                ExternalPlayerApp.MX_PLAYER_PRO,
                START_TICKS,
                mapOf("end_by" to "playback_completion", "position" to 900_000L),
            ),
        )
    }

    @Test
    fun `mx contract - user end is a stop at the reported position`() {
        assertEquals(
            ExternalPlaybackOutcome.StoppedAt(6_000_000_000L),
            externalPlaybackOutcome(
                ExternalPlayerApp.MX_PLAYER_FREE,
                START_TICKS,
                mapOf("end_by" to "user", "position" to 600_000L, "duration" to 900_000L),
            ),
        )
    }

    @Test
    fun `mx contract - user end without a usable position is a cancellation`() {
        assertEquals(
            ExternalPlaybackOutcome.Cancelled(START_TICKS),
            externalPlaybackOutcome(ExternalPlayerApp.MX_PLAYER_FREE, START_TICKS, mapOf("end_by" to "user")),
        )
        assertEquals(
            ExternalPlaybackOutcome.Cancelled(START_TICKS),
            externalPlaybackOutcome(ExternalPlayerApp.MX_PLAYER_FREE, START_TICKS, mapOf("end_by" to "user", "position" to 0)),
        )
    }

    @Test
    fun `mx contract - an unrecognized end_by falls through to the alias parse`() {
        // The alias parse: a positive `position` is a stop at that spot.
        assertEquals(
            ExternalPlaybackOutcome.StoppedAt(50_000L),
            externalPlaybackOutcome(
                ExternalPlayerApp.MX_PLAYER_FREE,
                START_TICKS,
                mapOf("end_by" to "unexpected", "position" to 5),
            ),
        )
    }

    // ── VLC contract ───────────────────────────────────────────────────────

    @Test
    fun `vlc contract - position equal to duration means completed`() {
        assertEquals(
            ExternalPlaybackOutcome.Completed(9_000_000_000L),
            externalPlaybackOutcome(
                ExternalPlayerApp.VLC,
                START_TICKS,
                mapOf("extra_position" to 900_000L, "extra_duration" to 900_000L),
            ),
        )
    }

    @Test
    fun `vlc contract - position past duration is completed too`() {
        assertEquals(
            ExternalPlaybackOutcome.Completed(9_001_000_000L),
            externalPlaybackOutcome(
                ExternalPlayerApp.VLC,
                START_TICKS,
                mapOf("extra_position" to 900_100L, "extra_duration" to 900_000L),
            ),
        )
    }

    @Test
    fun `vlc contract - position below duration is a stop at the position`() {
        assertEquals(
            ExternalPlaybackOutcome.StoppedAt(3_000_000_000L),
            externalPlaybackOutcome(
                ExternalPlayerApp.VLC,
                START_TICKS,
                mapOf("extra_position" to 300_000L, "extra_duration" to 900_000L),
            ),
        )
    }

    @Test
    fun `vlc contract - missing position is a cancellation`() {
        assertEquals(
            ExternalPlaybackOutcome.Cancelled(START_TICKS),
            externalPlaybackOutcome(ExternalPlayerApp.VLC, START_TICKS, emptyMap()),
        )
        assertEquals(
            ExternalPlaybackOutcome.Cancelled(START_TICKS),
            externalPlaybackOutcome(ExternalPlayerApp.VLC, START_TICKS, mapOf("extra_duration" to 900_000L)),
        )
    }
}
