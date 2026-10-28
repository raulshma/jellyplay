package com.raulshma.jellyplay.core.datastore.navigation

import androidx.compose.runtime.Immutable
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
 * **Spec derivation:** every key this store owns is declared exactly once as a
 * row in [NavigationPreferenceSpecs] (wire name, default, reset category,
 * read/write encoding) and the machinery below is derived from those rows —
 * each [Keys] member rebuilds its row's typed key from the row's wire name,
 * each [read] projection row delegates to its row encoding (the blobs' decodes
 * live in their rows' reads, memoised beside them), the single-key setters and
 * [restore] delegate to the rows' derived writes, and [resetKeysFor] filters
 * the rows by reset category. No hand-written residuals: every write this
 * store performs is a single-key mechanical write.
 *
 * **Storage:** reuses the shared `"user_prefs"` DataStore file; key strings match
 * the legacy `UserPreferencesStore.Keys` names — no migration file.
 */
class NavigationStore constructor(
    private val dataStore: DataStore<Preferences>,
    private val externalScope: CoroutineScope,
) {
    private val scope = externalScope

    /**
     * The store's DataStore keys, each derived from its
     * [NavigationPreferenceSpecs] row — the member rebuilds the row's typed
     * key from the row's single-declared wire name (`Preferences.Key`
     * equality is name-based, so these interoperate with any hand-built key of
     * the same name). Kept as a plain object rather than folded into the rows
     * because it is the reflection anchor for the JVM reset-coverage guard.
     */
    internal object Keys {
        val NAV_BAR_SHOW_LABELS = booleanPreferencesKey(NavigationPreferenceSpecs.NAV_BAR_SHOW_LABELS.keyName)
        val HIDE_BOTTOM_NAV_ON_SCROLL = booleanPreferencesKey(NavigationPreferenceSpecs.HIDE_BOTTOM_NAV_ON_SCROLL.keyName)
        val HIDDEN_NAV_ITEMS = stringPreferencesKey(NavigationPreferenceSpecs.HIDDEN_NAV_ITEMS.keyName)
        val NAV_ITEM_ORDER = stringPreferencesKey(NavigationPreferenceSpecs.NAV_ITEM_ORDER.keyName)
    }

    val navigation: StateFlow<NavigationSlice> =
        dataStore.sliceStateFlow(scope, seed = NavigationSlice(), read = ::read)

    /**
     * Pure read of the bottom-navigation customisation fields from a raw
     * [Preferences] snapshot, each field delegated to its
     * [NavigationPreferenceSpecs] row encoding. Exposed so the facade can fold
     * these into the whole-`UserPreferences` projection without duplicating
     * the read logic.
     */
    internal fun read(prefs: Preferences): NavigationSlice = NavigationSlice(
        navBarShowLabels = NavigationPreferenceSpecs.NAV_BAR_SHOW_LABELS.readFrom(prefs),
        hideBottomNavOnScroll = NavigationPreferenceSpecs.HIDE_BOTTOM_NAV_ON_SCROLL.readFrom(prefs),
        hiddenNavItems = NavigationPreferenceSpecs.HIDDEN_NAV_ITEMS.readFrom(prefs),
        navItemOrder = NavigationPreferenceSpecs.NAV_ITEM_ORDER.readFrom(prefs),
    )

    // ------------------------------------------------------------------
    // Setters — single-key setters delegate to their row's derived write
    // (the encoding — the typed slots, the Set/List JSON — is the row's,
    // not re-declared here).
    // ------------------------------------------------------------------

    suspend fun setNavBarShowLabels(show: Boolean) {
        dataStore.edit { NavigationPreferenceSpecs.NAV_BAR_SHOW_LABELS.writeTo(it, show) }
    }

    suspend fun setHideBottomNavOnScroll(hide: Boolean) {
        dataStore.edit { NavigationPreferenceSpecs.HIDE_BOTTOM_NAV_ON_SCROLL.writeTo(it, hide) }
    }

    suspend fun setHiddenNavItems(items: Set<String>) {
        dataStore.edit { NavigationPreferenceSpecs.HIDDEN_NAV_ITEMS.writeTo(it, items) }
    }

    suspend fun setNavItemOrder(order: List<String>) {
        dataStore.edit { NavigationPreferenceSpecs.NAV_ITEM_ORDER.writeTo(it, order) }
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
     * [category] — the [NavigationPreferenceSpecs] rows whose declared reset
     * category matches, mapped to their derived keys. The nav keys all sit in
     * the legacy `HOME_DISCOVERY` reset category.
     */
    internal fun resetKeysFor(category: PreferenceResetCategory): List<Preferences.Key<*>> =
        NavigationPreferenceSpecs.resetKeysFor(category)

    /**
     * Faithful inverse of [read]: writes every field of [slice] back to the
     * DataStore via its row's derived write (the same encoding the row reads
     * with — the Set/List blobs included).
     */
    suspend fun restore(slice: NavigationSlice) {
        dataStore.edit { prefs ->
            NavigationPreferenceSpecs.NAV_BAR_SHOW_LABELS.writeTo(prefs, slice.navBarShowLabels)
            NavigationPreferenceSpecs.HIDE_BOTTOM_NAV_ON_SCROLL.writeTo(prefs, slice.hideBottomNavOnScroll)
            NavigationPreferenceSpecs.HIDDEN_NAV_ITEMS.writeTo(prefs, slice.hiddenNavItems)
            NavigationPreferenceSpecs.NAV_ITEM_ORDER.writeTo(prefs, slice.navItemOrder)
        }
    }
}

/**
 * The bottom-navigation customisation preference slice. Plain data class.
 * Every default IS its [NavigationPreferenceSpecs] row's declared default —
 * read off the row via a plain accessor, so a preference's default is stated
 * exactly once, in its spec, and cannot drift between the spec (what an absent
 * key reads) and the slice (what a fresh in-memory instance serves). The
 * spec-default equality is pinned field-for-field by
 * `NavigationSpecDerivationTest`.
 */
@Immutable
@Serializable
data class NavigationSlice(
    val navBarShowLabels: Boolean = NavigationPreferenceSpecs.NAV_BAR_SHOW_LABELS.default,
    val hideBottomNavOnScroll: Boolean = NavigationPreferenceSpecs.HIDE_BOTTOM_NAV_ON_SCROLL.default,
    val hiddenNavItems: Set<String> = NavigationPreferenceSpecs.HIDDEN_NAV_ITEMS.default,
    val navItemOrder: List<String> = NavigationPreferenceSpecs.NAV_ITEM_ORDER.default,
)
