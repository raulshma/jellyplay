package com.raulshma.jellyplay.core.datastore

/**
 * Encrypted store for Overseerr secrets (API key, password, session cookie).
 *
 * Secrets live in a dedicated encrypted preferences file so Seerr credentials
 * stay isolated from *arr / subtitle-provider secrets. Encryption is owned by
 * the platform [SecureKeyValueStorage] implementation (Android:
 * EncryptedSharedPreferences with AES256_GCM master key, AES256_SIV keys,
 * AES256_GCM values — byte-for-byte the pre-KMP wiring).
 *
 * Constructed by the DI layer per platform; single instance per file.
 */
class SeerrSecureCredentialsStore(
    private val storage: SecureKeyValueStorage,
) {
    // Memoised raw values so hot read paths skip the per-call Tink decrypt of
    // EncryptedSharedPreferences (Android actual). The memos are only valid
    // because this class is the SOLE owner of the file: every write must route
    // through the setters and [clearAll] below.
    @Volatile
    private var cachedApiKey: String? = null
    @Volatile
    private var cachedSessionCookie: String? = null

    fun getApiKey(): String {
        val cached = cachedApiKey
        if (cached != null) return cached
        return (storage.getString(KEY_API_KEY, "") ?: "").also { cachedApiKey = it }
    }

    fun setApiKey(value: String) {
        storage.putString(KEY_API_KEY, value)
        cachedApiKey = value
    }

    fun getPassword(): String = storage.getString(KEY_PASSWORD, "") ?: ""

    fun setPassword(value: String) {
        storage.putString(KEY_PASSWORD, value)
    }

    fun getSessionCookie(): String {
        val cached = cachedSessionCookie
        if (cached != null) return cached
        return (storage.getString(KEY_SESSION_COOKIE, "") ?: "").also { cachedSessionCookie = it }
    }

    fun setSessionCookie(value: String) {
        storage.putString(KEY_SESSION_COOKIE, value)
        cachedSessionCookie = value
    }

    fun clearAll() {
        storage.remove(KEY_API_KEY)
        storage.remove(KEY_PASSWORD)
        storage.remove(KEY_SESSION_COOKIE)
        cachedApiKey = null
        cachedSessionCookie = null
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SESSION_COOKIE = "session_cookie"
    }
}
