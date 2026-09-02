package com.raulshma.jellyplay.core.ui.components

import android.util.Log

internal actual fun logUiWarning(tag: String, message: String, error: Throwable?) {
    Log.w(tag, message, error)
}

internal actual fun logUiDebug(tag: String, message: String) {
    Log.d(tag, message)
}
