package com.raulshma.jellyplay.core.ui.components

internal actual fun logUiWarning(tag: String, message: String, error: Throwable?) {
    System.err.println("W/$tag: $message${error?.let { ": $it" } ?: ""}")
}

internal actual fun logUiDebug(tag: String, message: String) {
    // Desktop debug chatter goes to stdout so it stays out of stderr warnings.
    println("D/$tag: $message")
}
