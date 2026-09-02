package com.raulshma.jellyplay.core.datastore

/**
 * wasm actual: UUID v4 formatted from [kotlin.random] bits (the stdlib has no
 * common UUID). Used for the persisted server/device identity string only —
 * uniqueness matters, not cryptographic entropy. String.format is unavailable
 * on wasm, so hex encoding is a lookup table.
 */
internal actual fun randomUuidString(): String {
    val bytes = ByteArray(16)
    kotlin.random.Random.nextBytes(bytes)
    bytes[6] = ((bytes[6].toInt() and 0x0f) or 0x40).toByte() // version 4
    bytes[8] = ((bytes[8].toInt() and 0x3f) or 0x80).toByte() // RFC 4122 variant
    val hexDigits = "0123456789abcdef"
    val hex = buildString(32) {
        for (b in bytes) {
            append(hexDigits[(b.toInt() shr 4) and 0xf])
            append(hexDigits[b.toInt() and 0xf])
        }
    }
    return buildString(36) {
        append(hex, 0, 8); append('-')
        append(hex, 8, 12); append('-')
        append(hex, 12, 16); append('-')
        append(hex, 16, 20); append('-')
        append(hex, 20, 32)
    }
}
