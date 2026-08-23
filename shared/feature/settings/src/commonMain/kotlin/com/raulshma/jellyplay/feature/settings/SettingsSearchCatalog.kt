package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchProvider

/**
 * The single aggregation of every settings-search item list, in the curated
 * flat order the old core/ui `SettingsSearchRegistry` used — tie-breaking in
 * [com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchMatcher] relies
 * on this order (stable sort preserves it for equal scores).
 *
 * The knowledge now lives with the screens: each list is declared next to the
 * screen (or screen family) its items deep-link into, so adding a settings
 * screen means adding its items in the same file neighborhood. The
 * `ss_cat_*` category strings stay in core/ui (shared rendering); each item's
 * title/subtitle strings live in this module's `strings.xml`.
 *
 * Serves two consumers: this module's own [SettingsScreen] search pipeline
 * (direct object access) and — through the Hilt binding in
 * `di/SettingsSearchModule` — any other feature that injects
 * [SettingsSearchProvider] (feature/home's header search).
 */
object SettingsSearchCatalog : SettingsSearchProvider {

    override val items: List<SettingsSearchItem> =
        AccountSearchItems +
            IntegrationsSearchItems +
            ActivityInsightsSearchItems +
            SystemSearchItems +
            AppearanceSettingsSearchItems +
            PlaybackSettingsSearchItems +
            MpvEngineSearchItems +
            VlcEngineSearchItems +
            ExoPlayerEngineSearchItems +
            SyncPlaySearchItems +
            CastingSearchItems +
            LiveTvSearchItems +
            AudioSettingsSearchItems +
            LanguageSettingsSearchItems +
            NotificationSettingsSearchItems +
            StorageSettingsSearchItems +
            SecuritySettingsSearchItems +
            BackupSettingsSearchItems +
            AboutSearchItems +
            ExperimentalSettingsSearchItems
}
