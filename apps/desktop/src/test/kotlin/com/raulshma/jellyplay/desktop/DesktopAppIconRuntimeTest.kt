package com.raulshma.jellyplay.desktop

import java.awt.GraphicsEnvironment
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Runtime icon + tray-availability decisions out of [DesktopAppIcon] (wave
 * 12A): these two pure functions decide whether the window/tray render an
 * icon at all, and Main.kt skips the tray entirely when either half fails —
 * so a regression here must fail HERE, not as a silent icon-less boot.
 *
 * Invariants pinned:
 *  - [desktopAppIconOrNull] loads the committed brand resource from the
 *    runtime classpath and decodes it at its NATIVE pixel size (the painter
 *    composes 1:1; AWT tray consumers scale themselves). The expected size
 *    is derived from the same bytes via ImageIO, so an icon regeneration
 *    that changes the size can only fail this test by breaking the decode.
 *  - Degradation honesty: a Painter is only returned when the bytes decoded;
 *    the null path (missing resource) cannot be produced in a test JVM that
 *    ships the resource — that half stays a main-source `runCatching`
 *    contract, documented rather than pinned.
 *  - [systemTrayAvailable] mirrors AWT semantics exactly: headless ⇒ false
 *    (tray icons need a real desktop session), and repeated probes agree
 *    (Main.kt reads it once, but the answer must not be a coin flip).
 *
 * The headless arm is assumption-guarded: on a headful dev machine the JVM
 * cannot be forced headless after startup, so that assertion only bites in
 * headless CI-style runs — exactly where it matters.
 */
class DesktopAppIconRuntimeTest {

    /** The same resource Main.kt's runtime icon loads; must be on the test classpath. */
    private fun iconBytes(): ByteArray? =
        object {}.javaClass.getResourceAsStream("/branding/jellyplay-icon.png")?.use { it.readBytes() }

    @Test
    fun `app icon decodes from the classpath at its native pixel size`() {
        val bytes = assertNotNull(iconBytes(), "branding icon must be on the runtime classpath")
        assertTrue(bytes.isNotEmpty(), "icon resource must not be empty")

        val expected = ImageIO.read(ByteArrayInputStream(bytes))
        assertNotNull(expected, "ImageIO must decode the same bytes (sanity baseline)")

        val painter = assertNotNull(desktopAppIconOrNull(), "a decodable icon must yield a painter")
        assertEquals(
            expected.width.toFloat(),
            painter.intrinsicSize.width,
            "painter width must equal the PNG's native width (1:1 compose contract)",
        )
        assertEquals(
            expected.height.toFloat(),
            painter.intrinsicSize.height,
            "painter height must equal the PNG's native height",
        )
        assertTrue(painter.intrinsicSize.width >= 256, "brand icon is a large asset, got ${painter.intrinsicSize}")
    }

    @Test
    fun `systemTrayAvailable is stable across repeated probes`() {
        val first = systemTrayAvailable()
        val second = systemTrayAvailable()
        assertEquals(first, second, "tray availability must not flip between probes in one JVM")
    }

    @Test
    fun `systemTrayAvailable is false in a headless environment`() {
        // kotlin-test has no assumption API on this classpath: a headful JVM
        // (dev machines) skips silently instead of failing — exactly the
        // JUnit-assumeTrue contract this arm needs.
        if (!GraphicsEnvironment.isHeadless()) return
        assertEquals(false, systemTrayAvailable())
    }
}
