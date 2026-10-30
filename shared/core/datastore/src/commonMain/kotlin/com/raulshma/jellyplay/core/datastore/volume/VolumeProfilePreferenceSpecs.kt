package com.raulshma.jellyplay.core.datastore.volume

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.playback.PlaybackPreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferencePlatformRule
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.VolumeBucket

/**
 * Memoisation holder for the [VolumeProfilePreferenceSpecs.VOLUME_PROFILES]
 * row's JSON decode, keyed on the raw string so the decode is skipped when the
 * key has not changed on a given `dataStore.data` emission. Moved here from a
 * per-`VolumeProfileStore` field with the row it belongs to (the playback
 * domain's passthrough-codec cache precedent): the decode is a pure function
 * of the raw string, and a null raw memoises its result exactly once
 * ([CachedJsonNullPolicy.MemoizeNull] — this store's pre-promotion policy).
 * See [PreferenceCodec.cachedJson].
 */
private var cachedVolumeProfiles: ParsedCache<Map<VolumeBucket, Float>> =
    ParsedCache(null, emptyMap())

/**
 * Lenient decode of the remembered-levels blob: unknown bucket names (a newer
 * client's content types) are dropped, out-of-range levels are coerced into
 * 0..1, and a corrupt blob degrades to no remembered levels rather than
 * throwing.
 */
private fun decodeVolumeProfiles(raw: String): Map<VolumeBucket, Float> = try {
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

/**
 * The volume-memory domain's single preference declaration: one
 * [PreferenceSpec] row per key `VolumeProfileStore` owns — the canonical
 * persisted key name (which is ALSO the legacy pre-typed-era wire name,
 * declared exactly once), the default, the reset category, and the read/write
 * encoding ([PreferenceSpec.plainBoolean] / [derived]).
 *
 * `VolumeProfileStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates
 * each slice field to its row encoding, the toggle's single-key setter
 * delegates to its row's derived write, and [resetKeysFor] filters the rows by
 * reset category — every key this store owns resets under the single
 * `PLAYBACK` bucket, matching every other in-player preference. Neither key is
 * a per-device row: both sync through the `prefs` adapter and both were
 * catalog-advertised before the spec tier (the supplemental rows the
 * generator carried until this migration).
 *
 * **The documented opt-outs (hand-written residuals, read-modify-write /
 * identity-by-absence by decision):** the [VOLUME_PROFILES] blob's user-facing
 * writes are the read-modify-write [VolumeProfileStore.setVolume] /
 * [VolumeProfileStore.clearVolume] verbs (four buckets make a diff-based edit
 * pointless), and [VolumeProfileStore.restore] removes the key when the
 * restored map is empty — the identity-by-absence convention
 * (`setTrackSelectionRules` precedent). A read-modify-write or a removal
 * cannot be a row encoding, so the blob row declares no `encode` and the
 * store keeps those hand-written lines (the setters re-derive the current map
 * through the row's own read).
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [VolumeProfileSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists and
 * search registration, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object VolumeProfilePreferenceSpecs {

    // ------------------------------------------------------------------
    // The row group — the ONE registration of this domain's rows, in
    // [VolumeProfileSlice] property order (the blob the toggle gates). A row
    // is constructed exactly once, here; [all], the reset derivation and
    // [searchEntries] all derive from the group.
    // ------------------------------------------------------------------

    private val volumeMemory: List<PreferenceSpec<*>> = listOf(
        /*
         * Reads [VolumeProfileSlice.volumes]. The JSON blob is an opaque
         * writer-produced string: the decode is lenient and memoised on the
         * raw string — see [cachedVolumeProfiles]. Not searchable — the
         * blob's user face IS the toggle row below (the levels are managed
         * through the player volume controls, not a settings picker).
         */
        PreferenceSpec.derived(
            keyName = "volume_profiles",
            default = emptyMap<VolumeBucket, Float>(),
            resetCategory = PreferenceResetCategory.PLAYBACK,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedVolumeProfiles,
                    default = emptyMap(),
                    parse = ::decodeVolumeProfiles,
                    cacheRef = { cachedVolumeProfiles = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                    onNull = { emptyMap() },
                )
            },
        ),
        /*
         * The master toggle. Born-typed (never in `PreferenceCodec`'s
         * booleans migration list — a legacy string slot cannot exist under
         * its name), so the fallback the [plainBoolean] row carries can never
         * match and the read is behaviorally identical to a plain typed read
         * (the audioeffects `volume_boost_*` precedent). The search face
         * reuses the hand fused row's ss_* keys: desktop-only, because the
         * feature only surfaces where the app owns a volume scalar (the
         * desktop mpv engine and the audio players — Android video stays on
         * the system `STREAM_MUSIC` model and never touches this store).
         */
        PreferenceSpec.plainBoolean(
            keyName = "remember_volume_per_content_type",
            default = true,
            resetCategory = PreferenceResetCategory.PLAYBACK,
            search = PreferenceSearchSpec(
                id = "remember_volume_per_content_type",
                titleKey = "ss_remember_volume_title",
                subtitleKey = "ss_remember_volume_subtitle",
                categoryKey = "ss_cat_playback",
                keywords = listOf("volume", "remember", "memory", "per content", "content type", "loudness", "level", "movies", "audiobooks"),
                routeKind = PlaybackPreferenceSpecs.ROUTE_PLAYBACK_SETTINGS,
                platformRule = PreferencePlatformRule.DESKTOP_ONLY,
            ),
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [VolumeProfileSlice] property order. Nothing re-lists the
     * rows: a row is registered exactly once, in its group, and appears here
     * (and in the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape
     * it.
     */
    val all: List<PreferenceSpec<*>> =
        volumeMemory

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — both keys clear under
     * `PLAYBACK` (in the group's declaration order: the blob, then the
     * toggle), so all other categories return an empty list. The facade
     * aggregates these lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a [PreferenceSearchSpec] (in [all] order) — the toggle only; the
     * blob's user face is the toggle beside it. Nothing re-lists the
     * searchable rows — a row that declares a search entry is in the catalog
     * by construction, and one without one is out by construction. The
     * settings feature matches entries to rows BY ID, so this list's order is
     * declaration order, not catalog order: the row lists on the feature side
     * own the catalog order.
     */
    val searchEntries: List<PreferenceSearchSpec> =
        all.mapNotNull { it.search }

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's Keys.
    // ------------------------------------------------------------------

    val VOLUME_PROFILES: PreferenceSpec<Map<VolumeBucket, Float>> get() = row("volume_profiles")

    val REMEMBER_VOLUME_PER_CONTENT_TYPE: PreferenceSpec<Boolean> get() = row("remember_volume_per_content_type")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [VolumeProfileStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "VolumeProfilePreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
