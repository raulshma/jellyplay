package com.raulshma.jellyplay.screensaver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the random Ken Burns transform bounds the TV screensaver animates
 * within: every transform starts from identity scale, zooms in by at most
 * 20% (scale 1.05–1.20 — zoom-OUT would expose the backdrop edges on a
 * 16:9 TV), and pans each axis by at most ±5% of the viewport. Repeated
 * draws must produce varying transforms (a stuck RNG would make the
 * slideshow feel frozen).
 */
class KenBurnsTransformTest {

    @Test
    fun `every transform zooms in from identity scale within the 5-20 percent band`() {
        repeat(1_000) {
            val t = KenBurnsTransform.random()

            assertEquals("scale must start at identity", 1f, t.scaleFrom, 0f)
            assertTrue(
                "scaleTo ${t.scaleTo} outside [1.05, 1.20]",
                t.scaleTo >= 1.05f && t.scaleTo < 1.20f,
            )
        }
    }

    @Test
    fun `pan offsets stay within half a percent of the viewport per axis`() {
        repeat(1_000) {
            val t = KenBurnsTransform.random()

            for (pan in floatArrayOf(t.panFromX, t.panToX, t.panFromY, t.panToY)) {
                assertTrue(
                    "pan $pan outside [-0.05, 0.05]",
                    pan >= -0.05f && pan < 0.05f,
                )
            }
        }
    }

    @Test
    fun `random transforms vary across draws`() {
        val scaleTargets = (1..100).map { KenBurnsTransform.random().scaleTo }.toSet()

        assertTrue(
            "100 draws collapsing to ${scaleTargets.size} distinct scale targets suggests a stuck RNG",
            scaleTargets.size > 50,
        )
    }
}
