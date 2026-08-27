package com.raulshma.jellyplay.core.datastore

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.browser.localStorage

/**
 * localStorage-backed [SecureKeyValueStorage] for the SEERR credential store
 * ONLY (wave 16B). Keys are `jellyplay/secure/seerr/<key>` (see
 * [DEFAULT_KEY_PREFIX]); values are Base64-of-UTF8 — the same encoding the
 * DataStore-over-localStorage adapter in `di/WebDatastoreModule.kt` uses, so
 * everything stored stays plain-text-decodable, never pretending to be
 * encrypted.
 *
 * WHY THIS EXISTS (the wave-16B scope change): web v1 kept ALL credential
 * stores in [WasmSecureKeyValueStorage] process memory — empty every boot.
 * That made the Seerr feature useless on web: of the two Seerr auth modes,
 * session-cookie auth is BROWSER-IMPOSSIBLE by platform rule (the `Cookie`
 * request header is a fetch-forbidden header name, so the browser strips it;
 * `Set-Cookie` is unreadable from JS — see SeerrWireSupport's WASM BROWSER
 * CAVEAT), which leaves the API key as the ONLY Seerr credential that can
 * ever function in a browser tab. An API key is user-entered CONFIGURATION
 * (gathered once from the Overseerr/Jellyseerr UI, same tier as the server
 * URL it pairs with), not a per-login secret — losing it on every reload
 * meant re-entering it every session with no path around that. Persisting it
 * across reloads is what makes the wave-16B credentials pane (apps/web
 * WebSeerrPane) an actual feature instead of a form.
 *
 * HONEST SECURITY CAVEAT: localStorage is readable by any script running in
 * the page origin (XSS-readable, not OS-encrypted like Android's
 * EncryptedSharedPreferences or the desktop OS keyring). This is ACCEPTED
 * for this one non-credential-tier secret deliberately — it is the same
 * exposure class as the "server URL" preference the shell already persists
 * to localStorage, and strictly better than the cookie alternative (which
 * both cannot work here and would be a live session secret). If a higher-
 * trust secret ever needs web persistence, this class is NOT the precedent:
 * it is scoped to Seerr by prefix and by the DI binding below.
 *
 * SCOPE GUARD: the OTHER credential stores (Jellyfin token, *arr keys,
 * subtitle-provider credentials) KEEP the session-memory
 * [WasmSecureKeyValueStorage] cut unchanged — `di/WebDatastoreModule.kt`
 * binds only [SeerrSecureCredentialsStore] to this class. A page-scoped
 * XSS can therefore never reach a Jellyfin/arr/subtitle secret, because none
 * ever leaves process memory on web.
 *
 * Failure behaviour mirrors the DataStore adapter: localStorage unavailable
 * (storage disabled / privacy mode) reads degrade to [defValue] and writes
 * degrade to session-only; a corrupt Base64 payload reads as absent.
 *
 * VERIFICATION: this file lives in wasmJsMain, so it has no jvmTest — the
 * pure Base64 encode/decode is kotlin.io.encoding.Base64 (stdlib, tested
 * upstream) and everything else is the `kotlinx.browser.localStorage` seam.
 * Persistence across reloads is instead browser-VERIFIED by the headless-Edge
 * CDP lane (tools/e2e/web-verify.mjs): the Seerr save step asserts via
 * Runtime.evaluate that both `jellyplay/secure/seerr/api_key` and the Seerr
 * DataStore key (`jellyplay/datastore/seerr_prefs.preferences_pb`) exist in
 * localStorage, and that the stored value decodes to the typed API key.
 */
@OptIn(ExperimentalEncodingApi::class)
class LocalStorageSecureKeyValueStorage(
    private val keyPrefix: String = DEFAULT_KEY_PREFIX,
) : SecureKeyValueStorage {

    override fun getString(key: String, defValue: String?): String? {
        val encoded = try {
            localStorage.getItem(keyPrefix + key)
        } catch (_: Throwable) {
            // localStorage unavailable: degrade to the default (session-only).
            return defValue
        } ?: return defValue
        return try {
            Base64.decode(encoded).decodeToString()
        } catch (_: IllegalArgumentException) {
            // Foreign/corrupt entry under our key: treat as no value.
            defValue
        }
    }

    override fun putString(key: String, value: String?) {
        if (value == null) {
            remove(key)
            return
        }
        try {
            localStorage.setItem(keyPrefix + key, Base64.encode(value.encodeToByteArray()))
        } catch (t: Throwable) {
            // Quota exceeded / storage disabled: the value stays usable for
            // this session; only persistence is lost (same degrade as the
            // DataStore adapter).
            println("JellyPlay datastore: localStorage write failed for ${keyPrefix + key} ($t)")
        }
    }

    override fun remove(key: String) {
        try {
            localStorage.removeItem(keyPrefix + key)
        } catch (_: Throwable) {
            // Storage unavailable: nothing persisted to remove.
        }
    }

    companion object {
        const val DEFAULT_KEY_PREFIX = "jellyplay/secure/seerr/"
    }
}
