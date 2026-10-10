package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_account
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_management
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_server_management_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_from_server
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_from_server_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sign_out_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_switch_user
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_switch_user_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_logout_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_logout_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_server_management_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_server_management_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_sign_out_from_server_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_sign_out_from_server_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_user_management_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_user_management_title

/**
 * The account domain's fused row declarations — the feature-side single home
 * of the main screen's account-group rows (the [AppearanceRows] template).
 * Each [SettingsRow] replaces the trio the domain used to declare per row:
 * the `SettingsSearchBinding` entry, the `SettingsRowRecord` entry, and the
 * `SettingsScreenIds` holder constant (all retired).
 *
 * HAND-MAINTAINED residuals: the account/session rows are navigation facts
 * whose knobs live in stores without spec machinery — they carry their full
 * hand search faces. The projection ([List.toSearchItems]) is the pure
 * hand-projection; search results, catalog order and group membership are
 * byte-identical to the retired declarations.
 */
internal object AccountRows {

    // -- The main screen's "Account / Users / Servers" group — navigation facts whose knobs live in spec-less stores — in catalog order. --

    val Logout = SettingsRow(
        id = "logout",
        icon = Tabler.Outline.Logout,
        titleRes = Res.string.settings_sign_out,
        subtitleRes = Res.string.settings_sign_out_subtitle,
        searchTitleRes = Res.string.ss_logout_title,
        searchSubtitleRes = Res.string.ss_logout_subtitle,
        keywords = listOf("sign out", "logout", "exit", "disconnect"),
        route = Route.Settings,
    )

    val SignOutFromServer = SettingsRow(
        id = "sign_out_from_server",
        icon = Tabler.Outline.Logout,
        titleRes = Res.string.settings_sign_out_from_server,
        subtitleRes = Res.string.settings_sign_out_from_server_subtitle,
        searchTitleRes = Res.string.ss_sign_out_from_server_title,
        searchSubtitleRes = Res.string.ss_sign_out_from_server_subtitle,
        keywords = listOf("sign out", "server", "remove device", "revoke", "session", "remote", "disconnect"),
        route = Route.Settings,
    )

    val ServerManagement = SettingsRow(
        id = "server_management",
        icon = Tabler.Outline.Server,
        titleRes = Res.string.settings_server_management,
        subtitleRes = Res.string.settings_server_management_subtitle,
        searchTitleRes = Res.string.ss_server_management_title,
        searchSubtitleRes = Res.string.ss_server_management_subtitle,
        keywords = listOf("server", "connection", "jellyfin", "address", "switch"),
        route = Route.ServerManagement(),
    )

    val UserManagement = SettingsRow(
        id = "user_management",
        icon = Tabler.Outline.Users,
        titleRes = Res.string.settings_switch_user,
        subtitleRes = Res.string.settings_switch_user_subtitle,
        searchTitleRes = Res.string.ss_user_management_title,
        searchSubtitleRes = Res.string.ss_user_management_subtitle,
        keywords = listOf("user", "accounts", "profile", "switch user", "admin"),
        route = Route.UserManagement(),
    )

    /**
     * Every fused account row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = AccountRowsList
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val accountCategory = CoreUiRes.string.ss_cat_account

internal val AccountRowsList: List<SettingsRow> = listOf(
    AccountRows.Logout,
    AccountRows.SignOutFromServer,
    AccountRows.ServerManagement,
    AccountRows.UserManagement,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val AccountGroup =
    AccountRowsList.asRowGroup("account", emptyList(), searchRoutes, accountCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val AccountSearchItems: List<SettingsSearchItem> = AccountGroup.items
