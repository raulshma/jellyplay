package com.raulshma.jellyplay.core.network

import kotlinx.browser.localStorage

/**
 * Random UUID v4 string, kotlin.random-backed (no common UUID in the stdlib).
 *
 * Byte-mirrors `shared/core/datastore`'s internal `randomUuidString` wasm
 * actual (same bit-twiddling, same hex-table formatting) — that one is
 * module-internal, so the network module carries its own copy. Used for the
 * probe fallback server id (matching `AuthApiClientImpl.probeServerInfo`'s
 * `UUID.randomUUID()` fallback) and as the GENERATOR for a fresh device id
 * when [persistedOrRandomDeviceId] finds none stored (wave 21C: the device id
 * itself is now persisted across boots — no longer "random PER BOOT").
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

/**
 * The device id for this browser origin, stable across boots (wave 21C):
 * DIRECT localStorage under [WASM_DEVICE_ID_STORAGE_KEY], plain UUID string.
 *
 * WHY DIRECT localStorage AND NOT THE (by-now-existing) DATASTORE LAYER: the
 * identity is consumed SYNCHRONOUSLY early in boot — the Koin factory in
 * networkWasmModule hands it into [WasmClientIdentity] as a plain `val` when
 * the auth client first resolves — while the webDatastoreModule DataStores are
 * async (Flow/suspend reads); blocking-firstOrNull-style bridging would drag
 * the whole identity seam into coroutine plumbing for one 36-char string.
 * The precedent is the wave-16B Seerr-credential store
 * (`shared/core/datastore` LocalStorageSecureKeyValueStorage): direct
 * `kotlinx.browser.localStorage` access for a small config-tier value, with
 * the same failure behaviour — storage unavailable (disabled / private mode)
 * degrades to the random per-session fallback and a failed WRITE never
 * disturbs the already-resolved id (the Koin single runs this exactly once
 * per boot; the value is immutable for the whole session afterwards).
 *
 * The read/write mechanics delegate to the pure [resolveWasmDeviceId] core
 * (commonMain, commonTest-pinned); this function is only the localStorage
 * seam, so it carries no unit test of its own — persistence across reloads
 * is browser-VERIFIED by the headless-Edge CDP lane (tools/e2e/
 * web-verify.mjs's DEVICE_ID step reads the key, reloads, re-reads, and
 * asserts equality + UUID shape).
 */
internal fun persistedOrRandomDeviceId(): String = resolveWasmDeviceId(
    stored = try {
        localStorage.getItem(WASM_DEVICE_ID_STORAGE_KEY)
    } catch (_: Throwable) {
        // localStorage unavailable (storage disabled / privacy mode): read as
        // absent — random session fallback below (Seerr-store degrade shape).
        null
    },
    generate = ::randomUuidV4,
    persist = { id ->
        // A throwing write is contained by resolveWasmDeviceId (session-only
        // id); quota/private-mode failures therefore cannot break boot.
        localStorage.setItem(WASM_DEVICE_ID_STORAGE_KEY, id)
    },
)
