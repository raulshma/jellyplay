package com.raulshma.jellyplay.core.data.playback

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Display
import com.raulshma.jellyplay.core.model.RefreshRateMode

/**
 * Switches the host display's mode to match the content's frame rate (and,
 * optionally, its resolution).
 *
 * Delegates the matching decision to the pure, testable [RefreshRateMatcher]
 * (5-tier fallback: exact res+rate → next-higher res + exact rate → exact res +
 * acceptable rate → next-higher res + acceptable rate → largest res). This
 * replaces the earlier single-tier "closest refresh rate at current resolution"
 * behaviour, which left 4K@24fps content stuck at 1080p@60 on panels whose
 * current mode was 1080p.
 *
 * The previous API ([matchFrameRate] with a boolean equivalent) is preserved;
 * the new [matchFrameRate] overload takes a [RefreshRateMode] so the caller can
 * opt into resolution switching. The original mode id is remembered and restored
 * by [restoreOriginalMode] on teardown.
 */
object FrameRateMatcher {

    @Volatile
    private var originalModeId: Int? = null

    fun matchFrameRate(
        activity: Activity,
        frameRate: Float?,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
        mode: RefreshRateMode = RefreshRateMode.OFF,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (mode == RefreshRateMode.OFF) return
        if (frameRate == null || frameRate <= 0f) return

        val display = activity.windowManager.defaultDisplay
        val modes = display.supportedModes ?: return
        val currentMode = display.mode

        originalModeId = originalModeId ?: currentMode.modeId

        // Build the current-mode projection. If the caller supplied a content
        // resolution, use it as the resolution target instead of the current
        // mode's resolution (so 4K content prefers a 4K mode even when the
        // activity is currently rendering at 1080p).
        val currentProjection = RefreshRateMatcher.DisplayMode(
            modeId = currentMode.modeId,
            physicalWidth = targetWidth ?: currentMode.physicalWidth,
            physicalHeight = targetHeight ?: currentMode.physicalHeight,
            refreshRate = currentMode.refreshRate,
        )

        val candidates = modes.map {
            RefreshRateMatcher.DisplayMode(
                modeId = it.modeId,
                physicalWidth = it.physicalWidth,
                physicalHeight = it.physicalHeight,
                refreshRate = it.refreshRate,
            )
        }

        val target = RefreshRateMatcher.findDisplayMode(
            modes = candidates,
            current = currentProjection,
            targetFps = frameRate,
            allowResolutionSwitch = mode == RefreshRateMode.FRAME_RATE_AND_RESOLUTION,
        ) ?: return

        if (target.modeId != currentMode.modeId) {
            val params = activity.window.attributes
            params.preferredDisplayModeId = target.modeId
            activity.window.attributes = params
            if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Switched display to mode ${target.modeId} " +
                        "(${target.physicalWidth}x${target.physicalHeight} @ ${target.refreshRate}Hz) " +
                        "for ${frameRate}fps content ($mode)"
                )
            }
        }
    }

    fun restoreOriginalMode(activity: Activity) {
        val modeId = originalModeId ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val params = activity.window.attributes
        params.preferredDisplayModeId = modeId
        activity.window.attributes = params
        originalModeId = null
        if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
            Log.d(TAG, "Restored original display mode $modeId")
        }
    }

    private const val TAG = "FrameRateMatcher"
}
