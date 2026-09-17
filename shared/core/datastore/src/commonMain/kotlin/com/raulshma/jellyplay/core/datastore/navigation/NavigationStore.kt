package com.raulshma.jellyplay.core.datastore.navigation

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.CachedJsonNullPolicy
import com.raulshma.jellyplay.core.datastore.ParsedCache
import com.raulshma.jellyplay.core.datastore.PreferenceCodec
import com.raulshma.jellyplay.core.datastore.sliceStateFlow
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable

/**
 * Deep module owning the **bottom-navigation customisation** preference domain:
 * whether the nav bar shows labels, whether it hides on scroll, the
 * hidden-items set + custom item ordering.
 *
 * Extracted from the `UserPreferencesStore` god object so this concern owns its
 * keys, its setters, its read projection, and its reset-key list end-to-end.
 * Mirrors the `PlaybackStore` / `AppearanceStore` shape.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class NavigationStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    private val json get() = PreferenceCodec.json

    internal object Keys {
        val NAV_BAR_SHOW_LABELS = booleanPreferencesKey("nav_bar_show_labels")
        val HIDE_BOTTOM_NAV_ON_SCROLL = booleanPreferencesKey("hide_bottom_nav_on_scroll")
        val HIDDEN_NAV_ITEMS = stringPreferencesKey("hidden_nav_items")
        val NAV_ITEM_ORDER = stringPreferencesKey("nav_item_order")
    }

    private var cachedHiddenNavItems = ParsedCache<Set<String>>(null, emptySet())
    private var cachedNavItemOrder = ParsedCache<List<String>>(null, emptyList())

    val navigation: StateFlow<NavigationSlice> =
        dataStore.sliceStateFlow(scope, seed = NavigationSlice(), read = ::read)

    internal fun read(prefs: Preferences): NavigationSlice = NavigationSlice(
        navBarShowLabels = PreferenceCodec.readBool(prefs, Keys.NAV_BAR_SHOW_LABELS, "nav_bar_show_labels", true),
        hideBottomNavOnScroll = PreferenceCodec.readBool(prefs, Keys.HIDE_BOTTOM_NAV_ON_SCROLL, "hide_bottom_nav_on_scroll", true),
        hiddenNavItems = readHiddenNavItems(prefs),
        navItemOrder = readNavItemOrder(prefs),
    )

    // MemoizeNull (this store's pre-promotion policy at every site): a null
    // raw is a cacheable input — the decoded value depends only on the raw
    // string, so a memoised default is safe to serve.
    private fun readHiddenNavItems(prefs: Preferences): Set<String> =
        PreferenceCodec.cachedJson(
            raw = prefs[Keys.HIDDEN_NAV_ITEMS],
            cache = cachedHiddenNavItems,
            default = emptySet(),
            parse = { json.decodeFromString<Set<String>>(it) },
            cacheRef = { cachedHiddenNavItems = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

    private fun readNavItemOrder(prefs: Preferences): List<String> =
        PreferenceCodec.cachedJson(
            raw = prefs[Keys.NAV_ITEM_ORDER],
            cache = cachedNavItemOrder,
            default = emptyList(),
            parse = { json.decodeFromString<List<String>>(it) },
            cacheRef = { cachedNavItemOrder = it },
            nullPolicy = CachedJsonNullPolicy.MemoizeNull,
        )

    // ------------------------------------------------------------------
    // Setters
    // ------------------------------------------------------------------

    suspend fun setNavBarShowLabels(show: Boolean) {
        dataStore.edit { it[Keys.NAV_BAR_SHOW_LABELS] = show }
    }

    suspend fun setHideBottomNavOnScroll(hide: Boolean) {
        dataStore.edit { it[Keys.HIDE_BOTTOM_NAV_ON_SCROLL] = hide }
    }

    suspend fun setHiddenNavItems(items: Set<String>) {
        dataStore.edit { it[Keys.HIDDEN_NAV_ITEMS] = json.encodeToString(items) }
    }

    suspend fun setNavItemOrder(order: List<String>) {
        dataStore.edit { it[Keys.NAV_ITEM_ORDER] = json.encodeToString(order) }
    }

    /**
     * Keys owned by this store, for factory-reset participation. Derived as the
     * union of the [resetKeysFor] category lists (in enum declaration order) —
     * those lists are what the facade actually resets, so deriving from them
     * (instead of maintaining a parallel hand-written union) keeps this list
     * from drifting out of sync. These are the nav keys split out of the
     * legacy `HOME_DISCOVERY` reset category.
     */
    internal val resetKeys: List<Preferences.Key<*>> =
        PreferenceResetCategory.entries.flatMap(::resetKeysFor)

    /**
     * Category reset participation: the subset of [resetKeys] that belongs to
     * [category]. The nav keys all sit in the legacy `HOME_DISCOVERY` reset
     * category.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> = when (category) {
        PreferenceResetCategory.HOME_DISCOVERY -> listOf(
            Keys.NAV_BAR_SHOW_LABELS,
            Keys.HIDE_BOTTOM_NAV_ON_SCROLL,
            Keys.HIDDEN_NAV_ITEMS,
            Keys.NAV_ITEM_ORDER,
        )
        else -> emptyList()
    }

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore using the same encoding as [restorePreferences] (Set/List via
     * this store's [json] codec).
     */
    suspend fun restore(slice: NavigationSlice) {
        dataStore.edit { it ->
            it[Keys.NAV_BAR_SHOW_LABELS] = slice.navBarShowLabels
            it[Keys.HIDE_BOTTOM_NAV_ON_SCROLL] = slice.hideBottomNavOnScroll
            it[Keys.HIDDEN_NAV_ITEMS] = json.encodeToString(slice.hiddenNavItems)
            it[Keys.NAV_ITEM_ORDER] = json.encodeToString(slice.navItemOrder)
        }
    }
}

/**
 * The bottom-navigation customisation preference slice. Plain data class.
 * Defaults mirror the projection defaults in [NavigationStore.read].
 */
@Immutable
@Serializable
data class NavigationSlice(
    val navBarShowLabels: Boolean = true,
    val hideBottomNavOnScroll: Boolean = true,
    val hiddenNavItems: Set<String> = emptySet(),
    val navItemOrder: List<String> = emptyList(),
)
