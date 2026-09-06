package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.model.currentPlatform
import com.raulshma.jellyplay.core.ui.settingssearch.ResolvedSettingsItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchProvider
import com.raulshma.jellyplay.core.ui.settingssearch.filterFor
import com.raulshma.jellyplay.core.ui.settingssearch.resolve
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Platform tags for [SettingsSearchItem.platforms]. Package-level so the
 * per-screen `*SearchItems` files tag without imports: a row that only
 * exists where the backing seam/platform exists must not surface as a stale
 * search hit elsewhere. TV-only rows stay tagged ANDROID — form factor is
 * the runtime `LocalTvMode` axis, not a platform.
 */
internal val ANDROID_ONLY_PLATFORMS: Set<PlatformKind> = setOf(PlatformKind.ANDROID)

/**
 * Tags every receiver item as offered on Android only — the whole-list form
 * of `platforms = ANDROID_ONLY_PLATFORMS` for lists whose backing surface
 * is Android-only (notifications, the Exo/VLC engine configs).
 */
internal fun List<SettingsSearchItem>.androidOnly(): List<SettingsSearchItem> =
    map { it.copy(platforms = ANDROID_ONLY_PLATFORMS) }

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

    /**
     * The real-locale resolve every default seam shares (a val so the
     * [SettingsSearchProvider.resolved] override below reuses it instead of
     * spelling a second `.resolve()` — the resolve-guard ratchet counts
     * those occurrences).
     */
    private val defaultResolve: suspend (List<SettingsSearchItem>) -> List<ResolvedSettingsItem> =
        { it.resolve() }

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
     * rendering, minus the items this platform does not offer
     * ([SettingsSearchItem.platforms] via [filterFor]) — a row that cannot
     * exist here must not surface as a search hit here. [items] itself stays
     * the full unfiltered catalog (the pinned integrity tests count it);
     * platform filtering happens at this funnel, so both consumers — the
     * settings screen search and feature/home's header search, which share
     * the [SettingsSearchProvider] binding — and [recentItems] inherit it.
     *
     * Dispatched onto [Dispatchers.Default] as a hard rule: one
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
        resolveCatalog: suspend (List<SettingsSearchItem>) -> List<ResolvedSettingsItem> = defaultResolve,
    ): List<ResolvedSettingsItem> = withContext(Dispatchers.Default) {
        resolveCatalog(items.filterFor(currentPlatform))
    }

    /** The [SettingsSearchProvider] funnel — same filtered resolve as above. */
    override suspend fun resolved(): List<ResolvedSettingsItem> = resolved(defaultResolve)

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
        resolveCatalog: suspend (List<SettingsSearchItem>) -> List<ResolvedSettingsItem> = defaultResolve,
    ): List<ResolvedSettingsItem> = withContext(Dispatchers.Default) {
        if (recentIds.isEmpty()) {
            emptyList()
        } else {
            val byId = resolved(resolveCatalog).associateBy { it.id }
            recentIds.mapNotNull { byId[it] }
        }
    }
}
