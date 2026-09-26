package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.outline.*
import com.composables.icons.tabler.Tabler
import com.raulshma.jellyplay.core.ui.generated.resources.Res as CoreUiRes
import com.raulshma.jellyplay.core.ui.generated.resources.ss_cat_backup_restore
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_export_settings
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_factory_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_import_settings
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_backup_export_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_backup_export_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_backup_import_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_backup_import_title
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_factory_reset_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.ss_factory_reset_title

/**
 * The single-source row ids of this file's settings-search declarations.
 * Every consumer — the `SettingsSearchItem` declarations below, the screen
 * rows' `highlighted` comparisons, the admissions keys and the row-total
 * derivations — references these constants, so each id literal exists
 * exactly once. The values are the persisted deep-link/recents contract:
 * they change only deliberately, here.
 */
internal object BackupSettingsIds {
    const val BACKUP_EXPORT = "backup_export"
    const val BACKUP_IMPORT = "backup_import"
    const val FACTORY_RESET = "factory_reset"
}

/**
 * Settings-search items for the "Backup" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to BackupSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val BackupSettingsRowRecords = listOf(
    SettingsRowRecord(
        id = BackupSettingsIds.BACKUP_EXPORT,
        titleRes = Res.string.settings_export_settings,
        searchTitleRes = Res.string.ss_backup_export_title,
        searchSubtitleRes = Res.string.ss_backup_export_subtitle,
        keywords = listOf("backup", "export", "save config", "migration"),
        route = Route.BackupSettings(),
        icon = Tabler.Outline.DatabaseExport
    ),
    SettingsRowRecord(
        id = BackupSettingsIds.BACKUP_IMPORT,
        titleRes = Res.string.settings_import_settings,
        searchTitleRes = Res.string.ss_backup_import_title,
        searchSubtitleRes = Res.string.ss_backup_import_subtitle,
        keywords = listOf("import", "restore", "load config", "backup restore"),
        route = Route.BackupSettings(),
        icon = Tabler.Outline.DatabaseImport
    ),
    SettingsRowRecord(
        id = BackupSettingsIds.FACTORY_RESET,
        titleRes = Res.string.settings_factory_reset,
        searchTitleRes = Res.string.ss_factory_reset_title,
        searchSubtitleRes = Res.string.ss_factory_reset_subtitle,
        keywords = listOf("factory", "reset", "defaults", "clear", "wipe"),
        route = Route.BackupSettings(),
        icon = Tabler.Outline.AlertTriangle,
        isAdvanced = true
    ))

/** The catalog projection of `BackupSettingsRowRecords`: the search faces + the shared category. */
internal val BackupSettingsSearchItems: List<SettingsSearchItem> = BackupSettingsRowRecords.toSearchItems(CoreUiRes.string.ss_cat_backup_restore)

