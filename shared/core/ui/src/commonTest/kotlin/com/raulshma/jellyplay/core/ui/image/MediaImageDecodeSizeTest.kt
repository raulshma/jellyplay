package com.raulshma.jellyplay.core.ui.image

import coil3.size.Size
import com.raulshma.jellyplay.core.model.ImageDecodeCap
import com.raulshma.jellyplay.core.model.PerformanceConfigResolver
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.model.PerformanceOverride
import com.raulshma.jellyplay.core.model.PerformanceOverrides
import com.raulshma.jellyplay.core.model.ResolvedPerformanceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins [mediaImageDecodeSize]'s decode policy — the ONE clamp definition the
 * display path, the list→detail handoff key and the hero rotation prefetch
 * all share (a size mismatch between any two produces a cache-key miss or a
 * re-decode). The compatibility contract: the AUTO folds reproduce the
 * legacy `performanceMode` boolean's decode behavior exactly (OFF = no
 * clamp, ON = the tiered 768²/256² clamp), while the explicit
 * [ImageDecodeCap] rows and UNLIMITED add the new semantics without touching
 * the AUTO poles.
 */
class MediaImageDecodeSizeTest {

    private val allAuto = PerformanceOverrides()

    private fun resolved(
        level: PerformanceLevel = PerformanceLevel.STANDARD,
        decodeCap: ImageDecodeCap = ImageDecodeCap.AUTO,
        suppressAnimations: PerformanceOverride = PerformanceOverride.AUTO,
    ): ResolvedPerformanceConfig = PerformanceConfigResolver.resolve(
        level = level,
        reduceMotionEnabled = false,
        overrides = allAuto.copy(
            imageDecodeCap = decodeCap,
            suppressAnimations = suppressAnimations,
        ),
    )

    // -- AUTO poles (the byte-identical contract) ----------------------------

    @Test
    fun autoStandard_appliesNoClamp() {
        // Today's performance-OFF: the requested bounds pass through, so the
        // default-install hero decodes full-res and cards at their adaptive
        // bounds. The resolver's STANDARD/BALANCED AUTO pair is (null, null)
        // — no clamp — matching the legacy `else -> size` branch.
        val config = resolved(level = PerformanceLevel.STANDARD)
        assertEquals(Size(360, 540), mediaImageDecodeSize(Size(360, 540), config))
        assertEquals(Size(1920, 1080), mediaImageDecodeSize(Size(1920, 1080), config))
    }

    @Test
    fun autoBalanced_appliesNoClamp() {
        val config = resolved(level = PerformanceLevel.BALANCED)
        assertEquals(Size(360, 540), mediaImageDecodeSize(Size(360, 540), config))
    }

    @Test
    fun autoPerformance_tiersLargeRequestsTo768AndSmallTo256() {
        // Today's performance-ON: the tiered clamp keeps full-bleed requests
        // (hero) crisp and collapses card requests to the thumbnail tier.
        val config = resolved(level = PerformanceLevel.PERFORMANCE)
        assertEquals(Size(768, 768), mediaImageDecodeSize(Size(1920, 1080), config))
        assertEquals(Size(768, 768), mediaImageDecodeSize(Size(1080, 1080), config))
        assertEquals(Size(256, 256), mediaImageDecodeSize(Size(360, 540), config))
    }

    // -- Structural opt-outs --------------------------------------------------

    @Test
    fun originalSize_neverClamped() {
        val config = resolved(level = PerformanceLevel.PERFORMANCE)
        assertEquals(Size.ORIGINAL, mediaImageDecodeSize(Size.ORIGINAL, config))
    }

    @Test
    fun unawareCaller_neverClamped() {
        // The full-bleed hero/backdrop opt-out: no posture level clamps these.
        val config = resolved(level = PerformanceLevel.PERFORMANCE)
        assertEquals(Size(1920, 1080), mediaImageDecodeSize(Size(1920, 1080), config, performanceModeAware = false))
        assertEquals(Size(360, 540), mediaImageDecodeSize(Size(360, 540), config, performanceModeAware = false))
    }

    // -- Explicit caps (the new rows) ------------------------------------------

    @Test
    fun explicitCap_atStandard_flattensEveryRequest() {
        // The explicit rows have no small/large tiering: the chosen pixel
        // height caps EVERY non-original request.
        val config = resolved(decodeCap = ImageDecodeCap.P512)
        assertEquals(Size(512, 512), mediaImageDecodeSize(Size(1920, 1080), config))
        assertEquals(Size(512, 512), mediaImageDecodeSize(Size(360, 540), config))
    }

    @Test
    fun unlimited_removesEveryClamp_atAnyLevel() {
        for (level in PerformanceLevel.entries) {
            val config = resolved(level = level, decodeCap = ImageDecodeCap.UNLIMITED)
            assertEquals(Size(1920, 1080), mediaImageDecodeSize(Size(1920, 1080), config), level.name)
            assertEquals(Size(360, 540), mediaImageDecodeSize(Size(360, 540), config), level.name)
        }
    }

    @Test
    fun animationsForcedOn_atStandard_keepsDecodeUnclamped() {
        // Decode policy is ORTHOGONAL to animation suppression: forcing
        // perfAnimations=ON at STANDARD arms the motion fold but the decode
        // pair stays (null, null) — only the PERFORMANCE level's AUTO fold
        // (or an explicit cap row) clamps decode.
        val config = resolved(suppressAnimations = PerformanceOverride.ON)
        assertTrue(config.suppressAnimations)
        assertEquals(Size(1920, 1080), mediaImageDecodeSize(Size(1920, 1080), config))
        assertEquals(Size(360, 540), mediaImageDecodeSize(Size(360, 540), config))
    }

    // -- Display-path key helpers ----------------------------------------------

    @Test
    fun defaultDecodeSize_passesThroughAtAutoStandard() {
        // The composable's 384² default: unchanged at the OFF pole.
        assertEquals(
            Size(384, 384),
            mediaImageDecodeSize(Size(384, 384), ResolvedPerformanceConfig.DEFAULT),
        )
    }
}
