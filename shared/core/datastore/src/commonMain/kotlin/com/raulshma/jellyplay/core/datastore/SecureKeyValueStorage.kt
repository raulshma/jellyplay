package com.raulshma.jellyplay.core.datastore

/**
 * Minimal encrypted key-value contract the secure credential stores build on.
 *
 * Synchronous get/put mirrors the SharedPreferences semantics the stores were
 * written against. Each platform backs it with its OS-encrypted store:
 * Android [androidx.security.crypto.EncryptedSharedPreferences], desktop the
 * OS keyring (Windows Credential Manager / macOS Keychain / Linux Secret
 * Service), web process-lifetime memory only (no persistent secret storage
 * in web v1 — plan §Phase W scope cut).
 *
 * There is deliberately no bulk `clear()`: the desktop keyring backend can't
 * enumerate entries, so credential stores clear by deleting each of their own
 * known keys.
 */
interface SecureKeyValueStorage {
    fun getString(key: String, defValue: String? = null): String?
    fun putString(key: String, value: String?)
    fun remove(key: String)
}
