package com.raulshma.jellyplay.core.datastore.shortcuts

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.raulshma.jellyplay.core.datastore.UserNamespacedKeys
import com.raulshma.jellyplay.core.datastore.spec.PreferenceSpec
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ShortcutDefinition

/**
 * The user-shortcuts preference declarations — one row per key, the wire name
 * declared exactly once here. Every row is a JSON blob under STRING storage
 * ([PreferenceSpec.custom], the `hidden_nav_items` encoding) whose codec the
 * owning store supplies; all three ride the legacy `HOME_DISCOVERY` reset
 * category alongside the nav-customization keys they sit next to.
 *
 * **Storage:** like `HomeDiscoveryStore`, every key is namespaced per active
 * user as `u_<userId>::<canonical>` — shortcuts are one user's personal
 * chrome, so user B must never inherit user A's pins. The rows declare the
 * canonical names (the stable backup/export format); the store rebuilds the
 * namespaced keys through [rawShortcutsUserKey] below.
 */
object ShortcutsPreferenceSpecs {

    /** JSON-encoded `List<ShortcutDefinition>` — the shortcut catalog the order lists reference. */
    val DEFINITIONS: PreferenceSpec<List<ShortcutDefinition>> = PreferenceSpec.custom(
        keyName = "shortcuts_definitions",
        default = emptyList(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    /** JSON-encoded `List<String>` — shortcut ids pinned to the home header dock, display order. */
    val HEADER_ORDER: PreferenceSpec<List<String>> = PreferenceSpec.custom(
        keyName = "shortcuts_header_order",
        default = emptyList(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    /** JSON-encoded `List<String>` — shortcut ids pinned to the nav overflow menu, display order. */
    val OVERFLOW_ORDER: PreferenceSpec<List<String>> = PreferenceSpec.custom(
        keyName = "shortcuts_overflow_order",
        default = emptyList(),
        resetCategory = PreferenceResetCategory.HOME_DISCOVERY,
    )

    val all: List<PreferenceSpec<*>> = listOf(DEFINITIONS, HEADER_ORDER, OVERFLOW_ORDER)
}

/**
 * The row's raw STRING slot rebuilt under [userId]'s namespace — the
 * shortcuts-domain spelling of the shared [UserNamespacedKeys] grammar (same
 * shape as the home-domain `rawUserKey` hook): a namespaced slot is always the
 * canonical row transformed by the grammar, never a second key literal.
 */
internal fun rawShortcutsUserKey(userId: String, row: PreferenceSpec<*>): Preferences.Key<String> =
    stringPreferencesKey(UserNamespacedKeys.name(userId, row.keyName))
