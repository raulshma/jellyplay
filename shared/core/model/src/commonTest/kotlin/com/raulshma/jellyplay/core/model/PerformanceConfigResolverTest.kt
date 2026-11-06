package com.raulshma.jellyplay.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins [PerformanceConfigResolver]'s fold — the per-level defaults, the
 * override precedence, the decode-cap mapping and the
 * [ResolvedPerformanceConfig.DEFAULT] constant — as the compatibility
 * contract with the app's historical single `performanceMode` boolean:
 * STANDARD (all-AUTO, reduce-motion off) IS performance-mode-OFF and
 * PERFORMANCE (all-AUTO) IS performance-mode-ON.
 */
class PerformanceConfigResolverTest {

    private val allAuto = PerformanceOverrides()

    @Test
    fun `STANDARD all-auto pins the performance-mode OFF posture`() {
        // Fresh-install posture: every effect armed, nothing suppressed.
        assertEquals(
            ResolvedPerformanceConfig(
                suppressAnimations = false,
                reducedMotion = false,
                blurHashPlaceholders = true,
                imageCrossfade = true,
                qualityBadges = true,
                ambientHeroVideo = true,
                backdropBlur = true,
                screensaverMotion = true,
                sharedElements = true,
                navTransitionsInstant = false,
                imageDecodeCapPx = null,
                decodeSmallRequestFloorPx = null,
            ),
            PerformanceConfigResolver.resolve(
                level = PerformanceLevel.STANDARD,
                reduceMotionEnabled = false,
                overrides = allAuto,
            ),
        )
    }

    @Test
    fun `BALANCED all-auto pins the middle-tier posture`() {
        // Middle tier: effects AND the BlurHash loading affordance stay on,
        // crossfade (a decoration) goes off, decode policy matches STANDARD's
        // no-clamp posture.
        assertEquals(
            ResolvedPerformanceConfig(
                suppressAnimations = false,
                reducedMotion = false,
                blurHashPlaceholders = true,
                imageCrossfade = false,
                qualityBadges = true,
                ambientHeroVideo = true,
                backdropBlur = true,
                screensaverMotion = true,
                sharedElements = true,
                navTransitionsInstant = false,
                imageDecodeCapPx = null,
                decodeSmallRequestFloorPx = null,
            ),
            PerformanceConfigResolver.resolve(
                level = PerformanceLevel.BALANCED,
                reduceMotionEnabled = false,
                overrides = allAuto,
            ),
        )
    }

    @Test
    fun `PERFORMANCE all-auto pins the performance-mode ON posture`() {
        // Everything suppressed, instant nav, the historical tiered decode
        // clamp (≥1080 → 768, smaller → 256).
        assertEquals(
            ResolvedPerformanceConfig(
                suppressAnimations = true,
                reducedMotion = true,
                blurHashPlaceholders = false,
                imageCrossfade = false,
                qualityBadges = false,
                ambientHeroVideo = false,
                backdropBlur = false,
                screensaverMotion = false,
                sharedElements = false,
                navTransitionsInstant = true,
                imageDecodeCapPx = 768,
                decodeSmallRequestFloorPx = 256,
            ),
            PerformanceConfigResolver.resolve(
                level = PerformanceLevel.PERFORMANCE,
                reduceMotionEnabled = false,
                overrides = allAuto,
            ),
        )
    }

    @Test
    fun `reduce motion ORs into reducedMotion on every level`() {
        // The historical `performanceMode || reduceMotion` fold: reduce-motion
        // alone turns reducedMotion on without touching any other default.
        for (level in PerformanceLevel.entries) {
            val off = PerformanceConfigResolver.resolve(level, reduceMotionEnabled = false, overrides = allAuto)
            val on = PerformanceConfigResolver.resolve(level, reduceMotionEnabled = true, overrides = allAuto)
            assertEquals(off.copy(reducedMotion = true), on, level.name)
        }
        // PERFORMANCE already suppresses animations, so reduce-motion adds nothing.
        val performance = PerformanceConfigResolver.resolve(
            PerformanceLevel.PERFORMANCE, reduceMotionEnabled = true, overrides = allAuto,
        )
        assertTrue(performance.reducedMotion)
    }

    @Test
    fun `every boolean override ON and OFF beats every level default`() {
        for (level in PerformanceLevel.entries) {
            for (slot in booleanOverrideSlots) {
                val on = PerformanceConfigResolver.resolve(
                    level = level,
                    reduceMotionEnabled = false,
                    overrides = slot.set(allAuto, PerformanceOverride.ON),
                )
                assertTrue(slot.read(on), "${level.name}/${slot.name}: ON must force true")
                val off = PerformanceConfigResolver.resolve(
                    level = level,
                    reduceMotionEnabled = false,
                    overrides = slot.set(allAuto, PerformanceOverride.OFF),
                )
                assertFalse(slot.read(off), "${level.name}/${slot.name}: OFF must force false")
            }
        }
    }

    @Test
    fun `an explicit override changes only its own slot`() {
        // No cascade: turning animations on manually must not drag any other
        // flag with it (reducedMotion follows suppressAnimations by the fold).
        assertEquals(
            ResolvedPerformanceConfig.DEFAULT.copy(
                suppressAnimations = true,
                reducedMotion = true,
            ),
            PerformanceConfigResolver.resolve(
                level = PerformanceLevel.STANDARD,
                reduceMotionEnabled = false,
                overrides = PerformanceOverrides(suppressAnimations = PerformanceOverride.ON),
            ),
        )
    }

    @Test
    fun `each explicit decode cap replaces the level policy`() {
        val expected = mapOf(
            ImageDecodeCap.UNLIMITED to (null to null),
            ImageDecodeCap.P1080 to (1080 to null),
            ImageDecodeCap.P720 to (720 to null),
            ImageDecodeCap.P512 to (512 to null),
            ImageDecodeCap.P256 to (256 to null),
        )
        for (level in PerformanceLevel.entries) {
            for ((cap, capAndFloor) in expected) {
                val resolved = PerformanceConfigResolver.resolve(
                    level = level,
                    reduceMotionEnabled = false,
                    overrides = PerformanceOverrides(imageDecodeCap = cap),
                )
                assertEquals(capAndFloor.first, resolved.imageDecodeCapPx, "${level.name}/$cap cap")
                assertEquals(capAndFloor.second, resolved.decodeSmallRequestFloorPx, "${level.name}/$cap floor")
            }
        }
    }

    @Test
    fun `PerformanceOverride resolve folds the tri-state over any default`() {
        assertEquals(false, PerformanceOverride.AUTO.resolve(false))
        assertEquals(true, PerformanceOverride.AUTO.resolve(true))
        assertEquals(true, PerformanceOverride.ON.resolve(false))
        assertEquals(true, PerformanceOverride.ON.resolve(true))
        assertEquals(false, PerformanceOverride.OFF.resolve(false))
        assertEquals(false, PerformanceOverride.OFF.resolve(true))
    }

    @Test
    fun `DEFAULT equals the all-auto STANDARD no-reduce-motion fold`() {
        assertEquals(
            PerformanceConfigResolver.resolve(
                level = PerformanceLevel.STANDARD,
                reduceMotionEnabled = false,
                overrides = PerformanceOverrides(),
            ),
            ResolvedPerformanceConfig.DEFAULT,
        )
    }

    /**
     * One row per boolean override slot in [PerformanceOverrides], mapped to
     * the [ResolvedPerformanceConfig] flag it must win over. Hand-maintained:
     * a new override/resolved pair needs a row here or its precedence is
     * unpinned.
     */
    private data class OverrideSlot(
        val name: String,
        val set: (PerformanceOverrides, PerformanceOverride) -> PerformanceOverrides,
        val read: (ResolvedPerformanceConfig) -> Boolean,
    )

    private val booleanOverrideSlots: List<OverrideSlot> = listOf(
        OverrideSlot("suppressAnimations", { o, v -> o.copy(suppressAnimations = v) }, { c -> c.suppressAnimations }),
        OverrideSlot("blurHashPlaceholders", { o, v -> o.copy(blurHashPlaceholders = v) }, { c -> c.blurHashPlaceholders }),
        OverrideSlot("imageCrossfade", { o, v -> o.copy(imageCrossfade = v) }, { c -> c.imageCrossfade }),
        OverrideSlot("qualityBadges", { o, v -> o.copy(qualityBadges = v) }, { c -> c.qualityBadges }),
        OverrideSlot("ambientHeroVideo", { o, v -> o.copy(ambientHeroVideo = v) }, { c -> c.ambientHeroVideo }),
        OverrideSlot("backdropBlur", { o, v -> o.copy(backdropBlur = v) }, { c -> c.backdropBlur }),
        OverrideSlot("screensaverMotion", { o, v -> o.copy(screensaverMotion = v) }, { c -> c.screensaverMotion }),
        OverrideSlot("sharedElements", { o, v -> o.copy(sharedElements = v) }, { c -> c.sharedElements }),
        OverrideSlot("navTransitionsInstant", { o, v -> o.copy(navTransitionsInstant = v) }, { c -> c.navTransitionsInstant }),
    )
}
