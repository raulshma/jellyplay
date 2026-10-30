package com.raulshma.jellyplay.core.datastore.downloads

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.AutoDownloadMode
import com.raulshma.jellyplay.core.model.DownloadQuality
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.datastore.toEnumOrNull

/**
 * The downloads domain's single preference declaration: one [PreferenceSpec]
 * row per key `DownloadsStore` owns — the canonical persisted key name (which
 * is ALSO the legacy pre-typed-era wire name, declared exactly once), the
 * default, the reset category, and the read/write encoding
 * ([PreferenceSpec.plainBoolean] / [plainInt] / [enumRow] / [derived] /
 * [PreferenceSpec.boolean] / [PreferenceSpec.string]).
 *
 * **Read policy:** every boolean / int row whose pre-spec hand read went
 * through the shared `PreferenceCodec.readBool/readInt` fallback uses the
 * encoded [plainBoolean] / [plainInt] declarations — including the keys that
 * sit deliberately OUTSIDE `PreferenceCodec`'s migration lists (only
 * `wifi_only_downloads`, `compact_episode_list` and `download_connections`
 * predate the typed-key era; the rest were born in it, so the fallback those
 * rows carry can never match and the reads are behaviorally identical to a
 * plain typed read). The rows whose hand read was the plain
 * `prefs[key] ?: default` use the declaration-only [PreferenceSpec.boolean] /
 * [PreferenceSpec.string] where one exists (`download_storage_location`,
 * `auto_delete_after_watch`, the downloads-list trio,
 * `downloads_selection_hint_seen`, `download_schedule_enabled/_wifi_only`,
 * and the legacy `auto_download_new_episodes` pair surface) — the read is
 * exactly the hand read; the schedule's two plain-read int slots ride
 * [plainInt] (the notification domain's rationale: no plain-declaration int
 * variant exists, and the born-typed fallback can never match).
 *
 * `DownloadsStore` is derived from these rows: the `Keys` members rebuild each
 * row's typed key from its wire name, the `read` projection delegates each
 * slice field to its row encoding, the derived rows of `restore` delegate to
 * the rows' derived writes, and `resetKeysFor` filters the rows by reset
 * category (every key this store owns sits in the single legacy
 * `DOWNLOADS_NETWORK` bucket). No row carries a search face: the domain's
 * settings-search entries stay the feature-side residual rows they always
 * were (the storage screen's downloads group), so this domain declares none.
 *
 * **The documented opt-outs (hand-written residuals — cross-key invariants,
 * per-user namespacing and read-side band policies a row encoding cannot
 * own):**
 *  - [AUTO_DOWNLOAD_MODE] reads the tri-state mode with the legacy-boolean
 *    fallback, but its write is the cross-key invariant
 *    ([DownloadsStore.setAutoDownloadMode] / `restore` mirror the mode onto
 *    [AUTO_DOWNLOAD_NEW_EPISODES] in the same edit so an app downgrade still
 *    reads a sane value) — encode stays null and the store keeps its
 *    hand-written write lines.
 *  - [AUTO_DOWNLOAD_SERVERS] is the per-user namespaced allow-list
 *    (`u_<userId>::` grammar): the flat canonical slot never holds data, so
 *    the row is the declaration-only [PreferenceSpec.custom] (the store's
 *    hand read/writes resolve the namespace off the snapshot — the
 *    `HomeDiscoveryStore` precedent).
 *  - the four retention/band rows ([MAX_CONCURRENT_DOWNLOADS],
 *    [AUTO_DOWNLOAD_LOOKAHEAD], [AUTO_DOWNLOAD_MAX_PER_PASS],
 *    [AUTO_DOWNLOAD_KEEP_DAYS]) keep the read-side bands in the store's read
 *    projection (the videoplayer precedent — a plainInt row carries no read
 *    transform) and the clamps in their setters; the rows declare the bands
 *    as catalog metadata.
 *  - the declaration-only rows keep the store's hand-written
 *    setter/restore lines (a declaration-only row has no derived write).
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [DownloadsSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists and
 * slice defaults, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object DownloadsPreferenceSpecs {

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [DownloadsSlice] property order (with [AUTO_DOWNLOAD_NEW_EPISODES]
    // beside its [AUTO_DOWNLOAD_MODE] legacy pair). A row is constructed
    // exactly once, here; [all], the reset derivation and [searchEntries]
    // all derive from the groups.
    // ------------------------------------------------------------------

    /** Download delivery, the tri-state auto-download mode pair, and retention. */
    private val autoDownload: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "wifi_only_downloads",
            default = true,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.plainInt(
            keyName = "download_connections",
            default = 4,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        /*
         * The 1..6 max-concurrent band: the store's read projection
         * post-clamps the row's read and the setter pre-clamps its write (the
         * rows carry no read transform — see the object KDoc's opt-outs);
         * the bounds here are catalog metadata.
         */
        PreferenceSpec.plainInt(
            keyName = "max_concurrent_downloads",
            default = 3,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
            min = 1,
            max = 6,
        ),
        PreferenceSpec.enumRow(
            keyName = "download_quality",
            default = DownloadQuality.ORIGINAL,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "smart_downloads_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        /*
         * Reads [DownloadsSlice.autoDownloadMode]. The tri-state new-episode
         * policy (gh-181): the canonical enum key wins; absent (or a stale
         * enum name) derives from the pre-gh-181 boolean — `true` meant
         * auto-download was on, which maps to INSTANT. The write is the
         * cross-key invariant that mirrors the mode onto the
         * [AUTO_DOWNLOAD_NEW_EPISODES] boolean beside it, so encode stays
         * null (see the object KDoc's opt-outs).
         */
        PreferenceSpec.derived(
            keyName = "auto_download_mode",
            default = AutoDownloadMode.NEVER,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
            read = { prefs, raw ->
                raw.toEnumOrNull<AutoDownloadMode>() ?: run {
                    if (AUTO_DOWNLOAD_NEW_EPISODES.readStored(prefs)) {
                        AutoDownloadMode.INSTANT
                    } else {
                        AutoDownloadMode.NEVER
                    }
                }
            },
        ),
        /*
         * The legacy pre-gh-181 boolean surface (the [AUTO_DOWNLOAD_MODE]
         * pair). Not a [DownloadsSlice] field — the enum owns the projection;
         * this key is written by the mode's mirror invariant and read only by
         * the mode row's fallback (and reset with the rest of the downloads
         * keys). Declared here so its wire name, default and reset
         * participation live with the rows instead of as magic literals.
         */
        PreferenceSpec.boolean(
            keyName = "auto_download_new_episodes",
            default = false,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        /** Lookahead window in episodes past the highest downloaded/watched (0 = all missing). */
        PreferenceSpec.plainInt(
            keyName = "auto_download_lookahead",
            default = DownloadsStore.DEFAULT_LOOKAHEAD,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
            min = 0,
            max = DownloadsStore.MAX_LOOKAHEAD,
        ),
        /** Global per-pass enqueue budget across series (0 = unlimited). */
        PreferenceSpec.plainInt(
            keyName = "auto_download_max_per_pass",
            default = 0,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
            min = 0,
            max = DownloadsStore.MAX_PER_PASS_LIMIT,
        ),
        /** Keep-days retention window for completed downloads (0 = off). */
        PreferenceSpec.plainInt(
            keyName = "auto_download_keep_days",
            default = 0,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
            min = 0,
        ),
        /*
         * The active user's auto-download server allow-list (empty = all
         * servers), persisted under the `u_<userId>::` namespaced key — the
         * flat canonical slot NEVER holds data (every read/write resolves the
         * namespace off the snapshot; pre-login reads serve the default), so
         * the row is the declaration-only [PreferenceSpec.custom] and the
         * store's hand read/writes are the codec (see the object KDoc's
         * opt-outs).
         */
        PreferenceSpec.custom(
            keyName = "auto_download_servers",
            default = emptySet<String>(),
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
    )

    /** Storage cap/location, the downloads-list surface state, and auto-delete. */
    private val storageAndListState: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainInt(
            keyName = "max_download_storage_gb",
            default = 0,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        /*
         * Downloads-screen list state (gh-175 follow-up): the persisted
         * All/Movies/Series filter and Recent/Name/Size sort + watched-status
         * filter, stored as enum name strings. The enums live in the
         * downloads feature (this module cannot see them), so unknown values
         * degrade at the feature's decode side — an empty string means
         * "never set" (the feature's defaults), which is the declared
         * default.
         */
        PreferenceSpec.string(
            keyName = "downloads_list_filter",
            default = "",
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.string(
            keyName = "downloads_list_sort",
            default = "",
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.string(
            keyName = "downloads_list_status_filter",
            default = "",
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        /*
         * Downloads-screen onboarding: the long-press-to-select hint has been
         * dismissed once (the user entered selection mode), so the banner
         * never renders again. `true` is terminal — there is no un-see.
         */
        PreferenceSpec.boolean(
            keyName = "downloads_selection_hint_seen",
            default = false,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.string(
            keyName = "download_storage_location",
            default = "INTERNAL",
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        /*
         * Auto-delete-after-watch: when ON, a downloaded item is removed from
         * disk once it reaches 100% played. This is an unrequested,
         * destructive, opt-in behaviour; see
         * `PlayedStateSync.maybeAutoDeleteAfterWatch`. Default OFF.
         */
        PreferenceSpec.boolean(
            keyName = "auto_delete_after_watch",
            default = false,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
    )

    /** The cellular size warning + the download schedule window. */
    private val cellularAndSchedule: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainInt(
            keyName = "cellular_download_size_warning_mb",
            default = 0,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.boolean(
            keyName = "download_schedule_enabled",
            default = false,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.plainInt(
            keyName = "download_schedule_start",
            default = 0,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.plainInt(
            keyName = "download_schedule_end",
            default = 6,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
        PreferenceSpec.boolean(
            keyName = "download_schedule_wifi_only",
            default = true,
            resetCategory = PreferenceResetCategory.DOWNLOADS_NETWORK,
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [DownloadsSlice] property order (the legacy
     * [AUTO_DOWNLOAD_NEW_EPISODES] pair surface included). Nothing re-lists
     * the rows: a row is registered exactly once, in its group, and appears
     * here (and in the reset derivation and [searchEntries]) automatically.
     * The derivation-integrity test iterates this — a group entry cannot
     * escape it.
     */
    val all: List<PreferenceSpec<*>> =
        autoDownload +
            storageAndListState +
            cellularAndSchedule

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every key this store
     * owns sits in the single legacy `DOWNLOADS_NETWORK` bucket (which the
     * network/offline keys also share), so all other categories return an
     * empty list. The facade aggregates these lists instead of a central
     * `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a [PreferenceSearchSpec] (in [all] order) — which today is
     * none. The downloads knobs' search faces stay the feature-side residual
     * rows they always were (the storage screen's downloads group owns
     * them), so this domain declares none, exactly the player-engine domain's
     * stance.
     */
    val searchEntries: List<PreferenceSearchSpec> = emptyList()

    // ------------------------------------------------------------------
    // Named accessors — the public lookup face of the rows, one per group
    // entry, each naming its row's wire name. Plain map lookups, no
    // reflection (the spec tier's R8 stance); a name that no group declares
    // fails fast in [row], and the JVM derivation test pins every accessor
    // against the store's Keys.
    // ------------------------------------------------------------------

    val WIFI_ONLY_DOWNLOADS: PreferenceSpec<Boolean> get() = row("wifi_only_downloads")

    val DOWNLOAD_CONNECTIONS: PreferenceSpec<Int> get() = row("download_connections")

    val MAX_CONCURRENT_DOWNLOADS: PreferenceSpec<Int> get() = row("max_concurrent_downloads")

    val DOWNLOAD_QUALITY: PreferenceSpec<DownloadQuality> get() = row("download_quality")

    val SMART_DOWNLOADS_ENABLED: PreferenceSpec<Boolean> get() = row("smart_downloads_enabled")

    val AUTO_DOWNLOAD_MODE: PreferenceSpec<AutoDownloadMode> get() = row("auto_download_mode")

    val AUTO_DOWNLOAD_NEW_EPISODES: PreferenceSpec<Boolean> get() = row("auto_download_new_episodes")

    val AUTO_DOWNLOAD_LOOKAHEAD: PreferenceSpec<Int> get() = row("auto_download_lookahead")

    val AUTO_DOWNLOAD_MAX_PER_PASS: PreferenceSpec<Int> get() = row("auto_download_max_per_pass")

    val AUTO_DOWNLOAD_KEEP_DAYS: PreferenceSpec<Int> get() = row("auto_download_keep_days")

    val AUTO_DOWNLOAD_SERVERS: PreferenceSpec<Set<String>> get() = row("auto_download_servers")

    val MAX_DOWNLOAD_STORAGE_GB: PreferenceSpec<Int> get() = row("max_download_storage_gb")

    val DOWNLOADS_LIST_FILTER: PreferenceSpec<String> get() = row("downloads_list_filter")

    val DOWNLOADS_LIST_SORT: PreferenceSpec<String> get() = row("downloads_list_sort")

    val DOWNLOADS_LIST_STATUS_FILTER: PreferenceSpec<String> get() = row("downloads_list_status_filter")

    val DOWNLOADS_SELECTION_HINT_SEEN: PreferenceSpec<Boolean> get() = row("downloads_selection_hint_seen")

    val DOWNLOAD_STORAGE_LOCATION: PreferenceSpec<String> get() = row("download_storage_location")

    val AUTO_DELETE_AFTER_WATCH: PreferenceSpec<Boolean> get() = row("auto_delete_after_watch")

    val CELLULAR_DOWNLOAD_SIZE_WARNING_MB: PreferenceSpec<Int> get() = row("cellular_download_size_warning_mb")

    val DOWNLOAD_SCHEDULE_ENABLED: PreferenceSpec<Boolean> get() = row("download_schedule_enabled")

    val DOWNLOAD_SCHEDULE_START: PreferenceSpec<Int> get() = row("download_schedule_start")

    val DOWNLOAD_SCHEDULE_END: PreferenceSpec<Int> get() = row("download_schedule_end")

    val DOWNLOAD_SCHEDULE_WIFI_ONLY: PreferenceSpec<Boolean> get() = row("download_schedule_wifi_only")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [DownloadsStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "DownloadsPreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
