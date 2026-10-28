package com.raulshma.jellyplay.core.datastore.audiocache

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.AudioCacheNetworkPolicy
import com.raulshma.jellyplay.core.model.PreferenceResetCategory

/**
 * The audio-cache policy domain's single preference declaration: one
 * [PreferenceSpec] row per spec-declarable key `AudioCacheStore` owns — the
 * canonical persisted key name (which is ALSO the legacy pre-typed-era wire
 * name, declared exactly once), the default, the reset category, and the
 * read/write encoding ([PreferenceSpec.plainInt] / [enumRow] /
 * [PreferenceSpec.boolean]).
 *
 * **Read policy:** these keys were never string-typed — born in the
 * typed-key era, deliberately outside `PreferenceCodec`'s migration lists,
 * and the hand reads were the plain `prefs[key] ?: default`. So the
 * [AUDIO_CACHING_ENABLED] row is the declaration-only [PreferenceSpec.boolean]
 * (the read is exactly the hand read; the store keeps its hand-written
 * setter/restore line). The int rows ride the encoded [plainInt] — the long/
 * int slot has no declaration-only form in the spec tier, and the fallback
 * those rows carry can never match (no legacy string slot can exist under
 * their names), so the reads are behaviorally identical to the plain typed
 * reads (the audioeffects born-typed precedent). The
 * [AUDIO_CACHE_NETWORK_POLICY] row is [enumRow] — the plain string-slot
 * parse the hand read always was.
 *
 * `AudioCacheStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates
 * each slice field to its row encoding, the single-key setters and the
 * derived rows of `restore` delegate to the rows' derived writes, and
 * `resetKeysFor` filters the rows by reset category (every key this store
 * owns sits in the single `AUDIO_CACHE` bucket). No row carries a search
 * face: the domain's settings-search entries stay the feature-side residual
 * rows they always were (the audio-cache screen's groups own them), so this
 * domain declares none.
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [AudioCacheSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists and
 * slice defaults, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object AudioCachePreferenceSpecs {

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [AudioCacheSlice] property order. A row is constructed exactly once,
    // here; [all], the reset derivation and [searchEntries] all derive from
    // the groups.
    // ------------------------------------------------------------------

    /** Caching on/off, size cap, prefetch lookahead + backfill, network gate, cellular cap. */
    private val cachePolicy: List<PreferenceSpec<*>> = listOf(
        /*
         * The plain-read boolean: the hand read was the typed
         * `prefs[key] ?: true` (the key was born in the typed-key era — a
         * legacy string slot cannot exist under its name), so the row is the
         * declaration-only [PreferenceSpec.boolean] and keeps the store's
         * hand-written setter/restore line (the library
         * `confirm_library_reset` precedent).
         */
        PreferenceSpec.boolean(
            keyName = "audio_caching_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.AUDIO_CACHE,
        ),
        PreferenceSpec.plainInt(
            keyName = "audio_cache_size_mb",
            default = 1024,
            resetCategory = PreferenceResetCategory.AUDIO_CACHE,
        ),
        PreferenceSpec.plainInt(
            keyName = "audio_prefetch_lookahead",
            default = 3,
            resetCategory = PreferenceResetCategory.AUDIO_CACHE,
        ),
        PreferenceSpec.plainInt(
            keyName = "audio_prefetch_backfill",
            default = 5,
            resetCategory = PreferenceResetCategory.AUDIO_CACHE,
        ),
        PreferenceSpec.enumRow(
            keyName = "audio_cache_network_policy",
            default = AudioCacheNetworkPolicy.DEFAULT,
            resetCategory = PreferenceResetCategory.AUDIO_CACHE,
        ),
        PreferenceSpec.plainInt(
            keyName = "audio_cache_cellular_monthly_cap_mb",
            default = 500,
            resetCategory = PreferenceResetCategory.AUDIO_CACHE,
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [AudioCacheSlice] property order. Nothing re-lists the rows:
     * a row is registered exactly once, in its group, and appears here (and
     * in the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape
     * it.
     */
    val all: List<PreferenceSpec<*>> = cachePolicy

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every key this store
     * owns sits in the single `AUDIO_CACHE` bucket, so all other categories
     * return an empty list. The facade aggregates these lists instead of a
     * central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above
     * that carries a [PreferenceSearchSpec] (in [all] order) — which today is
     * none. The audio-cache knobs' search faces stay the feature-side
     * residual rows they always were (the audio screen's groups own them),
     * the library domain's stance.
     */
    val searchEntries: List<PreferenceSearchSpec> = emptyList()

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's [AudioCacheStore.Keys].
    // ------------------------------------------------------------------

    val AUDIO_CACHING_ENABLED: PreferenceSpec<Boolean> get() = row("audio_caching_enabled")

    val AUDIO_CACHE_SIZE_MB: PreferenceSpec<Int> get() = row("audio_cache_size_mb")

    val AUDIO_PREFETCH_LOOKAHEAD: PreferenceSpec<Int> get() = row("audio_prefetch_lookahead")

    val AUDIO_PREFETCH_BACKFILL: PreferenceSpec<Int> get() = row("audio_prefetch_backfill")

    val AUDIO_CACHE_NETWORK_POLICY: PreferenceSpec<AudioCacheNetworkPolicy> get() =
        row("audio_cache_network_policy")

    val AUDIO_CACHE_CELLULAR_MONTHLY_CAP_MB: PreferenceSpec<Int> get() =
        row("audio_cache_cellular_monthly_cap_mb")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [AudioCacheStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "AudioCachePreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
