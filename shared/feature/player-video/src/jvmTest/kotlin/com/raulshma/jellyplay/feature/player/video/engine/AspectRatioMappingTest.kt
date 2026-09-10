package com.raulshma.jellyplay.feature.player.video.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the engine-neutral AspectRatio → per-engine native vocabulary mapping
 * extracted from the three adapters, including the previously-silent gaps:
 * Exo's CROP→ZOOM and libVLC's declared lack of a CROP control.
 */
class AspectRatioMappingTest {

    // ── Exo (media3 resize mode + numeric aspect) ──

    @Test
    fun exo_fitAndAuto_mapToResizeModeFit() {
        for (ratio in listOf(AspectRatio.FIT, AspectRatio.AUTO)) {
            val plan = AspectRatioMapping.exoPlan(ratio)
            assertEquals(AspectRatioMapping.ResizeMode.FIT, plan.resizeMode)
            assertEquals(0f, plan.aspectValue)
        }
    }

    @Test
    fun exo_fill_mapsToResizeModeFill() {
        val plan = AspectRatioMapping.exoPlan(AspectRatio.FILL)
        assertEquals(AspectRatioMapping.ResizeMode.FILL, plan.resizeMode)
        assertEquals(0f, plan.aspectValue)
    }

    @Test
    fun exo_crop_mapsToZoom() {
        // CROP must reach media3 as RESIZE_MODE_ZOOM, never fall through to FIT.
        val plan = AspectRatioMapping.exoPlan(AspectRatio.CROP)
        assertEquals(AspectRatioMapping.ResizeMode.ZOOM, plan.resizeMode)
        assertEquals(0f, plan.aspectValue)
    }

    @Test
    fun exo_fixedRatios_mapToFixedWidthWithNumericAspect() {
        val sixteen9 = AspectRatioMapping.exoPlan(AspectRatio.RATIO_16_9)
        assertEquals(AspectRatioMapping.ResizeMode.FIXED_WIDTH, sixteen9.resizeMode)
        assertEquals(16f / 9f, sixteen9.aspectValue)
        assertEquals(4f / 3f, AspectRatioMapping.exoPlan(AspectRatio.RATIO_4_3).aspectValue)
        assertEquals(21f / 9f, AspectRatioMapping.exoPlan(AspectRatio.RATIO_21_9).aspectValue)
    }

    // ── mpv (aspect override + panscan + subtitle margins) ──

    @Test
    fun mpv_fit_clearsOverrideAndPanscan() {
        val plan = AspectRatioMapping.mpvPlan(AspectRatio.FIT)
        assertEquals("-1", plan.aspectOverride)
        assertEquals(0.0, plan.panscan)
        assertEquals("no", plan.subUseMargins)
        assertEquals("no", plan.subAssForceMargins)
    }

    @Test
    fun mpv_auto_clearsOverrideAndPanscan() {
        val plan = AspectRatioMapping.mpvPlan(AspectRatio.AUTO)
        assertEquals("-1", plan.aspectOverride)
        assertEquals(0.0, plan.panscan)
    }

    @Test
    fun mpv_crop_engagesPanscanAndSubtitleMargins() {
        val plan = AspectRatioMapping.mpvPlan(AspectRatio.CROP)
        assertEquals("-1", plan.aspectOverride)
        assertEquals(1.0, plan.panscan)
        assertEquals("yes", plan.subUseMargins)
        assertEquals("yes", plan.subAssForceMargins)
    }

    @Test
    fun mpv_fixedRatio_encodesAspectOverrideAsReducedFraction_andSkipsCropMachinery() {
        // (ratio * 100).toInt() : 100, reduced by gcd — the mpv-native spelling
        // the engine previously hand-rolled inline.
        assertEquals("177:100", AspectRatioMapping.mpvPlan(AspectRatio.RATIO_16_9).aspectOverride)
        assertEquals("133:100", AspectRatioMapping.mpvPlan(AspectRatio.RATIO_4_3).aspectOverride)
        assertEquals("233:100", AspectRatioMapping.mpvPlan(AspectRatio.RATIO_21_9).aspectOverride)
        val plan = AspectRatioMapping.mpvPlan(AspectRatio.RATIO_16_9)
        assertEquals(0.0, plan.panscan)
        assertEquals("no", plan.subUseMargins)
        assertEquals("no", plan.subAssForceMargins)
    }

    // ── libVLC (aspect string override; no zoom/crop control) ──

    @Test
    fun vlc_fixedRatio_setsNativeAspectString_withoutScaleReset() {
        val plan = AspectRatioMapping.vlcPlan(AspectRatio.RATIO_16_9)
        assertEquals((16f / 9f).toString(), plan.aspectRatioOverride)
        assertFalse(plan.resetScale)
    }

    @Test
    fun vlc_fitFillAutoCrop_clearToNativeFrame() {
        // CROP is unsupported on libVLC's Android MediaPlayer (3.x) — it
        // deliberately resolves to the same native-frame reset as
        // FIT/FILL/AUTO instead of silently doing nothing undocumented.
        for (ratio in listOf(AspectRatio.FIT, AspectRatio.AUTO, AspectRatio.FILL, AspectRatio.CROP)) {
            val plan = AspectRatioMapping.vlcPlan(ratio)
            assertNull(plan.aspectRatioOverride)
            assertTrue(plan.resetScale, "ratio=$ratio must reset scale")
        }
    }
}
