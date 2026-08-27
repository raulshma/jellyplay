package com.raulshma.jellyplay.core.data.log

/**
 * Wasm actual of the [Log] facade: `[LEVEL][tag] message` lines on the JS
 * console via `println` (stderr has no wasm equivalent; everything lands in
 * the browser console), mirroring the desktop jvmMain actual's shape. There are no
 * levels beyond the D/I/W/E shapes — same cut the desktop actual documents.
 * Stack traces: `Throwable.stackTraceToString()` (multiplatform since 1.4).
 * Returns 0 — there is no logcat line count to report.
 */
actual object Log {
    actual fun d(tag: String, message: String): Int {
        println("[D][$tag] $message")
        return 0
    }

    actual fun d(tag: String, message: String, throwable: Throwable?): Int {
        println("[D][$tag] $message")
        throwable?.let { println(it.stackTraceToString()) }
        return 0
    }

    actual fun i(tag: String, message: String): Int {
        println("[I][$tag] $message")
        return 0
    }

    actual fun w(tag: String, message: String): Int {
        println("[W][$tag] $message")
        return 0
    }

    actual fun w(tag: String, throwable: Throwable): Int {
        println("[W][$tag] $throwable")
        println(throwable.stackTraceToString())
        return 0
    }

    actual fun w(tag: String, message: String, throwable: Throwable?): Int {
        println("[W][$tag] $message")
        throwable?.let { println(it.stackTraceToString()) }
        return 0
    }

    actual fun e(tag: String, message: String): Int {
        println("[E][$tag] $message")
        return 0
    }

    actual fun e(tag: String, message: String, throwable: Throwable?): Int {
        println("[E][$tag] $message")
        throwable?.let { println(it.stackTraceToString()) }
        return 0
    }
}
