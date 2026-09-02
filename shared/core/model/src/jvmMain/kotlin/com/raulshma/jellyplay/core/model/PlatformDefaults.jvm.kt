package com.raulshma.jellyplay.core.model

actual fun wallNowMillis(): Long = System.currentTimeMillis()

actual fun deviceModel(): String =
    System.getProperty("os.name")?.ifBlank { null } ?: "Desktop"

private val startMark = System.nanoTime()

actual fun monotonicNowMillis(): Long = (System.nanoTime() - startMark) / 1_000_000
