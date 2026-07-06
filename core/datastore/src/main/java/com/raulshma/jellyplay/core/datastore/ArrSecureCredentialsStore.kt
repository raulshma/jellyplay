package com.raulshma.jellyplay.core.datastore

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted store for manually-entered Radarr / Sonarr server credentials.
 *
 * Mirrors [SeerrSecureCredentialsStore] verbatim in encryption scheme
 * (AES256_GCM master key, AES256_SIV keys, AES256_GCM values) but with a
 * dedicated preferences file so *arr secrets are isolated from Seerr secrets.
 * Manual server configs are serialized to a JSON array under a single key.
 *
 * Auto-discovered servers (sourced from Seerr's `/service/` endpoints at
 * runtime) are **not** persisted here — they live only in memory inside
 * [com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl] so they can
 * be refreshed whenever Seerr config changes.
 *
 * Resolved via `@Inject constructor` + `@Singleton` (no DI module needed),
 * matching the [SeerrSecureCredentialsStore] precedent.
 */
@Singleton
class ArrSecureCredentialsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "arr_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // A lenient Json is sufficient here — the model is internally controlled
    // (we both write and read it), but leniency protects against future field
    // additions if a newer build reads an older entry.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val serializer = ListSerializer(ArrServerConfig.serializer())

    /**
     * Returns the manually-entered servers, parsed from the encrypted JSON
     * blob. Empty list on first install, on parse failure, or after [clearAll].
     * Parse failures degrade to empty rather than throwing — losing manual
     * *arr config is preferable to crashing the app on a corrupt blob.
     */
    fun getManualServers(): List<ArrServerConfig> {
        val raw = prefs.getString(KEY_MANUAL_SERVERS, null) ?: return emptyList()
        return runCatching {
            json.decodeFromString(serializer, raw)
        }.getOrDefault(emptyList())
    }

    /**
     * Replaces the entire manual-server list. Callers should read-modify-write
     * (load via [getManualServers], mutate, then write back).
     */
    fun setManualServers(servers: List<ArrServerConfig>) {
        val onlyManual = servers.filter { it.isManual }
        val raw = json.encodeToString(serializer, onlyManual)
        prefs.edit().putString(KEY_MANUAL_SERVERS, raw).apply()
    }

    /** Removes all manual servers of [kind]; leaves the other kind untouched. */
    fun clearManual(kind: ArrServiceKind) {
        val remaining = getManualServers().filterNot { it.kind == kind }
        prefs.edit().putString(KEY_MANUAL_SERVERS, json.encodeToString(serializer, remaining)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_MANUAL_SERVERS = "arr_manual_servers"
    }
}
