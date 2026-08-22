package com.raulshma.jellyplay.core.data.log

/**
 * Android actual of the [Log] facade: straight delegation to
 * `android.util.Log` — identical tags, identical return values, no behavior
 * change for migrated code.
 */
actual object Log {
    actual fun d(tag: String, message: String): Int = android.util.Log.d(tag, message)

    actual fun d(tag: String, message: String, throwable: Throwable?): Int =
        android.util.Log.d(tag, message, throwable)

    actual fun i(tag: String, message: String): Int = android.util.Log.i(tag, message)

    actual fun w(tag: String, message: String): Int = android.util.Log.w(tag, message)

    actual fun w(tag: String, throwable: Throwable): Int = android.util.Log.w(tag, throwable)

    actual fun w(tag: String, message: String, throwable: Throwable?): Int =
        android.util.Log.w(tag, message, throwable)

    actual fun e(tag: String, message: String): Int = android.util.Log.e(tag, message)

    actual fun e(tag: String, message: String, throwable: Throwable?): Int =
        android.util.Log.e(tag, message, throwable)
}
