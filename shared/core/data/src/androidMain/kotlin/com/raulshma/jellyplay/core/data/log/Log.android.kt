package com.raulshma.jellyplay.core.data.log

/**
 * Android actual of the [Log] facade: delegation to `android.util.Log` —
 * identical tags, identical return values for migrated code — behind a
 * runtime minimum-level gate on the `d`/`i` overloads.
 *
 * [minimumLevel] defaults to [android.util.Log.WARN], so release builds
 * short-circuit debug/info calls at the facade (no tag/message handoff to
 * logcat); debug builds raise it to [android.util.Log.DEBUG] at startup via
 * [setVerboseLoggingEnabled]. R8 additionally strips `d`/`i` call sites —
 * arguments included — from minified release builds, so the gate is
 * defense-in-depth for the non-minified debuggable release builds.
 * `w`/`e` always delegate.
 */
actual object Log {
    /**
     * Minimum `android.util.Log` priority a call must clear to reach logcat
     * (compared against the `DEBUG`/`INFO`/`WARN` constants; the gate covers
     * only the `d`/`i` overloads). The release default of
     * [android.util.Log.WARN] suppresses d/i; debug builds raise it to
     * [android.util.Log.DEBUG] via [setVerboseLoggingEnabled]. Volatile:
     * written once at startup, read from arbitrary worker threads.
     */
    @Volatile
    var minimumLevel: Int = android.util.Log.WARN

    /**
     * Idempotently pins the gate: [android.util.Log.DEBUG] when [enabled]
     * (debuggable builds — verbose d/i reach logcat), the release default
     * [android.util.Log.WARN] when not. Called from the app shell's
     * `onCreate`.
     */
    fun setVerboseLoggingEnabled(enabled: Boolean) {
        minimumLevel = if (enabled) android.util.Log.DEBUG else android.util.Log.WARN
    }

    actual fun d(tag: String, message: String): Int {
        if (minimumLevel > android.util.Log.DEBUG) return 0
        return android.util.Log.d(tag, message)
    }

    actual fun d(tag: String, message: String, throwable: Throwable?): Int {
        if (minimumLevel > android.util.Log.DEBUG) return 0
        return android.util.Log.d(tag, message, throwable)
    }

    actual fun i(tag: String, message: String): Int {
        if (minimumLevel > android.util.Log.DEBUG) return 0
        return android.util.Log.i(tag, message)
    }

    actual fun w(tag: String, message: String): Int = android.util.Log.w(tag, message)

    actual fun w(tag: String, throwable: Throwable): Int = android.util.Log.w(tag, throwable)

    actual fun w(tag: String, message: String, throwable: Throwable?): Int =
        android.util.Log.w(tag, message, throwable)

    actual fun e(tag: String, message: String): Int = android.util.Log.e(tag, message)

    actual fun e(tag: String, message: String, throwable: Throwable?): Int =
        android.util.Log.e(tag, message, throwable)
}
