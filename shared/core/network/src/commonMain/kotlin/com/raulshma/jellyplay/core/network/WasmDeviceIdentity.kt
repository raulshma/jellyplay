package com.raulshma.jellyplay.core.network

/**
 * localStorage key under which the wasm network stack persists the device id
 * (wave 21C): a PLAIN UUID v4 string, no encoding — unlike the Seerr creds
 * store's Base64-of-UTF8 values, the only writer here is our own generator
 * (the wasm `randomUuidV4` in WasmIdentity.kt; ASCII hex + hyphens), so there
 * is nothing to decode and a foreign/corrupt entry is simply regenerated over
 * (see [isCanonicalUuidV4Text]). The key literal is load-bearing beyond this
 * module: the headless-Edge CDP lane (tools/e2e/web-verify.mjs) reads
 * `localStorage["jellyplay/device-id"]` to browser-verify persistence across
 * a reload, and the commonTest pin keeps the two from drifting apart.
 */
internal const val WASM_DEVICE_ID_STORAGE_KEY = "jellyplay/device-id"

/**
 * Format gate for a stored device id: exactly the canonical lowercase UUID v4
 * text the wasm generator (`randomUuidV4`, WasmIdentity.kt) writes —
 * 8-4-4-4-12 hex groups, version nibble '4' at index 14, RFC 4122 variant
 * nibble (8/9/a/b) at index 19. Uppercase, braces, urn prefixes, whitespace,
 * wrong version/variant nibbles and non-hex characters all fail, so anything
 * that is not OUR value reads as absent and gets regenerated instead of being
 * put on the wire.
 */
internal fun isCanonicalUuidV4Text(value: String): Boolean {
    if (value.length != 36) return false
    for (index in value.indices) {
        val c = value[index]
        val ok = when (index) {
            8, 13, 18, 23 -> c == '-'
            else -> c in '0'..'9' || c in 'a'..'f'
        }
        if (!ok) return false
    }
    return value[14] == '4' && value[19] in "89ab"
}

/**
 * Pure decision core of the persistent wasm device identity (wave 21C):
 * a stored value that passes [isCanonicalUuidV4Text] is returned VERBATIM
 * (same device id across browser reloads — the server's device list stops
 * growing one entry per reload); anything else (absent, storage-unavailable
 * null, foreign/corrupt text under the key) generates a fresh id via
 * [generate] and hands it to [persist] — invoked AT MOST once, with any
 * [persist] failure contained here (storage disabled / private mode /
 * quota exceeded degrade to a session-only id, exactly like the
 * localStorage-backed Seerr credential store's failure behaviour).
 *
 * The result is whatever the caller keeps it as: the Koin single in
 * networkWasmModule resolves this ONCE per boot into the immutable
 * [com.raulshma.jellyplay.core.network.api.WasmClientIdentity.deviceId], so
 * the id stays stable for the whole session regardless of LATER storage
 * failures — only the next cold boot re-reads localStorage.
 *
 * Lives in commonMain (same precedent as `resolveWasmPlaybackFlags`) so
 * commonTest — which runs through jvmTest, the only enabled test target —
 * can pin the format gate and the absent/corrupt/throwing-storage decisions;
 * the actual localStorage seam stays in wasmJsMain (`WasmIdentity.kt`).
 */
internal fun resolveWasmDeviceId(
    stored: String?,
    generate: () -> String,
    persist: (String) -> Unit,
): String {
    if (stored != null && isCanonicalUuidV4Text(stored)) return stored
    val id = generate()
    try {
        persist(id)
    } catch (_: Throwable) {
        // Storage unavailable: session-only id (documented degrade).
    }
    return id
}
