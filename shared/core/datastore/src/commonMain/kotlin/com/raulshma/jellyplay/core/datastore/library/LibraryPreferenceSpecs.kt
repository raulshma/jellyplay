package com.raulshma.jellyplay.core.datastore.library

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.DetailBackdropResolution
import com.raulshma.jellyplay.core.model.DetailChapterCardSize
import com.raulshma.jellyplay.core.model.DetailDensity
import com.raulshma.jellyplay.core.model.DetailStaggerStyle
import com.raulshma.jellyplay.core.model.FilmographySort
import com.raulshma.jellyplay.core.model.GroupBy
import com.raulshma.jellyplay.core.model.LibraryViewMode
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.PromotedRailSource
import com.raulshma.jellyplay.core.model.SmartPlayPriority
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
private var cachedDetailHiddenFranchiseIds: ParsedCache<Set<String>> = ParsedCache(null, emptySet())

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
        // ── The detail-screen QoL knobs (density / limits / chips / ────────
        // sections). All residual rows: no search face (the feature-side
        // rows on the appearance screen's "Library & Cards" group own the
        // catalog entries), every row in the legacy HOME_DISCOVERY bucket.
        // The int rows' bounds are catalog metadata only — the store's
        // setters are the clamp owners (the poster-size precedent).

        /** Cast rail item size on the media-detail header (people geometry). */
        PreferenceSpec.enumRow(
            keyName = "detail_cast_item_size",
            default = DetailDensity.DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Cast "See all" threshold: the row hides people behind it above this count. */
        PreferenceSpec.plainInt(
            keyName = "detail_cast_see_all_limit",
            default = DETAIL_CAST_SEE_ALL_LIMIT_DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            min = DETAIL_CAST_SEE_ALL_LIMIT_MIN,
            max = DETAIL_CAST_SEE_ALL_LIMIT_MAX,
        ),
        /** Parallax (translate + zoom) scroll on the detail hero backdrop. */
        PreferenceSpec.plainBoolean(
            keyName = "detail_backdrop_parallax",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Overview synopsis collapsed line count (the "Read more" toggle). */
        PreferenceSpec.plainInt(
            keyName = "detail_overview_collapse_lines",
            default = DETAIL_OVERVIEW_COLLAPSE_LINES_DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            min = DETAIL_OVERVIEW_COLLAPSE_LINES_MIN,
            max = DETAIL_OVERVIEW_COLLAPSE_LINES_MAX,
        ),
        /** Hide played episodes from season lists. */
        PreferenceSpec.plainBoolean(
            keyName = "detail_hide_watched_episodes",
            default = false,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Ask for confirmation before marking a single item played/unplayed. */
        PreferenceSpec.plainBoolean(
            keyName = "detail_confirm_mark_played",
            default = false,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Header studio chips. */
        PreferenceSpec.plainBoolean(
            keyName = "detail_show_studio_chips",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Header genre chips. */
        PreferenceSpec.plainBoolean(
            keyName = "detail_show_genre_chips",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Header tag chips. */
        PreferenceSpec.plainBoolean(
            keyName = "detail_show_tag_chips",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Header skip-intro/credits availability chips. */
        PreferenceSpec.plainBoolean(
            keyName = "detail_show_skip_chips",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Small poster thumbnails on the season tab bar. */
        PreferenceSpec.plainBoolean(
            keyName = "detail_season_tabs_artwork",
            default = false,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Cast & Crew grid density (the grid's column minimum). */
        PreferenceSpec.enumRow(
            keyName = "detail_people_grid_density",
            default = DetailDensity.DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Local/remote source badges on the trailer cards. */
        PreferenceSpec.plainBoolean(
            keyName = "detail_show_trailer_badges",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /**
         * Persist the detail screen's inline section hides (the franchise
         * eye-hide memory) instead of keeping them session-only. The Up-Next
         * eye-hide persists through `show_detail_up_next` unconditionally.
         */
        PreferenceSpec.plainBoolean(
            keyName = "detail_persist_section_hides",
            default = false,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /**
         * The persisted franchise eye-hide memory (collection ids the user
         * eye-hid while `detail_persist_section_hides` was on). JSON id set;
         * not surfaced as a settings row — managed inline by the eye-hide
         * (the my-media-hidden-libraries precedent: the spec stays faceless).
         */
        PreferenceSpec.derived(
            keyName = "detail_hidden_franchise_ids",
            default = emptySet<String>(),
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedDetailHiddenFranchiseIds,
                    default = emptySet(),
                    parse = { PreferenceCodec.json.decodeFromString<Set<String>>(it) },
                    cacheRef = { cachedDetailHiddenFranchiseIds = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
        /** "More like this" row bound (both stock arms). */
        PreferenceSpec.plainInt(
            keyName = "detail_similar_limit",
            default = DETAIL_SIMILAR_LIMIT_DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            min = DETAIL_SIMILAR_LIMIT_MIN,
            max = DETAIL_SIMILAR_LIMIT_MAX,
        ),
        /** "Airing next" strip bound. */
        PreferenceSpec.plainInt(
            keyName = "detail_upcoming_limit",
            default = DETAIL_UPCOMING_LIMIT_DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            min = DETAIL_UPCOMING_LIMIT_MIN,
            max = DETAIL_UPCOMING_LIMIT_MAX,
        ),
        // ── The detail-screen QoL knobs, wave 2 (D17–D28). Same residual
        // shape as the wave above: no search face (the feature-side rows on
        // the appearance screen's "Library & Cards" group own the catalog
        // entries), every row in the legacy HOME_DISCOVERY bucket. The int
        // rows' bounds are catalog metadata only — the store's setters are
        // the clamp owners.

        /**
         * The detail body's stored section permutation (CSV over the
         * section-kind names). Declaration-only string row (the downloads
         * list-state precedent): the read is the plain typed slot, the
         * setter/restore lines stay hand-written, and the feature side owns
         * the reconcile (unknown tokens dropped, missing appended canonically).
         */
        PreferenceSpec.string(
            keyName = "detail_section_order",
            default = "",
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Entrance-stagger step: FAST=0.03f / DEFAULT=0.045f (shipped) / SLOW=0.06f. */
        PreferenceSpec.enumRow(
            keyName = "detail_stagger_style",
            default = DetailStaggerStyle.DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Hero-backdrop decode target: 1280x720 / 1920x1080 (shipped) / 2560x1440. */
        PreferenceSpec.enumRow(
            keyName = "detail_backdrop_resolution",
            default = DetailBackdropResolution.FHD_1920,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Chapter thumbnail card size multiplier (±20% over the shipped geometry). */
        PreferenceSpec.enumRow(
            keyName = "detail_chapter_card_size",
            default = DetailChapterCardSize.DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Review-card preview line count (int, clamped 2..12). */
        PreferenceSpec.plainInt(
            keyName = "detail_reviews_preview_lines",
            default = DETAIL_REVIEWS_PREVIEW_LINES_DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            min = DETAIL_REVIEWS_PREVIEW_LINES_MIN,
            max = DETAIL_REVIEWS_PREVIEW_LINES_MAX,
        ),
        /** Person filmography ordering (DEFAULT = the shipped newest-first year sort). */
        PreferenceSpec.enumRow(
            keyName = "detail_filmography_sort",
            default = FilmographySort.DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Franchise arm BoxSet-row bound (int, clamped 1..5). */
        PreferenceSpec.plainInt(
            keyName = "detail_franchise_box_limit",
            default = DETAIL_FRANCHISE_BOX_LIMIT_DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            min = DETAIL_FRANCHISE_BOX_LIMIT_MIN,
            max = DETAIL_FRANCHISE_BOX_LIMIT_MAX,
        ),
        /** Franchise arm per-row member page bound (int, clamped 10..100). */
        PreferenceSpec.plainInt(
            keyName = "detail_franchise_member_limit",
            default = DETAIL_FRANCHISE_MEMBER_LIMIT_DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            min = DETAIL_FRANCHISE_MEMBER_LIMIT_MIN,
            max = DETAIL_FRANCHISE_MEMBER_LIMIT_MAX,
        ),
        /** Smart-play dispatch candidate preference (DEFAULT = resume > next-up > replay). */
        PreferenceSpec.enumRow(
            keyName = "detail_smart_play_priority",
            default = SmartPlayPriority.DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /**
         * Stream-picker fallback language (3-letter code). Declaration-only
         * string row like [detail_section_order]; the setter validates
         * (lowercase, 3-letter tokens only, else the default).
         */
        PreferenceSpec.string(
            keyName = "detail_stream_fallback_language",
            default = DETAIL_STREAM_FALLBACK_LANGUAGE_DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** Which ⋮-menu preset the landscape/TV promoted rail shows. */
        PreferenceSpec.enumRow(
            keyName = "detail_promoted_rail_source",
            default = PromotedRailSource.DEFAULT,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
        ),
        /** 2-line overview snippets on the season lists' episode rows. */
        PreferenceSpec.plainBoolean(
            keyName = "detail_episode_overview_preview",
            default = false,
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

    // The detail-screen QoL knobs (see the detailToggles group's trailing
    // block for the per-row docs).

    val DETAIL_CAST_ITEM_SIZE: PreferenceSpec<DetailDensity> get() = row("detail_cast_item_size")

    val DETAIL_CAST_SEE_ALL_LIMIT: PreferenceSpec<Int> get() = row("detail_cast_see_all_limit")

    val DETAIL_BACKDROP_PARALLAX: PreferenceSpec<Boolean> get() = row("detail_backdrop_parallax")

    val DETAIL_OVERVIEW_COLLAPSE_LINES: PreferenceSpec<Int> get() = row("detail_overview_collapse_lines")

    val DETAIL_HIDE_WATCHED_EPISODES: PreferenceSpec<Boolean> get() = row("detail_hide_watched_episodes")

    val DETAIL_CONFIRM_MARK_PLAYED: PreferenceSpec<Boolean> get() = row("detail_confirm_mark_played")

    val DETAIL_SHOW_STUDIO_CHIPS: PreferenceSpec<Boolean> get() = row("detail_show_studio_chips")

    val DETAIL_SHOW_GENRE_CHIPS: PreferenceSpec<Boolean> get() = row("detail_show_genre_chips")

    val DETAIL_SHOW_TAG_CHIPS: PreferenceSpec<Boolean> get() = row("detail_show_tag_chips")

    val DETAIL_SHOW_SKIP_CHIPS: PreferenceSpec<Boolean> get() = row("detail_show_skip_chips")

    val DETAIL_SEASON_TABS_ARTWORK: PreferenceSpec<Boolean> get() = row("detail_season_tabs_artwork")

    val DETAIL_PEOPLE_GRID_DENSITY: PreferenceSpec<DetailDensity> get() = row("detail_people_grid_density")

    val DETAIL_SHOW_TRAILER_BADGES: PreferenceSpec<Boolean> get() = row("detail_show_trailer_badges")

    val DETAIL_PERSIST_SECTION_HIDES: PreferenceSpec<Boolean> get() = row("detail_persist_section_hides")

    val DETAIL_HIDDEN_FRANCHISE_IDS: PreferenceSpec<Set<String>> get() = row("detail_hidden_franchise_ids")

    val DETAIL_SIMILAR_LIMIT: PreferenceSpec<Int> get() = row("detail_similar_limit")

    val DETAIL_UPCOMING_LIMIT: PreferenceSpec<Int> get() = row("detail_upcoming_limit")

    // The detail-screen QoL knobs, wave 2 (see the detailToggles group's
    // trailing block for the per-row docs).

    val DETAIL_SECTION_ORDER: PreferenceSpec<String> get() = row("detail_section_order")

    val DETAIL_STAGGER_STYLE: PreferenceSpec<DetailStaggerStyle> get() = row("detail_stagger_style")

    val DETAIL_BACKDROP_RESOLUTION: PreferenceSpec<DetailBackdropResolution> get() = row("detail_backdrop_resolution")

    val DETAIL_CHAPTER_CARD_SIZE: PreferenceSpec<DetailChapterCardSize> get() = row("detail_chapter_card_size")

    val DETAIL_REVIEWS_PREVIEW_LINES: PreferenceSpec<Int> get() = row("detail_reviews_preview_lines")

    val DETAIL_FILMOGRAPHY_SORT: PreferenceSpec<FilmographySort> get() = row("detail_filmography_sort")

    val DETAIL_FRANCHISE_BOX_LIMIT: PreferenceSpec<Int> get() = row("detail_franchise_box_limit")

    val DETAIL_FRANCHISE_MEMBER_LIMIT: PreferenceSpec<Int> get() = row("detail_franchise_member_limit")

    val DETAIL_SMART_PLAY_PRIORITY: PreferenceSpec<SmartPlayPriority> get() = row("detail_smart_play_priority")

    val DETAIL_STREAM_FALLBACK_LANGUAGE: PreferenceSpec<String> get() = row("detail_stream_fallback_language")

    val DETAIL_PROMOTED_RAIL_SOURCE: PreferenceSpec<PromotedRailSource> get() = row("detail_promoted_rail_source")

    val DETAIL_EPISODE_OVERVIEW_PREVIEW: PreferenceSpec<Boolean> get() = row("detail_episode_overview_preview")

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
