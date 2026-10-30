package com.raulshma.jellyplay.core.datastore.library

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.serialization.encodeToString

/**
 * Memoisation holders for the three per-library override maps' JSON decodes,
 * keyed on the raw string so a decode is skipped when its key has not changed
 * on a given `dataStore.data` emission. Moved here from per-`LibraryStore`
 * fields with the rows they belong to (the playback domain's passthrough-codec
 * cache precedent): each decode is a pure function of its raw string, and a
 * null raw memoises its result exactly once
 * ([CachedJsonNullPolicy.MemoizeNull] — this store's pre-promotion policy at
 * every site; a null raw is a cacheable input because the decoded value
 * depends only on the raw string). See [PreferenceCodec.cachedJson].
 */
private var cachedDefaultLibrarySortOrders: ParsedCache<Map<String, String>> = ParsedCache(null, emptyMap())
private var cachedLibraryViewModes: ParsedCache<Map<String, String>> = ParsedCache(null, emptyMap())
private var cachedLibraryFilters: ParsedCache<Map<String, String>> = ParsedCache(null, emptyMap())

/**
 * The library-browsing domain's single preference declaration: one
 * [PreferenceSpec] row per key `LibraryStore` owns — the canonical persisted
 * key name (which is ALSO the legacy pre-typed-era wire name, declared exactly
 * once), the default, the reset category, and the read/write encoding
 * ([PreferenceSpec.plainBoolean] / [plainFloat] / [enumRow] / [derived] /
 * [PreferenceSpec.boolean]).
 *
 * **Read policy:** every boolean / float row's pre-spec hand read went through
 * the shared `PreferenceCodec.readBool/readFloat` fallback (the legacy-string
 * dance), so those rows use the encoded [plainBoolean] / [plainFloat]
 * declarations — including the twenty-one keys that sit deliberately OUTSIDE
 * `PreferenceCodec`'s migration lists: they were born in the typed-key era (a
 * legacy string slot cannot exist under their names), so the fallback those
 * rows carry can never match and the reads are behaviorally identical to a
 * plain typed read. The `confirm_library_reset` row is the one whose hand read
 * was the plain `prefs[key] ?: default`, so it is the declaration-only
 * [PreferenceSpec.boolean] (the read is exactly the hand read). The two
 * enum-by-name rows ([LIBRARY_VIEW_MODE], [LIBRARY_GROUP_BY]) are [enumRow] —
 * the plain string slot parse the hand reads always were.
 *
 * `LibraryStore` is derived from these rows: the `Keys` members rebuild each
 * row's typed key from its wire name, the `read` projection delegates each
 * slice field to its row encoding, the single-key setters and the derived rows
 * of `restore` delegate to the rows' derived writes, and `resetKeysFor`
 * filters the rows by reset category (every key this store owns sits in the
 * single legacy `HOME_DISCOVERY` bucket). No row carries a search face: the
 * domain's settings-search entries stay the feature-side residual rows they
 * always were (the appearance screen's "Library & Cards" group), so this
 * domain declares none.
 *
 * **The documented opt-outs (hand-written residuals, cross-key or
 * read-modify-write by decision):**
 *  - the three per-library override maps' user-facing writes
 *    (`setDefaultLibrarySortOrder` / `setLibraryViewMode(libraryId, …)` /
 *    `setLibraryFilters`) are read-modify-write verbs — decode the current
 *    map, put the one entry, re-encode, in one transaction — which a row
 *    encoding cannot own. The rows still declare the wire name, default,
 *    read and the whole-map encode `restore` writes.
 *  - the [CONFIRM_LIBRARY_RESET] declaration-only row keeps the store's
 *    hand-written setter/restore line (a declaration-only row has no derived
 *    write).
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [LibrarySlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists and
 * slice defaults, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object LibraryPreferenceSpecs {

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [LibrarySlice] property order. A row is constructed exactly once,
    // here; [all], the reset derivation and [searchEntries] all derive
    // from the groups.
    // ------------------------------------------------------------------

    /** The default view mode + the per-library override maps. */
    private val viewAndOverrides: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.enumRow(
            keyName = "library_view_mode",
            default = LibraryViewMode.GRID,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /*
         * The per-library default-sort overrides, keyed by Jellyfin library
         * id. The decode is memoised on the raw string — see
         * [cachedDefaultLibrarySortOrders]. The user-facing write is the
         * read-modify-write [LibraryStore.setDefaultLibrarySortOrder] verb
         * (hand-written — see the object KDoc's opt-outs); the row's derived
         * write is the same whole-map encode [LibraryStore.restore] writes.
         */
        PreferenceSpec.derived(
            keyName = "default_library_sort_orders",
            default = emptyMap<String, String>(),
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedDefaultLibrarySortOrders,
                    default = emptyMap(),
                    parse = { PreferenceCodec.json.decodeFromString<Map<String, String>>(it) },
                    cacheRef = { cachedDefaultLibrarySortOrders = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
        // Same shape as the sort overrides — see the row above.
        PreferenceSpec.derived(
            keyName = "library_view_modes",
            default = emptyMap<String, String>(),
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedLibraryViewModes,
                    default = emptyMap(),
                    parse = { PreferenceCodec.json.decodeFromString<Map<String, String>>(it) },
                    cacheRef = { cachedLibraryViewModes = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
        // Same shape as the sort overrides — see the row above.
        PreferenceSpec.derived(
            keyName = "library_filters",
            default = emptyMap<String, String>(),
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedLibraryFilters,
                    default = emptyMap(),
                    parse = { PreferenceCodec.json.decodeFromString<Map<String, String>>(it) },
                    cacheRef = { cachedLibraryFilters = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
    )

    /** The episode-list browsing toggles. */
    private val episodeToggles: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "hide_episode_thumbnails",
            default = false,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "episodes_descending",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "skip_specials",
            default = false,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        // The one episode toggle whose key IS in the legacy migration list
        // (the pre-typed era wrote it) — the fallback this row carries is the
        // live migration read.
        PreferenceSpec.plainBoolean(
            keyName = "compact_episode_list",
            default = false,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_detail_up_next",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Whether virtual (missing/unaired) episodes appear in season views. */
        PreferenceSpec.plainBoolean(
            keyName = "show_missing_episodes",
            default = false,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
    )

    /** Poster sizing + the detail-header display pair. */
    private val displayKnobs: List<PreferenceSpec<*>> = listOf(
        /*
         * Poster-size multiplier: 1.0 = the adaptive default cell size. The
         * read carries no clamp (the hand plain read never did); the setter
         * is the clamp owner, coercing into [POSTER_SIZE_MIN]..
         * [POSTER_SIZE_MAX].
         */
        PreferenceSpec.plainFloat(
            keyName = "library_poster_size",
            default = DEFAULT_POSTER_SIZE,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            min = POSTER_SIZE_MIN,
            max = POSTER_SIZE_MAX,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "prefer_logos",
            default = false,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.enumRow(
            keyName = "library_group_by",
            default = GroupBy.NONE,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /*
         * The one plain-read boolean: the hand read was the typed
         * `prefs[key] ?: true` (the key was born in the typed-key era — a
         * legacy string slot cannot exist under its name), so the row is the
         * declaration-only [PreferenceSpec.boolean] and keeps the store's
         * hand-written setter/restore line.
         */
        PreferenceSpec.boolean(
            keyName = "confirm_library_reset",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
    )

    /** The detail-screen display toggles. */
    private val detailToggles: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "show_external_links",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_tagline",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "prefer_local_trailers",
            default = false,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_more_in_collection",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_upcoming_strip",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "play_theme_music",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_reviews",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_episode_stream_badges",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_filmography_roles",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_frequent_costars",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "trailer_preview_on_focus",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_promoted_actions_landscape",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        PreferenceSpec.plainBoolean(
            keyName = "show_download_progress",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [LibrarySlice] property order. Nothing re-lists the rows:
     * a row is registered exactly once, in its group, and appears here (and
     * in the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape it.
     */
    val all: List<PreferenceSpec<*>> =
        viewAndOverrides +
            episodeToggles +
            displayKnobs +
            detailToggles

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — every key this store
     * owns sits in the single legacy `HOME_DISCOVERY` bucket (the
     * library-view/sort/filter + episode keys split out of that category), so
     * all other categories return an empty list. The facade aggregates these
     * lists instead of a central `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a [PreferenceSearchSpec] (in [all] order) — which today is none.
     * The library knobs' search faces stay the feature-side residual rows they
     * always were (the appearance screen's "Library & Cards" group owns
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

    val LIBRARY_VIEW_MODE: PreferenceSpec<LibraryViewMode> get() = row("library_view_mode")

    val DEFAULT_LIBRARY_SORT_ORDERS: PreferenceSpec<Map<String, String>> get() = row("default_library_sort_orders")

    val LIBRARY_VIEW_MODES: PreferenceSpec<Map<String, String>> get() = row("library_view_modes")

    val LIBRARY_FILTERS: PreferenceSpec<Map<String, String>> get() = row("library_filters")

    val HIDE_EPISODE_THUMBNAILS: PreferenceSpec<Boolean> get() = row("hide_episode_thumbnails")

    val EPISODES_DESCENDING: PreferenceSpec<Boolean> get() = row("episodes_descending")

    val SKIP_SPECIALS: PreferenceSpec<Boolean> get() = row("skip_specials")

    val COMPACT_EPISODE_LIST: PreferenceSpec<Boolean> get() = row("compact_episode_list")

    val SHOW_DETAIL_UP_NEXT: PreferenceSpec<Boolean> get() = row("show_detail_up_next")

    val SHOW_MISSING_EPISODES: PreferenceSpec<Boolean> get() = row("show_missing_episodes")

    val LIBRARY_POSTER_SIZE: PreferenceSpec<Float> get() = row("library_poster_size")

    val PREFER_LOGOS: PreferenceSpec<Boolean> get() = row("prefer_logos")

    val LIBRARY_GROUP_BY: PreferenceSpec<GroupBy> get() = row("library_group_by")

    val CONFIRM_LIBRARY_RESET: PreferenceSpec<Boolean> get() = row("confirm_library_reset")

    val SHOW_EXTERNAL_LINKS: PreferenceSpec<Boolean> get() = row("show_external_links")

    val SHOW_TAGLINE: PreferenceSpec<Boolean> get() = row("show_tagline")

    val PREFER_LOCAL_TRAILERS: PreferenceSpec<Boolean> get() = row("prefer_local_trailers")

    val SHOW_MORE_IN_COLLECTION: PreferenceSpec<Boolean> get() = row("show_more_in_collection")

    val SHOW_UPCOMING_STRIP: PreferenceSpec<Boolean> get() = row("show_upcoming_strip")

    val PLAY_THEME_MUSIC: PreferenceSpec<Boolean> get() = row("play_theme_music")

    val SHOW_REVIEWS: PreferenceSpec<Boolean> get() = row("show_reviews")

    val SHOW_EPISODE_STREAM_BADGES: PreferenceSpec<Boolean> get() = row("show_episode_stream_badges")

    val SHOW_FILMOGRAPHY_ROLES: PreferenceSpec<Boolean> get() = row("show_filmography_roles")

    val SHOW_FREQUENT_COSTARS: PreferenceSpec<Boolean> get() = row("show_frequent_costars")

    val TRAILER_PREVIEW_ON_FOCUS: PreferenceSpec<Boolean> get() = row("trailer_preview_on_focus")

    val SHOW_PROMOTED_ACTIONS_LANDSCAPE: PreferenceSpec<Boolean> get() = row("show_promoted_actions_landscape")

    val SHOW_DOWNLOAD_PROGRESS: PreferenceSpec<Boolean> get() = row("show_download_progress")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [LibraryStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "LibraryPreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
