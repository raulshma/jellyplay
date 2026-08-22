package com.raulshma.jellyplay.core.datastore

import com.github.javakeyring.Keyring

/**
 * OS-keyring-backed [SecureKeyValueStorage] for desktop: Windows Credential
 * Manager, macOS Keychain, or Linux Secret Service/KWallet via java-keyring
 * (JNA).
 *
 * When no OS backend is available (e.g. headless Linux without libsecret) the
 * store degrades to process-lifetime memory so the app keeps running —
 * secrets just don't survive a restart until a backend exists. Write failures
 * (backend present but flaky) mirror into the memory fallback so
 * read-after-write stays consistent for the session.
 *
 * Entry names are namespaced per [service] ("JellyPlay/&lt;file&gt;") so the
 * three credential files stay isolated, matching the per-file isolation on
 * Android.
 */
class DesktopSecureKeyValueStorage(
    private val service: String,
) : SecureKeyValueStorage {
    private val keyring: Keyring? = runCatching { Keyring.create() }.getOrNull()
    private val fallback = mutableMapOf<String, String>()

    override fun getString(key: String, defValue: String?): String? {
        val ring = keyring ?: return fallback[key] ?: defValue
        return runCatching { ring.getPassword(service, key) }
            .getOrDefault(fallback[key])
            ?: defValue
    }

    override fun putString(key: String, value: String?) {
        if (!writeThrough(key, value)) {
            if (value == null) fallback.remove(key) else fallback[key] = value
        }
    }

    override fun remove(key: String) {
        if (!writeThrough(key, null)) {
            fallback.remove(key)
        }
    }

    private fun writeThrough(key: String, value: String?): Boolean {
        val ring = keyring ?: return false
        return runCatching {
            if (value == null) {
                ring.deletePassword(service, key)
            } else {
                ring.setPassword(service, key, value)
            }
        }.isSuccess
    }
}
