package com.raulshma.jellyplay.feature.insights.heatmap

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Wave 20C: the desktop heatmap share's file half — PNG bytes landing under
 * the given directory with the expected name shape. The GraphicsLayer
 * capture (a live skia scene) and the AWT Desktop.open handoff are
 * interactive/dependent on a display session and stay manually-verified;
 * everything the share does to disk once pixels exist is pinned here.
 */
class DesktopHeatmapShareIoTest {

    @Test
    fun `writeHeatmapPng writes a decodable PNG with the share name shape`() {
        val dir = createTempDirectory("jp-heatmap-uk").toFile()
        val image = BufferedImage(4, 2, BufferedImage.TYPE_INT_ARGB)
        image.setRGB(0, 0, 0xFF00FF00.toInt())

        val file = writeHeatmapPng(image, dir)

        assertEquals(dir.absolutePath, file.parentFile?.absolutePath)
        assertTrue(
            file.name.matches(Regex("watch_progress_heatmap_\\d+\\.png")),
            "unexpected name ${file.name}",
        )
        // PNG magic — the file is a real PNG, not an empty/blank artifact.
        val bytes = file.readBytes()
        assertEquals(0x89.toByte(), bytes[0])
        assertEquals('P'.code.toByte(), bytes[1])
        assertEquals('N'.code.toByte(), bytes[2])
        assertEquals('G'.code.toByte(), bytes[3])
        // Round-trip: same dimensions back out of ImageIO.
        val decoded = ImageIO.read(file)
        assertEquals(4, decoded.width)
        assertEquals(2, decoded.height)
    }
}
