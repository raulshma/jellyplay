package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchProvider
import com.raulshma.jellyplay.core.ui.settingssearch.resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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

    /**
     * The whole catalog resolved to the current locale for fuzzy matching and
     * rendering. Dispatched onto [Dispatchers.Default] as a hard rule: one
     * resolve call fans out to a compose-resources read per catalog entry
     * (257 items × title/subtitle/category = 771 reads today), and each
     * not-yet-cached read blocks its caller — on Android the runtime resolves
     * a string via `runBlocking` on the composition thread and re-opens the
     * per-locale asset, inflating from byte 0 to the entry offset (the app's
     * largest table, 105-137 KB per locale). Resolving the catalog on the
     * caller's (main) thread is the settings-open ANR — see
     * [SettingsSearchCatalogPrewarmer] for the warm-up that turns these reads
     * into runtime-cache hits.
     *
     * [resolveCatalog] is a test seam.
     */
    suspend fun resolved(
        resolveCatalog: suspend (List<SettingsSearchItem>) -> List<ResolvedSettingsItem> = { it.resolve() },
    ): List<ResolvedSettingsItem> = withContext(Dispatchers.Default) {
        resolveCatalog(items)
    }

    /**
     * Projects the recent-setting ids against the fully resolved catalog;
     * stale ids (catalog entries that no longer exist) drop out via
     * mapNotNull. Empty ids short-circuit without touching the catalog. The
     * catalog resolve rides the [resolved] off-main path — never the
     * caller's thread, cold or warm.
     *
     * [resolveCatalog] is a test seam.
     */
    suspend fun recentItems(
        recentIds: List<String>,
        resolveCatalog: suspend (List<SettingsSearchItem>) -> List<ResolvedSettingsItem> = { it.resolve() },
    ): List<ResolvedSettingsItem> = withContext(Dispatchers.Default) {
        if (recentIds.isEmpty()) {
            emptyList()
        } else {
            val byId = resolved(resolveCatalog).associateBy { it.id }
            recentIds.mapNotNull { byId[it] }
        }
    }
}
