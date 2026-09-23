package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.raulshma.jellyplay.core.model.PlatformKind
import com.raulshma.jellyplay.core.ui.navigation.Route
import com.raulshma.jellyplay.core.ui.settingssearch.SettingsSearchItem
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * One settings row's full identity — the row-twin record (candidate B1). The
 * ids holders single-homed each row's ID; this record single-homes its TITLE
 * resources: every row's display title used to exist twice as two independent
 * string resources — the screen row read `settings_<id>` (or a hand-picked
 * twin) while the search hit read `ss_<id>_title` — and the deliberate
 * search-only subtitle (`ss_<id>_subtitle`, deliberately more descriptive)
 * was unmarked as such.
 *
 * Now each row's record names every face exactly once:
 *  - [titleRes] — the settings-screen row's title resource (`settings_*`).
 *    The screens render it through [rowTitle], so the resource is referenced
 *    from exactly one place in code: this record. Null only for the
 *    documented exceptions whose screen face has no single title resource
 *    (hand-built pickers / enum-driven rows — see the per-file lists).
 *  - [searchTitleRes] — the search hit's title resource (`ss_*_title`). Where
 *    it differs from [titleRes] (deliberately descriptive), the field NAME
 *    marks it: the ss_* resource stays, now marked-by-position instead of
 *    being an orphan twin.
 *  - [searchSubtitleRes] — the search hit's subtitle resource
 *    (`ss_*_subtitle`), the deliberately-more-descriptive search-only text.
 *
 * The remaining fields ([keywords], [route], [icon], [isAdvanced],
 * [platforms]) mirror the `SettingsSearchItem` contract so the derived item
 * is a pure projection: the per-screen `*SearchItems.kt` files declare
 * `List<SettingsRowRecord>`s and feed [toSearchItems] to produce the
 * `*SearchItems` lists the groups/catalog consume — the declaration you read
 * IS the row record, and the catalog projection adds only the shared
 * `ss_cat_*` category resource.
 *
 * Both string resources of every twin pair stay in strings.xml (all locales)
 * — nothing was deleted; the record only pins which face is which.
 */
internal data class SettingsRowRecord(
    val id: String,
    /** The settings-screen row's title resource (`settings_*`), or null for the documented no-screen-title exceptions. */
    val titleRes: StringResource?,
    /** The search hit's title resource (`ss_*_title`) — named separately even where it equals [titleRes]. */
    val searchTitleRes: StringResource,
    /** The search hit's deliberately-more-descriptive subtitle resource (`ss_*_subtitle`). */
    val searchSubtitleRes: StringResource,
    val keywords: List<String>,
    val route: Route,
    val icon: ImageVector,
    val isAdvanced: Boolean = false,
    val platforms: Set<PlatformKind> = PlatformKind.entries.toSet(),
)

/**
 * The catalog projection of a record: [SettingsSearchItem] with the search
 * faces + shared `ss_cat_*` [categoryRes]. Pure — the item list derived from
 * a record list is byte-identical in behavior to the retired hand-written
 * declarations (the `SettingsSearchCatalogTest` ratchets pin the result).
 */
internal fun SettingsRowRecord.toSearchItem(categoryRes: StringResource): SettingsSearchItem =
    SettingsSearchItem(
        id = id,
        titleRes = searchTitleRes,
        subtitleRes = searchSubtitleRes,
        categoryRes = categoryRes,
        keywords = keywords,
        route = route,
        icon = icon,
        isAdvanced = isAdvanced,
        platforms = platforms,
    )

/** The list form of [toSearchItems]' per-record projection, preserving order. */
internal fun List<SettingsRowRecord>.toSearchItems(categoryRes: StringResource): List<SettingsSearchItem> =
    map { it.toSearchItem(categoryRes) }

/**
 * The registry of every row record across all screens. The screens' title
 * rendering goes through [rowTitle], which resolves a row id (always a
 * `*Ids` holder constant — the same single-sourced ids the declarations,
 * admissions and highlights use) to its record's screen title. A missing id
 * or a no-screen-title exception row fails loudly instead of rendering the
 * wrong text.
 */
internal object SettingsRowRecords {

    /** Every record, in catalog order (the record lists are declared in the same order as their `*SearchItems`). */
    val all: List<SettingsRowRecord> = listOf(
        AccountRowRecords,
        IntegrationsRowRecords,
        ActivityInsightsRowRecords,
        SystemRowRecords,
        AppearanceThemeRowRecords,
        AppearanceNavigationRowRecords,
        AppearanceLibraryRowRecords,
        AppearanceHomeLayoutRowRecords,
        AppearancePerformanceRowRecords,
        AppearanceEyeCareRowRecords,
        AppearanceNewsletterRowRecords,
        PlaybackSettingsRowRecords,
        PlaybackAdvancedVideoRowRecords,
        MpvEngineRowRecords,
        VlcEngineRowRecords,
        ExoPlayerEngineRowRecords,
        SyncPlayRowRecords,
        CastingRowRecords,
        LiveTvRowRecords,
        AudioSettingsRowRecords,
        AudioCacheRowRecords,
        LanguageSettingsRowRecords,
        TrackSelectionRowRecords,
        NotificationSettingsRowRecords,
        StorageCacheRowRecords,
        StorageNetworkRowRecords,
        StorageDownloadsRowRecords,
        SecuritySettingsRowRecords,
        BackupSettingsRowRecords,
        AboutRowRecords,
    ).flatten()

    /** The records by row id — single-valued (the contract test pins record↔holder↔catalog integrity). */
    val byId: Map<String, SettingsRowRecord> = all.associateBy { it.id }
}

/**
 * The screen-side consumer of the records: the ONE place a settings row's
 * title resource is resolved from. `title = rowTitle(SomeIds.X)` replaces the
 * per-row `stringResource(Res.string.settings_x)` so the `settings_*`
 * resource itself is referenced only by the row's record.
 */
@Composable
internal fun rowTitle(id: String): String {
    val record = SettingsRowRecords.byId.getValue(id)
    val res = requireNotNull(record.titleRes) {
        "settings row \"$id\" declares no screen title resource (documented no-screen-title exception)"
    }
    return stringResource(res)
}
