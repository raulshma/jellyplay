package com.raulshma.jellyplay.feature.player.video.engine

/**
 * Pure, testable mapping from the engine-neutral [AspectRatio] to each
 * engine's native aspect vocabulary, extracted from the three hand-mirrored
 * `setAspectRatio` bodies (one of which — libVLC — silently dropped CROP).
 * The engines only APPLY the returned plan to their native handles; the enum
 * → native decision lives here and is pinned by unit tests.
 */
internal object AspectRatioMapping {

    // ── Media3 ──────────────────────────────────────────────────────────────

    /**
     * Media3-neutral resize-mode selector mirroring the
     * AspectRatioFrameLayout.RESIZE_MODE_* constants, so no media3 integer
     * crosses the engine seam (the adapter owns that final selector→constant
     * step, inside the only adapter that hosts an AspectRatioFrameLayout).
     */
    enum class ResizeMode { FIT, FILL, ZOOM, FIXED_WIDTH }

    data class ExoAspectRatioPlan(
        val resizeMode: ResizeMode,
        /** Numeric aspect for AspectRatioFrameLayout.setAspectRatio; 0 clears. */
        val aspectValue: Float,
    )

    fun exoPlan(ratio: AspectRatio): ExoAspectRatioPlan = ExoAspectRatioPlan(
        resizeMode = when (ratio) {
            AspectRatio.FIT, AspectRatio.AUTO -> ResizeMode.FIT
            AspectRatio.FILL -> ResizeMode.FILL
            AspectRatio.CROP -> ResizeMode.ZOOM
            AspectRatio.RATIO_16_9, AspectRatio.RATIO_4_3, AspectRatio.RATIO_21_9 -> ResizeMode.FIXED_WIDTH
        },
        aspectValue = ratio.ratio?.takeIf { it > 0f } ?: 0f,
    )

    // ── mpv ─────────────────────────────────────────────────────────────────

    /**
     * mpv applies CROP as a full panscan plus subtitle margins (captions ride
     * the visible frame instead of the cropped-away canvas); fixed ratios ride
     * video-aspect-override as a reduced `w:h` fraction.
     */
    data class MpvAspectRatioPlan(
        /** `video-aspect-override` value; "-1" clears the override. */
        val aspectOverride: String,
        val panscan: Double,
        val subUseMargins: String,
        val subAssForceMargins: String,
    )

    fun mpvPlan(ratio: AspectRatio): MpvAspectRatioPlan {
        val numeric = ratio.ratio
        val aspectOverride = if (numeric != null && numeric > 0f) {
            val w = (numeric * 100).toInt()
            val h = 100
            val gcd = gcd(w, h)
            "${w / gcd}:${h / gcd}"
        } else {
            "-1"
        }
        val isZoom = ratio == AspectRatio.CROP
        return MpvAspectRatioPlan(
            aspectOverride = aspectOverride,
            panscan = if (isZoom) 1.0 else 0.0,
            subUseMargins = if (isZoom) "yes" else "no",
            subAssForceMargins = if (isZoom) "yes" else "no",
        )
    }

    // ── libVLC ──────────────────────────────────────────────────────────────

    data class VlcAspectRatioPlan(
        /** Native aspect string; null clears the override (native frame). */
        val aspectRatioOverride: String?,
        /** Companion `scale = 0f` reset that clears a previous override. */
        val resetScale: Boolean,
    )

    /**
     * libVLC's Android MediaPlayer (3.x) offers no zoom/crop control the
     * engine could drive, so CROP — like FIT/FILL/AUTO — deliberately resolves
     * to the native-frame reset. Declared here (previously a silent
     * fall-through that made CROP a no-op on this engine) so the gap is
     * visible and pinned by tests.
     */
    fun vlcPlan(ratio: AspectRatio): VlcAspectRatioPlan {
        val numeric = ratio.ratio?.takeIf { it > 0f }
        return if (numeric != null) {
            VlcAspectRatioPlan(aspectRatioOverride = numeric.toString(), resetScale = false)
        } else {
            VlcAspectRatioPlan(aspectRatioOverride = null, resetScale = true)
        }
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val temp = y
            y = x % y
            x = temp
        }
        return x
    }
}
