package com.raulshma.jellyplay.core.network

/**
 * Random UUID v4 string, kotlin.random-backed (no common UUID in the stdlib).
 *
 * Byte-mirrors `shared/core/datastore`'s internal `randomUuidString` wasm
 * actual (same bit-twiddling, same hex-table formatting) — that one is
 * module-internal, so the network module carries its own copy. Used for the
 * wasm device id (random PER BOOT — no persisted device identity on wasm v1)
 * and the probe fallback server id, matching
 * `AuthApiClientImpl.probeServerInfo`'s `UUID.randomUUID()` fallback.
 */
internal fun randomUuidV4(): String {
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
