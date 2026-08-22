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

    actual fun isDebugEnabled(tag: String): Boolean = true
}
