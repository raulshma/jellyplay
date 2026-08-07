package com.raulshma.jellyplay.core.datastore.engine

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.di.ApplicationScope
import com.raulshma.jellyplay.core.datastore.di.UserPreferencesDataStore
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deep module owning the **player-engine configuration** preference domain:
 * the three JSON-encoded engine configs (mpv / libVLC / ExoPlayer) plus the two
 * per-item recall maps (media-stream selections and video-effects overrides).
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters (including the LRU-cap invariants below), its read
 * projection (with [ParsedCache] memoisation of the JSON blobs), and its
 * reset-key list end-to-end. Mirrors the `PlaybackStore` / `AppearanceStore`
 * shape.
 *
 * **LRU-cap invariants owned here:** the two per-item maps
 * ([setMediaStreamSelection] / [setVideoEffectsForItem]) are capped at 100
 * entries — when an insert pushes the size over 100, the oldest entries
 * (insertion order) are evicted in the same atomic edit so the JSON blob never
 * grows unbounded. Which player is *selected* by default is owned by
 * `PlaybackStore` (setPreferredPlayer) — not here.
 *
 * **Reset scope:** the three engine-config keys reset under
 * `PreferenceResetCategory.PLAYER_ENGINES`; the two per-item maps are runtime
 * state (see the facade's `resetExcludedKeys`) so they are *not* in
 * [resetKeys], but they are full slice fields with setters.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore; key strings match the
 * legacy `UserPreferencesStore.Keys` names — no migration file.
 */
@Singleton
class PlayerEngineStore @Inject constructor(
    @UserPreferencesDataStore private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val json get() = PreferenceCodec.json

    internal object Keys {
        val MPV_CONFIG = stringPreferencesKey("mpv_config")
        val LIBVLC_CONFIG = stringPreferencesKey("libvlc_config")
        val EXO_CONFIG = stringPreferencesKey("exo_config")
        val MEDIA_STREAM_SELECTIONS = stringPreferencesKey("media_stream_selections")
        val VIDEO_EFFECTS_SELECTIONS = stringPreferencesKey("video_effects_selections")
    }

    private val sharedPrefs: Flow<Preferences> = dataStore.data
        .catch { _ -> androidx.datastore.preferences.core.emptyPreferences() }

    // Memoisation holders for the JSON-decoded engine-config blobs, keyed on the
    // raw string so the decode is skipped when the underlying key has not
    // changed on a given `dataStore.data` emission.
    private var cachedMpvConfig: ParsedCache<MpvEngineConfig> = ParsedCache(null, MpvEngineConfig())
    private var cachedLibVlcConfig: ParsedCache<LibVlcEngineConfig> = ParsedCache(null, LibVlcEngineConfig())
    private var cachedExoPlayerConfig: ParsedCache<ExoPlayerEngineConfig> = ParsedCache(null, ExoPlayerEngineConfig())
    private var cachedMediaStreamSelections: ParsedCache<Map<String, MediaStreamSelection>> = ParsedCache(null, emptyMap())
    private var cachedVideoEffectsByItem: ParsedCache<Map<String, VideoEffectsConfig>> = ParsedCache(null, emptyMap())

    val playerEngine: StateFlow<PlayerEngineSlice> = sharedPrefs
        .map { read(it) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, PlayerEngineSlice())

    /**
     * Pure read of the engine-config fields from a raw [Preferences] snapshot,
     * memoising each JSON blob via [ParsedCache] so an unrelated preference
     * write does not re-decode them. Exposed so the facade can fold these into
     * the whole-`UserPreferences` projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): PlayerEngineSlice {
        val mpvConfigRaw = prefs[Keys.MPV_CONFIG]
        val mpvConfig = if (mpvConfigRaw != cachedMpvConfig.raw) {
            try {
                mpvConfigRaw?.let { json.decodeFromString<MpvEngineConfig>(it) } ?: MpvEngineConfig()
            } catch (_: Exception) { MpvEngineConfig() }.also { cachedMpvConfig = ParsedCache(mpvConfigRaw, it) }
        } else cachedMpvConfig.value

        val libVlcConfigRaw = prefs[Keys.LIBVLC_CONFIG]
        val libVlcConfig = if (libVlcConfigRaw != cachedLibVlcConfig.raw) {
            try {
                libVlcConfigRaw?.let { json.decodeFromString<LibVlcEngineConfig>(it) } ?: LibVlcEngineConfig()
            } catch (_: Exception) { LibVlcEngineConfig() }.also { cachedLibVlcConfig = ParsedCache(libVlcConfigRaw, it) }
        } else cachedLibVlcConfig.value

        val exoPlayerConfigRaw = prefs[Keys.EXO_CONFIG]
        val exoPlayerConfig = if (exoPlayerConfigRaw != cachedExoPlayerConfig.raw) {
            try {
                exoPlayerConfigRaw?.let { json.decodeFromString<ExoPlayerEngineConfig>(it) } ?: ExoPlayerEngineConfig()
            } catch (_: Exception) { ExoPlayerEngineConfig() }.also { cachedExoPlayerConfig = ParsedCache(exoPlayerConfigRaw, it) }
        } else cachedExoPlayerConfig.value

        val mediaStreamSelectionsRaw = prefs[Keys.MEDIA_STREAM_SELECTIONS]
        val mediaStreamSelections = if (mediaStreamSelectionsRaw != cachedMediaStreamSelections.raw) {
            readMediaStreamSelections(prefs)
                .also { cachedMediaStreamSelections = ParsedCache(mediaStreamSelectionsRaw, it) }
        } else cachedMediaStreamSelections.value

        val videoEffectsByItemRaw = prefs[Keys.VIDEO_EFFECTS_SELECTIONS]
        val videoEffectsByItem = if (videoEffectsByItemRaw != cachedVideoEffectsByItem.raw) {
            readVideoEffectsByItem(prefs)
                .also { cachedVideoEffectsByItem = ParsedCache(videoEffectsByItemRaw, it) }
        } else cachedVideoEffectsByItem.value

        return PlayerEngineSlice(
            mpvConfig = mpvConfig,
            libVlcConfig = libVlcConfig,
            exoPlayerConfig = exoPlayerConfig,
            mediaStreamSelections = mediaStreamSelections,
            videoEffectsByItem = videoEffectsByItem,
        )
    }

    private fun readMediaStreamSelections(prefs: Preferences): Map<String, MediaStreamSelection> {
        val raw = prefs[Keys.MEDIA_STREAM_SELECTIONS] ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, MediaStreamSelection>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun readVideoEffectsByItem(prefs: Preferences): Map<String, VideoEffectsConfig> {
        val raw = prefs[Keys.VIDEO_EFFECTS_SELECTIONS] ?: return emptyMap()
        return try {
            json.decodeFromString<Map<String, VideoEffectsConfig>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setMpvConfig(config: MpvEngineConfig) {
        dataStore.edit { it[Keys.MPV_CONFIG] = json.encodeToString(config) }
    }

    suspend fun setLibVlcConfig(config: LibVlcEngineConfig) {
        dataStore.edit { it[Keys.LIBVLC_CONFIG] = json.encodeToString(config) }
    }

    suspend fun setExoPlayerConfig(config: ExoPlayerEngineConfig) {
        dataStore.edit { it[Keys.EXO_CONFIG] = json.encodeToString(config) }
    }

    /**
     * Persist the per-item audio/subtitle selection for [itemId]. Passing both
     * stream indices `null` clears the entry. The map is capped at 100 entries
     * (LRU by insertion order): on overflow the oldest entries are evicted in
     * the same atomic edit so the JSON blob never grows unbounded.
     */
    suspend fun setMediaStreamSelection(
        itemId: String,
        audioStreamIndex: Int? = null,
        subtitleStreamIndex: Int? = null,
    ) {
        dataStore.edit { prefs ->
            val current = readMediaStreamSelections(prefs).toMutableMap()
            if (audioStreamIndex == null && subtitleStreamIndex == null) {
                current.remove(itemId)
            } else {
                current[itemId] = MediaStreamSelection(
                    audioStreamIndex = audioStreamIndex,
                    subtitleStreamIndex = subtitleStreamIndex,
                )
            }
            if (current.size > 100) {
                val excess = current.size - 100
                current.keys.take(excess).forEach { current.remove(it) }
            }
            prefs[Keys.MEDIA_STREAM_SELECTIONS] = json.encodeToString(current)
        }
    }

    /**
     * Persist the per-item video filter settings for [itemId]. Passing a neutral
     * config (all defaults) clears the entry so storage does not grow
     * unbounded. Matches the [setMediaStreamSelection] 100-entry LRU cap.
     */
    suspend fun setVideoEffectsForItem(itemId: String, effects: VideoEffectsConfig) {
        dataStore.edit { prefs ->
            val current = readVideoEffectsByItem(prefs).toMutableMap()
            if (effects.isNeutral) {
                current.remove(itemId)
            } else {
                current[itemId] = effects
            }
            // Match the MediaStreamSelection LRU cap so per-item state stays bounded.
            if (current.size > 100) {
                val excess = current.size - 100
                current.keys.take(excess).forEach { current.remove(it) }
            }
            prefs[Keys.VIDEO_EFFECTS_SELECTIONS] = json.encodeToString(current)
        }
    }

    /**
     * Keys owned by this store that reset under
     * `PreferenceResetCategory.PLAYER_ENGINES`. The two per-item maps
     * ([Keys.MEDIA_STREAM_SELECTIONS] / [Keys.VIDEO_EFFECTS_SELECTIONS]) are
     * runtime state and are deliberately excluded from category reset (they
     * live in the facade's `resetExcludedKeys`); they remain full slice fields
     * with their own setters.
     */
    internal val resetKeys: List<Preferences.Key<*>> = listOf(
        Keys.MPV_CONFIG, Keys.LIBVLC_CONFIG, Keys.EXO_CONFIG,
    )

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. Every engine-config key this store owns descends under
     * `PreferenceResetCategory.PLAYER_ENGINES`. The two per-item maps
     * ([Keys.MEDIA_STREAM_SELECTIONS] / [Keys.VIDEO_EFFECTS_SELECTIONS]) are
     * runtime state and deliberately excluded from category reset (facade's
     * `resetExcludedKeys`), so they appear in no category list here.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.PLAYER_ENGINES -> listOf(
            Keys.MPV_CONFIG, Keys.LIBVLC_CONFIG, Keys.EXO_CONFIG,
        )
        else -> emptyList()
    }

    /**
     * Restore-backup participation: writes the three JSON-encoded engine
     * configs owned by this store from a decoded [UserPreferences], mirroring
     * the facade's `encodeDefaultsJson` round-trips exactly. The per-item
     * recall maps are runtime state and are not restored here (facade rule).
     */
    internal suspend fun restorePreferences(
        userPreferences: com.raulshma.jellyplay.core.model.legacy.UserPreferences,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.MPV_CONFIG] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<MpvEngineConfig>(),
                userPreferences.mpvConfig,
            )
            prefs[Keys.LIBVLC_CONFIG] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<LibVlcEngineConfig>(),
                userPreferences.libVlcConfig,
            )
            prefs[Keys.EXO_CONFIG] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<ExoPlayerEngineConfig>(),
                userPreferences.exoPlayerConfig,
            )
        }
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore. The three engine configs use the `encodeDefaultsJson` +
     * serializer round-trip from [restorePreferences]; the two per-item maps use
     * the plain `json` round-trip from [restorePerItemMaps] (with neutral-entry
     * stripping and the 100-entry [capMap] eviction folded in so the bound has a
     * single owner).
     */
    suspend fun restore(slice: PlayerEngineSlice) {
        dataStore.edit { prefs ->
            prefs[Keys.MPV_CONFIG] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<MpvEngineConfig>(),
                slice.mpvConfig,
            )
            prefs[Keys.LIBVLC_CONFIG] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<LibVlcEngineConfig>(),
                slice.libVlcConfig,
            )
            prefs[Keys.EXO_CONFIG] = PreferenceCodec.encodeDefaultsJson.encodeToString(
                kotlinx.serialization.serializer<ExoPlayerEngineConfig>(),
                slice.exoPlayerConfig,
            )

            val streams = slice.mediaStreamSelections
                .filter { (_, v) -> v.audioStreamIndex != null || v.subtitleStreamIndex != null }
                .let { capMap(it) }
            prefs[Keys.MEDIA_STREAM_SELECTIONS] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, MediaStreamSelection>>(), streams,
            )

            val effects = slice.videoEffectsByItem
                .filter { (_, v) -> !v.isNeutral }
                .let { capMap(it) }
            prefs[Keys.VIDEO_EFFECTS_SELECTIONS] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, VideoEffectsConfig>>(), effects,
            )
        }
    }

    /**
     * Bulk-restores the two per-item recall maps from a decoded backup,
     * applying the same neutral-entry stripping and 100-entry LRU eviction as
     * [setMediaStreamSelection] / [setVideoEffectsForItem] so the cap has a
     * single owner. Previously the facade wrote these blobs directly, which
     * bypassed the bound and let a restore grow storage unbounded.
     *
     * `MediaStreamSelection` entries with both indices `null` and neutral
     * `VideoEffectsConfig` entries are dropped, matching the per-item setters'
     * clear-on-neutral semantics. Eviction preserves the most-recently-written
     * entries (map insertion order), matching the per-item eviction.
     */
    internal suspend fun restorePerItemMaps(
        mediaStreamSelections: Map<String, MediaStreamSelection>,
        videoEffectsByItem: Map<String, VideoEffectsConfig>,
    ) {
        dataStore.edit { prefs ->
            val streams = mediaStreamSelections
                .filter { (_, v) -> v.audioStreamIndex != null || v.subtitleStreamIndex != null }
                .let { capMap(it) }
            prefs[Keys.MEDIA_STREAM_SELECTIONS] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, MediaStreamSelection>>(), streams,
            )

            val effects = videoEffectsByItem
                .filter { (_, v) -> !v.isNeutral }
                .let { capMap(it) }
            prefs[Keys.VIDEO_EFFECTS_SELECTIONS] = json.encodeToString(
                kotlinx.serialization.serializer<Map<String, VideoEffectsConfig>>(), effects,
            )
        }
    }

    /**
     * Evicts the oldest entries beyond the 100-entry cap, mirroring the
     * `current.size > 100` eviction in the per-item setters (map iteration is
     * insertion-ordered in Kotlin's `LinkedHashMap`, so `entries.drop(n)`
     * removes the n oldest keys, matching `current.keys.take(excess)` eviction).
     */
    private fun <K, V> capMap(map: Map<K, V>): Map<K, V> {
        if (map.size <= 100) return map
        val keep = map.entries.drop(map.size - 100)
        return LinkedHashMap<K, V>(keep.size).apply {
            keep.forEach { put(it.key, it.value) }
        }
    }
}

/**
 * The player-engine configuration preference slice. Plain data class.
 * Defaults mirror the projection defaults in [PlayerEngineStore.read].
 */
@Immutable
@Serializable
data class PlayerEngineSlice(
    val mpvConfig: MpvEngineConfig = MpvEngineConfig(),
    val libVlcConfig: LibVlcEngineConfig = LibVlcEngineConfig(),
    val exoPlayerConfig: ExoPlayerEngineConfig = ExoPlayerEngineConfig(),
    val mediaStreamSelections: Map<String, MediaStreamSelection> = emptyMap(),
    val videoEffectsByItem: Map<String, VideoEffectsConfig> = emptyMap(),
)
