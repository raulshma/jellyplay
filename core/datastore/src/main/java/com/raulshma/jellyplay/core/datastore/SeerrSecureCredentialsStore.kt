package com.raulshma.jellyplay.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SeerrSecureCredentialsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "seerr_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // Memoised raw values so hot read paths skip the per-call Tink decrypt of
    // [EncryptedSharedPreferences]. The memos are only valid because this class
    // is the SOLE owner of the file: every write must route through the setters
    // and [clearAll] below. Any future writer that touches
    // "seerr_secure_prefs" directly (e.g. a backup/restore flow) must either go
    // through this class or invalidate these memos — otherwise restored values
    // would be shadowed by stale ones until process death.
    @Volatile
    private var cachedApiKey: String? = null
    @Volatile
    private var cachedSessionCookie: String? = null

    fun getApiKey(): String {
        val cached = cachedApiKey
        if (cached != null) return cached
        return prefs.getString(KEY_API_KEY, "").also { cachedApiKey = it } ?: ""
    }

    fun setApiKey(value: String) {
        prefs.edit().putString(KEY_API_KEY, value).apply()
        cachedApiKey = value
    }

    fun getPassword(): String = prefs.getString(KEY_PASSWORD, "") ?: ""

    fun setPassword(value: String) {
        prefs.edit().putString(KEY_PASSWORD, value).apply()
    }

    fun getSessionCookie(): String {
        val cached = cachedSessionCookie
        if (cached != null) return cached
        return prefs.getString(KEY_SESSION_COOKIE, "").also { cachedSessionCookie = it } ?: ""
    }

    fun setSessionCookie(value: String) {
        prefs.edit().putString(KEY_SESSION_COOKIE, value).apply()
        cachedSessionCookie = value
    }

    fun clearAll() {
        prefs.edit().clear().apply()
        cachedApiKey = null
        cachedSessionCookie = null
    }

    companion object {
        private const val KEY_API_KEY = "api_key"
        private const val KEY_PASSWORD = "password"
        private const val KEY_SESSION_COOKIE = "session_cookie"
    }
}
