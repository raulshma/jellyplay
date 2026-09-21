package com.raulshma.jellyplay.core.ui.navigation

/**
 * The persisted nav-customization vocabulary — the stable string keys a
 * top-level [Route] is stored under in the nav preferences, and the
 * [applyNavCustomization] hide/order policy every shell applies. Split out of
 * NavKey.kt, whose class KDoc documents the restore contract governing every
 * [Route] declared there; the package (and therefore every persisted binary
 * name and the R8 keep rule) is unchanged.
 */
/**
 * The stable string key a top-level [Route] is persisted under in the
 * nav-customization preferences (`hiddenNavItems` / `navItemOrder`).
 *
 * Deliberately NOT `::class.simpleName`: the customization UI stores literal
 * strings ("Home", "LiveTv", …), so deriving the comparison key at runtime from
 * the class name silently breaks the setting whenever the two sides disagree
 * (a renamed route class, obfuscation, a new shell deriving keys differently).
 * An explicit, exhaustive-at-the-top mapping keeps the persisted vocabulary
 * independent of class identity; values match what previous releases stored,
 * so existing installs keep their configuration.
 *
 * Single source of truth: derived from the [NAV_DESTINATIONS] registry (one
 * row per route carries its key), and [CUSTOMIZABLE_TOP_LEVEL_ROUTES] derives
 * from this map — the key vocabulary and the customizable-route enumeration
 * cannot drift apart.
 */
private val NAV_KEYS_BY_ROUTE: Map<Route, String> =
    linkedMapOf(*NAV_DESTINATIONS.map { it.route to it.key }.toTypedArray())

val Route.navKey: String
    get() = NAV_KEYS_BY_ROUTE[this]
        // Only non-customizable (detail/parameterized) routes reach the
        // fallback; every top-level nav destination is registered above. A
        // new top-level route that forgets to register a key shows up in
        // ApplyNavCustomizationTest, not as a silently broken customization
        // after the next rename/obfuscation.
        ?: this::class.simpleName ?: toString()

/**
 * The string key under which [Route.Shortcuts] is hidden via
 * [com.raulshma.jellyplay.core.model.UserPreferences.hiddenNavItems].
 *
 * Centralised so the nav-bar composition (`JellyPlayApp`) and the customization
 * UI (`NavigationCustomizationGroup`) reference one source of truth.
 */
val SHORTCUTS_NAV_KEY: String = Route.Shortcuts.navKey

/**
 * Every top-level destination whose [navKey] is customizable through the nav
 * preferences — the union of the Android floating-bar and desktop-rail
 * vocabularies. Derived from the [NAV_KEYS_BY_ROUTE] registration so a new
 * top-level route is registered exactly once (ApplyNavCustomizationTest pins
 * that every entry here carries an explicit key).
 */
val CUSTOMIZABLE_TOP_LEVEL_ROUTES: List<Route> = NAV_KEYS_BY_ROUTE.keys.toList()

/**
 * Applies the stored nav customization (hidden set + custom order) to a
 * top-level route → label map, in one place.
 *
 * Single source of truth shared by every shell that renders top-level
 * navigation (Android floating bar, desktop rail, TV drawer) so the
 * "settings write keys ⇄ shell compares keys" contract lives next to the
 * [navKey] vocabulary instead of being re-implemented per shell. Entries whose
 * [navKey] is in [hiddenNavItems] are dropped; the rest are ordered by
 * [navItemOrder] (unknown order entries are ignored, unordered survivors keep
 * their input order).
 */
fun applyNavCustomization(
    routes: Map<Route, String>,
    hiddenNavItems: Set<String> = emptySet(),
    navItemOrder: List<String> = emptyList(),
): LinkedHashMap<Route, String> {
    val ordered = applyNavCustomization(
        routes.entries.toList(),
        { it.key.navKey },
        hiddenNavItems,
        navItemOrder,
    )
    return linkedMapOf<Route, String>().apply { ordered.forEach { put(it.key, it.value) } }
}

/**
 * List form of the map-based [applyNavCustomization] for shells whose nav
 * items carry more than a label (icon, visual group — e.g. the desktop rail
 * descriptors): filters and orders the items themselves instead of forcing
 * the caller to round-trip through a route→label map and back.
 */
fun <T> applyNavCustomization(
    items: List<T>,
    keyOf: (T) -> String,
    hiddenNavItems: Set<String> = emptySet(),
    navItemOrder: List<String> = emptyList(),
): List<T> {
    val filtered = items.filter { keyOf(it) !in hiddenNavItems }
    if (navItemOrder.isEmpty()) return filtered
    val ordered = mutableListOf<T>()
    for (name in navItemOrder) {
        filtered.filterTo(ordered) { keyOf(it) == name }
    }
    filtered.filterTo(ordered) { keyOf(it) !in navItemOrder }
    return ordered
}
