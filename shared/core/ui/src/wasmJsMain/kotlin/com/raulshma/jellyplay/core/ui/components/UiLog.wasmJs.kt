package com.raulshma.jellyplay.core.ui.components

/**
 * Wasm UI logging: `println` routes to the JS console under Kotlin/wasm, so
 * the textual JVM shapes (`W/tag:` / `D/tag:` prefixes) are kept verbatim.
 * Stack traces degrade to the exception summary (`error?.let { ": $it" }`)
 * since the browser owns real trace capture in DevTools.
 */
internal actual fun logUiWarning(tag: String, message: String, error: Throwable?) {
    println("W/$tag: $message${error?.let { ": $it" } ?: ""}")
}

internal actual fun logUiDebug(tag: String, message: String) {
    println("D/$tag: $message")
}
