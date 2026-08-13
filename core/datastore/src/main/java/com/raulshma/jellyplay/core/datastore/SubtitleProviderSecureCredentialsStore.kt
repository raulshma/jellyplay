package com.raulshma.jellyplay.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted store for subtitle-provider credentials (Wyzie API key,
 * OpenSubtitles username/password + cached JWT).
 *
 * Mirrors [ArrSecureCredentialsStore] / [SeerrSecureCredentialsStore] verbatim
 * in encryption scheme (AES256_GCM master key, AES256_SIV keys, AES256_GCM
 * values) with a dedicated preferences file (`subtitle_provider_secure_prefs`)
 * so subtitle secrets are isolated from *arr / Seerr secrets. Each provider's
 * credentials are serialized to JSON under a per-provider key.
 *
 * Resolved via `@Inject constructor` + `@Singleton` (no DI module needed),
 * matching the sibling stores.
 *
 * Parse failures degrade to null rather than throwing — losing a corrupt
 * credential entry is preferable to crashing the app, and the user can simply
 * re-enter the key.
 */
@Singleton
class SubtitleProviderSecureCredentialsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "subtitle_provider_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // Lenient: the credentials model is internally controlled (we write+read it),
    // but leniency protects against forward-compatible field additions.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun keyFor(kind: SubtitleProviderKind): String = "creds_${kind.name.lowercase()}"

    /** Returns the stored credentials for [kind], or null if none/unparseable. */
    fun getCredentials(kind: SubtitleProviderKind): SubtitleProviderCredentials? {
        val raw = prefs.getString(keyFor(kind), null) ?: return null
        return runCatching {
            json.decodeFromString(SubtitleProviderCredentials.serializer(), raw)
        }.getOrNull()
    }

    /**
     * Persists [credentials] for [kind]. Callers should read-modify-write for
     * OpenSubtitles JWT refresh (load → copy with new jwt → save) so the
     * username/password are preserved.
     */
    fun setCredentials(kind: SubtitleProviderKind, credentials: SubtitleProviderCredentials) {
        val raw = json.encodeToString(SubtitleProviderCredentials.serializer(), credentials)
        prefs.edit().putString(keyFor(kind), raw).apply()
    }

    /** Removes the credentials for a single provider. */
    fun clearCredentials(kind: SubtitleProviderKind) {
        prefs.edit().remove(keyFor(kind)).apply()
    }
}
