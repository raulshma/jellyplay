package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.model.TrickplayInfo
import com.raulshma.jellyplay.core.model.TrickplayMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the trickplay presentation policy (#7, [TrickplayOverlayPolicy.kt]):
 * the tile-index fold (the SAME floor-and-clamp TrickplayManager applies),
 * the filmstrip window math, the mode→presentation fold both call sites
 * render through, the anchor mapping, and the manifest-missing fallback.
 */
class TrickplayTileIndexTest {

    private val info = TrickplayInfo(
        width = 160, height = 90, tileWidth = 5, tileHeight = 3,
        thumbnailCount = 100, interval = 10_000, bandwidth = 32_000,
    )

    @Test
    fun floorsPositionOverInterval() {
        assertEquals(0, trickplayTileIndex(0L, info.interval, info.thumbnailCount))
        assertEquals(0, trickplayTileIndex(9_999L, info.interval, info.thumbnailCount))
        assertEquals(1, trickplayTileIndex(10_000L, info.interval, info.thumbnailCount))
        assertEquals(42, trickplayTileIndex(429_999L, info.interval, info.thumbnailCount))
    }

    @Test
    fun clampsIntoTheManifest() {
        assertEquals(0, trickplayTileIndex(-5_000L, info.interval, info.thumbnailCount))
        assertEquals(99, trickplayTileIndex(9_999_999L, info.interval, info.thumbnailCount))
    }

    @Test
    fun guardsMalformedManifests() {
        // A zero/negative interval floors to 1 (the TrickplayManager guard) —
        // no ArithmeticException; a zero count collapses to tile 0.
        assertEquals(0, trickplayTileIndex(5_000L, 0, 0))
        assertEquals(5_000, trickplayTileIndex(5_000L, 0, 100_000))
        assertEquals(0, trickplayTileIndex(5_000L, 10_000, -1))
    }
}

class FilmstripWindowTest {

    @Test
    fun centersTheWindowOnTheSeekTile() {
        assertEquals(8..12, filmstripWindow(centerIndex = 10, tileCount = 100))
        assertEquals(0..4, filmstripWindow(centerIndex = 2, tileCount = 100))
    }

    @Test
    fun pinsTheWindowInsideTheManifest() {
        // Leading edge: a center near 0 slides right, never past the start.
        assertEquals(0..4, filmstripWindow(centerIndex = 0, tileCount = 100))
        assertEquals(0..4, filmstripWindow(centerIndex = 1, tileCount = 100))
        assertEquals(0..4, filmstripWindow(centerIndex = 2, tileCount = 100))
        assertEquals(1..5, filmstripWindow(centerIndex = 3, tileCount = 100))
        // Trailing edge: a center near the end slides left.
        assertEquals(95..99, filmstripWindow(centerIndex = 98, tileCount = 100))
        assertEquals(95..99, filmstripWindow(centerIndex = 99, tileCount = 100))
    }

    @Test
    fun clampsAnOutOfRangeCenterIntoTheManifest() {
        assertEquals(95..99, filmstripWindow(centerIndex = 500, tileCount = 100))
        assertEquals(0..4, filmstripWindow(centerIndex = -3, tileCount = 100))
    }

    @Test
    fun aManifestSmallerThanTheWindowRendersAllTiles() {
        assertEquals(0..2, filmstripWindow(centerIndex = 1, tileCount = 3))
        assertEquals(0..0, filmstripWindow(centerIndex = 0, tileCount = 1))
    }

    @Test
    fun emptyWhenThereIsNothingToRender() {
        assertTrue(filmstripWindow(centerIndex = 0, tileCount = 0).isEmpty())
        assertTrue(filmstripWindow(centerIndex = 5, tileCount = -1).isEmpty())
        assertTrue(filmstripWindow(centerIndex = 5, tileCount = 100, visible = 0).isEmpty())
        assertTrue(filmstripWindow(centerIndex = 5, tileCount = 100, visible = -2).isEmpty())
    }

    @Test
    fun theWindowSizeIsStableWhileSliding() {
        // The DEFAULT visible count: every in-range center yields exactly the
        // same window size — the filmstrip never reflows mid-drag.
        for (center in listOf(0, 1, 7, 50, 97, 99)) {
            assertEquals(FILMSTRIP_VISIBLE_TILES, filmstripWindow(center, tileCount = 100).toList().size)
        }
    }
}

class TrickplayPresentationTest {

    private val info = TrickplayInfo(
        width = 160, height = 90, tileWidth = 5, tileHeight = 3,
        thumbnailCount = 100, interval = 10_000, bandwidth = 32_000,
    )

    @Test
    fun bubbleModeRendersTheBubble() {
        assertEquals(
            TrickplayPresentation.Bubble,
            trickplayPresentation(TrickplayMode.BUBBLE, 50_000L, info.interval, info.thumbnailCount),
        )
    }

    @Test
    fun filmstripModeRendersTheWindowAroundTheSeekTile() {
        assertEquals(
            TrickplayPresentation.Filmstrip(listOf(8, 9, 10, 11, 12)),
            trickplayPresentation(TrickplayMode.FILMSTRIP, 105_000L, info.interval, info.thumbnailCount),
        )
    }

    @Test
    fun fullscreenModeRendersFullscreen() {
        assertEquals(
            TrickplayPresentation.Fullscreen,
            trickplayPresentation(TrickplayMode.FULLSCREEN, 0L, info.interval, info.thumbnailCount),
        )
    }

    @Test
    fun aMissingManifestDegradesEveryModeToTheBubble() {
        // The gesture site's gate does NOT require a manifest — this is the
        // reachable path there, not a defensive dead branch.
        for (mode in TrickplayMode.entries) {
            assertEquals(
                TrickplayPresentation.Bubble,
                trickplayPresentation(mode, 50_000L, intervalMs = 0, thumbnailCount = 100),
                "$mode with a zero interval must degrade to the bubble",
            )
            assertEquals(
                TrickplayPresentation.Bubble,
                trickplayPresentation(mode, 50_000L, intervalMs = 10_000, thumbnailCount = 0),
                "$mode with an empty manifest must degrade to the bubble",
            )
        }
    }
}

class TrickplayAnchorTest {

    @Test
    fun bubbleFloatsAboveTheControls() {
        assertEquals(
            TrickplayAnchor.FLOATING_BUBBLE,
            trickplayAnchor(TrickplayMode.BUBBLE, hasTrickplayInfo = true),
        )
    }

    @Test
    fun filmstripSitsAtTheSeekBarLine() {
        assertEquals(
            TrickplayAnchor.SEEK_BAR_LINE,
            trickplayAnchor(TrickplayMode.FILMSTRIP, hasTrickplayInfo = true),
        )
    }

    @Test
    fun fullscreenFillsTheSurface() {
        assertEquals(
            TrickplayAnchor.FULL_SURFACE,
            trickplayAnchor(TrickplayMode.FULLSCREEN, hasTrickplayInfo = true),
        )
    }

    @Test
    fun theManifestFallbackRidesTheBubblesAnchor() {
        for (mode in TrickplayMode.entries) {
            assertEquals(
                TrickplayAnchor.FLOATING_BUBBLE,
                trickplayAnchor(mode, hasTrickplayInfo = false),
                "$mode without a manifest must fall back to the bubble's anchor",
            )
        }
    }
}

class TrickplayTileAspectTest {

    @Test
    fun derivesTheTileAspectFromTheManifestDimensions() {
        val info = TrickplayInfo(
            width = 320, height = 180, tileWidth = 5, tileHeight = 3,
            thumbnailCount = 100, interval = 10_000, bandwidth = 32_000,
        )
        assertEquals(320f / 180f, trickplayTileAspect(info))
    }

    @Test
    fun fallsBackToSixteenNineForAMalformedManifest() {
        assertEquals(16f / 9f, trickplayTileAspect(null))
        val zeroWidth = TrickplayInfo(
            width = 0, height = 180, tileWidth = 5, tileHeight = 3,
            thumbnailCount = 100, interval = 10_000, bandwidth = 32_000,
        )
        assertEquals(16f / 9f, trickplayTileAspect(zeroWidth))
        val zeroHeight = TrickplayInfo(
            width = 320, height = 0, tileWidth = 5, tileHeight = 3,
            thumbnailCount = 100, interval = 10_000, bandwidth = 32_000,
        )
        assertEquals(16f / 9f, trickplayTileAspect(zeroHeight))
    }
}
