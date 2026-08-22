package com.raulshma.jellyplay.core.model

import kotlin.time.TimeSource

/** Kotlin/Wasm JS interop: `Date.now()` epoch millis. */
private fun jsDateNow(): Double = js("Date.now()")

actual fun wallNowMillis(): Long = jsDateNow().toLong()

actual fun deviceModel(): String = "Web"

private val startMark = TimeSource.Monotonic.markNow()

actual fun monotonicNowMillis(): Long = startMark.elapsedNow().inWholeMilliseconds
