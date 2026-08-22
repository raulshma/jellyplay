package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.arr.ArrServerConfig
import com.raulshma.jellyplay.core.model.arr.ArrServiceKind
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Encrypted store for manually-entered Radarr / Sonarr server credentials.
 *
 * Mirrors [SeerrSecureCredentialsStore]: a dedicated encrypted preferences
 * file keeps *arr secrets isolated from Seerr secrets; encryption is owned by
 * the platform [SecureKeyValueStorage] implementation. Manual server configs
 * are serialized to a JSON array under a single key.
 *
 * Auto-discovered servers (sourced from Seerr's `/service/` endpoints at
 * runtime) are **not** persisted here — they live only in memory inside
 * [com.raulshma.jellyplay.core.data.repository.ArrRepositoryImpl] so they can
 * be refreshed whenever Seerr config changes.
 */
class ArrSecureCredentialsStore(
    private val storage: SecureKeyValueStorage,
) {
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
        val raw = storage.getString(KEY_MANUAL_SERVERS, null) ?: return emptyList()
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
        storage.putString(KEY_MANUAL_SERVERS, raw)
    }

    /** Removes all manual servers of [kind]; leaves the other kind untouched. */
    fun clearManual(kind: ArrServiceKind) {
        val remaining = getManualServers().filterNot { it.kind == kind }
        storage.putString(KEY_MANUAL_SERVERS, json.encodeToString(serializer, remaining))
    }

    fun clearAll() {
        storage.remove(KEY_MANUAL_SERVERS)
    }

    companion object {
        private const val KEY_MANUAL_SERVERS = "arr_manual_servers"
    }
}
