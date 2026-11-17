package com.raulshma.jellyplay.feature.photos

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the photo viewer's ordered back ladder (adjustments panel → info
 * overlay → filmstrip → slideshow → exit) — the pure fold both back sites
 * (predictive-back [JellyPlayBackHandler] and the TV remote's D-pad Back)
 * consume, which used to be hand-copied and could drift apart.
 */
class PhotoViewerBackActionTest {

    // ── single-layer arms ───────────────────────────────────────────────────

    @Test
    fun `adjustments panel open closes it`() {
        assertEquals(
            PhotoViewerBackAction.CloseAdjustments,
            photoViewerBackAction(
                showAdjustments = true,
                showInfo = false,
                showFilmstrip = false,
                isSlideshowActive = false,
            ),
        )
    }

    @Test
    fun `info overlay open dismisses it`() {
        assertEquals(
            PhotoViewerBackAction.DismissInfo,
            photoViewerBackAction(
                showAdjustments = false,
                showInfo = true,
                showFilmstrip = false,
                isSlideshowActive = false,
            ),
        )
    }

    @Test
    fun `filmstrip open closes it`() {
        assertEquals(
            PhotoViewerBackAction.CloseFilmstrip,
            photoViewerBackAction(
                showAdjustments = false,
                showInfo = false,
                showFilmstrip = true,
                isSlideshowActive = false,
            ),
        )
    }

    @Test
    fun `active slideshow stops it`() {
        assertEquals(
            PhotoViewerBackAction.StopSlideshow,
            photoViewerBackAction(
                showAdjustments = false,
                showInfo = false,
                showFilmstrip = false,
                isSlideshowActive = true,
            ),
        )
    }

    @Test
    fun `bare viewer exits`() {
        assertEquals(
            PhotoViewerBackAction.ExitViewer,
            photoViewerBackAction(
                showAdjustments = false,
                showInfo = false,
                showFilmstrip = false,
                isSlideshowActive = false,
            ),
        )
    }

    // ── precedence (top of the ladder wins) ────────────────────────────────

    @Test
    fun `adjustments outrank the whole ladder`() {
        assertEquals(
            PhotoViewerBackAction.CloseAdjustments,
            photoViewerBackAction(
                showAdjustments = true,
                showInfo = true,
                showFilmstrip = true,
                isSlideshowActive = true,
            ),
        )
    }

    @Test
    fun `adjustments outrank info overlay`() {
        assertEquals(
            PhotoViewerBackAction.CloseAdjustments,
            photoViewerBackAction(
                showAdjustments = true,
                showInfo = true,
                showFilmstrip = false,
                isSlideshowActive = false,
            ),
        )
    }

    @Test
    fun `adjustments open closes them even with slideshow active`() {
        assertEquals(
            PhotoViewerBackAction.CloseAdjustments,
            photoViewerBackAction(
                showAdjustments = true,
                showInfo = false,
                showFilmstrip = false,
                isSlideshowActive = true,
            ),
        )
    }

    @Test
    fun `info outranks filmstrip and slideshow`() {
        assertEquals(
            PhotoViewerBackAction.DismissInfo,
            photoViewerBackAction(
                showAdjustments = false,
                showInfo = true,
                showFilmstrip = true,
                isSlideshowActive = true,
            ),
        )
    }

    @Test
    fun `filmstrip outranks slideshow`() {
        assertEquals(
            PhotoViewerBackAction.CloseFilmstrip,
            photoViewerBackAction(
                showAdjustments = false,
                showInfo = false,
                showFilmstrip = true,
                isSlideshowActive = true,
            ),
        )
    }

    @Test
    fun `slideshow outranks exit`() {
        assertEquals(
            PhotoViewerBackAction.StopSlideshow,
            photoViewerBackAction(
                showAdjustments = false,
                showInfo = false,
                showFilmstrip = false,
                isSlideshowActive = true,
            ),
        )
    }
}
