package com.raulshma.jellyplay.core.network

// println maps onto console output in the wasmJs browser target (the stdlib's
// wasm console implementation), so the facade needs no JS interop to stay
// compile-clean here.
actual object NetworkLog {
    actual fun d(tag: String, message: String) {
        println("D/$tag: $message")
    }

    actual fun w(tag: String, message: String) {
        println("W/$tag: $message")
    }

    actual fun w(tag: String, message: String, error: Throwable?) {
        println("W/$tag: $message${error?.let { ": ${it.message}" } ?: ""}")
    }

    actual fun e(tag: String, message: String, error: Throwable?) {
        println("E/$tag: $message${error?.let { ": ${it.message}" } ?: ""}")
    }

    // Gated like the android/jvm actuals (debug builds only) — leaving this
    // true made RetryPolicy's per-attempt logs unconditional in the browser
    // console. Flip locally when debugging the web client.
    actual fun isDebugEnabled(tag: String): Boolean = false
}
