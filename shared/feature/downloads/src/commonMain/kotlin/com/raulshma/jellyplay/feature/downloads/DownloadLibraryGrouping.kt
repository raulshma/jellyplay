package com.raulshma.jellyplay.feature.downloads

import androidx.compose.ui.state.ToggleableState
import com.raulshma.jellyplay.core.model.DownloadItem
import com.raulshma.jellyplay.core.model.DownloadStatus
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.downloads.generated.resources.Res
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_filter_all
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_filter_movies
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_filter_series
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_sort_name
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_sort_recent
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_sort_size
import org.jetbrains.compose.resources.StringResource

/**
 * Series/season grouping for the downloads list (gh-175): downloaded episodes
 * render as expandable series groups — with season sub-groups when a series
 * spans more than one downloaded season — and the episodes inside every group
 * sort by numeric season, then episode number, regardless of when each file
 * was downloaded or how the list above the group level is sorted. Everything
 * here is pure and offline-safe: the linkage fields were persisted on each
 * download row at queue time, so no server access is involved.
 */

/**
 * Type filter for the downloads list — All / Movies / Series. Note the
 * deliberate asymmetry with [OfflineLibraryFilter.SERIES]: the queue holds
 * per-episode download rows, so Series here matches EPISODE files, while the
 * offline library's Series tab matches top-level SERIES rows.
 */
enum class DownloadListFilter(val labelRes: StringResource) {
    ALL(Res.string.downloads_filter_all),
    MOVIES(Res.string.downloads_filter_movies),
    SERIES(Res.string.downloads_filter_series),
    ;

    /** Whether [item] belongs in this filter's view (Series means episode files). */
    fun accepts(item: DownloadItem): Boolean = when (this) {
        ALL -> true
        MOVIES -> item.mediaType == MediaType.MOVIE
        SERIES -> item.mediaType == MediaType.EPISODE
    }

    companion object {
        /** Tolerant decode of the persisted value — an unknown name falls back to ALL. */
        fun fromName(name: String?): DownloadListFilter =
            entries.firstOrNull { it.name == name } ?: ALL
    }
}

/**
 * Sort for the downloads list. Orders series groups and flat rows relative to
 * each other only — the order INSIDE a group is always numeric playback order
 * (see [downloadedSeasons]) so no sort choice can scramble episode order.
 */
enum class DownloadListSort(val labelRes: StringResource) {
    RECENT(Res.string.downloads_sort_recent),
    NAME(Res.string.downloads_sort_name),
    SIZE(Res.string.downloads_sort_size),
    ;

    companion object {
        /** Tolerant decode of the persisted value — an unknown name falls back to RECENT. */
        fun fromName(name: String?): DownloadListSort =
            entries.firstOrNull { it.name == name } ?: RECENT
    }
}

/**
 * The series/season coordinates of one downloaded episode, with the fallback
 * chain that keeps legacy rows groupable: a missing series id falls back to
 * the stored series name. A row with neither (null [seriesKey]) stays
 * standalone — it renders as its own flat row, never merged into a bogus
 * "unknown" series group.
 */
internal data class EpisodePosition(
    val id: String,
    val seriesId: String?,
    val seriesName: String?,
    val seasonId: String?,
    val seasonName: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
) {
    /** Group identity, or null when the row has no series identity at all. */
    val seriesKey: String?
        get() = seriesId?.takeIf(String::isNotBlank)?.let { "series:$it" }
            ?: seriesName?.takeIf(String::isNotBlank)?.let { "series-name:$it" }

    val seasonKey: String
        get() = seasonNumber?.let { "number:$it" }
            ?: seasonId?.takeIf(String::isNotBlank)?.let { "id:$it" }
            ?: "unknown"
}

internal val DownloadItem.episodePosition: EpisodePosition
    get() = EpisodePosition(id, seriesId, seriesName, seasonId, seasonName, seasonNumber, episodeNumber)

/** One season's slice of a series' downloaded episodes, in playback order. */
internal data class DownloadSeason<T>(
    val key: String,
    val number: Int?,
    val name: String?,
    val episodes: List<T>,
)

/**
 * Groups episodes into seasons and orders everything numerically: episodes by
 * season → episode number (missing numbers last, id as the stable tiebreak),
 * seasons by season number — numberless legacy seasons fall back to
 * digit-aware name order ([compareNatural]), then the stable key. Deliberately
 * independent of any title/recent sort — downloading E08 before E03 must
 * still display E03 first.
 */
internal fun <T> downloadedSeasons(
    episodes: List<T>,
    position: (T) -> EpisodePosition,
): List<DownloadSeason<T>> = episodes.map { it to position(it) }
    .groupBy { (_, location) -> location.seasonKey }
    .map { (key, rows) ->
        val location = rows.first().second
        DownloadSeason(
            key = key,
            number = location.seasonNumber,
            name = rows.firstNotNullOfOrNull { it.second.seasonName?.takeIf(String::isNotBlank) },
            episodes = rows.sortedWith(
                compareBy<Pair<T, EpisodePosition>> { it.second.episodeNumber ?: Int.MAX_VALUE }
                    .thenBy { it.second.id },
            ).map { it.first },
        )
    }.sortedWith(
        compareBy<DownloadSeason<T>> { it.number ?: Int.MAX_VALUE }
            .thenComparator { a, b ->
                // Seasons without a number (legacy rows) tie on the primary
                // key — order them by digit-aware name so "Season 10" still
                // sorts after "Season 2"; nameless ones fall to the stable key.
                val byName = if (a.name != null && b.name != null) compareNatural(a.name, b.name) else 0
                byName.takeIf { it != 0 } ?: a.key.compareTo(b.key)
            },
    )

/** One rendered row of the grouped downloads list. */
internal sealed interface DownloadListRow {
    val key: String

    /**
     * Expandable series group header. [episodeIds] backs the tri-state group
     * checkbox in selection mode (covers collapsed episodes too) and arrives
     * in the group's numeric playback order — the order the header's Play
     * action walks when hunting the next unwatched episode. [totalBytes] and
     * [completedCount] feed the header's size/progress summary line.
     */
    data class SeriesHeader(
        override val key: String,
        val name: String,
        val episodeCount: Int,
        val seasonCount: Int,
        val episodeIds: List<String>,
        val totalBytes: Long,
        val completedCount: Int,
    ) : DownloadListRow

    /** Season sub-header — only emitted while a series spans 2+ seasons. */
    data class SeasonHeader(
        override val key: String,
        val name: String?,
        val number: Int?,
        val episodeCount: Int,
        val episodeIds: List<String>,
        val totalBytes: Long,
        val completedCount: Int,
    ) : DownloadListRow

    /** A single download row — exactly the flat-list entry the screen always had. */
    data class Item(val download: DownloadItem) : DownloadListRow {
        override val key = "download:${download.id}"
    }
}

/**
 * One series' downloaded episodes, grouped into ordered seasons — the
 * group-level unit the list is built from.
 */
private data class SeriesGroup(
    val key: String,
    val name: String,
    val seasons: List<DownloadSeason<DownloadItem>>,
    val totalBytes: Long,
    /** Position of the group's newest episode in the DAO's createdAt-DESC list. */
    val firstIndex: Int,
) {
    val episodeCount: Int get() = seasons.sumOf { it.episodes.size }
    val completedCount: Int
        get() = seasons.sumOf { season -> season.episodes.count { it.status == DownloadStatus.COMPLETED } }
}

/**
 * A top-level unit awaiting emission — a series group or one flat row —
 * carrying all three sort keys so any sort orders a mixed list uniformly.
 * Kotlin sorts are stable, so ties keep the DAO order.
 */
private sealed interface TopLevel {
    /** Position in the DAO's createdAt-DESC list (RECENT's ordering key). */
    val position: Int
    val label: String
    val bytes: Long

    data class Group(val group: SeriesGroup, override val position: Int) : TopLevel {
        override val label: String get() = group.name
        override val bytes: Long get() = group.totalBytes
    }

    data class Flat(val item: DownloadItem, override val position: Int) : TopLevel {
        override val label: String get() = item.name
        override val bytes: Long get() = item.totalSizeBytes
    }
}

/**
 * Builds the grouped downloads list. Identified episodes become series groups
 * (season sub-headers only when a series spans 2+ seasons — a single
 * downloaded season lists its episodes straight under the series); episodes
 * with no series identity at all, and every non-episode media type, stay flat
 * rows. Series groups and flat rows merge into one sequence under [sort];
 * [expandedKeys] decides which groups disclose their episodes.
 */
internal fun downloadListRows(
    downloads: List<DownloadItem>,
    filter: DownloadListFilter,
    sort: DownloadListSort,
    expandedKeys: Set<String>,
): List<DownloadListRow> {
    // Positions for the RECENT sort must live in ONE space — the DAO list's
    // createdAt-DESC order — so episodes and flat rows are indexed here,
    // before the filter partitions them.
    val indexedDownloads = downloads.mapIndexed { index, item -> index to item }
    val episodes = indexedDownloads.filter { (_, item) -> item.mediaType == MediaType.EPISODE }
    val groupedEpisodes = episodes.mapNotNull { (index, item) ->
        item.episodePosition.seriesKey?.let { Triple(it, index, item) }
    }
    // Episodes with no series identity render flat — they're still episode
    // files, so the Series tab shows them (as rows), the Movies tab doesn't.
    val standaloneEpisodes = episodes.filter { (_, item) -> item.episodePosition.seriesKey == null }
    // Movies are the only grouped-by-nothing type the Movies tab shows.
    val flatItems = when (filter) {
        DownloadListFilter.MOVIES -> indexedDownloads.filter { (_, item) -> item.mediaType == MediaType.MOVIE }
        DownloadListFilter.SERIES -> standaloneEpisodes
        DownloadListFilter.ALL -> standaloneEpisodes +
            indexedDownloads.filter { (_, item) -> item.mediaType != MediaType.EPISODE }
    }

    val groups = if (filter == DownloadListFilter.MOVIES) {
        emptyList()
    } else {
        groupedEpisodes
            .groupBy { (key, _, _) -> key }
            .map { (key, indexed) ->
                val groupEpisodes = indexed.map { it.third }
                SeriesGroup(
                    key = key,
                    name = groupEpisodes.firstNotNullOfOrNull { it.seriesName?.takeIf(String::isNotBlank) }
                        ?: groupEpisodes.first().name,
                    seasons = downloadedSeasons(groupEpisodes) { it.episodePosition },
                    totalBytes = groupEpisodes.sumOf { it.totalSizeBytes },
                    firstIndex = indexed.minOf { it.second },
                )
            }
    }

    val topLevel = buildList {
        groups.forEach { add(TopLevel.Group(it, it.firstIndex)) }
        flatItems.forEach { (index, item) -> add(TopLevel.Flat(item, index)) }
    }
    val ordered = when (sort) {
        DownloadListSort.RECENT -> topLevel.sortedBy { it.position }
        DownloadListSort.NAME -> topLevel.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        DownloadListSort.SIZE -> topLevel.sortedByDescending { it.bytes }
    }

    return buildList {
        for (unit in ordered) {
            when (unit) {
                is TopLevel.Flat -> add(DownloadListRow.Item(unit.item))
                is TopLevel.Group -> {
                    val group = unit.group
                    add(
                        DownloadListRow.SeriesHeader(
                            key = group.key,
                            name = group.name,
                            episodeCount = group.episodeCount,
                            seasonCount = group.seasons.size,
                            episodeIds = group.seasons.flatMap { it.episodes }.map { it.id },
                            totalBytes = group.totalBytes,
                            completedCount = group.completedCount,
                        ),
                    )
                    if (group.key !in expandedKeys) continue
                    if (group.seasons.size <= 1) {
                        group.seasons.firstOrNull()?.episodes?.forEach { add(DownloadListRow.Item(it)) }
                    } else {
                        for (season in group.seasons) {
                            val seasonKey = seasonRowKey(group.key, season.key)
                            add(
                                DownloadListRow.SeasonHeader(
                                    key = seasonKey,
                                    name = season.name,
                                    number = season.number,
                                    episodeCount = season.episodes.size,
                                    episodeIds = season.episodes.map { it.id },
                                    totalBytes = season.episodes.sumOf { it.totalSizeBytes },
                                    completedCount = season.episodes.count { it.status == DownloadStatus.COMPLETED },
                                ),
                            )
                            if (seasonKey in expandedKeys) {
                                season.episodes.forEach { add(DownloadListRow.Item(it)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Tri-state read behind a group header's checkbox: off / all / partial. */
internal fun groupSelectionState(ids: Collection<String>, selectedIds: Set<String>): ToggleableState = when {
    ids.isEmpty() || ids.none { it in selectedIds } -> ToggleableState.Off
    ids.all { it in selectedIds } -> ToggleableState.On
    else -> ToggleableState.Indeterminate
}

/**
 * The episode a group header's Play action targets: the first UNPLAYED
 * COMPLETED download in the group's playback order (a header's
 * [DownloadListRow.SeriesHeader.episodeIds] order — numeric season → episode),
 * falling back to the first COMPLETED download when every completed episode in
 * the group is already watched. Rows still in flight are never targeted — the
 * same `status == COMPLETED` gate the row-level Play applies — so a header
 * Play can't attempt to play a file that isn't on disk yet; a group with
 * nothing completed yields null and the click is a no-op. [playedItemIds]
 * holds media item ids the caller resolved from the offline DB (already
 * normalized by the ≥95% watched rule) — this function stays pure on that set.
 */
internal fun groupPlayTarget(
    groupDownloads: List<DownloadItem>,
    playedItemIds: Set<String>,
): DownloadItem? =
    groupDownloads.firstOrNull {
        it.status == DownloadStatus.COMPLETED && it.mediaItemId !in playedItemIds
    } ?: groupDownloads.firstOrNull { it.status == DownloadStatus.COMPLETED }

/**
 * The expanded-key of a season sub-header — the single shape both
 * [downloadListRows]' emission and [DownloadsViewModel.expandAll]'s
 * derivation must agree on.
 */
internal fun seasonRowKey(seriesKey: String, seasonKey: String): String = "$seriesKey/season/$seasonKey"

/**
 * Digit-aware string comparison for the legacy rows that carry no numeric
 * season number: their relative order reads off the season NAME ("Season 2"
 * before "Season 10"), never off raw lexicographic order. Digit runs compare
 * by length then lexicographically — equivalent to numeric order for
 * non-zero-padded numbers, and overflow-proof at any run length.
 */
internal fun compareNatural(left: String, right: String): Int {
    var li = 0
    var ri = 0
    while (li < left.length && ri < right.length) {
        if (left[li].isDigit() && right[ri].isDigit()) {
            var le = li
            while (le < left.length && left[le].isDigit()) le++
            var re = ri
            while (re < right.length && right[re].isDigit()) re++
            val lNum = left.substring(li, le)
            val rNum = right.substring(ri, re)
            val cmp = lNum.length.compareTo(rNum.length).takeIf { it != 0 } ?: lNum.compareTo(rNum)
            if (cmp != 0) return cmp
            li = le
            ri = re
        } else {
            val cmp = left[li].compareTo(right[ri])
            if (cmp != 0) return cmp
            li++
            ri++
        }
    }
    return (left.length - li).compareTo(right.length - ri)
}

/**
 * The write behind a group header's checkbox: a fully selected group clears,
 * anything else (partial or off) selects the whole group.
 */
internal fun toggledGroupSelection(selectedIds: Set<String>, groupIds: Collection<String>): Set<String> =
    if (groupIds.isNotEmpty() && groupIds.all { it in selectedIds }) {
        selectedIds - groupIds.toSet()
    } else {
        selectedIds + groupIds
    }
