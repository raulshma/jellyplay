package com.raulshma.jellyplay.core.datastore.volume

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
 *
 * **Spec derivation:** every key this store owns is declared exactly once as a
 * row in [VolumeProfilePreferenceSpecs] (wire name, default, reset category,
 * read/write encoding) and the machinery below is derived from those rows —
 * each [Keys] member rebuilds its row's typed key from the row's wire name,
 * each [read] projection row delegates to its row encoding (the blob's decode
 * lives in the [VolumeProfilePreferenceSpecs.VOLUME_PROFILES] row's read,
 * memoised beside the row), the toggle's single-key setter delegates to its
 * row's derived write, and [resetKeysFor] filters the rows by reset category.
 *
 * **Hand-written residuals (the documented opt-outs — see the specs object
 * KDoc):** [setVolume] / [clearVolume] are read-modify-writes of the whole
 * JSON map (four buckets make a diff-based edit pointless) and [restore]
 * removes the blob key when the restored map is empty (the identity-by-absence
 * convention) — a read-modify-write or a removal cannot be a row encoding.
 */
class VolumeProfileStore constructor(
    private val dataStore: DataStore<Preferences>,
    externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [VolumeProfilePreferenceSpecs] row — the member rebuilds the row's typed
     * key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key of
     * the same name). Kept as a plain object rather than folded into the rows
     * because it is the reflection anchor for the JVM reset-coverage guard.
     */
    internal object Keys {
        val VOLUME_PROFILES = stringPreferencesKey(VolumeProfilePreferenceSpecs.VOLUME_PROFILES.keyName)
        val REMEMBER_VOLUME_PER_CONTENT_TYPE = booleanPreferencesKey(VolumeProfilePreferenceSpecs.REMEMBER_VOLUME_PER_CONTENT_TYPE.keyName)
    }

    /** The volume-memory slice, derived directly from the raw DataStore. */
    val volumeProfile: StateFlow<VolumeProfileSlice> =
        dataStore.sliceStateFlow(scope, seed = VolumeProfileSlice(), read = ::read)

    /**
     * Pure read of the volume-memory fields from a raw [Preferences]
     * snapshot, each field delegated to its [VolumeProfilePreferenceSpecs] row
     * encoding.
     */
    internal fun read(prefs: Preferences): VolumeProfileSlice = VolumeProfileSlice(
        volumes = VolumeProfilePreferenceSpecs.VOLUME_PROFILES.readFrom(prefs),
        rememberVolumePerContentType = VolumeProfilePreferenceSpecs.REMEMBER_VOLUME_PER_CONTENT_TYPE.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — the blob's read-modify-write verbs stay hand-written (the
    // documented opt-out); they re-derive the current map through the row's
    // own read. The toggle delegates to its row's derived write.
    // ------------------------------------------------------------------

    /**
     * Remembers [level] (normalized 0..1, coerced) for [bucket]. Read-modify-
     * writes the whole JSON map — four buckets make a diff-based edit
     * pointless.
     */
    suspend fun setVolume(bucket: VolumeBucket, level: Float) {
        dataStore.edit { prefs ->
            val current = VolumeProfilePreferenceSpecs.VOLUME_PROFILES.readFrom(prefs)
            prefs[Keys.VOLUME_PROFILES] = PreferenceCodec.json.encodeToString(
                (current + (bucket to level.coerceIn(0f, 1f)))
                    .mapKeys { it.key.name },
            )
        }
    }

    /** Forgets one bucket's remembered level (factory-reset helper). */
    suspend fun clearVolume(bucket: VolumeBucket) {
        dataStore.edit { prefs ->
            val current = VolumeProfilePreferenceSpecs.VOLUME_PROFILES.readFrom(prefs)
            prefs[Keys.VOLUME_PROFILES] = PreferenceCodec.json.encodeToString(
                (current - bucket).mapKeys { it.key.name },
            )
        }
    }

    suspend fun setRememberVolumePerContentType(enabled: Boolean) {
        dataStore.edit { VolumeProfilePreferenceSpecs.REMEMBER_VOLUME_PER_CONTENT_TYPE.writeTo(it, enabled) }
    }

    /**
     * Faithful inverse of [read] (backup restore path). An empty volume map
     * removes the key so storage carries no no-op blob — the
     * identity-by-absence convention (`setTrackSelectionRules` precedent).
     * Hand-written: the conditional removal cannot be a row encoding.
     */
    suspend fun restore(slice: VolumeProfileSlice) {
        dataStore.edit { prefs ->
            if (slice.volumes.isEmpty()) prefs.remove(Keys.VOLUME_PROFILES)
            else prefs[Keys.VOLUME_PROFILES] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                slice.volumes.mapKeys { it.key.name },
            )
            VolumeProfilePreferenceSpecs.REMEMBER_VOLUME_PER_CONTENT_TYPE.writeTo(prefs, slice.rememberVolumePerContentType)
        }
    }

    /**
     * Category reset participation: the [VolumeProfilePreferenceSpecs] rows
     * whose declared reset category matches — both keys clear under PLAYBACK,
     * matching every other in-player preference.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        VolumeProfilePreferenceSpecs.resetKeysFor(category)
}

/**
 * The volume-memory slice. `@Serializable` so it can ride the backup slice
 * machinery like every other domain slice; `@Immutable` for cheap Compose
 * stability reads at the UI edge. Every default IS its
 * [VolumeProfilePreferenceSpecs] row's declared default — read off the row via
 * a plain accessor, so a preference's default is stated exactly once, in its
 * spec, and cannot drift between the spec (what an absent key reads) and the
 * slice (what a fresh in-memory instance serves). The spec-default equality is
 * pinned field-for-field by `VolumeProfileSpecDerivationTest`.
 */
@Immutable
@Serializable
data class VolumeProfileSlice(
    /** Remembered normalized levels (0..1) per bucket; absent = never set. */
    val volumes: Map<VolumeBucket, Float> = VolumeProfilePreferenceSpecs.VOLUME_PROFILES.default,
    /** Master toggle — default ON where the feature is available at all. */
    val rememberVolumePerContentType: Boolean = VolumeProfilePreferenceSpecs.REMEMBER_VOLUME_PER_CONTENT_TYPE.default,
) {
    /** The remembered level for [bucket], or null when none was ever stored. */
    fun volumeFor(bucket: VolumeBucket): Float? = volumes[bucket]
}
