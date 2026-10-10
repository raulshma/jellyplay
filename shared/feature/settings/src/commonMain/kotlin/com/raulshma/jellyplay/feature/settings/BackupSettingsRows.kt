package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_backup_restore
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_backup_restore
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_export_settings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_export_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_export_settings_secrets
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_export_settings_secrets_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_settings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_settings_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_backup_export_secrets_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_backup_export_secrets_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_backup_export_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_backup_export_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_backup_import_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_backup_import_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_factory_reset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_factory_reset_title

/**
 * The backup domain's fused row declarations — the feature-side single home
 * of the backup screen's rows (the [AppearanceRows] template). Each
 * [SettingsRow] replaces the trio the domain used to declare per row: the
 * `SettingsSearchBinding` entry, the `SettingsRowRecord` entry, and the
 * `BackupSettingsIds` holder constant (all retired).
 *
 * HAND-MAINTAINED residuals: the backup actions live in stores without spec
 * machinery — they carry their full hand search faces. The projection
 * ([List.toSearchItems]) is the pure hand-projection; search results, catalog
 * order and group membership are byte-identical to the retired declarations.
 */
internal object BackupRows {

    // -- The "Backup" group's three rows — export, import, factory reset — in catalog order. --

    val BackupExport = SettingsRow(
        id = "backup_export",
        icon = Tabler.Outline.DatabaseExport,
        titleRes = Res.string.settings_export_settings,
        subtitleRes = Res.string.settings_export_settings_subtitle,
        searchTitleRes = Res.string.ss_backup_export_title,
        searchSubtitleRes = Res.string.ss_backup_export_subtitle,
        keywords = listOf("backup", "export", "save config", "migration"),
        route = Route.BackupSettings(),
    )

    /**
     * Wave 3 — the secrets-carrying export: same file sink as [BackupExport],
     * plus the passphrase-encrypted secrets block (third-party API keys + the
     * saved Jellyfin server list; never access tokens). Opens the passphrase
     * dialog between the file pick and the write.
     */
    val BackupExportSecrets = SettingsRow(
        id = "backup_export_secrets",
        icon = Tabler.Outline.Lock,
        titleRes = Res.string.settings_export_settings_secrets,
        subtitleRes = Res.string.settings_export_settings_secrets_subtitle,
        searchTitleRes = Res.string.ss_backup_export_secrets_title,
        searchSubtitleRes = Res.string.ss_backup_export_secrets_subtitle,
        keywords = listOf("backup", "export", "secrets", "api keys", "passphrase", "encrypted"),
        route = Route.BackupSettings(),
    )

    val BackupImport = SettingsRow(
        id = "backup_import",
        icon = Tabler.Outline.DatabaseImport,
        titleRes = Res.string.settings_import_settings,
        subtitleRes = Res.string.settings_import_settings_subtitle,
        searchTitleRes = Res.string.ss_backup_import_title,
        searchSubtitleRes = Res.string.ss_backup_import_subtitle,
        keywords = listOf("import", "restore", "load config", "backup restore"),
        route = Route.BackupSettings(),
    )

    val FactoryReset = SettingsRow(
        id = "factory_reset",
        icon = Tabler.Outline.AlertTriangle,
        titleRes = Res.string.settings_factory_reset,
        subtitleRes = Res.string.settings_factory_reset_subtitle,
        searchTitleRes = Res.string.ss_factory_reset_title,
        searchSubtitleRes = Res.string.ss_factory_reset_subtitle,
        keywords = listOf("factory", "reset", "defaults", "clear", "wipe"),
        route = Route.BackupSettings(),
        isAdvanced = true,
    )

    /**
     * Every fused backup row — the ratchet's vocabulary. A computed accessor
     * (not an initializer): the group row lists are top-level vals declared
     * later in this file, and an eager field would turn the
     * object-to-file-facade initialization order into a cycle.
     */
    val all: List<SettingsRow>
        get() = BackupRowsList
}

// ---------------------------------------------------------------------
// The spec-derived derivation inputs: the searchable semantics live on the
// datastore-side spec declarations where they exist; the ordered row lists
// below are the spine — presentation faces, catalog order, gates.
// ---------------------------------------------------------------------

private val searchRoutes: Map<String, Route> = emptyMap()

private val backupCategory = CoreUiRes.string.ss_cat_backup_restore

internal val BackupRowsList: List<SettingsRow> = listOf(
    BackupRows.BackupExport,
    BackupRows.BackupExportSecrets,
    BackupRows.BackupImport,
    BackupRows.FactoryReset,
)

/**
 * The screen groups — items AND per-row admissions derive from the row
 * lists above in one act ([List.asRowGroup]), so the declaration is the
 * single home of the groups' order, faces and gates.
 */
internal val BackupGroup =
    BackupRowsList.asRowGroup("backup", emptyList(), searchRoutes, backupCategory)

// The catalog projections, kept as named vals — the search/catalog-order
// pins (SpecDerivedSearchItemsTest, SettingsSearchCatalogTest) read these
// lists.

internal val BackupSettingsSearchItems: List<SettingsSearchItem> = BackupGroup.items

// -- The domain's root-screen entrance declaration --

/** The backup domain's root-screen entrance — the ONE ordered declaration that drives both the settings root's `item_backup` section emission (icon/title/route id) and its entrance-step index (spliced into [SETTINGS_ENTRANCE_SECTIONS] at this render position). */
internal val BackupEntrance = SettingsEntranceSectionRow(
    key = "item_backup",
    rowId = "backup",
    icon = Tabler.Outline.DatabaseExport,
    titleRes = Res.string.settings_backup_restore,
)
