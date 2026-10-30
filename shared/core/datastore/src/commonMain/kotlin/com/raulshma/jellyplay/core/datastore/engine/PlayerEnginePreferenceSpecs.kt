package com.raulshma.jellyplay.core.datastore.engine

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.ExoPlayerEngineConfig
import com.raulshma.jellyplay.core.model.LibVlcEngineConfig
import com.raulshma.jellyplay.core.model.MediaStreamSelection
import com.raulshma.jellyplay.core.model.MpvEngineConfig
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.VideoEffectsConfig
import kotlinx.serialization.encodeToString

/**
 * Memoisation holders for the five JSON blob rows' decodes, keyed on the raw
 * string so a decode is skipped when its key has not changed on a given
 * `dataStore.data` emission. Moved here from per-`PlayerEngineStore` fields
 * with the rows they belong to (the playback domain's passthrough-codec cache
 * precedent): each decode is a pure function of its raw string, and a null raw
 * memoises its result exactly once ([CachedJsonNullPolicy.MemoizeNull] — this
 * store's pre-promotion policy at every site). See [PreferenceCodec.cachedJson].
 */
private var cachedMpvConfig: ParsedCache<MpvEngineConfig> = ParsedCache(null, MpvEngineConfig())
private var cachedLibVlcConfig: ParsedCache<LibVlcEngineConfig> = ParsedCache(null, LibVlcEngineConfig())
private var cachedExoPlayerConfig: ParsedCache<ExoPlayerEngineConfig> = ParsedCache(null, ExoPlayerEngineConfig())
private var cachedMediaStreamSelections: ParsedCache<Map<String, MediaStreamSelection>> = ParsedCache(null, emptyMap())
private var cachedVideoEffectsByItem: ParsedCache<Map<String, VideoEffectsConfig>> = ParsedCache(null, emptyMap())

/**
 * The player-engine configuration domain's single preference declaration: one
 * [PreferenceSpec] row per key `PlayerEngineStore` owns — the canonical
 * persisted key name (which is ALSO the legacy pre-typed-era wire name,
 * declared exactly once), the default, the reset category, and the read/write
 * encoding ([PreferenceSpec.derived] — this domain is five JSON blobs).
 *
 * `PlayerEngineStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates
 * each slice field to its row encoding, the three config setters and the
 * config rows of `restore` delegate to the rows' derived writes, and
 * `resetKeysFor` filters the rows by reset category — so a row cannot drift
 * from the machinery that reads or writes it. The three engine-config blobs
 * reset under `PLAYER_ENGINES`; the two per-item recall maps are runtime state
 * and carry no category (they appear in no reset list, exactly as the
 * pre-spec store excluded them — the facade's `resetExcludedKeys` ledger).
 *
 * **The documented opt-outs (encode = null):** the two per-item recall maps'
 * writes are never single-slot — every write site is a read-modify-write verb
 * carrying the clear-on-neutral/null semantics plus the 100-entry LRU cap
 * ([PlayerEngineStore.setMediaStreamSelection] / [PlayerEngineStore.setVideoEffectsForItem],
 * and `restore`/`restorePerItemMaps` fold the same strip + cap in) — a
 * cross-entry invariant a row encoding cannot own. The rows still declare the
 * wire name, default and read so the read projection and reset ledger derive;
 * the store keeps its hand-written write lines.
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [PlayerEngineSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists and
 * slice defaults, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object PlayerEnginePreferenceSpecs {

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [PlayerEngineSlice] property order. A row is constructed exactly once,
    // here; [all], the reset derivation and [searchEntries] all derive from
    // the groups.
    // ------------------------------------------------------------------

    /** The three JSON-encoded engine configs (`PLAYER_ENGINES` reset). */
    private val engineConfigs: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.derived(
            keyName = "mpv_config",
            default = MpvEngineConfig(),
            resetCategory = PreferenceResetCategory.PLAYER_ENGINES,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedMpvConfig,
                    default = MpvEngineConfig(),
                    parse = { PreferenceCodec.json.decodeFromString<MpvEngineConfig>(it) },
                    cacheRef = { cachedMpvConfig = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
        PreferenceSpec.derived(
            keyName = "libvlc_config",
            default = LibVlcEngineConfig(),
            resetCategory = PreferenceResetCategory.PLAYER_ENGINES,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedLibVlcConfig,
                    default = LibVlcEngineConfig(),
                    parse = { PreferenceCodec.json.decodeFromString<LibVlcEngineConfig>(it) },
                    cacheRef = { cachedLibVlcConfig = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
        PreferenceSpec.derived(
            keyName = "exo_config",
            default = ExoPlayerEngineConfig(),
            resetCategory = PreferenceResetCategory.PLAYER_ENGINES,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedExoPlayerConfig,
                    default = ExoPlayerEngineConfig(),
                    parse = { PreferenceCodec.json.decodeFromString<ExoPlayerEngineConfig>(it) },
                    cacheRef = { cachedExoPlayerConfig = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
    )

    /**
     * The two per-item recall maps — runtime state, deliberately outside every
     * reset category. JSON blobs with the documented write opt-out (see the
     * object KDoc): the rows own the wire name, default and read; the store's
     * read-modify-write verbs own the writes.
     */
    private val perItemMaps: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.derived(
            keyName = "media_stream_selections",
            default = emptyMap<String, MediaStreamSelection>(),
            resetCategory = null,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedMediaStreamSelections,
                    default = emptyMap(),
                    parse = { PreferenceCodec.json.decodeFromString<Map<String, MediaStreamSelection>>(it) },
                    cacheRef = { cachedMediaStreamSelections = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
        ),
        PreferenceSpec.derived(
            keyName = "video_effects_selections",
            default = emptyMap<String, VideoEffectsConfig>(),
            resetCategory = null,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedVideoEffectsByItem,
                    default = emptyMap(),
                    parse = { PreferenceCodec.json.decodeFromString<Map<String, VideoEffectsConfig>>(it) },
                    cacheRef = { cachedVideoEffectsByItem = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [PlayerEngineSlice] property order. Nothing re-lists the rows:
     * a row is registered exactly once, in its group, and appears here (and in
     * the reset derivation) automatically. The derivation-integrity test
     * iterates this — a group entry cannot escape it.
     */
    val all: List<PreferenceSpec<*>> =
        engineConfigs +
            perItemMaps

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — the three engine-config
     * blobs sit in `PLAYER_ENGINES`, the two per-item maps in none. The facade
     * aggregates these lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a search entry. All five rows are JSON blobs whose user faces
     * are the mpv/libVLC/ExoPlayer config screens' individual knobs (and the
     * per-item state is not a setting at all), so this domain declares none —
     * the search faces stay the feature-side residuals they always were.
     */
    val searchEntries: List<PreferenceSearchSpec> = emptyList()

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's Keys.
    // ------------------------------------------------------------------

    val MPV_CONFIG: PreferenceSpec<MpvEngineConfig> get() = row("mpv_config")

    val LIBVLC_CONFIG: PreferenceSpec<LibVlcEngineConfig> get() = row("libvlc_config")

    val EXO_CONFIG: PreferenceSpec<ExoPlayerEngineConfig> get() = row("exo_config")

    val MEDIA_STREAM_SELECTIONS: PreferenceSpec<Map<String, MediaStreamSelection>> get() = row("media_stream_selections")

    val VIDEO_EFFECTS_SELECTIONS: PreferenceSpec<Map<String, VideoEffectsConfig>> get() = row("video_effects_selections")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [PlayerEngineStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "PlayerEnginePreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
