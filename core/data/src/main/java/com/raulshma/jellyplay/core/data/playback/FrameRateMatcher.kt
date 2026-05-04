package com.raulshma.jellyplay.core.data.playback

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.Window

object FrameRateMatcher {

    private var originalModeId: Int? = null

    fun matchFrameRate(activity: Activity, frameRate: Float?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        if (frameRate == null || frameRate <= 0f) return

        val display = activity.windowManager.defaultDisplay
        val modes = display.supportedModes ?: return
        val currentMode = display.mode

        originalModeId = originalModeId ?: currentMode.modeId

        val targetMode = modes
            .filter { it.physicalWidth == currentMode.physicalWidth && it.physicalHeight == currentMode.physicalHeight }
            .minByOrNull { kotlin.math.abs(it.refreshRate - frameRate) }
            ?: return

        if (targetMode.modeId != currentMode.modeId) {
            val params = activity.window.attributes
            params.preferredDisplayModeId = targetMode.modeId
            activity.window.attributes = params
            Log.d(TAG, "Switched display to mode ${targetMode.modeId} (${targetMode.refreshRate}Hz) for ${frameRate}fps content")
        }
    }

    fun restoreOriginalMode(activity: Activity) {
        val modeId = originalModeId ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return

        val params = activity.window.attributes
        params.preferredDisplayModeId = modeId
        activity.window.attributes = params
        originalModeId = null
        Log.d(TAG, "Restored original display mode $modeId")
    }

    private const val TAG = "FrameRateMatcher"
}
