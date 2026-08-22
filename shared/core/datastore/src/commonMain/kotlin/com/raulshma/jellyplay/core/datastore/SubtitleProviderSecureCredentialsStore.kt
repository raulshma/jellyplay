package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderCredentials
import com.raulshma.jellyplay.core.model.subtitle.SubtitleProviderKind
import kotlinx.serialization.json.Json

/**
 * Encrypted store for subtitle-provider credentials (Wyzie API key,
 * OpenSubtitles username/password + cached JWT).
 *
 * Mirrors [ArrSecureCredentialsStore] / [SeerrSecureCredentialsStore]: a
 * dedicated encrypted preferences file keeps subtitle secrets isolated from
 * *arr / Seerr secrets; encryption is owned by the platform
 * [SecureKeyValueStorage] implementation. Each provider's credentials are
 * serialized to JSON under a per-provider key.
 *
 * Parse failures degrade to null rather than throwing — losing a corrupt
 * credential entry is preferable to crashing the app, and the user can simply
 * re-enter the key.
 */
class SubtitleProviderSecureCredentialsStore(
    private val storage: SecureKeyValueStorage,
) {
    // Lenient: the credentials model is internally controlled (we write+read it),
    // but leniency protects against forward-compatible field additions.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private fun keyFor(kind: SubtitleProviderKind): String = "creds_${kind.name.lowercase()}"

    /** Returns the stored credentials for [kind], or null if none/unparseable. */
    fun getCredentials(kind: SubtitleProviderKind): SubtitleProviderCredentials? {
        val raw = storage.getString(keyFor(kind), null) ?: return null
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
        storage.putString(keyFor(kind), raw)
    }

    /** Removes the credentials for a single provider. */
    fun clearCredentials(kind: SubtitleProviderKind) {
        storage.remove(keyFor(kind))
    }
}
