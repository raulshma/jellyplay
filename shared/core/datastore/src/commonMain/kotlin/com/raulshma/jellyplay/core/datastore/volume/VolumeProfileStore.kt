package com.raulshma.jellyplay.core.datastore.volume

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.VolumeBucket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Per-content-type volume memory. Owns two keys in the shared
 * `"user_prefs"` DataStore:
 *  - `volume_profiles` — one JSON `Map<String, Float>` of remembered
 *    normalized levels (0..1) keyed by [VolumeBucket] name;
 *  - `remember_volume_per_content_type` — the master toggle (default ON:
 *    the feature only surfaces on platforms where the app owns a volume
 *    scalar, so on-by-default is the intended behavior "where available").
 *
 * Deliberately scoped (the design decision in the plan): Android video stays
 * on the system `STREAM_MUSIC` model and never touches this store; the
 * buckets are written by the desktop mpv engine's user-initiated volume path
 * and the audio players, and applied at session start.
 */
class VolumeProfileStore constructor(
    private val dataStore: DataStore<Preferences>,
    externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val VOLUME_PROFILES = stringPreferencesKey("volume_profiles")
        val REMEMBER_VOLUME_PER_CONTENT_TYPE = booleanPreferencesKey("remember_volume_per_content_type")
    }

    private var cachedVolumes: ParsedCache<Map<VolumeBucket, Float>> =
        ParsedCache(null, emptyMap())

    /** The volume-memory slice, derived directly from the raw DataStore. */
    val volumeProfile: StateFlow<VolumeProfileSlice> =
        dataStore.sliceStateFlow(scope, seed = VolumeProfileSlice(), read = ::read)

    /**
     * Pure read of the volume-memory fields from a raw [Preferences]
     * snapshot. The JSON blob decode is memoised (typed-key reads below it
     * re-run per emission); a corrupt blob degrades to no remembered levels
     * rather than throwing.
     */
    internal fun read(prefs: Preferences): VolumeProfileSlice {
        val volumes = PreferenceCodec.cachedJson(
            raw = prefs[Keys.VOLUME_PROFILES],
            cache = cachedVolumes,
            default = emptyMap(),
            parse = ::decodeVolumes,
            onNull = { emptyMap() },
            cacheRef = { cachedVolumes = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )
        return VolumeProfileSlice(
            volumes = volumes,
            rememberVolumePerContentType = PreferenceCodec.readBool(
                prefs,
                Keys.REMEMBER_VOLUME_PER_CONTENT_TYPE,
                "remember_volume_per_content_type",
                true,
            ),
        )
    }

    private fun decodeVolumes(raw: String): Map<VolumeBucket, Float> = try {
        PreferenceCodec.json.decodeFromString<Map<String, Float>>(raw)
            .mapNotNull { (bucketStr, level) ->
                val bucket = VolumeBucket.entries.firstOrNull { it.name == bucketStr }
                    ?: return@mapNotNull null
                bucket to level.coerceIn(0f, 1f)
            }
            .toMap()
    } catch (_: Exception) {
        emptyMap()
    }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    /**
     * Remembers [level] (normalized 0..1, coerced) for [bucket]. Read-modify-
     * writes the whole JSON map — four buckets make a diff-based edit
     * pointless.
     */
    suspend fun setVolume(bucket: VolumeBucket, level: Float) {
        dataStore.edit { prefs ->
            val current = decodeVolumes(prefs[Keys.VOLUME_PROFILES] ?: "{}")
            prefs[Keys.VOLUME_PROFILES] = PreferenceCodec.json.encodeToString(
                (current + (bucket to level.coerceIn(0f, 1f)))
                    .mapKeys { it.key.name },
            )
        }
    }

    /** Forgets one bucket's remembered level (factory-reset helper). */
    suspend fun clearVolume(bucket: VolumeBucket) {
        dataStore.edit { prefs ->
            val current = decodeVolumes(prefs[Keys.VOLUME_PROFILES] ?: "{}")
            prefs[Keys.VOLUME_PROFILES] = PreferenceCodec.json.encodeToString(
                (current - bucket).mapKeys { it.key.name },
            )
        }
    }

    suspend fun setRememberVolumePerContentType(enabled: Boolean) {
        dataStore.edit { it[Keys.REMEMBER_VOLUME_PER_CONTENT_TYPE] = enabled }
    }

    /**
     * Faithful inverse of [read] (backup restore path). An empty volume map
     * removes the key so storage carries no no-op blob — the
     * identity-by-absence convention (`setTrackSelectionRules` precedent).
     */
    suspend fun restore(slice: VolumeProfileSlice) {
        dataStore.edit { prefs ->
            if (slice.volumes.isEmpty()) prefs.remove(Keys.VOLUME_PROFILES)
            else prefs[Keys.VOLUME_PROFILES] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                slice.volumes.mapKeys { it.key.name },
            )
            prefs[Keys.REMEMBER_VOLUME_PER_CONTENT_TYPE] = slice.rememberVolumePerContentType
        }
    }

    /**
     * Category reset participation: both keys clear under PLAYBACK, matching
     * every other in-player preference.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        when (category) {
            PreferenceResetCategory.PLAYBACK -> listOf(
                Keys.VOLUME_PROFILES,
                Keys.REMEMBER_VOLUME_PER_CONTENT_TYPE,
            )
            else -> emptyList()
        }
}

/**
 * The volume-memory slice. `@Serializable` so it can ride the backup slice
 * machinery like every other domain slice; `@Immutable` for cheap Compose
 * stability reads at the UI edge.
 */
@Immutable
@Serializable
data class VolumeProfileSlice(
    /** Remembered normalized levels (0..1) per bucket; absent = never set. */
    val volumes: Map<VolumeBucket, Float> = emptyMap(),
    /** Master toggle — default ON where the feature is available at all. */
    val rememberVolumePerContentType: Boolean = true,
) {
    /** The remembered level for [bucket], or null when none was ever stored. */
    fun volumeFor(bucket: VolumeBucket): Float? = volumes[bucket]
}
