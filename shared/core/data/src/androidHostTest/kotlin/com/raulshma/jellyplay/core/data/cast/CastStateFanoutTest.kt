package com.raulshma.jellyplay.core.data.cast

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pure value-mapping tests for [castStateFanout] — the extracted per-strategy
 * field fan of CastManager's `updateCastState`. No Robolectric, no Android:
 * what is pinned here is exactly which manager-flow fields each strategy
 * refresh contributes and — the declared divergences — which fields each
 * strategy deliberately leaves alone:
 *
 *  - DLNA contributes position / duration / isPlaying / volume only;
 *  - Jellyfin contributes those four plus title / subtitle;
 *  - every other name (google, unknown / ad-hoc registrations)
 *    rides the local CastPlayer payload and contributes position / duration /
 *    buffered / isPlaying / volume;
 *  - title / subtitle are Jellyfin-only (Google Cast titles ride MediaItem
 *    metadata; DLNA exposes none) and bufferedPositionMs is local-player-only
 *    (a media3 concept neither renderer protocol reports);
 *  - a null payload for the active branch leaves every field untouched.
 */
class CastStateFanoutTest {

    private val dlna = DlnaRendererState(
        positionMs = 12_000L,
        durationMs = 300_000L,
        isPlaying = true,
        volume = 0.25f,
    )

    private val jellyfin = JellyfinNowPlayingState(
        positionMs = 1_000L,
        durationMs = 2_000L,
        isPlaying = true,
        volume = 0.5f,
        title = "Pilot",
        subtitle = "Show · S1E1",
    )

    private val player = CastPlayerSnapshot(
        position = 5_000L,
        duration = 6_000L,
        buffered = 5_500L,
        isPlaying = false,
        volume = 0.9f,
    )

    @Test
    fun `DLNA fan-out contributes position duration isPlaying and volume only`() {
        val fanout = castStateFanout(CastStrategyNames.DLNA, dlna = dlna)

        assertEquals(12_000L, fanout.positionMs)
        assertEquals(300_000L, fanout.durationMs)
        assertTrue(fanout.isPlaying!!)
        assertEquals(0.25f, fanout.volume)
        assertNull(fanout.bufferedPositionMs)
        assertNull(fanout.title)
        assertNull(fanout.subtitle)
    }

    @Test
    fun `Jellyfin fan-out contributes position duration isPlaying volume title and subtitle only`() {
        val fanout = castStateFanout(CastStrategyNames.JELLYFIN, jellyfin = jellyfin)

        assertEquals(1_000L, fanout.positionMs)
        assertEquals(2_000L, fanout.durationMs)
        assertTrue(fanout.isPlaying!!)
        assertEquals(0.5f, fanout.volume)
        assertEquals("Pilot", fanout.title)
        assertEquals("Show · S1E1", fanout.subtitle)
        assertNull(fanout.bufferedPositionMs)
    }

    @Test
    fun `local player fan-out contributes position duration buffered isPlaying and volume only`() {
        val fanout = castStateFanout(CastStrategyNames.GOOGLE, player = player)

        assertEquals(5_000L, fanout.positionMs)
        assertEquals(6_000L, fanout.durationMs)
        assertEquals(5_500L, fanout.bufferedPositionMs)
        assertFalse(fanout.isPlaying!!)
        assertEquals(0.9f, fanout.volume)
        assertNull(fanout.title)
        assertNull(fanout.subtitle)
    }

    @Test
    fun `DECLARED divergence - only Jellyfin contributes title and subtitle`() {
        val dlnaFanout = castStateFanout(CastStrategyNames.DLNA, dlna = dlna)
        val playerFanout = castStateFanout(CastStrategyNames.GOOGLE, player = player)

        assertNull(dlnaFanout.title)
        assertNull(dlnaFanout.subtitle)
        assertNull(playerFanout.title)
        assertNull(playerFanout.subtitle)

        val jellyfinFanout = castStateFanout(CastStrategyNames.JELLYFIN, jellyfin = jellyfin)
        assertEquals("Pilot", jellyfinFanout.title)
        assertEquals("Show · S1E1", jellyfinFanout.subtitle)
    }

    @Test
    fun `DECLARED divergence - only the local player transport contributes buffered position`() {
        val dlnaFanout = castStateFanout(CastStrategyNames.DLNA, dlna = dlna)
        val jellyfinFanout = castStateFanout(CastStrategyNames.JELLYFIN, jellyfin = jellyfin)

        assertNull(dlnaFanout.bufferedPositionMs)
        assertNull(jellyfinFanout.bufferedPositionMs)

        val playerFanout = castStateFanout(CastStrategyNames.GOOGLE, player = player)
        assertEquals(5_500L, playerFanout.bufferedPositionMs)
    }

    @Test
    fun `unknown strategy names ride the local player fan-out`() {
        // Same else-arm as the manager's transport dispatch: unknown /
        // ad-hoc registered strategies all ride the manager-owned CastPlayer.
        for (name in listOf("custom-strategy")) {
            val fanout = castStateFanout(name, player = player)

            assertEquals(5_000L, fanout.positionMs)
            assertEquals(6_000L, fanout.durationMs)
            assertEquals(5_500L, fanout.bufferedPositionMs)
            assertFalse(fanout.isPlaying!!)
            assertEquals(0.9f, fanout.volume)
        }
    }

    @Test
    fun `a null payload for the active strategy leaves every field untouched`() {
        // Totality: the manager always gathers the active branch's payload,
        // but a null one must degrade to "write nothing", never crash or
        // partially default.
        val fanout = castStateFanout(CastStrategyNames.JELLYFIN)

        assertNull(fanout.positionMs)
        assertNull(fanout.durationMs)
        assertNull(fanout.bufferedPositionMs)
        assertNull(fanout.isPlaying)
        assertNull(fanout.volume)
        assertNull(fanout.title)
        assertNull(fanout.subtitle)
    }

    @Test
    fun `payloads of non-active strategies are ignored`() {
        val fanout = castStateFanout(CastStrategyNames.DLNA, dlna = dlna, jellyfin = jellyfin, player = player)

        assertEquals(12_000L, fanout.positionMs)
        assertEquals(300_000L, fanout.durationMs)
        assertNull(fanout.title)
        assertNull(fanout.bufferedPositionMs)
    }
}
