package com.raulshma.jellyplay.core.network

import android.util.Log

actual object NetworkLog {
    actual fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    actual fun w(tag: String, message: String) {
        Log.w(tag, message)
    }

    actual fun w(tag: String, message: String, error: Throwable?) {
        Log.w(tag, message, error)
    }

    actual fun e(tag: String, message: String, error: Throwable?) {
        Log.e(tag, message, error)
    }

    actual fun isDebugEnabled(tag: String): Boolean = Log.isLoggable(tag, Log.DEBUG)
}
