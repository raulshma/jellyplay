package com.raulshma.jellyplay.core.datastore.audiocache

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **audio-cache policy** preference domain: whether
 * caching is on, the cache size cap, prefetch lookahead + backfill, the network
 * policy gating proactive warming, and the cellular monthly cap.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, setters, read projection, and reset-key list end-to-end. Mirrors the
 * `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Spec derivation:** every spec-declarable key this store owns is declared
 * exactly once as a row in [AudioCachePreferenceSpecs] (wire name, default,
 * reset category, read/write encoding) and the machinery below is derived
 * from those rows — each [Keys] member rebuilds its row's typed key from the
 * row's wire name, each [read] projection row delegates to its row encoding,
 * the single-key setters and the derived rows of [restore] delegate to the
 * rows' derived writes, and [resetKeysFor] filters the rows by reset
 * category. Every key this store owns resets under the single `AUDIO_CACHE`
 * category.
 *
 * **Hand-written residuals (the documented opt-outs — see the spec KDoc):**
 * the [AudioCachePreferenceSpecs.AUDIO_CACHING_ENABLED] row is the
 * declaration-only [com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec.boolean]
 * (the hand read was the plain `prefs[key] ?: true`): its setter/restore
 * lines stay hand-written (a declaration-only row has no derived write).
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class AudioCacheStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [AudioCachePreferenceSpecs] row — the member rebuilds the row's typed
     * key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key
     * of the same name). Kept as a plain object rather than folded into the
     * rows because it is the reflection anchor for the JVM reset-coverage
     * guard.
     */
    internal object Keys {
        val AUDIO_CACHING_ENABLED = booleanPreferencesKey(AudioCachePreferenceSpecs.AUDIO_CACHING_ENABLED.keyName)
        val AUDIO_CACHE_SIZE_MB = intPreferencesKey(AudioCachePreferenceSpecs.AUDIO_CACHE_SIZE_MB.keyName)
        val AUDIO_PREFETCH_LOOKAHEAD = intPreferencesKey(AudioCachePreferenceSpecs.AUDIO_PREFETCH_LOOKAHEAD.keyName)
        val AUDIO_PREFETCH_BACKFILL = intPreferencesKey(AudioCachePreferenceSpecs.AUDIO_PREFETCH_BACKFILL.keyName)
        val AUDIO_CACHE_NETWORK_POLICY =
            stringPreferencesKey(AudioCachePreferenceSpecs.AUDIO_CACHE_NETWORK_POLICY.keyName)
        val AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB =
            intPreferencesKey(AudioCachePreferenceSpecs.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB.keyName)
    }

    val audioCache: StateFlow<AudioCacheSlice> =
        dataStore.sliceStateFlow(scope, seed = AudioCacheSlice(), read = ::read)

    /**
     * Pure read of the audio-cache policy fields from a raw [Preferences]
     * snapshot, each field delegated to its [AudioCachePreferenceSpecs] row
     * encoding (the declaration-only caching-enabled row via its plain
     * [com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec.readStored]
     * — the exact hand read). Exposed so the facade can fold these into the
     * whole-`UserPreferences` projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): AudioCacheSlice = AudioCacheSlice(
        audioCachingEnabled = AudioCachePreferenceSpecs.AUDIO_CACHING_ENABLED.readStored(prefs),
        audioCacheSizeMb = AudioCachePreferenceSpecs.AUDIO_CACHE_SIZE_MB.readFrom(prefs),
        audioPrefetchLookahead = AudioCachePreferenceSpecs.AUDIO_PREFETCH_LOOKAHEAD.readFrom(prefs),
        audioPrefetchBackfill = AudioCachePreferenceSpecs.AUDIO_PREFETCH_BACKFILL.readFrom(prefs),
        audioCacheNetworkPolicy = AudioCachePreferenceSpecs.AUDIO_CACHE_NETWORK_POLICY.readFrom(prefs),
        audioCacheCellularMonthlyCapMb =
            AudioCachePreferenceSpecs.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write
    // (the encoding — enum-by-name, plain slot — is the row's, not
    // re-declared here); the declaration-only caching-enabled row keeps its
    // hand-written line.
    // ------------------------------------------------------------------

    /**
     * The declaration-only caching-enabled row (no derived write — see the
     * class KDoc's opt-outs): the hand-written plain slot write.
     */
    suspend fun setAudioCachingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_CACHING_ENABLED] = enabled }
    }

    suspend fun setAudioCacheSizeMb(sizeMb: Int) {
        dataStore.edit { AudioCachePreferenceSpecs.AUDIO_CACHE_SIZE_MB.writeTo(it, sizeMb) }
    }

    suspend fun setAudioPrefetchLookahead(lookahead: Int) {
        dataStore.edit { AudioCachePreferenceSpecs.AUDIO_PREFETCH_LOOKAHEAD.writeTo(it, lookahead) }
    }

    suspend fun setAudioPrefetchBackfill(backfill: Int) {
        dataStore.edit { AudioCachePreferenceSpecs.AUDIO_PREFETCH_BACKFILL.writeTo(it, backfill) }
    }

    suspend fun setAudioCacheNetworkPolicy(policy: AudioCacheNetworkPolicy) {
        dataStore.edit { AudioCachePreferenceSpecs.AUDIO_CACHE_NETWORK_POLICY.writeTo(it, policy) }
    }

    suspend fun setAudioCacheCellularMonthlyCapMb(capMb: Int) {
        dataStore.edit { AudioCachePreferenceSpecs.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB.writeTo(it, capMb) }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync.
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [AudioCachePreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. Every audio-cache key
     * sits in `AUDIO_CACHE`, so all other categories return an empty list.
     * The facade aggregates these lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        AudioCachePreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore — the derived rows via their row's derived write (the same
     * encoding the row reads with), the declaration-only caching-enabled row
     * via its hand-written line.
     */
    suspend fun restore(slice: AudioCacheSlice) {
        dataStore.edit { prefs ->
            prefs[Keys.AUDIO_CACHING_ENABLED] = slice.audioCachingEnabled
            AudioCachePreferenceSpecs.AUDIO_CACHE_SIZE_MB.writeTo(prefs, slice.audioCacheSizeMb)
            AudioCachePreferenceSpecs.AUDIO_PREFETCH_LOOKAHEAD.writeTo(prefs, slice.audioPrefetchLookahead)
            AudioCachePreferenceSpecs.AUDIO_PREFETCH_BACKFILL.writeTo(prefs, slice.audioPrefetchBackfill)
            AudioCachePreferenceSpecs.AUDIO_CACHE_NETWORK_POLICY.writeTo(prefs, slice.audioCacheNetworkPolicy)
            AudioCachePreferenceSpecs.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB
                .writeTo(prefs, slice.audioCacheCellularMonthlyCapMb)
        }
    }
}

/**
 * The audio-cache policy preference slice. Plain data class.
 * Every default IS its [AudioCachePreferenceSpecs] row's declared default —
 * read off the row via a plain accessor, so a preference's default is stated
 * exactly once, in its spec, and cannot drift between the spec (what an
 * absent key reads) and the slice (what a fresh in-memory instance serves).
 * The spec-default equality is pinned field-for-field by
 * `AudioCacheSpecDerivationTest`.
 */
@Immutable
@Serializable
data class AudioCacheSlice(
    val audioCachingEnabled: Boolean = AudioCachePreferenceSpecs.AUDIO_CACHING_ENABLED.default,
    val audioCacheSizeMb: Int = AudioCachePreferenceSpecs.AUDIO_CACHE_SIZE_MB.default,
    val audioPrefetchLookahead: Int = AudioCachePreferenceSpecs.AUDIO_PREFETCH_LOOKAHEAD.default,
    val audioPrefetchBackfill: Int = AudioCachePreferenceSpecs.AUDIO_PREFETCH_BACKFILL.default,
    val audioCacheNetworkPolicy: AudioCacheNetworkPolicy =
        AudioCachePreferenceSpecs.AUDIO_CACHE_NETWORK_POLICY.default,
    val audioCacheCellularMonthlyCapMb: Int =
        AudioCachePreferenceSpecs.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB.default,
)
