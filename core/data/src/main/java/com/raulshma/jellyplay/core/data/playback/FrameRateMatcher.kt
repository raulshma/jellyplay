package com.raulshma.jellyplay.core.data.playback

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Window

object FrameRateMatcher {

    @Volatile
    private var originalModeId: Int? = null

    fun matchFrameRate(
        activity: Activity,
        frameRate: Float?,
        targetWidth: Int? = null,
        targetHeight: Int? = null,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (frameRate == null || frameRate <= 0f) return

        val display = activity.windowManager.defaultDisplay
        val modes = display.supportedModes ?: return
        val currentMode = display.mode

        originalModeId = originalModeId ?: currentMode.modeId

        val resolutionFiltered = if (targetWidth != null && targetHeight != null) {
            modes.filter { mode ->
                (mode.physicalWidth == targetWidth && mode.physicalHeight == targetHeight) ||
                    (mode.physicalWidth == currentMode.physicalWidth && mode.physicalHeight == currentMode.physicalHeight)
            }
        } else {
            modes.filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
        }

        val targetMode = resolutionFiltered
            .minByOrNull { kotlin.math.abs(it.refreshRate - frameRate) }
            ?: return

        if (targetMode.modeId != currentMode.modeId) {
            val params = activity.window.attributes
            params.preferredDisplayModeId = targetMode.modeId
            activity.window.attributes = params
            if (com.raulshma.jellyplay.core.data.BuildConfig.DEBUG) {
                Log.d(TAG, "Switched display to mode ${targetMode.modeId} (${targetMode.physicalWidth}x${targetMode.physicalHeight} @ ${targetMode.refreshRate}Hz) for ${frameRate}fps content")
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
