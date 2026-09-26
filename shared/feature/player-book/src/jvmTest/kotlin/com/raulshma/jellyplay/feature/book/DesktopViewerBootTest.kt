package com.raulshma.jellyplay.feature.book

import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.feature.book.epub.KcefStatus
import com.raulshma.jellyplay.feature.book.epub.pageIdentityProbe
import com.raulshma.jellyplay.feature.book.epub.pageIdentityStamp
import com.raulshma.jellyplay.feature.book.epub.parsePageIdentityResult
import com.raulshma.jellyplay.feature.book.epub.shouldStartViewerInit
import com.raulshma.jellyplay.feature.book.epub.syncBrowserSurfaceVisibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the desktop viewer boot gate (DesktopEpubReaderHost): the
 * page-identity probe/stamp vocabulary behind the phantom-Finished guard, the
 * tri-state probe-answer read the retry loop decides on, and the init-retry
 * admission behind KcefRuntime.ensureStarted.
 *
 * Context: a failed KCEF init used to strand every EPUB on the "preparing
 * reading locations" veil — null progress plus a blank WebView is
 * indistinguishable from a slow boot without the [KcefStatus] phase — and a
 * single swallowed probe callback (the desktop evaluate path drops null
 * results without invoking the callback) stranded pageLoaded with no retry.
 */
class DesktopViewerBootTest {

    @Test
    fun `probe compares the generation, stamp assigns it`() {
        assertEquals("window.__jellyPlayPageGeneration === 3", pageIdentityProbe(3))
        assertEquals("window.__jellyPlayPageGeneration = 3", pageIdentityStamp(3))
    }

    @Test
    fun `probe answer true means known page, false means fresh page`() {
        assertEquals(true, parsePageIdentityResult("true"))
        assertEquals(false, parsePageIdentityResult("false"))
    }

    @Test
    fun `probe answer tolerates the evaluate callback quoting`() {
        assertEquals(true, parsePageIdentityResult("\"true\""))
        assertEquals(false, parsePageIdentityResult("\"false\""))
        assertEquals(true, parsePageIdentityResult("  true  "))
        assertEquals(false, parsePageIdentityResult("\n\"false\"\n"))
    }

    @Test
    fun `probe answer degrades to unknown on missing or garbage payloads`() {
        assertNull(parsePageIdentityResult(null))
        assertNull(parsePageIdentityResult(""))
        assertNull(parsePageIdentityResult("   "))
        assertNull(parsePageIdentityResult("undefined"))
        assertNull(parsePageIdentityResult("null"))
        assertNull(parsePageIdentityResult("0"))
        assertNull(parsePageIdentityResult("True"))
    }

    @Test
    fun `viewer init starts unless already starting or ready`() {
        assertTrue(shouldStartViewerInit(KcefStatus.IDLE))
        assertFalse(shouldStartViewerInit(KcefStatus.STARTING))
        assertFalse(shouldStartViewerInit(KcefStatus.READY))
    }

    @Test
    fun `viewer init retries after failure or restart-required`() {
        // A failed init must not latch the reader broken for the process
        // lifetime — reopening the reader retries.
        assertTrue(shouldStartViewerInit(KcefStatus.FAILED))
        assertTrue(shouldStartViewerInit(KcefStatus.RESTART_REQUIRED))
    }

    @Test
    fun `chrome floats everywhere but over windowed desktop CEF`() {
        // The desktop browser is a heavyweight surface above all Compose
        // overlays, so its chrome lays out around the content region.
        assertTrue(epubChromeOverlaysContent(PlatformKind.ANDROID))
        assertFalse(epubChromeOverlaysContent(PlatformKind.DESKTOP))
    }

    @Test
    fun `browser canvas and wrapper unmap while an overlay holds the screen`() {
        // The M3 sheet/dialog windows composite UNDER windowed CEF, so the
        // browser must be unmapped for a raised sheet to show over the
        // content region — and remapped once the sheet dismisses. Both the
        // canvas and its Swing wrapper hide (wrapper-only proved fragile when
        // the interop container inserts an intermediate parent; canvas-only
        // would leave the wrapper's L&F background painting the white band).
        val wrapper = javax.swing.JPanel()
        val canvas = java.awt.Container()
        wrapper.add(canvas)
        assertTrue(wrapper.isVisible)
        assertTrue(canvas.isVisible)
        syncBrowserSurfaceVisibility(canvas, overlayActive = true)
        // invokeLater when off-EDT: pump the EDT so the queued hide lands.
        javax.swing.SwingUtilities.invokeAndWait { }
        assertFalse(wrapper.isVisible)
        assertFalse(canvas.isVisible)
        syncBrowserSurfaceVisibility(canvas, overlayActive = false)
        javax.swing.SwingUtilities.invokeAndWait { }
        assertTrue(wrapper.isVisible)
        assertTrue(canvas.isVisible)
    }

    @Test
    fun `browser surface sync finds the wrapper through an intermediate parent`() {
        // The interop container may insert a non-Swing parent between the
        // canvas and the SwingPanel wrapper — the walk must skip it and still
        // unmap the wrapper (the old direct-parent cast returned without
        // hiding, leaving the book painting through the sheet's middle band).
        val wrapper = javax.swing.JPanel()
        val intermediate = java.awt.Panel()
        val canvas = java.awt.Container()
        intermediate.add(canvas)
        wrapper.add(intermediate)
        syncBrowserSurfaceVisibility(canvas, overlayActive = true)
        javax.swing.SwingUtilities.invokeAndWait { }
        assertFalse(wrapper.isVisible)
        assertFalse(canvas.isVisible)
        syncBrowserSurfaceVisibility(canvas, overlayActive = false)
        javax.swing.SwingUtilities.invokeAndWait { }
        assertTrue(wrapper.isVisible)
        assertTrue(canvas.isVisible)
    }

    @Test
    fun `browser surface sync hides a wrapper-less canvas and never the window`() {
        // Null is a no-op (a not-yet-created browser). A not-yet-added canvas
        // still hides ITSELF so a sheet raised before attach lands — the old
        // direct-parent cast dropped that toggle. The walk never climbs into
        // a Window.
        syncBrowserSurfaceVisibility(null, overlayActive = true)
        val orphan = java.awt.Container()
        syncBrowserSurfaceVisibility(orphan, overlayActive = true)
        javax.swing.SwingUtilities.invokeAndWait { }
        assertFalse(orphan.isVisible)
        syncBrowserSurfaceVisibility(orphan, overlayActive = false)
        javax.swing.SwingUtilities.invokeAndWait { }
        assertTrue(orphan.isVisible)
    }
}
