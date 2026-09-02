package com.raulshma.jellyplay.core.datastore

/**
 * Process-lifetime [SecureKeyValueStorage] for web. Web v1 has no persistent
 * secret storage (plan §Phase W scope cut): credentials live only as long as
 * the page session, and users re-enter them per session.
 */
class WasmSecureKeyValueStorage : SecureKeyValueStorage {
    private val map = mutableMapOf<String, String>()

    override fun getString(key: String, defValue: String?): String? =
        map[key] ?: defValue

    override fun putString(key: String, value: String?) {
        if (value == null) map.remove(key) else map[key] = value
    }

    override fun remove(key: String) {
        map.remove(key)
    }
}
