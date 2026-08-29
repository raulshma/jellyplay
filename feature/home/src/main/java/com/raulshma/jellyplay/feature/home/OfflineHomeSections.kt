package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.toMediaItem
import java.util.PriorityQueue

/**
 * Filters the offline library by the current home mode: [HomeMode.MUSIC] keeps audio/music
 * types, everything else excludes them so video and music home screens never mix.
 */
internal fun filterOfflineByMode(
    library: List<OfflineMediaItem>,
    homeMode: HomeMode,
): List<OfflineMediaItem> {
    val musicTypes = setOf(MediaType.AUDIO, MediaType.MUSIC, MediaType.ALBUM, MediaType.ARTIST)
    return if (homeMode == HomeMode.MUSIC) {
        library.filter { it.mediaType in musicTypes }
    } else {
        library.filter { it.mediaType !in musicTypes }
    }
}

/**
 * Localized row titles for the offline-derived home sections. Resolved at the
 * call site (stringResource is composable) and passed into the pure
 * [buildOfflineHomeSections], keeping the mapper unit-testable.
 */
@Immutable
internal data class OfflineHomeSectionTitles(
    val continueWatching: String,
    val recentlyDownloaded: String,
    val movies: String,
    val series: String,
    val music: String,
)

@Composable
internal fun rememberOfflineHomeSectionTitles(): OfflineHomeSectionTitles =
    OfflineHomeSectionTitles(
        continueWatching = stringResource(R.string.home_continue_watching),
        recentlyDownloaded = stringResource(R.string.home_recently_downloaded),
        movies = stringResource(R.string.home_movies),
        series = stringResource(R.string.home_series),
        music = stringResource(R.string.home_music),
    )

/** Number of items shown in the "Recently Downloaded" row. */
private const val RECENT_LIMIT = 10

/**
 * Derives the home sections shown while offline from the (mode-filtered)
 * offline library. Every section is typed [HomeSectionType.DOWNLOADED] so
 * [com.raulshma.jellyplay.feature.home.HomeContentList] renders it through the
 * offline card row (local artwork, offline click routing) while keeping the
 * online home's section chrome — the offline home IS the normal home, just
 * populated from downloads (issue #147).
 *
 * Rows: Continue Watching (1–95% progress, most recently played first),
 * Recently Downloaded (newest [RECENT_LIMIT] by download date), then
 * Movies / Series / Music. Empty partitions are omitted.
 *
 * Items are mapped to [MediaItem] for section identity (keys, focus, dedupe);
 * the offline originals are re-resolved by id at render time from the same
 * list, so local poster paths stay available to the cards.
 */
internal fun buildOfflineHomeSections(
    library: List<OfflineMediaItem>,
    titles: OfflineHomeSectionTitles,
): List<HomeSection> {
    if (library.isEmpty()) return emptyList()

    // Single pass: partition by type, collect continue-watching, and track the
    // newest RECENT_LIMIT items via a bounded min-heap (O(n log k) instead of a
    // full sort on every offline-library emission).
    val continueWatching = ArrayList<OfflineMediaItem>()
    val movies = ArrayList<OfflineMediaItem>()
    val series = ArrayList<OfflineMediaItem>()
    val music = ArrayList<OfflineMediaItem>()
    val recentHeap = PriorityQueue<OfflineMediaItem>(compareBy { it.createdAt })
    for (item in library) {
        if (item.playedPercentage in 1.0..94.99) continueWatching += item
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
    continueWatching.sortWith(
        compareByDescending<OfflineMediaItem> { it.lastPlayedDate ?: "" }
            .thenByDescending { it.createdAt }
    )
    val recent = recentHeap.sortedByDescending { it.createdAt }

    return buildList {
        if (continueWatching.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_continue_watching",
                    title = titles.continueWatching,
                    type = HomeSectionType.DOWNLOADED,
                    items = continueWatching.map { it.toMediaItem() },
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
