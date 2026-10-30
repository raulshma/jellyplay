package com.raulshma.jellyplay.core.datastore.engine

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

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
 * **Spec derivation:** every key this store owns is declared exactly once as a
 * row in [PlayerEnginePreferenceSpecs] (wire name, default, reset category,
 * read/write encoding) and the machinery below is derived from those rows —
 * each [Keys] member rebuilds its row's typed key from the row's wire name,
 * each [read] projection row delegates to its row encoding (the memoised JSON
 * decodes live in the rows now), the three config setters and the config rows
 * of [restore] delegate to the rows' derived writes, and [resetKeysFor]
 * filters the rows by reset category. The two per-item maps' rows carry the
 * documented write opt-out (encode = null): their writes are the
 * read-modify-write verbs below, each owning the clear-on-neutral/null
 * semantics plus the 100-entry LRU cap.
 *
 * **LRU-cap invariants owned here (hand-written by decision — a read-modify-
 * write with a cross-entry cap cannot be a row encoding):** the two per-item
 * maps ([setMediaStreamSelection] / [setVideoEffectsForItem]) are capped at 100
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
 *
 * **Residual (accepted):** the slice plumbing stays hand-written by the
 * no-reflection rule — adding a preference still means one
 * [PlayerEnginePreferenceSpecs] row + one [PlayerEngineSlice] property + one
 * [read] / [restore] row (and its setter) + one write-through test line. See
 * the spec KDoc.
 */
class PlayerEngineStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val json get() = PreferenceCodec.json

    /**
     * The store's DataStore keys, each derived from its
     * [PlayerEnginePreferenceSpecs] row — the member rebuilds the row's typed
     * key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key of
     * the same name). Kept as a plain object rather than folded into the rows
     * because it is the reflection anchor for the JVM reset-coverage guard and
     * the key-identity reference for the hand-written per-item verbs below.
     */
    internal object Keys {
        val MPV_CONFIG = stringPreferencesKey(PlayerEnginePreferenceSpecs.MPV_CONFIG.keyName)
        val LIBVLC_CONFIG = stringPreferencesKey(PlayerEnginePreferenceSpecs.LIBVLC_CONFIG.keyName)
        val EXO_CONFIG = stringPreferencesKey(PlayerEnginePreferenceSpecs.EXO_CONFIG.keyName)
        val MEDIA_STREAM_SELECTIONS = stringPreferencesKey(PlayerEnginePreferenceSpecs.MEDIA_STREAM_SELECTIONS.keyName)
        val VIDEO_EFFECTS_SELECTIONS = stringPreferencesKey(PlayerEnginePreferenceSpecs.VIDEO_EFFECTS_SELECTIONS.keyName)
    }

    val playerEngine: StateFlow<PlayerEngineSlice> =
        dataStore.sliceStateFlow(scope, seed = PlayerEngineSlice(), read = ::read)

    /**
     * Pure read of the engine-config fields from a raw [Preferences] snapshot,
     * each field delegated to its [PlayerEnginePreferenceSpecs] row encoding
     * (which memoises each JSON blob so an unrelated preference write does not
     * re-decode it). Exposed so the facade can fold these into the
     * whole-`UserPreferences` projection without duplicating the read logic.
     */
    internal fun read(prefs: Preferences): PlayerEngineSlice = PlayerEngineSlice(
        mpvConfig = PlayerEnginePreferenceSpecs.MPV_CONFIG.readFrom(prefs),
        libVlcConfig = PlayerEnginePreferenceSpecs.LIBVLC_CONFIG.readFrom(prefs),
        exoPlayerConfig = PlayerEnginePreferenceSpecs.EXO_CONFIG.readFrom(prefs),
        mediaStreamSelections = PlayerEnginePreferenceSpecs.MEDIA_STREAM_SELECTIONS.readFrom(prefs),
        videoEffectsByItem = PlayerEnginePreferenceSpecs.VIDEO_EFFECTS_SELECTIONS.readFrom(prefs),
    )

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
    // Setters — the three config setters delegate to their row's derived
    // write; the per-item verbs below are the documented write opt-out of
    // their rows (read-modify-write + LRU cap, never a single-slot write).
    // ------------------------------------------------------------------

    suspend fun setMpvConfig(config: MpvEngineConfig) {
        dataStore.edit { PlayerEnginePreferenceSpecs.MPV_CONFIG.writeTo(it, config) }
    }

    suspend fun setLibVlcConfig(config: LibVlcEngineConfig) {
        dataStore.edit { PlayerEnginePreferenceSpecs.LIBVLC_CONFIG.writeTo(it, config) }
    }

    suspend fun setExoPlayerConfig(config: ExoPlayerEngineConfig) {
        dataStore.edit { PlayerEnginePreferenceSpecs.EXO_CONFIG.writeTo(it, config) }
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
        setMediaStreamSelection(itemId, MediaStreamSelection(audioStreamIndex, subtitleStreamIndex))
    }

    /**
     * Full-form writer: persists the whole [MediaStreamSelection] for [itemId]
     * — indices plus the descriptor fields (label/language) the restore
     * ladder revalidates a stored index against. `null` clears the entry.
     * Callers that only need to write/clear one axis build the value from the
     * currently-stored selection so the other axis's descriptors survive.
     * Same 100-entry LRU cap as the index-form writer.
     */
    suspend fun setMediaStreamSelection(itemId: String, selection: MediaStreamSelection?) {
        dataStore.edit { prefs ->
            val current = readMediaStreamSelections(prefs).toMutableMap()
            if (selection == null || (selection.audioStreamIndex == null && selection.subtitleStreamIndex == null)) {
                current.remove(itemId)
            } else {
                current[itemId] = selection
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
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync. The two per-item maps
     * ([Keys.MEDIA_STREAM_SELECTIONS] / [Keys.VIDEO_EFFECTS_SELECTIONS]) are
     * runtime state and are deliberately excluded from category reset (they
     * live in the facade's `resetExcludedKeys`); they remain full slice fields
     * with their own setters.
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category] — the [PlayerEnginePreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. The three engine-config
     * keys this store owns descend under `PreferenceResetCategory.PLAYER_ENGINES`.
     * The two per-item maps ([Keys.MEDIA_STREAM_SELECTIONS] /
     * [Keys.VIDEO_EFFECTS_SELECTIONS]) are runtime state and deliberately
     * excluded from category reset (facade's `resetExcludedKeys`), so they
     * appear in no category list here.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        PlayerEnginePreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore. The three engine configs write through their rows' derived
     * writes (the lenient `json` encode — value-equivalent to the
     * defaults-encoding the pre-spec restore used, since the omitted defaults
     * decode back to their Kotlin defaults); the two per-item maps keep their
     * hand-written rows (the rows' documented write opt-out — with
     * neutral-entry stripping and the 100-entry [capMap] eviction folded in so
     * the bound has a single owner).
     */
    suspend fun restore(slice: PlayerEngineSlice) {
        dataStore.edit { prefs ->
            PlayerEnginePreferenceSpecs.MPV_CONFIG.writeTo(prefs, slice.mpvConfig)
            PlayerEnginePreferenceSpecs.LIBVLC_CONFIG.writeTo(prefs, slice.libVlcConfig)
            PlayerEnginePreferenceSpecs.EXO_CONFIG.writeTo(prefs, slice.exoPlayerConfig)

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
     * single owner and a restore can never grow storage past the bound.
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
 * Every default IS its [PlayerEnginePreferenceSpecs] row's declared default —
 * read off the row via a plain accessor, so a preference's default is stated
 * exactly once, in its spec, and cannot drift between the spec (what an absent
 * key reads) and the slice (what a fresh in-memory instance serves). The
 * spec-default equality is pinned field-for-field by
 * `PlayerEngineSpecDerivationTest`.
 */
@Immutable
@Serializable
data class PlayerEngineSlice(
    val mpvConfig: MpvEngineConfig = PlayerEnginePreferenceSpecs.MPV_CONFIG.default,
    val libVlcConfig: LibVlcEngineConfig = PlayerEnginePreferenceSpecs.LIBVLC_CONFIG.default,
    val exoPlayerConfig: ExoPlayerEngineConfig = PlayerEnginePreferenceSpecs.EXO_CONFIG.default,
    val mediaStreamSelections: Map<String, MediaStreamSelection> = PlayerEnginePreferenceSpecs.MEDIA_STREAM_SELECTIONS.default,
    val videoEffectsByItem: Map<String, VideoEffectsConfig> = PlayerEnginePreferenceSpecs.VIDEO_EFFECTS_SELECTIONS.default,
)
