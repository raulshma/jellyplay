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
 *  - [searchTitleRes] — the search hit's title resource (`ss_*_title`), or
 *    null where the search hit merely restates the row's screen title: the
 *    default-title fold ([toSearchItem]'s `searchTitleRes ?: titleRes`)
 *    resolves those hits to [titleRes], so a restating declaration names ONE
 *    resource instead of a value-twin pair. Where the search title genuinely
 *    differs from [titleRes] (deliberately descriptive, or a translation that
 *    diverged in any locale), the field NAME marks it: the ss_* resource
 *    stays, marked-by-position instead of being an orphan twin.
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
 * **Spec-derived rows carry a SCREEN FACE only.** In the domains the
 * spec-derived catalog covers, the search faces live on the datastore-side
 * spec declarations and the record reduces to `(id, [titleRes], [icon])` — [route] null and
 * the search faces defaulted — because the row's title/icon still serve
 * [rowTitle] / [rowIcon]. The projection for those rows is
 * `List<SettingsRowRecord>.toSearchItems(List<PreferenceSearchSpec>, …)`
 * (SettingsSearchBinding.kt); a converted record that still carried hand
 * search faces fails the derivation loudly.
 *
 * A defaulted (null) [searchTitleRes] means the search hit reuses the row's
 * screen title; the ss_* twins that only ever restated that title were
 * deleted from the resource files, and the deliberately-distinct search
 * titles stay pinned by the explicit field.
 */
internal data class SettingsRowRecord(
    val id: String,
    /** The settings-screen row's title resource (`settings_*`), or null for the documented no-screen-title exceptions. */
    val titleRes: StringResource?,
    /**
     * The search hit's title resource (`ss_*_title`), or null where the search
     * hit restates the row's screen title (the default-title fold resolves it
     * to [titleRes] — same resource id, or values equal in every locale).
     * Also null for spec-derived rows (they carry no hand search faces).
     */
    val searchTitleRes: StringResource? = null,
    /** The search hit's subtitle resource; null for spec-derived rows. */
    val searchSubtitleRes: StringResource? = null,
    val keywords: List<String> = emptyList(),
    /** The deep-link route; null for spec-derived rows (the spec's routeKind owns the route). */
    val route: Route? = null,
    val icon: ImageVector,
    val isAdvanced: Boolean = false,
    val platforms: Set<PlatformKind> = PlatformKind.entries.toSet(),
)

/**
 * The catalog projection of a record: [SettingsSearchItem] with the search
 * faces + shared `ss_cat_*` [categoryRes]. Pure — the item list derived from
 * a record list is byte-identical in behavior to the retired hand-written
 * declarations (the `SettingsSearchCatalogTest` ratchets pin the result).
 * The title is the default-title fold: an explicit [SettingsRowRecord.searchTitleRes]
 * wins; a null one resolves to the record's [SettingsRowRecord.titleRes] —
 * the same resource the restating declaration used to restate, so the
 * projected hit is unchanged. A record that declares NEITHER face cannot
 * project and fails loudly (the [rowTitle] miss pattern).
 */
internal fun SettingsRowRecord.toSearchItem(categoryRes: StringResource): SettingsSearchItem =
    SettingsSearchItem(
        id = id,
        titleRes = requireNotNull(searchTitleRes ?: titleRes) {
            "settings row \"$id\" declares neither a search title nor a screen title to default to"
        },
        subtitleRes = requireNotNull(searchSubtitleRes) {
            "settings row \"$id\" declares no search subtitle (hand rows must carry the full search faces; spec-derived rows project through toSearchItems(List<PreferenceSearchSpec>, …))"
        },
        categoryRes = categoryRes,
        keywords = keywords,
        route = requireNotNull(route) {
            "settings row \"$id\" declares no route (hand rows must carry the full search faces; spec-derived rows project through toSearchItems(List<PreferenceSearchSpec>, …))"
        },
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
        HomeDisplayRowRecords,
        HomeNextUpRowRecords,
        HomeLayoutRowRecords,
        HomeCardsRowRecords,
        AppearanceThemeRowRecords,
        AppearanceNavigationRowRecords,
        AppearanceLibraryRowRecords,
        AppearancePerformanceRowRecords,
        AppearanceEyeCareRowRecords,
        AppearanceNewsletterRowRecords,
        PlaybackSettingsRowRecords,
        PlaybackAdvancedVideoRowRecords,
        MpvEngineRowRecords,
        VlcEngineRowRecords,
        ExoPlayerEngineRowRecords,
        ExternalEngineRowRecords,
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

/**
 * [rowTitle]'s icon twin: the ONE place a settings row's leading icon is
 * resolved from. `icon = rowIcon(SomeIds.X)` replaces the per-row hand-written
 * `Tabler.Outline.X` so the record's [SettingsRowRecord.icon] field is
 * referenced from exactly one declaration home — the record is the single
 * source, and any screen/record icon drift resolves to the record's icon.
 * Non-composable: the icon is a plain [ImageVector] field, so no composition
 * is needed. A missing id fails loudly (the same getValue miss pattern as
 * [rowTitle]); every id [rowTitle] accepts has a record, so the two resolvers
 * accept the same id vocabulary.
 */
internal fun rowIcon(id: String): ImageVector = SettingsRowRecords.byId.getValue(id).icon
