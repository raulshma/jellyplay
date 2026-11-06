package com.raulshma.jellyplay.feature.player.video

import coil3.size.Size
import com.raulshma.jellyplay.core.model.DECODE_FULL_BLEED_FLOOR_PX
import com.raulshma.jellyplay.core.model.DevicePerformanceTier
import com.raulshma.jellyplay.core.model.ImageDecodeCap
import com.raulshma.jellyplay.core.model.MediaStream
import com.raulshma.jellyplay.core.model.PerformanceConfigResolver
import com.raulshma.jellyplay.core.model.PerformanceLevel
import com.raulshma.jellyplay.core.model.PerformanceOverrides
import com.raulshma.jellyplay.core.model.ResolvedPerformanceConfig
import com.raulshma.jellyplay.core.model.StreamType
import com.raulshma.jellyplay.core.ui.image.mediaImageDecodeSize
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the ambient-backdrop policy (#12, [AmbientBackdropPolicy.kt]): the
 * aspect comparison that decides whether the video is letterboxed/pillarboxed
 * (bars exist → blurred backdrop), the video-stream aspect derivation, and
 * the layer's cost posture — the explicit 720² decode bound (the downscaled
 * request, and its pass-through contract with the image pipeline's posture
 * clamp) plus the LOW-tier blur drop.
 */
class AmbientBackdropPolicyTest {

    @Test
    fun disabledIsAlwaysOff() {
        assertFalse(shouldShowAmbientBackdrop(2.35f, 16f / 9f, enabled = false))
    }

    @Test
    fun unknownGeometryStaysOff() {
        // No stream dims yet (load in flight / audio-only) — never guess.
        assertFalse(shouldShowAmbientBackdrop(null, 16f / 9f, enabled = true))
        assertFalse(shouldShowAmbientBackdrop(16f / 9f, null, enabled = true))
        assertFalse(shouldShowAmbientBackdrop(0f, 16f / 9f, enabled = true))
        assertFalse(shouldShowAmbientBackdrop(16f / 9f, -1f, enabled = true))
    }

    @Test
    fun aFillingVideoHasNoBars() {
        assertFalse(shouldShowAmbientBackdrop(16f / 9f, 16f / 9f, enabled = true))
        assertFalse(shouldShowAmbientBackdrop(1.777f, 1.778f, enabled = true), "rounding-level sameness fills")
    }

    @Test
    fun letterboxedAndPillarboxedVideosShowTheBackdrop() {
        // 2.35:1 cinemascope on a 16:9 screen — letterbox bars.
        assertTrue(shouldShowAmbientBackdrop(2.35f, 16f / 9f, enabled = true))
        // 4:3 classic TV on a 16:9 screen — pillarbox bars.
        assertTrue(shouldShowAmbientBackdrop(4f / 3f, 16f / 9f, enabled = true))
        // A phone-portrait video on a landscape screen.
        assertTrue(shouldShowAmbientBackdrop(9f / 16f, 16f / 9f, enabled = true))
        // Aspect order is irrelevant (the comparison is symmetric).
        assertTrue(shouldShowAmbientBackdrop(16f / 9f, 2.35f, enabled = true))
    }

    @Test
    fun aDifferenceInsideTheToleranceCountsAsFilling() {
        // ~1.1% spread — below the 2% tolerance, treated as no bars.
        assertFalse(shouldShowAmbientBackdrop(1.78f, 1.801f, enabled = true))
    }

    @Test
    fun theToleranceConstantMatchesTheContract() {
        assertEquals(1.02f, AMBIENT_ASPECT_TOLERANCE)
    }

    // -- The cost posture: downscale first, then tier-gate the blur ----------

    /**
     * The resolved postures the decode-bound contract is checked against:
     * every clamp shape the image pipeline can hold (no clamp, the tiered
     * AUTO fold, an explicit flat cap, unlimited).
     */
    private fun decodePostures(): List<Pair<String, ResolvedPerformanceConfig>> = listOf(
        "STANDARD AUTO (fresh install — no clamp)" to ResolvedPerformanceConfig.DEFAULT,
        "BALANCED AUTO (no clamp)" to PerformanceConfigResolver.resolve(
            level = PerformanceLevel.BALANCED,
            reduceMotionEnabled = false,
            overrides = PerformanceOverrides(),
        ),
        "PERFORMANCE AUTO (tiered 768/256 clamp)" to PerformanceConfigResolver.resolve(
            level = PerformanceLevel.PERFORMANCE,
            reduceMotionEnabled = false,
            overrides = PerformanceOverrides(),
        ),
        "explicit P720 flat cap" to PerformanceConfigResolver.resolve(
            level = PerformanceLevel.STANDARD,
            reduceMotionEnabled = false,
            overrides = PerformanceOverrides(imageDecodeCap = ImageDecodeCap.P720),
        ),
        "UNLIMITED" to PerformanceConfigResolver.resolve(
            level = PerformanceLevel.STANDARD,
            reduceMotionEnabled = false,
            overrides = PerformanceOverrides(imageDecodeCap = ImageDecodeCap.UNLIMITED),
        ),
    )

    @Test
    fun theDecodeBoundIsThe720PolicyConstant() {
        // Pinned literally: the bound IS the policy — a silent drift here
        // re-inflates the backdrop's bitmap straight back toward a full
        // 4K decode. 720 sits just under the pipeline's 768 px full-bleed
        // tier; a 720² box caps the ARGB bitmap at ~2 MB against tens of MB
        // for an unbounded 4K still.
        assertEquals(720, AMBIENT_BACKDROP_DECODE_PX)
    }

    @Test
    fun theExplicitBoundPassesThroughEveryPostureUnclamped() {
        // The layer opts out of the posture clamp (performanceModeAware =
        // false), so mediaImageDecodeSize must return the explicit 720²
        // request byte-identically under EVERY posture — the memory bound
        // comes from the request itself, never from the user's performance
        // settings. A clamp leaking into the unaware branch would shrink the
        // backdrop behind the user's back; a clamp SKIPPED here would mean
        // the layer's contract drifted from the pipeline's.
        val bound = Size(AMBIENT_BACKDROP_DECODE_PX, AMBIENT_BACKDROP_DECODE_PX)
        for ((label, config) in decodePostures()) {
            assertEquals(
                bound,
                mediaImageDecodeSize(bound, config, performanceModeAware = false),
                label,
            )
        }
    }

    @Test
    fun theArmedClampWouldCrushTheBoundRequestToTheFloorTier() {
        // Why performanceModeAware = false is load-bearing, pinned by its
        // CONTRAST: with the clamp armed (the PERFORMANCE AUTO fold), the
        // 720² request's largest side sits below the 1080 full-bleed
        // threshold, so the tiered fold collapses it to the 256² floor —
        // the crushed-to-a-thumbnail backdrop the opt-out exists to avoid.
        // (The unaware path keeps the explicit bound instead — the test
        // above.)
        val performance = PerformanceConfigResolver.resolve(
            level = PerformanceLevel.PERFORMANCE,
            reduceMotionEnabled = false,
            overrides = PerformanceOverrides(),
        )
        assertEquals(
            Size(DECODE_FULL_BLEED_FLOOR_PX, DECODE_FULL_BLEED_FLOOR_PX),
            mediaImageDecodeSize(Size(AMBIENT_BACKDROP_DECODE_PX, AMBIENT_BACKDROP_DECODE_PX), performance),
        )
    }

    @Test
    fun lowTierDropsTheBlurButKeepsTheLayer() {
        // LOW keeps the downscaled artwork + scrim (the bars still glow —
        // the middle ground between dropping the layer entirely and paying
        // the per-frame effect) and sheds only the blur.
        assertFalse(shouldBlurAmbientBackdrop(DevicePerformanceTier.LOW))
    }

    @Test
    fun mediumAndHighTiersKeepTheBlur() {
        assertTrue(shouldBlurAmbientBackdrop(DevicePerformanceTier.MEDIUM))
        assertTrue(shouldBlurAmbientBackdrop(DevicePerformanceTier.HIGH))
    }
}

class VideoStreamAspectTest {

    private fun videoStream(width: Int?, height: Int?) = MediaStream(
        index = 0,
        type = StreamType.VIDEO,
        width = width,
        height = height,
    )

    @Test
    fun derivesFromTheFirstVideoStream() {
        val streams = listOf(
            MediaStream(index = 0, type = StreamType.AUDIO),
            videoStream(1920, 1080),
            videoStream(640, 480),
        )
        assertEquals(1920f / 1080f, videoStreamAspect(streams))
    }

    @Test
    fun nullWithoutUsableGeometry() {
        assertNull(videoStreamAspect(emptyList()))
        assertNull(videoStreamAspect(listOf(MediaStream(index = 0, type = StreamType.AUDIO))))
        assertNull(videoStreamAspect(listOf(videoStream(null, 1080))))
        assertNull(videoStreamAspect(listOf(videoStream(1920, null))))
        assertNull(videoStreamAspect(listOf(videoStream(0, 1080))))
        assertNull(videoStreamAspect(listOf(videoStream(1920, 0))))
    }
}
