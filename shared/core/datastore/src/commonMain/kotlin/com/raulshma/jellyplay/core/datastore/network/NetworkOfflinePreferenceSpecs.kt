package com.raulshma.jellyplay.core.datastore.network

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.MeteredNetworkBehavior
import com.raulshma.jellyplay.core.model.NetworkTimeoutPreset
import com.raulshma.jellyplay.core.model.PreferenceResetCategory

/**
 * The network / offline-mode domain's single preference declaration: one
 * [PreferenceSpec] row per spec-declarable key `NetworkOfflineStore` owns —
 * the canonical persisted key name (which is ALSO the legacy pre-typed-era
 * wire name, declared exactly once), the default, the reset category, and the
 * read/write encoding ([PreferenceSpec.plainBoolean] / [plainInt] /
 * [plainLong] / [enumRow] / [PreferenceSpec.string]).
 *
 * **Read policy:** every boolean row's pre-spec hand read went through the
 * shared `PreferenceCodec.readBool` fallback (the legacy-string dance), and
 * [MAX_CACHE_SIZE_MB]'s through `readInt` — so those rows use the encoded
 * [plainBoolean] / [plainInt] declarations. The five booleans
 * ([MANUAL_OFFLINE_ENABLED], [AUTO_OFFLINE_ENABLED], [ADAPTIVE_BITRATE_ENABLED],
 * [DATA_SAVER_ENABLED], [VERBOSE_NETWORK_LOGGING]) sit deliberately OUTSIDE
 * `PreferenceCodec`'s migration lists — they were born in the typed-key era
 * (a legacy string slot cannot exist under their names), so the fallback
 * those rows carry can never match and the reads are behaviorally identical
 * to a plain typed read (the audioeffects precedent); [MAX_CACHE_SIZE_MB] and
 * [AUTO_DELETE_CACHE] ARE in the migration lists, so their rows are the live
 * migration reads. The two enum-by-name rows ([METERED_NETWORK_BEHAVIOR],
 * [NETWORK_TIMEOUT_PRESET]) are [enumRow] — the plain string-slot parse the
 * hand reads always were. [MANUAL_BANDWIDTH_CAP] rides [plainLong] even
 * though its hand read was the plain typed `prefs[key] ?: default`: the key
 * was born typed and outside the migration lists, so the row's legacy
 * fallback can never match either — behaviorally identical, and [plainLong]
 * is the long slot's only declaration in the spec tier.
 *
 * `NetworkOfflineStore` is derived from these rows: the `Keys` members
 * rebuild each row's typed key from its wire name, the `read` projection
 * delegates each slice field to its row encoding, the single-key setters and
 * the derived rows of `restore` delegate to the rows' derived writes, and
 * `resetKeysFor` filters the rows by reset category (every row this domain
 * declares sits in the legacy `DOWNLOADS_NETWORK` bucket). No row carries a
 * search face: the domain's settings-search entries stay the feature-side
 * residual rows they always were (the network screen's groups own them), so
 * this domain declares none.
 *
 * **The documented opt-outs (hand-written residuals — see the store KDoc):**
 *  - the [HOME_STREAMING_QUALITY] row is the declaration-only
 *    [PreferenceSpec.string] (the hand read was the plain
 *    `prefs[key] ?: default` — the home-quality projection is carried as the
 *    raw persisted name, degraded at the caller); its setter/restore lines
 *    stay hand-written (a declaration-only row has no derived write).
 *  - the self-signed trust-host grant set (`self_signed_trust_hosts`) has no
 *    row at all: it is a DataStore StringSet slot and [com.raulshma.jellyplay
 *    .core.datastore.spec.PreferenceStorage] has no STRING_SET member — the
 *    spec tier declares single-value storages only. The store keeps the key,
 *    its read, its read-modify-write grant/revoke verbs and its `restore`
 *    line hand-written, and its reset participation is appended to the
 *    derived list beside the rows.
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [NetworkOfflineSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists and
 * slice defaults, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object NetworkOfflinePreferenceSpecs {

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [NetworkOfflineSlice] property order. A row is constructed exactly
    // once, here; [all], the reset derivation and [searchEntries] all derive
    // from the groups.
    // ------------------------------------------------------------------

    /** Offline mode (manual + auto) and the manual bandwidth cap. */
    private val offlineMode: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "manual_offline_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "auto_offline_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.plainLong(
            keyName = "manual_bandwidth_cap",
            default = 0L,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
    )

    /** The home-streaming quality (raw persisted enum name). */
    private val homeStreamingQuality: List<PreferenceSpec<*>> = listOf(
        /*
         * The home-streaming quality as the persisted enum NAME
         * (the `StreamingQuality` vocabulary, "AUTO" default). Carried as a
         * raw String so an unknown name from a newer binary degrades to the
         * caller's handling instead of a hard parse failure here — the hand
         * read was the plain `prefs[key] ?: default`, so the row is the
         * declaration-only [PreferenceSpec.string] (the read is exactly the
         * hand read; the store keeps its hand-written setter/restore line).
         */
        PreferenceSpec.string(
            keyName = "home_network_streaming_quality",
            default = "AUTO",
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
    )

    /** Metered behaviour, data-saver, verbose logging, and the timeout preset. */
    private val meteredAndSavers: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
            keyName = "metered_network_behavior",
            default = MeteredNetworkBehavior.WARN,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "adaptive_bitrate_enabled",
            default = true,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "data_saver_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "verbose_network_logging",
            default = false,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.enumRow(
            keyName = "network_timeout_preset",
            default = NetworkTimeoutPreset.DEFAULT,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
    )

    /** The image-cache size cap + auto-delete toggle. */
    private val imageCache: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainInt(
            keyName = "max_cache_size_mb",
            default = 0,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "auto_delete_cache",
            default = true,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [NetworkOfflineSlice] property order. Nothing re-lists the
     * rows: a row is registered exactly once, in its group, and appears here
     * (and in the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape
     * it. The hand-residual trust-host set key is deliberately absent (see
     * the object KDoc's opt-outs).
     */
    val all: List<PreferenceSpec<*>> =
        offlineMode +
            homeStreamingQuality +
            meteredAndSavers +
            imageCache

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every row this domain
     * declares sits in the legacy `DOWNLOADS_NETWORK` bucket (the
     * network/offline subset; the download keys belong to the downloads
     * domain), so all other categories return an empty list. The
     * trust-host set key is NOT here — the store appends its hand-residual
     * reset participation beside this derivation.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above
     * that carries a [PreferenceSearchSpec] (in [all] order) — which today is
     * none. The network knobs' search faces stay the feature-side residual
     * rows they always were (the network screen's groups own them), the
     * library domain's stance.
     */
    val searchEntries: List<PreferenceSearchSpec> = emptyList()

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's [NetworkOfflineStore.Keys].
    // ------------------------------------------------------------------

    val MANUAL_OFFLINE_ENABLED: PreferenceSpec<Boolean> get() = row("manual_offline_enabled")

    val AUTO_OFFLINE_ENABLED: PreferenceSpec<Boolean> get() = row("auto_offline_enabled")

    val MANUAL_BANDWIDTH_CAP: PreferenceSpec<Long> get() = row("manual_bandwidth_cap")

    val HOME_STREAMING_QUALITY: PreferenceSpec<String> get() = row("home_network_streaming_quality")

    val METERED_NETWORK_BEHAVIOR: PreferenceSpec<MeteredNetworkBehavior> get() = row("metered_network_behavior")

    val ADAPTIVE_BITRATE_ENABLED: PreferenceSpec<Boolean> get() = row("adaptive_bitrate_enabled")

    val DATA_SAVER_ENABLED: PreferenceSpec<Boolean> get() = row("data_saver_enabled")

    val VERBOSE_NETWORK_LOGGING: PreferenceSpec<Boolean> get() = row("verbose_network_logging")

    val NETWORK_TIMEOUT_PRESET: PreferenceSpec<NetworkTimeoutPreset> get() = row("network_timeout_preset")

    val MAX_CACHE_SIZE_MB: PreferenceSpec<Int> get() = row("max_cache_size_mb")

    val AUTO_DELETE_CACHE: PreferenceSpec<Boolean> get() = row("auto_delete_cache")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [NetworkOfflineStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "NetworkOfflinePreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
