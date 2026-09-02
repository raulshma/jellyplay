package com.raulshma.jellyplay.core.datastore.audiocache

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
 * These keys were never string-typed in the legacy store, so the reads below
 * use plain `prefs[key] ?: default` rather than the `PreferenceCodec` legacy
 * helpers — no dual-read fallback is needed.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class AudioCacheStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    internal object Keys {
        val AUDIO_CACHING_ENABLED = booleanPreferencesKey("audio_caching_enabled")
        val AUDIO_CACHE_SIZE_MB = intPreferencesKey("audio_cache_size_mb")
        val AUDIO_PREFETCH_LOOKAHEAD = intPreferencesKey("audio_prefetch_lookahead")
        val AUDIO_PREFETCH_BACKFILL = intPreferencesKey("audio_prefetch_backfill")
        val AUDIO_CACHE_NETWORK_POLICY = stringPreferencesKey("audio_cache_network_policy")
        val AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB = intPreferencesKey("audio_cache_cellular_monthly_cap_mb")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> emptyPreferences() }

    val audioCache: StateFlow<AudioCacheSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, AudioCacheSlice())

    internal fun read(prefs: Preferences): AudioCacheSlice = AudioCacheSlice(
        audioCachingEnabled = prefs[Keys.AUDIO_CACHING_ENABLED] ?: true,
        audioCacheSizeMb = prefs[Keys.AUDIO_CACHE_SIZE_MB] ?: 1024,
        audioPrefetchLookahead = prefs[Keys.AUDIO_PREFETCH_LOOKAHEAD] ?: 3,
        audioPrefetchBackfill = prefs[Keys.AUDIO_PREFETCH_BACKFILL] ?: 5,
        audioCacheNetworkPolicy = try {
            AudioCacheNetworkPolicy.valueOf(
                prefs[Keys.AUDIO_CACHE_NETWORK_POLICY] ?: AudioCacheNetworkPolicy.DEFAULT.name
            )
        } catch (_: Exception) {
            AudioCacheNetworkPolicy.DEFAULT
        },
        audioCacheCellularMonthlyCapMb = prefs[Keys.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB] ?: 500,
    )

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setAudioCachingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUDIO_CACHING_ENABLED] = enabled }
    }

    suspend fun setAudioCacheSizeMb(sizeMb: Int) {
        dataStore.edit { it[Keys.AUDIO_CACHE_SIZE_MB] = sizeMb }
    }

    suspend fun setAudioPrefetchLookahead(lookahead: Int) {
        dataStore.edit { it[Keys.AUDIO_PREFETCH_LOOKAHEAD] = lookahead }
    }

    suspend fun setAudioPrefetchBackfill(backfill: Int) {
        dataStore.edit { it[Keys.AUDIO_PREFETCH_BACKFILL] = backfill }
    }

    suspend fun setAudioCacheNetworkPolicy(policy: AudioCacheNetworkPolicy) {
        dataStore.edit { it[Keys.AUDIO_CACHE_NETWORK_POLICY] = policy.name }
    }

    suspend fun setAudioCacheCellularMonthlyCapMb(capMb: Int) {
        dataStore.edit { it[Keys.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB] = capMb }
    }

    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.AUDIO_CACHING_ENABLED, Keys.AUDIO_CACHE_SIZE_MB,
        Keys.AUDIO_PREFETCH_LOOKAHEAD, Keys.AUDIO_PREFETCH_BACKFILL,
        Keys.AUDIO_CACHE_NETWORK_POLICY, Keys.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB,
    )

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. Every audio-cache key sits in `AUDIO_CACHE`, so all other
     * categories return an empty list. The facade aggregates these lists
     * instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.AUDIO_CACHE -> listOf(
            Keys.AUDIO_CACHING_ENABLED, Keys.AUDIO_CACHE_SIZE_MB,
            Keys.AUDIO_PREFETCH_LOOKAHEAD, Keys.AUDIO_PREFETCH_BACKFILL,
            Keys.AUDIO_CACHE_NETWORK_POLICY, Keys.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the audio-cache keys owned by this
     * store from a decoded [UserPreferences]. The facade calls this (and every
     * other store's hook) instead of writing these keys itself.
     *
     * Mirrors the legacy facade behaviour exactly.
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { it ->
            it[Keys.AUDIO_CACHING_ENABLED] = userPreferences.audioCachingEnabled
            it[Keys.AUDIO_CACHE_SIZE_MB] = userPreferences.audioCacheSizeMb
            it[Keys.AUDIO_PREFETCH_LOOKAHEAD] = userPreferences.audioPrefetchLookahead
            it[Keys.AUDIO_PREFETCH_BACKFILL] = userPreferences.audioPrefetchBackfill
            it[Keys.AUDIO_CACHE_NETWORK_POLICY] = userPreferences.audioCacheNetworkPolicy.name
            it[Keys.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB] = userPreferences.audioCacheCellularMonthlyCapMb
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences].
     */
    suspend fun restore(slice: AudioCacheSlice) {
        dataStore.edit { it ->
            it[Keys.AUDIO_CACHING_ENABLED] = slice.audioCachingEnabled
            it[Keys.AUDIO_CACHE_SIZE_MB] = slice.audioCacheSizeMb
            it[Keys.AUDIO_PREFETCH_LOOKAHEAD] = slice.audioPrefetchLookahead
            it[Keys.AUDIO_PREFETCH_BACKFILL] = slice.audioPrefetchBackfill
            it[Keys.AUDIO_CACHE_NETWORK_POLICY] = slice.audioCacheNetworkPolicy.name
            it[Keys.AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB] = slice.audioCacheCellularMonthlyCapMb
        }
    }
}

/**
 * The audio-cache policy preference slice. Plain data class.
 * Defaults mirror the projection defaults in [AudioCacheStore.read].
 */
@Immutable
@Serializable
data class AudioCacheSlice(
    val audioCachingEnabled: Boolean = true,
    val audioCacheSizeMb: Int = 1024,
    val audioPrefetchLookahead: Int = 3,
    val audioPrefetchBackfill: Int = 5,
    val audioCacheNetworkPolicy: AudioCacheNetworkPolicy = AudioCacheNetworkPolicy.DEFAULT,
    val audioCacheCellularMonthlyCapMb: Int = 500,
)
