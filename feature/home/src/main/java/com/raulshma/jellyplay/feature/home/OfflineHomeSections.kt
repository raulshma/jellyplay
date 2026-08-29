package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.OFFLINE_WATCHED_THRESHOLD
import com.raulshma.jellyplay.core.model.toMediaItem
import java.util.PriorityQueue

/**
 * Filters the offline items by the current home mode: [HomeMode.MUSIC] keeps audio/music
 * types, everything else excludes them so video and music home screens never mix.
 */
internal fun filterOfflineByMode(
    items: List<OfflineMediaItem>,
    homeMode: HomeMode,
): List<OfflineMediaItem> {
    val musicTypes = setOf(MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM, MediaType.ARTIST)
    return if (homeMode == HomeMode.MUSIC) {
        items.filter { it.mediaType in musicTypes }
    } else {
        items.filter { it.mediaType !in musicTypes }
    }
}

/**
 * Id → offline-item lookup shared by the DOWNLOADED rows' original
 * re-resolution and the offline hero's click routing. Spans the library and
 * the downloaded episodes — the top-level library excludes episodes by design.
 */
internal fun offlineItemsById(
    library: List<OfflineMediaItem>,
    episodes: List<OfflineMediaItem>,
): Map<String, OfflineMediaItem> = (library + episodes).associateBy { it.id }

/**
 * Localized row titles for the offline-derived home sections. Resolved at the
 * call site (stringResource is composable) and passed into the pure
 * [buildOfflineHomeSections], keeping the mapper unit-testable.
 */
@Immutable
internal data class OfflineHomeSectionTitles(
    val continueWatching: String,
    val nextUp: String,
    val recentlyDownloaded: String,
    val movies: String,
    val series: String,
    val music: String,
)

@Composable
internal fun rememberOfflineHomeSectionTitles(): OfflineHomeSectionTitles =
    OfflineHomeSectionTitles(
        continueWatching = stringResource(R.string.home_continue_watching),
        nextUp = stringResource(R.string.home_next_up),
        recentlyDownloaded = stringResource(R.string.home_recently_downloaded),
        movies = stringResource(R.string.home_movies),
        series = stringResource(R.string.home_series),
        music = stringResource(R.string.home_music),
    )

/**
 * The CW/NextUp prefs the offline home rows honor, mirrored from the same
 * prefs snapshot that builds the online [com.raulshma.jellyplay.core.model.HomeSectionQuery]
 * (see [HomeViewModel]'s prefs collector) so the offline home never contradicts
 * the user's online home layout. Public because [HomeUiState] exposes it.
 */
@Immutable
data class OfflineHomeSectionPrefs(
    val continueWatchingEnabled: Boolean = true,
    val nextUpEnabled: Boolean = true,
    val hiddenCwItemIds: Set<String> = emptySet(),
    val nextUpExcludedSeriesIds: Set<String> = emptySet(),
    val mergeCwAndNextUp: Boolean = false,
)

/** Number of items shown in the "Recently Downloaded" row. */
private const val RECENT_LIMIT = 10

/**
 * Row cap for the offline Next Up section — mirrors the network layer's
 * default `getNextUp(limit = 20)`.
 */
private const val NEXT_UP_LIMIT = 20

/**
 * Played percentage at which an item stops counting as in-progress — derived
 * from [OFFLINE_WATCHED_THRESHOLD] (the display normalization in
 * [com.raulshma.jellyplay.core.model.OfflineMediaItem.toMediaItem] and the
 * player's watched trigger) so every surface agrees on "finished".
 */
private const val WATCHED_PERCENTAGE = OFFLINE_WATCHED_THRESHOLD * 100

/**
 * Derives the home sections shown while offline from the (mode-filtered)
 * offline library and its downloaded episodes. Every section is typed
 * [HomeSectionType.DOWNLOADED] so [com.raulshma.jellyplay.feature.home.HomeContentList]
 * renders it through the offline card row (local artwork, offline click
 * routing) while keeping the online home's section chrome — the offline home IS
 * the normal home, just populated from downloads (issue #147).
 *
 * Rows, each omitted when empty or disabled by [OfflineHomeSectionPrefs]:
 *  - Continue Watching — movies + episodes with 1–95% progress, most recently
 *    played first, minus the user's hidden CW items.
 *  - Next Up — per downloaded series, the first not-finished downloaded
 *    episode in season/episode order; series ordered by most recent watch,
 *    capped at [NEXT_UP_LIMIT], minus the user's excluded series. When
 *    [OfflineHomeSectionPrefs.mergeCwAndNextUp] is set, Next Up items are
 *    appended into the Continue Watching row instead (deduplicated), mirroring
 *    the online [com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase].
 *  - Recently Downloaded (newest [RECENT_LIMIT] by download date), then
 *    Movies / Series / Music.
 *
 * Items are mapped to [MediaItem] for section identity (keys, focus, dedupe);
 * the offline originals are re-resolved by id at render time from the same
 * lists, so local poster paths stay available to the cards.
 */
internal fun buildOfflineHomeSections(
    library: List<OfflineMediaItem>,
    episodes: List<OfflineMediaItem>,
    titles: OfflineHomeSectionTitles,
    prefs: OfflineHomeSectionPrefs,
): List<HomeSection> {
    if (library.isEmpty() && episodes.isEmpty()) return emptyList()

    // Single pass over the library: partition by type and track the newest
    // RECENT_LIMIT items via a bounded min-heap (O(n log k) instead of a full
    // sort on every offline-library emission).
    val movies = ArrayList<OfflineMediaItem>()
    val series = ArrayList<OfflineMediaItem>()
    val music = ArrayList<OfflineMediaItem>()
    val recentHeap = PriorityQueue<OfflineMediaItem>(compareBy { it.createdAt })
    for (item in library) {
        when (item.mediaType) {
            MediaType.MOVIE -> movies += item
            MediaType.SERIES -> series += item
            MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM -> music += item
            // Other types (PHOTO, PHOTO_FOLDER, …) have no home row here.
            else -> Unit
        }
        if (recentHeap.size < RECENT_LIMIT) {
            recentHeap.add(item)
        } else {
            val oldest = recentHeap.peek()
            if (oldest != null && item.createdAt > oldest.createdAt) {
                recentHeap.poll()
                recentHeap.add(item)
            }
        }
    }
    val recent = recentHeap.sortedByDescending { it.createdAt }

    // Continue Watching spans movies AND downloaded episodes (episodes are
    // excluded from the top-level library by design, hence the separate list).
    // SERIES rows are dropped: their aggregate progress is a hierarchy echo,
    // not a resume point — the episodes themselves carry the real progress.
    val continueWatching = if (prefs.continueWatchingEnabled) {
        (library.asSequence().filter { it.mediaType != MediaType.SERIES } + episodes.asSequence())
            .filter { it.playedPercentage >= 1.0 && it.playedPercentage < WATCHED_PERCENTAGE }
            .filter { it.id !in prefs.hiddenCwItemIds }
            .sortedWith(
                compareByDescending<OfflineMediaItem> { it.lastPlayedDate ?: "" }
                    .thenByDescending { it.createdAt }
            )
            .toList()
    } else {
        emptyList()
    }

    val nextUp = if (prefs.nextUpEnabled) {
        computeOfflineNextUp(episodes, prefs.nextUpExcludedSeriesIds)
    } else {
        emptyList()
    }

    // Merge mode folds Next Up into the Continue Watching row (deduped) and
    // drops the separate row — the offline mirror of the online merge pref.
    val mergedContinueWatching = if (prefs.mergeCwAndNextUp && nextUp.isNotEmpty()) {
        val seen = continueWatching.mapTo(HashSet()) { it.id }
        continueWatching + nextUp.filter { it.id !in seen }
    } else {
        continueWatching
    }
    val showNextUpRow = prefs.nextUpEnabled && !prefs.mergeCwAndNextUp && nextUp.isNotEmpty()

    return buildList {
        if (mergedContinueWatching.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_continue_watching",
                    title = titles.continueWatching,
                    type = HomeSectionType.DOWNLOADED,
                    items = mergedContinueWatching.map { it.toMediaItem() },
                )
            )
        }
        if (showNextUpRow) {
            add(
                HomeSection(
                    id = "offline_next_up",
                    title = titles.nextUp,
                    type = HomeSectionType.DOWNLOADED,
                    items = nextUp.map { it.toMediaItem() },
                )
            )
        }
        if (recent.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_recently_downloaded",
                    title = titles.recentlyDownloaded,
                    type = HomeSectionType.DOWNLOADED,
                    items = recent.map { it.toMediaItem() },
                )
            )
        }
        if (movies.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_movies",
                    title = titles.movies,
                    type = HomeSectionType.DOWNLOADED,
                    items = movies.map { it.toMediaItem() },
                )
            )
        }
        if (series.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_series",
                    title = titles.series,
                    type = HomeSectionType.DOWNLOADED,
                    items = series.map { it.toMediaItem() },
                )
            )
        }
        if (music.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_music",
                    title = titles.music,
                    type = HomeSectionType.DOWNLOADED,
                    items = music.map { it.toMediaItem() },
                )
            )
        }
    }
}

/**
 * Offline Next Up: for each downloaded series, the first not-finished
 * downloaded episode in season/episode order (Jellyfin's server-side rule,
 * restricted to what is on the device). Series rows are ordered by the most
 * recent watch activity across their episodes — the show the user was last
 * watching surfaces the next episode first — then by series id for a stable
 * order among untouched series. Excluded series are dropped; the row is
 * capped at [NEXT_UP_LIMIT].
 *
 * Strict chronology: an episode before a downloaded gap (e.g. S1E5 not
 * downloaded, S1E6 downloaded) is never skipped past — S1E6 only surfaces
 * after S1E5 is watched or downloaded, matching server semantics rather than
 * inventing an order the user didn't watch in.
 */
private fun computeOfflineNextUp(
    episodes: List<OfflineMediaItem>,
    excludedSeriesIds: Set<String>,
): List<OfflineMediaItem> {
    if (episodes.isEmpty()) return emptyList()

    /** A series' next-up candidate plus its most recent watch activity. */
    data class SeriesNextUp(
        val episode: OfflineMediaItem,
        val lastWatched: String,
    )

    val bySeries = LinkedHashMap<String, MutableList<OfflineMediaItem>>()
    for (episode in episodes) {
        val seriesId = episode.seriesId ?: continue
        if (seriesId in excludedSeriesIds) continue
        bySeries.getOrPut(seriesId) { ArrayList() }.add(episode)
    }
    val candidates = ArrayList<SeriesNextUp>(bySeries.size)
    for ((_, group) in bySeries) {
        // Season/episode order; nulls first (mirrors the DAO query's ASC sort).
        val ordered = group.sortedWith(
            compareBy(
                { it.seasonNumber ?: Int.MIN_VALUE },
                { it.episodeNumber ?: Int.MIN_VALUE },
            )
        )
        val next = ordered.firstOrNull { !it.isPlayed && it.playedPercentage < WATCHED_PERCENTAGE } ?: continue
        candidates += SeriesNextUp(next, ordered.maxOf { it.lastPlayedDate ?: "" })
    }
    return candidates
        .sortedWith(
            compareByDescending<SeriesNextUp> { it.lastWatched }
                .thenBy { it.episode.seriesId }
        )
        .take(NEXT_UP_LIMIT)
        .map { it.episode }
}
