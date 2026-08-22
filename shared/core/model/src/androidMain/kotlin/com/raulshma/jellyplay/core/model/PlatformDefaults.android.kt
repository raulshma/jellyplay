package com.raulshma.jellyplay.core.model

import android.os.Build
import android.os.SystemClock

actual fun wallNowMillis(): Long = System.currentTimeMillis()

actual fun deviceModel(): String = Build.MODEL.orEmpty().ifBlank { "Android" }

actual fun monotonicNowMillis(): Long = SystemClock.elapsedRealtime()
