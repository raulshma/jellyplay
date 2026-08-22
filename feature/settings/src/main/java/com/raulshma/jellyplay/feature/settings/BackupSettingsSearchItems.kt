package com.raulshma.jellyplay.feature.settings

import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem

/**
 * Settings-search items for the "Backup" group of the old core/ui
 * SettingsSearchRegistry, moved verbatim (ids, keywords, routes, icons, isAdvanced
 * flags) next to BackupSettingsScreen. Aggregated in [SettingsSearchCatalog].
 */
internal val BackupSettingsSearchItems = listOf(
    SettingsSearchItem(
        id = "backup_export",
        titleRes = R.string.ss_backup_export_title,
        subtitleRes = R.string.ss_backup_export_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_backup_restore,
        keywords = listOf("backup", "export", "save config", "migration"),
        route = Route.BackupSettings(),
        icon = Tabler.Outline.DatabaseExport
    ),
    SettingsSearchItem(
        id = "backup_import",
        titleRes = R.string.ss_backup_import_title,
        subtitleRes = R.string.ss_backup_import_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_backup_restore,
        keywords = listOf("import", "restore", "load config", "backup restore"),
        route = Route.BackupSettings(),
        icon = Tabler.Outline.DatabaseImport
    ),
    SettingsSearchItem(
        id = "factory_reset",
        titleRes = R.string.ss_factory_reset_title,
        subtitleRes = R.string.ss_factory_reset_subtitle,
        categoryRes = com.raulshma.jellyplay.core.ui.R.string.ss_cat_backup_restore,
        keywords = listOf("factory", "reset", "defaults", "clear", "wipe"),
        route = Route.BackupSettings(),
        icon = Tabler.Outline.AlertTriangle,
        isAdvanced = true
    ),
)
