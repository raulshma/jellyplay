package com.raulshma.jellyplay.feature.home

import com.raulshma.jellyplay.core.model.TrickplayInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Covers the pure decisions behind the H13 wide-card trickplay peek:
 *  - [trickplayTileAt] (the player's index math, hoisted: position → thumbnail
 *    index + sprite sheet)
 *  - [shouldLoadTrickplayPeek] (the suppression ladder: TV ∧ pref ∧
 *    ¬performance ∧ projected Trickplay ∧ resume position)
 *
 * No Compose/Koin needed — both are plain functions.
 */
class HomeTrickplayPeekTest {

    /** 10×10 grid of 320×180 tiles, 40 s strip at a 4 s interval. */
    private fun info(
        interval: Int = 4_000,
        thumbnailCount: Int = 10,
        tileWidth: Int = 2,
        tileHeight: Int = 2,
    ) = TrickplayInfo(
        width = 320,
        height = 180,
        tileWidth = tileWidth,
        tileHeight = tileHeight,
        thumbnailCount = thumbnailCount,
        interval = interval,
        bandwidth = 0,
    )

    // -- trickplayTileAt -------------------------------------------------------

    @Test
    fun `resolves the tile index at the resume position`() {
        // 9.5 s into a 4 s-interval strip → tile 2, still in sheet 0.
        val location = trickplayTileAt(positionMs = 9_500, info = info())
        assertEquals(2, location.thumbnailIndex)
        assertEquals(0, location.sheetIndex)
    }

    @Test
    fun `position zero maps to the first tile`() {
        val location = trickplayTileAt(positionMs = 0, info = info())
        assertEquals(0, location.thumbnailIndex)
        assertEquals(0, location.sheetIndex)
    }

    @Test
    fun `clamps past the end of the strip`() {
        // Far (but Int-safe) beyond the strip: 1e9 ms / 4 s = 250 000 → tile 9.
        val location = trickplayTileAt(positionMs = 1_000_000_000L, info = info(thumbnailCount = 10))
        assertEquals(9, location.thumbnailIndex)
    }

    @Test
    fun `splits the strip across sprite sheets`() {
        // 5×5 grid = 25 tiles per sheet; tile 30 lives on sheet 1.
        val location = trickplayTileAt(
            positionMs = 120_000,
            info = info(interval = 4_000, thumbnailCount = 50, tileWidth = 5, tileHeight = 5),
        )
        assertEquals(30, location.thumbnailIndex)
        assertEquals(1, location.sheetIndex)
    }

    @Test
    fun `degenerate interval does not divide by zero`() {
        // A stale/manifest-less server payload can carry 0 — the player's
        // guard coerces to 1 ms; the home math must survive the same input.
        val location = trickplayTileAt(positionMs = 5, info = info(interval = 0))
        assertEquals(5, location.thumbnailIndex)
    }

    @Test
    fun `degenerate thumbnail count clamps to zero`() {
        val location = trickplayTileAt(positionMs = 10_000, info = info(thumbnailCount = 0))
        assertEquals(0, location.thumbnailIndex)
    }

    // -- shouldLoadTrickplayPeek ------------------------------------------------

    private fun gate(
        isTv: Boolean = true,
        prefEnabled: Boolean = true,
        performanceMode: Boolean = false,
        trickplayInfo: TrickplayInfo? = info(),
        playbackPositionTicks: Long? = 60_000_000L, // 10 minutes
    ) = shouldLoadTrickplayPeek(
        isTv = isTv,
        prefEnabled = prefEnabled,
        performanceMode = performanceMode,
        trickplayInfo = trickplayInfo,
        playbackPositionTicks = playbackPositionTicks,
    )

    @Test
    fun `loads on tv with every gate open`() {
        assertTrue(gate())
    }

    @Test
    fun `never loads off tv (touch is v1-none)`() {
        assertFalse(gate(isTv = false))
    }

    @Test
    fun `suppresses when the pref is off`() {
        assertFalse(gate(prefEnabled = false))
    }

    @Test
    fun `suppresses in performance mode`() {
        assertFalse(gate(performanceMode = true))
    }

    @Test
    fun `suppresses without projected trickplay info`() {
        // Offline rows (and listings that never projected `Trickplay`) map to
        // null — degrade to nothing, never a speculative fetch.
        assertFalse(gate(trickplayInfo = null))
    }

    @Test
    fun `suppresses without a resume position`() {
        assertFalse(gate(playbackPositionTicks = null))
        assertFalse(gate(playbackPositionTicks = 0L))
    }
}
