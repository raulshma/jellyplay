package com.raulshma.jellyplay.feature.downloads

import androidx.compose.ui.state.ToggleableState
import com.raulshma.jellyplay.core.data.repository.DownloadProgress
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
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_active
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_completed
import com.raulshma.jellyplay.feature.downloads.generated.resources.downloads_status_failed
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
 * Status filter for the downloads list — All / Active / Completed / Failed
 * (gh-178): the queue screen shows every status mixed together, and the
 * reporter's ask was a surface that isolates what's in flight so it can be
 * controlled from inside the app. Active folds the four in-flight statuses
 * (PENDING/QUEUED/DOWNLOADING/PAUSED — the same set the per-row transfer
 * controls address) into one view; CANCELLED rows delete themselves on cancel
 * and never linger to be filtered.
 */
enum class DownloadStatusFilter(val labelRes: StringResource) {
    ALL(Res.string.downloads_filter_all),
    ACTIVE(Res.string.downloads_status_active),
    COMPLETED(Res.string.downloads_status_completed),
    FAILED(Res.string.downloads_status_failed),
    ;

    /** Whether [item] belongs in this filter's view. */
    fun accepts(item: DownloadItem): Boolean = when (this) {
        ALL -> true
        ACTIVE -> item.status in ACTIVE_STATUSES
        COMPLETED -> item.status == DownloadStatus.COMPLETED
        FAILED -> item.status == DownloadStatus.FAILED
    }

    companion object {
        /**
         * The in-flight statuses an Active view shows — the [DownloadBulkAction.CANCEL]
         * admission column, folded off the table so the filter can never drift
         * from the transfer controls it isolates.
         */
        val ACTIVE_STATUSES: Set<DownloadStatus> =
            requireNotNull(DownloadActions.admittedStatuses(DownloadBulkAction.CANCEL))

        /** Tolerant decode of the persisted value — an unknown name falls back to ALL. */
        fun fromName(name: String?): DownloadStatusFilter =
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

/**
 * Locally-resolved series artwork for a group header: the series poster saved
 * with the offline metadata at download time (a local file path — the
 * `OfflineMediaCard` rendering seam), keyed by raw series id.
 */
internal data class SeriesArt(val posterPath: String?, val blurHash: String?)

/** One rendered row of the grouped downloads list. */
internal sealed interface DownloadListRow {
    val key: String

    /**
     * A header over a group of episode ids (series or season) — the shape the
     * screen's shared header wiring (tri-state checkbox, transfer controls)
     * keys on, so both header kinds wire identically. Sealed so the row
     * `when` stays exhaustive over the three leaves.
     */
    sealed interface GroupHeader : DownloadListRow {
        val episodeIds: List<String>
    }

    /**
     * Expandable series group header. [episodeIds] backs the tri-state group
     * checkbox in selection mode (covers collapsed episodes too) and arrives
     * in the group's numeric playback order — the order the header's Play
     * action walks when hunting the next unwatched episode. [totalBytes] and
     * [completedCount] feed the header's size/progress summary line.
     * [imageUrl]/[imageBlurHash] prefer the series poster from the caller's
     * [SeriesArt] map, falling back to a group episode's still, so the header
     * carries artwork like every other row on the screen.
     */
    data class SeriesHeader(
        override val key: String,
        val name: String,
        val episodeCount: Int,
        val seasonCount: Int,
        override val episodeIds: List<String>,
        val totalBytes: Long,
        val completedCount: Int,
        val imageUrl: String? = null,
        val imageBlurHash: String? = null,
    ) : GroupHeader

    /** Season sub-header — only emitted while a series spans 2+ seasons. */
    data class SeasonHeader(
        override val key: String,
        val name: String?,
        val number: Int?,
        val episodeCount: Int,
        override val episodeIds: List<String>,
        val totalBytes: Long,
        val completedCount: Int,
    ) : GroupHeader

    /**
     * A single download row — exactly the flat-list entry the screen always
     * had. [insideGroup] marks an episode disclosed under an expanded group
     * header (series or season) so the screen can indent it under that header;
     * flat rows — movies and episodes with no series identity — stay false.
     */
    data class Item(val download: DownloadItem, val insideGroup: Boolean = false) : DownloadListRow {
        override val key = "download:${download.id}"
    }
}

/**
 * One series' downloaded episodes, grouped into ordered seasons — the
 * group-level unit the list is built from.
 */
private data class SeriesGroup(
    val key: String,
    /** Raw server series id, when the episodes carry one — the [seriesArt] lookup key. */
    val seriesId: String?,
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
 * [expandedKeys] decides which groups disclose their episodes. [seriesArt]
 * supplies locally-saved series posters for the group headers; a group whose
 * series has no saved poster (or whose episodes carry only stills) falls back
 * to the first completed episode's still art inside the emission itself.
 */
internal fun downloadListRows(
    downloads: List<DownloadItem>,
    filter: DownloadListFilter,
    sort: DownloadListSort,
    expandedKeys: Set<String>,
    seriesArt: Map<String, SeriesArt> = emptyMap(),
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
                    seriesId = groupEpisodes.firstNotNullOfOrNull { it.seriesId?.takeIf(String::isNotBlank) },
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
                    // Header artwork: the locally-saved series poster when one
                    // exists, else the first completed episode's still (then
                    // any episode's still) so the header matches the artwork
                    // every flat row carries. Blurhash follows whichever
                    // source won.
                    val art = group.seriesId?.let(seriesArt::get)
                    val episodes = group.seasons.flatMap { it.episodes }
                    val still = episodes.firstOrNull {
                        it.status == DownloadStatus.COMPLETED && !it.imageUrl.isNullOrBlank()
                    } ?: episodes.firstOrNull { !it.imageUrl.isNullOrBlank() }
                    add(
                        DownloadListRow.SeriesHeader(
                            key = group.key,
                            name = group.name,
                            episodeCount = group.episodeCount,
                            seasonCount = group.seasons.size,
                            episodeIds = episodes.map { it.id },
                            totalBytes = group.totalBytes,
                            completedCount = group.completedCount,
                            imageUrl = art?.posterPath ?: still?.imageUrl,
                            imageBlurHash = if (art?.posterPath != null) art.blurHash else still?.imageBlurHash,
                        ),
                    )
                    if (group.key !in expandedKeys) continue
                    if (group.seasons.size <= 1) {
                        group.seasons.firstOrNull()?.episodes?.forEach { add(DownloadListRow.Item(it, insideGroup = true)) }
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
                                season.episodes.forEach { add(DownloadListRow.Item(it, insideGroup = true)) }
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
 * The shared arithmetic behind both transfer fractions — Σ live-overridden
 * downloaded / Σ live-overridden totalSizeBytes over exactly [items]:
 * in-flight rows read their live bytes from [liveProgressById] and completed
 * ones count as landed. Rows whose total size is still unknown (0 — a
 * PENDING/QUEUED row before its size probe runs at transfer start) weigh in
 * at the AVERAGE known total of [items], per the header spec: the % spans the
 * whole download set INCLUDING queued media. The estimate is replaced by the
 * row's real size once its probe lands (the % may shift then — the accepted
 * cost of including unprobed rows at all), and an all-unknown scope reads 0.
 * The result is clamped to 0..1 (no known bytes → 0).
 *
 * The total is live-overridden exactly like the bytes: the engine learns the
 * size AFTER the row flipped to DOWNLOADING (and the resume path backfills
 * late — see [DownloadItemRow]'s matching merge), so the structural item can
 * still carry the insert-time 0 while the live row already has the
 * authoritative value. A live total of 0 (never probed) falls back to the
 * structural value — a known total is never zeroed by an unknown one.
 */
private fun summedTransferFraction(
    items: List<DownloadItem>,
    liveProgressById: Map<String, DownloadProgress>,
): Float {
    var downloaded = 0L
    var knownTotal = 0L
    var knownItems = 0
    var unknownItems = 0
    for (item in items) {
        val live = liveProgressById[item.id]
        downloaded += live?.downloadedBytes ?: item.downloadedBytes
        val total = live?.totalSizeBytes?.takeIf { it > 0L } ?: item.totalSizeBytes
        if (total > 0L) {
            knownTotal += total
            knownItems++
        } else {
            unknownItems++
        }
    }
    val total = if (unknownItems > 0 && knownItems > 0) {
        knownTotal + knownTotal / knownItems * unknownItems
    } else {
        knownTotal
    }
    return if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * The COLLAPSED group header's whole-group transfer fraction: Σ downloaded /
 * Σ totalSizeBytes over EVERY episode in the group — completed ones count as
 * landed, in-flight ones read their live bytes from [liveProgressById],
 * queued ones weigh in at the group's average known size ([summedTransferFraction])
 * — so the bar reads as "how far along is this series' download set" next to
 * the meta line's "N of M downloaded". Null unless at least one episode is in
 * the Active set (PENDING/QUEUED/DOWNLOADING/PAUSED — a paused-only group
 * still shows how far it got); a completed-only group hides the bar so
 * finished history can't pin a permanent 100%.
 */
internal fun downloadGroupProgressFraction(
    episodes: List<DownloadItem>,
    liveProgressById: Map<String, DownloadProgress>,
): Float? {
    if (episodes.none { it.status in DownloadStatusFilter.ACTIVE_STATUSES }) return null
    return summedTransferFraction(episodes, liveProgressById)
}

/**
 * The app bar's accumulated-ring fraction: [statuses] (the Active set) drives
 * VISIBILITY only — null when no item is in flight so finished history can't
 * pin the ring at 100% — while the SUMS span the whole to-download set
 * ([items] minus FAILED rows, which leave until retried so a dead row can't
 * stall the ring below a reachable 100%). Landed media staying in both sums
 * is the fix for the per-item rebase: with the queue transferring one media
 * at a time, an active-set-only fold made the ring climb to ~100% and
 * collapse toward 0 at every item boundary. Unknown totals estimate at the
 * scope's average ([summedTransferFraction]).
 */
internal fun aggregateProgressFraction(
    items: List<DownloadItem>,
    statuses: Set<DownloadStatus>,
    liveProgressById: Map<String, DownloadProgress>,
): Float? {
    if (items.none { it.status in statuses }) return null
    val inScope = items.filter { it.status != DownloadStatus.FAILED }
    return summedTransferFraction(inScope, liveProgressById)
}

/**
 * The episode a group header's Play action targets: the first UNPLAYED
 * COMPLETED download in the group's playback order (a header's
 * [DownloadListRow.SeriesHeader.episodeIds] order — numeric season → episode),
 * falling back to the first COMPLETED download when every completed episode in
 * the group is already watched. Rows still in flight are never targeted — the
 * COMPLETED-is-playable gate is the module's [DownloadActions.isPlayable]
 * fold, the same one the row-level Play applies — so a header
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
        DownloadActions.isPlayable(it) && it.mediaItemId !in playedItemIds
    } ?: groupDownloads.firstOrNull(DownloadActions::isPlayable)

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
