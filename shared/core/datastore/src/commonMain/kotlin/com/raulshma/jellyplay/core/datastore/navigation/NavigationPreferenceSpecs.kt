package com.raulshma.jellyplay.core.datastore.navigation

import androidx.datastore.preferences.core.Preferences
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.appearance.AppearancePreferenceSpecs
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSearchSpec
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.serialization.encodeToString

/**
 * Memoisation holders for the two JSON blob rows' decodes, keyed on the raw
 * string so a decode is skipped when its key has not changed on a given
 * `dataStore.data` emission. Moved here from per-`NavigationStore` fields with
 * the rows they belong to (the playback domain's passthrough-codec cache
 * precedent): each decode is a pure function of its raw string, and a null raw
 * memoises its result exactly once ([CachedJsonNullPolicy.MemoizeNull] — this
 * store's pre-promotion policy at every site). See [PreferenceCodec.cachedJson].
 */
private var cachedHiddenNavItems = ParsedCache<Set<String>>(null, emptySet())
private var cachedNavItemOrder = ParsedCache<List<String>>(null, emptyList())

/**
 * The bottom-navigation customisation domain's single preference declaration:
 * one [PreferenceSpec] row per key `NavigationStore` owns — the canonical
 * persisted key name (which is ALSO the legacy pre-typed-era wire name,
 * declared exactly once), the default, the reset category, and the read/write
 * encoding ([PreferenceSpec.plainBoolean] / [derived]).
 *
 * **Read policy:** `nav_bar_show_labels` IS in `PreferenceCodec`'s
 * legacy-string → typed-key migration list (the pre-typed era wrote it) and
 * the hand read went through `PreferenceCodec.readBool` — the [plainBoolean]
 * row verbatim; `hide_bottom_nav_on_scroll` is born-typed (never in the
 * list — a legacy string slot cannot exist under its name), so the fallback
 * its row carries can never match and the read is behaviorally identical to a
 * plain typed read (the audioeffects `volume_boost_*` precedent).
 *
 * `NavigationStore` is derived from these rows: the `Keys` members rebuild
 * each row's typed key from its wire name, the `read` projection delegates
 * each slice field to its row encoding, the single-key setters and [restore]
 * delegate to the rows' derived writes (the blobs' encodes are the same whole-
 * collection JSON the hand setters wrote), and [resetKeysFor] filters the rows
 * by reset category — every key this store owns sits in the legacy
 * `HOME_DISCOVERY` bucket. No hand-written residuals: every write this store
 * performs is a single-key mechanical write.
 *
 * Adding a preference: one group entry here, one named accessor beside the
 * others, one [NavigationSlice] property whose default reads the row, one
 * `read()` row, one `restore()` row (and its setter) — plus one write-through
 * test line; the derivation covers key identity, encodings, reset lists and
 * search registration, not the slice plumbing or its coverage.
 *
 * Public (the [com.raulshma.jellyplay.core.datastore.experimental
 * .ExperimentalPreferenceSpecs] precedent): the settings feature derives its
 * spec-backed catalog rows from [searchEntries], so the object must cross the
 * module boundary even though the store itself stays internal.
 */
object NavigationPreferenceSpecs {

    // ------------------------------------------------------------------
    // The row groups — the ONE registration of this domain's rows, in
    // [NavigationSlice] property order. A row is constructed exactly once,
    // here; [all], the reset derivation and [searchEntries] all derive from
    // the groups.
    // ------------------------------------------------------------------

    /** The bottom bar's two behavior toggles. */
    private val navBar: List<PreferenceSpec<*>> = listOf(
        PreferenceSpec.plainBoolean(
            keyName = "nav_bar_show_labels",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            search = PreferenceSearchSpec(
                id = "nav_labels",
                titleKey = "ss_nav_labels_title",
                subtitleKey = "ss_nav_labels_subtitle",
                categoryKey = "ss_cat_appearance",
                keywords = listOf("navigation", "labels", "text", "icons", "bottom bar"),
                routeKind = AppearancePreferenceSpecs.ROUTE_APPEARANCE_SETTINGS,
                isAdvanced = true,
            ),
        ),
        PreferenceSpec.plainBoolean(
            keyName = "hide_bottom_nav_on_scroll",
            default = true,
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            search = PreferenceSearchSpec(
                id = "nav_hide_on_scroll",
                // One-key family: the search hit restates the row's screen
                // title (the fold — no ss_*_title twin).
                titleKey = "settings_nav_hide_on_scroll",
                subtitleKey = "settings_nav_hide_on_scroll_on",
                categoryKey = "ss_cat_appearance",
                keywords = listOf("navigation", "hide", "scroll", "auto hide", "bottom bar", "collapsible"),
                routeKind = AppearancePreferenceSpecs.ROUTE_APPEARANCE_SETTINGS,
            ),
        ),
    )

    /** The hidden-items set + custom ordering (the customization sheet's data). */
    private val items: List<PreferenceSpec<*>> = listOf(
        /*
         * The hidden bottom-bar items (JSON blob; decode memoised on the raw
         * string — see [cachedHiddenNavItems]). Not searchable — the set is
         * edited through the nav-bar customization sheet (the
         * `nav_bar_customization` entrance row is that sheet's catalog fact,
         * not this knob), and the reset review covers it under its
         * diff_hidden_nav_items row.
         */
        PreferenceSpec.derived(
            keyName = "hidden_nav_items",
            default = emptySet<String>(),
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedHiddenNavItems,
                    default = emptySet(),
                    parse = { PreferenceCodec.json.decodeFromString<Set<String>>(it) },
                    cacheRef = { cachedHiddenNavItems = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
        /*
         * The custom item ordering (JSON blob; decode memoised on the raw
         * string — see [cachedNavItemOrder]). Not searchable — the order is
         * edited through the nav-bar customization sheet beside the hidden
         * set, and the reset review covers it under its diff_nav_item_order
         * row.
         */
        PreferenceSpec.derived(
            keyName = "nav_item_order",
            default = emptyList<String>(),
            resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
            read = { _, raw ->
                PreferenceCodec.cachedJson(
                    raw = raw,
                    cache = cachedNavItemOrder,
                    default = emptyList(),
                    parse = { PreferenceCodec.json.decodeFromString<List<String>>(it) },
                    cacheRef = { cachedNavItemOrder = it },
                    nullPolicy = CachedJsonNullPolicy.MemoizeNull,
                )
            },
            encode = { PreferenceCodec.json.encodeToString(it) },
        ),
    )

    /**
     * Every row this domain declares — the concatenation of the group lists
     * above, in [NavigationSlice] property order. Nothing re-lists the rows:
     * a row is registered exactly once, in its group, and appears here (and in
     * the reset derivation and [searchEntries]) automatically. The
     * derivation-integrity test iterates this — a group entry cannot escape it.
     */
    val all: List<PreferenceSpec<*>> =
        navBar +
            items

    /**
     * Category reset participation, derived as the rows whose declared
     * [PreferenceSpec.resetCategory] is [category] — the nav keys all sit in
     * the legacy `HOME_DISCOVERY` bucket, so all other categories return an
     * empty list. The facade aggregates these lists instead of a central
     * `when` switch.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        all.filter { it.resetCategory == category }.map { it.typedKey() }

    /**
     * The domain's settings-search declarations, DERIVED: every row above that
     * carries a [PreferenceSearchSpec] (in [all] order) — the two toggles;
     * the customization blobs are sheet-managed data, not search faces.
     * Nothing re-lists the searchable rows — a row that declares a search
     * entry is in the catalog by construction, and one without one is out by
     * construction. The settings feature matches entries to rows BY ID, so
     * this list's order is declaration order, not catalog order: the row lists
     * on the feature side own the catalog order.
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

    val NAV_BAR_SHOW_LABELS: PreferenceSpec<Boolean> get() = row("nav_bar_show_labels")

    val HIDE_BOTTOM_NAV_ON_SCROLL: PreferenceSpec<Boolean> get() = row("hide_bottom_nav_on_scroll")

    val HIDDEN_NAV_ITEMS: PreferenceSpec<Set<String>> get() = row("hidden_nav_items")

    val NAV_ITEM_ORDER: PreferenceSpec<List<String>> get() = row("nav_item_order")

    /** The rows keyed by wire name — the accessor lookup's memo (built once). */
    private val rowsByName: Map<String, PreferenceSpec<*>> by lazy { all.associateBy { it.keyName } }

    /**
     * The lookup behind every named accessor: the group-declared row whose
     * wire name is [keyName], typed to the accessor's declared spec type.
     * Fails fast on a name no group declares — the no-reflection substitute
     * for a misnamed-reference compile error, and the derivation test pins
     * every accessor against the store's [NavigationStore.Keys].
     */
    private fun <T> row(keyName: String): PreferenceSpec<T> {
        val found = requireNotNull(rowsByName[keyName]) {
            "NavigationPreferenceSpecs has no row '$keyName' — an accessor names a wire name no group declares"
        }
        @Suppress("UNCHECKED_CAST")
        return found as PreferenceSpec<T>
    }
}
