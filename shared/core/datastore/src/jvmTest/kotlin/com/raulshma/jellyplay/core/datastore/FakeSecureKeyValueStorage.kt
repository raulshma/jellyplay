package com.raulshma.jellyplay.core.datastore

/**
 * Hand-written in-memory [SecureKeyValueStorage] fake backing the secure
 * credential stores in tests (the module convention — no mockk). Mirrors the
 * platform contract: `putString(key, null)` removes the entry and `getString`
 * returns [defValue] when the key is absent.
 *
 * The backing map is exposed read-only so tests can seed corrupt payloads
 * (e.g. a malformed `arr_manual_servers` blob) directly.
 */
class FakeSecureKeyValueStorage(
    private val map: MutableMap<String, String> = mutableMapOf(),
) : SecureKeyValueStorage {

    /** Direct map access for seeding pre-corrupted state in tests. */
    val raw: MutableMap<String, String> get() = map

    override fun getString(key: String, defValue: String?): String? = map[key] ?: defValue

    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }
}
