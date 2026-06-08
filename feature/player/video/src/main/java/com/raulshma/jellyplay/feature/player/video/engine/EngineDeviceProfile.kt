package com.raulshma.jellyplay.feature.player.video.engine

import android.app.ActivityManager
import android.content.Context

internal object EngineDeviceProfile {
    fun isLowRamDevice(context: Context): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return am.isLowRamDevice || am.memoryClass <= 256
    }
}
