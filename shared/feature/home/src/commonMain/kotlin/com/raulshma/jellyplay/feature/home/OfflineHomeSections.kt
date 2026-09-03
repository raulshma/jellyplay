package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.home.generated.resources.Res
import com.raulshma.jellyplay.feature.home.generated.resources.home_continue_watching
import com.raulshma.jellyplay.feature.home.generated.resources.home_movies
import com.raulshma.jellyplay.feature.home.generated.resources.home_music
import com.raulshma.jellyplay.feature.home.generated.resources.home_next_up
import com.raulshma.jellyplay.feature.home.generated.resources.home_recently_downloaded
import com.raulshma.jellyplay.feature.home.generated.resources.home_series
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaTypeGroup
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.isFinishedOffline
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.model.typeGroup
import java.util.PriorityQueue

/**
 * Everything the offline home renders, derived in one place. The screen
 * remembers ONE [OfflineHomeContent] per (library, episodes, mode, titles,
 * prefs) change and passes it down as a single value — the derived sections,
 * the id→item lookup (built once per emission, shared by the DOWNLOADED rows
 * and the hero's click routing) and the raw lists the inline Downloaded row's
 * dedupe reads. Previously each consumer re-derived its own slice from the
 * UiState mirrors, and [itemsById] was built twice per tree on different
 * remember keys.
 *
 * The row titles are localized strings, so the aggregate is built at the
 * call site ([rememberOfflineHomeSectionTitles] is composable) — the UiState
 * mirrors keep carrying the raw repository emissions.
 */
@Immutable
internal data class OfflineHomeContent(
    /** Mode-filtered library (the render-relevant slice; episodes excluded by design). */
    val library: List<OfflineMediaItem>,
    /** Mode-filtered downloaded episodes feeding CW / Next Up. */
    val episodes: List<OfflineMediaItem>,
    /** The offline-derived sections (CW/Next Up keep their online types so
     * they render through the same wide-card row; the rest are [HomeSectionType.DOWNLOADED]). */
    val sections: List<HomeSection>,
    /** Id → item across [library] + [episodes] — built once, here. */
    val itemsById: Map<String, OfflineMediaItem>,
)

/**
 * Derives the offline home's render model in one pass. See [OfflineHomeContent].
 */
internal fun buildOfflineHomeContent(
    library: List<OfflineMediaItem>,
    episodes: List<OfflineMediaItem>,
    homeMode: HomeMode,
    titles: OfflineHomeSectionTitles,
    prefs: OfflineHomeSectionPrefs,
    cachedLayout: List<HomeSection> = emptyList(),
): OfflineHomeContent {
    val filteredLibrary = filterOfflineByMode(library, homeMode)
    val filteredEpisodes = filterOfflineByMode(episodes, homeMode)
    return OfflineHomeContent(
        library = filteredLibrary,
        episodes = filteredEpisodes,
        sections = buildOfflineHomeSections(filteredLibrary, filteredEpisodes, titles, prefs, cachedLayout),
        itemsById = offlineItemsById(filteredLibrary, filteredEpisodes),
    )
}

/**
 * Filters the offline items by the current home mode: [HomeMode.MUSIC] keeps
 * music-shelf types, everything else excludes them so video and music home
 * screens never mix. The partition itself is [OfflineMediaTypeGroup] — the
 * one shared with the downloads screen's filter.
 */
internal fun filterOfflineByMode(
    items: List<OfflineMediaItem>,
    homeMode: HomeMode,
): List<OfflineMediaItem> =
    if (homeMode == HomeMode.MUSIC) {
        items.filter { it.typeGroup == OfflineMediaTypeGroup.MUSIC }
    } else {
        items.filter { it.typeGroup != OfflineMediaTypeGroup.MUSIC }
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
        continueWatching = stringResource(Res.string.home_continue_watching),
        nextUp = stringResource(Res.string.home_next_up),
        recentlyDownloaded = stringResource(Res.string.home_recently_downloaded),
        movies = stringResource(Res.string.home_movies),
        series = stringResource(Res.string.home_series),
        music = stringResource(Res.string.home_music),
    )

/**
 * The CW/NextUp prefs the offline home rows honor, mirrored from the same
 * prefs snapshot that builds the online [com.raulshma.jellyplay.core.model.HomeSectionQuery]
 * (see [HomeViewModel]'s prefs collector) so the offline home never contradicts
 * the user's online home layout. [sectionOrder] additionally carries the
 * user's global section ordering: every offline row sorts by it (mirror and
 * fallback alike — the persisted snapshot's raw order is the network fetch
 * order, not the user's layout), while types absent from the order (the
 * offline-only DOWNLOADED rows) keep their fixed tail order. Public because
 * [HomeUiState] exposes it.
 */
@Immutable
data class OfflineHomeSectionPrefs(
    val continueWatchingEnabled: Boolean = true,
    val nextUpEnabled: Boolean = true,
    /** All currently-enabled configurable section types — filters the cached-layout mirror (#147). */
    val enabledSectionTypes: Set<HomeSectionType> = HomeSectionType.CONFIGURABLE.toSet(),
    /** Per-library DISABLED types, keyed by library id — same shape as the online overrides. */
    val libraryOverrides: Map<String, Set<HomeSectionType>> = emptyMap(),
    val hiddenCwItemIds: Set<String> = emptySet(),
    val nextUpExcludedSeriesIds: Set<String> = emptySet(),
    val mergeCwAndNextUp: Boolean = false,
    val sectionOrder: List<HomeSectionType> = HomeSectionType.CONFIGURABLE,
    /** Mirrors the online Next Up rewatching toggle (`enableRewatching`). */
    val nextUpRewatching: Boolean = false,
    /** Mirrors the online Next Up date cutoff in days (`nextUpDateCutoff`); 0 = no cutoff. */
    val nextUpMaxDays: Int = 0,
)

/** Number of items shown in the "Recently Downloaded" row. */
private const val RECENT_LIMIT = 10

/**
 * Row cap for the offline Next Up section — mirrors the network layer's
 * default `getNextUp(limit = 20)`.
 */
private const val NEXT_UP_LIMIT = 20

/**
 * Row cap for the offline Continue Watching section — mirrors the network
 * layer's default `getResumeItems(limit = 20)`.
 */
private const val CONTINUE_WATCHING_LIMIT = 20

/**
 * Derives the home sections shown while offline from the (mode-filtered)
 * offline library and its downloaded episodes. Two shapes, in precedence
 * order (issue #147: "literally the home layout, filtered for downloaded"):
 *
 *  1. **Cached-layout mirror** — when [cachedLayout] (the last persisted
 *     online snapshot) is non-empty and at least one of its rows survives the
 *     downloaded filter, the offline home reproduces the ONLINE layout:
 *     same section types, titles (per-library "Latest …" rows,
 *     recommendation "Because you watched …" headers) and library ids, with
 *     each row's items filtered to what is downloaded. Continue Watching /
 *     Next Up keep their sections but render the locally derived lists (local
 *     playback progress is fresher than the snapshot). Rows the user has
 *     since disabled via the CURRENT prefs drop out, and the surviving rows
 *     re-sort by the user's CURRENT section order — the snapshot is persisted
 *     in fetch order (pre-ordering), so its raw order is the network build
 *     order, not the user's layout.
 *  2. **Generic fallback** — no snapshot (fresh install / cleared data) or
 *     every mirrored row filtered to empty: the historical fixed rows.
 *
 * Continue Watching and Next Up keep their online types
 * ([HomeSectionType.CONTINUE_WATCHING] / [HomeSectionType.NEXT_UP]) so
 * [com.raulshma.jellyplay.feature.home.HomeContentList] renders them through
 * the same wide-card row as the online home; every other offline section
 * (mirrored or fallback) renders through the offline poster-card row.
 *
 * Rows, each omitted when empty or disabled by [OfflineHomeSectionPrefs]:
 *  - Continue Watching — downloaded movies + episodes with a resume position
 *    (the server's `IsResumable` rule: position > 0, under the watched
 *    threshold), most recently played first, minus the user's hidden CW
 *    items, capped at [CONTINUE_WATCHING_LIMIT].
 *  - Next Up — the local mirror of Jellyfin's server-side rule over the
 *    downloaded episodes (see [computeOfflineNextUp]): per series with watch
 *    activity, the first unplayed episode strictly after the highest played
 *    one; mid-watch (resumable) episodes stay in Continue Watching instead.
 *    When [OfflineHomeSectionPrefs.mergeCwAndNextUp] is set, Next Up items
 *    are appended into the Continue Watching row instead (deduplicated),
 *    mirroring the online
 *    [com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase].
 *  - Recently Downloaded (newest [RECENT_LIMIT] by download date), then
 *    Movies / Series / Music.
 *
 * All rows — mirrored and fallback alike — sort by the user's global section
 * ordering ([OfflineHomeSectionPrefs.sectionOrder]); types absent from the
 * order (offline-only DOWNLOADED rows, non-configurable mirror types) keep
 * their relative order after the configured ones — see [orderOfflineSections].
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
    cachedLayout: List<HomeSection> = emptyList(),
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

    // Continue Watching mirrors the server's resume query
    // (ItemsController.GetResumeItems → IsResumable): non-folder items with a
    // playback position, sorted DatePlayed-desc. Downloaded SERIES rows are
    // dropped: their aggregate progress is a hierarchy echo, not a resume
    // point — the episodes themselves carry the real progress. The server
    // zeroes the position once an item is played (UserDataManager marks
    // MaxResumePct-crossing plays complete), so the local mirrors of those
    // rules are `position > 0`, the app's 95% watched threshold, and a small
    // minimum-progress floor (the server's MinResumePct analog — a few
    // seconds scrubbed into a file is not a resume point).
    val continueWatching = if (prefs.continueWatchingEnabled) {
        (library.asSequence().filter { it.mediaType != MediaType.SERIES } + episodes.asSequence())
            .filter { it.hasResumePosition() }
            .filter { it.playedPercentage >= 1.0 && !it.isPlayed && !it.isFinishedOffline }
            .filter { it.id !in prefs.hiddenCwItemIds }
            .sortedWith(
                compareByDescending<OfflineMediaItem> { isoEpochMillis(it.lastPlayedDate) ?: Long.MIN_VALUE }
                    .thenByDescending { it.createdAt }
            )
            .take(CONTINUE_WATCHING_LIMIT)
            .toList()
    } else {
        emptyList()
    }

    val nextUp = if (prefs.nextUpEnabled) {
        computeOfflineNextUp(episodes, prefs)
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

    val sections = buildList {
        if (mergedContinueWatching.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_continue_watching",
                    title = titles.continueWatching,
                    type = HomeSectionType.CONTINUE_WATCHING,
                    items = mergedContinueWatching.map { it.toMediaItem() },
                )
            )
        }
        if (showNextUpRow) {
            add(
                HomeSection(
                    id = "offline_next_up",
                    title = titles.nextUp,
                    type = HomeSectionType.NEXT_UP,
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
    // Cached-layout mirror first (#147): when the snapshot exists and yields
    // at least one row, it IS the offline layout. Generic fallback rows are
    // then APPENDED for content the mirror does not already surface — so a
    // download whose snapshot row dropped (type disabled at fetch time, empty
    // mirror filter) is still reachable, and re-enabled-while-offline types
    // degrade to their nearest generic row instead of vanishing.
    if (cachedLayout.isNotEmpty()) {
        val itemsById = offlineItemsById(library, episodes)
        val mirrored = mirrorCachedLayoutSections(
            cachedLayout,
            itemsById,
            prefs,
            titles,
            DerivedCwNextUp(mergedContinueWatching, showNextUpRow, nextUp),
        )
        if (mirrored.isNotEmpty()) {
            val covered = mirrored
                .asSequence()
                .flatMap { it.items.asSequence() }
                .mapTo(HashSet()) { it.id }
            // Mirror + fallback rows sort TOGETHER by the user's CURRENT
            // section order: the snapshot is persisted in fetch order (the
            // repo writes it before OrderHomeSectionsUseCase runs online), so
            // its raw order is the network build order, not the user's
            // layout. Stable sort keeps same-type rows (per-library
            // "Latest …") in snapshot order and leaves the offline-only
            // DOWNLOADED rows at the tail. Each fallback row keeps only its
            // uncovered items: a partially covered generic row must not
            // re-show its covered items in a second row (mirrored row +
            // fallback tail).
            val fallback = sections.mapNotNull { row ->
                val uncovered = row.items.filter { it.id !in covered }
                if (uncovered.isEmpty()) null else row.copy(items = uncovered)
            }
            return orderOfflineSections(mirrored + fallback, prefs.sectionOrder)
        }
    }

    return orderOfflineSections(sections, prefs.sectionOrder)
}

/**
 * The locally derived Continue Watching / Next Up values that always travel
 * together into the layout mirror: the merged CW∪Next-Up list (merge pref
 * set), whether the separate Next Up row still renders, and that row's items.
 * Derived once in [buildOfflineHomeSections].
 */
private data class DerivedCwNextUp(
    val mergedContinueWatching: List<OfflineMediaItem>,
    val showNextUpRow: Boolean,
    val nextUp: List<OfflineMediaItem>,
)

/**
 * Mirrors the cached online layout onto the offline home: each snapshot row
 * survives with its type/title/libraryId intact, its items filtered to
 * the downloaded [itemsById] originals. CW / Next Up swap in the locally
 * derived lists (local progress beats the snapshot). Rows drop when the
 * CURRENT prefs disable their type (or the per-library override for a
 * LATEST_MEDIA row), when nothing in them is downloaded, or when unplayable
 * offline (LIVE_TV). Row order is re-normalized by the caller against the
 * user's CURRENT section order — the snapshot is persisted in fetch order
 * (before [com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase]
 * runs online), so same-type rows keep their snapshot relative order via the
 * stable sort, but the rows themselves do NOT keep the snapshot's raw order.
 *
 * Coverage: rows whose content the mirror cannot surface are not lost —
 * [buildOfflineHomeSections] appends the generic fallback rows for any item
 * id the mirrored rows do not already show, so every download stays reachable
 * while offline even when its snapshot row dropped or its type was absent
 * from the snapshot.
 */
private fun mirrorCachedLayoutSections(
    cachedLayout: List<HomeSection>,
    itemsById: Map<String, OfflineMediaItem>,
    prefs: OfflineHomeSectionPrefs,
    titles: OfflineHomeSectionTitles,
    cwNextUp: DerivedCwNextUp,
): List<HomeSection> = buildList {
    for (cached in cachedLayout) {
        when (cached.type) {
            HomeSectionType.CONTINUE_WATCHING -> {
                if (prefs.continueWatchingEnabled && cwNextUp.mergedContinueWatching.isNotEmpty()) {
                    add(
                        HomeSection(
                            id = "offline_continue_watching",
                            title = titles.continueWatching,
                            type = HomeSectionType.CONTINUE_WATCHING,
                            items = cwNextUp.mergedContinueWatching.map { it.toMediaItem() },
                        )
                    )
                }
            }
            HomeSectionType.NEXT_UP -> {
                if (cwNextUp.showNextUpRow) {
                    add(
                        HomeSection(
                            id = "offline_next_up",
                            title = titles.nextUp,
                            type = HomeSectionType.NEXT_UP,
                            items = cwNextUp.nextUp.map { it.toMediaItem() },
                        )
                    )
                }
            }
            // Unplayable offline; DOWNLOADED never appears in an online snapshot.
            HomeSectionType.LIVE_TV, HomeSectionType.DOWNLOADED -> Unit
            else -> {
                // Configurable types honor the CURRENT enablement (the snapshot
                // reflects prefs at fetch time; a toggle made while offline wins).
                if (cached.type.isConfigurable && cached.type !in prefs.enabledSectionTypes) continue
                val libraryId = cached.libraryId
                if (libraryId != null && cached.type in prefs.libraryOverrides[libraryId].orEmpty()) continue
                val downloaded = cached.items.mapNotNull { itemsById[it.id] }
                if (downloaded.isNotEmpty()) {
                    add(
                        HomeSection(
                            id = "offline_${cached.id}",
                            title = cached.title,
                            type = cached.type,
                            items = downloaded.map { it.toMediaItem() },
                            seedItem = if (cached.type == HomeSectionType.RECOMMENDATIONS) cached.seedItem else null,
                            libraryId = libraryId,
                            collectionType = cached.collectionType,
                        )
                    )
                }
            }
        }
    }
}

/**
 * Orders the offline rows — mirrored snapshot rows and generic fallback rows
 * alike — by the user's global section ordering. Without this the offline
 * mirror would show the network FETCH order (the snapshot is persisted before
 * OrderHomeSectionsUseCase runs online), not the user's configured layout.
 * Stable sort: same-type rows (per-library "Latest …") keep their relative
 * order, and rows whose type is absent from the order — offline-only rows
 * (Recently Downloaded, Movies, Series, Music, all DOWNLOADED) and
 * non-configurable mirror types — sort AFTER the configured types in their
 * build order; the default order ([HomeSectionType.CONFIGURABLE], CW before
 * Next Up) reproduces the historical fixed layout exactly.
 */
internal fun orderOfflineSections(
    sections: List<HomeSection>,
    sectionOrder: List<HomeSectionType>,
): List<HomeSection> = sections.sortedBy { section ->
    val index = sectionOrder.indexOf(section.type)
    if (index >= 0) index else sectionOrder.size
}

/**
 * Offline Next Up — the local mirror of Jellyfin's server-side rule
 * (TVSeriesManager + NextUpService), restricted to what is downloaded:
 *
 *  1. **Series selection** — only series with watch activity (any downloaded
 *     episode carrying a parseable `lastPlayedDate`); ordered by most recent activity,
 *     capped, and dropped entirely when older than the user's Next Up date
 *     cutoff (`nextUpMaxDays`, the `nextUpDateCutoff` param the online fetch
 *     sends).
 *  2. **Anchor** — the highest (season, episode) PLAYED episode (specials
 *     excluded, matching the server's `ParentIndexNumber != 0` filter).
 *  3. **Next episode** — the first UNPLAYED episode strictly AFTER the
 *     anchor in (season, episode) order; with no played episode yet, the
 *     first unplayed episode of the series. A candidate that already has a
 *     resume position is skipped (server: `EnableResumable = false` —
 *     mid-watch episodes live in Continue Watching, not Next Up).
 *  4. **Rewatching** — with the pref on (the online `enableRewatching`
 *     param), a second per-series pass picks the first PLAYED episode after
 *     the most-recently-played one, appended alongside the regular entry.
 *
 * Entries sort by their series' most recent watch activity (most recent
 * first), then series id for stability; the row is capped at [NEXT_UP_LIMIT].
 * Sorting by the series activity — not the anchor episode's date — keeps a
 * series whose only activity is a resumable (unplayed) episode ranked by
 * when that watch happened instead of sinking to the row's tail.
 *
 * Stored `lastPlayedDate` strings mix server-synced ISO stamps (UTC `Z`,
 * nanosecond fraction) with local `OffsetDateTime.now().toString()` writes
 * (host-zone offset, variable precision), so every date ordering and the
 * cutoff compare go through [isoEpochMillis] — those forms are NOT
 * lexicographically comparable (a `+02:00` stamp vs a `Z` stamp mis-orders
 * by hours).
 *
 * Named divergence: the server additionally interleaves specials
 * (`DisplaySpecialsWithinSeasons`) via aired-before/after ordering — the
 * offline row does not persist that metadata, so specials (season 0) are
 * excluded here, matching the server's base season/episode order.
 */
private fun computeOfflineNextUp(
    episodes: List<OfflineMediaItem>,
    prefs: OfflineHomeSectionPrefs,
): List<OfflineMediaItem> {
    if (episodes.isEmpty()) return emptyList()

    /** One next-up row entry: the episode plus its sort key (series activity, epoch millis). */
    data class NextUpEntry(
        val episode: OfflineMediaItem,
        val lastWatched: Long,
        val seriesId: String,
    )

    // Specials (season 0) and unsighted episodes (null season) are not
    // Next Up material — the server's `ParentIndexNumber != 0` filter.
    val nonSpecials = episodes.filter { it.seasonNumber != null && it.seasonNumber != 0 }

    val bySeries = LinkedHashMap<String, MutableList<OfflineMediaItem>>()
    for (episode in nonSpecials) {
        val seriesId = episode.seriesId ?: continue
        if (seriesId in prefs.nextUpExcludedSeriesIds) continue
        bySeries.getOrPut(seriesId) { ArrayList() }.add(episode)
    }

    // Same cutoff the online fetch sends as `nextUpDateCutoff`: now - maxDays.
    val cutoffMillis = prefs.nextUpMaxDays.takeIf { it > 0 }
        ?.let { System.currentTimeMillis() - it.toLong() * MILLIS_PER_DAY }

    val entries = ArrayList<NextUpEntry>(bySeries.size)
    for ((seriesId, group) in bySeries) {
        // Season/episode order; nulls first (mirrors the DAO query's ASC sort).
        val ordered = group.sortedWith(seasonEpisodeOrder)

        // Series eligibility: any watch activity, within the date cutoff.
        val lastActivityMillis =
            ordered.mapNotNull { isoEpochMillis(it.lastPlayedDate) }.maxOrNull() ?: continue
        if (cutoffMillis != null && lastActivityMillis < cutoffMillis) continue

        // Anchor: the highest played episode by (season, episode).
        val anchor = ordered.lastOrNull { it.isPlayed }

        // First unplayed episode strictly after the anchor (all unplayed
        // when nothing is played yet). Resumable candidates are skipped —
        // they render in Continue Watching instead.
        val unplayed = ordered.asSequence().filter { !it.isPlayed }
        val regularCandidate =
            (if (anchor != null) unplayed.filter { isAfter(it, anchor) } else unplayed)
                .filter { !it.hasResumePosition() }
                .firstOrNull()
        if (regularCandidate != null) {
            entries += NextUpEntry(regularCandidate, lastActivityMillis, seriesId)
        }

        // Rewatch pass: the first PLAYED episode after the most-recently
        // played one (server keys the rewatch anchor by date, not position).
        if (prefs.nextUpRewatching) {
            val played = ordered.filter { it.isPlayed }
            val dateAnchor =
                played.maxByOrNull { isoEpochMillis(it.lastPlayedDate) ?: Long.MIN_VALUE }
            if (dateAnchor != null) {
                val rewatchCandidate = played
                    .filter { isAfter(it, dateAnchor) }
                    .filter { !it.hasResumePosition() }
                    .minWithOrNull(seasonEpisodeOrder)
                if (rewatchCandidate != null) {
                    entries += NextUpEntry(rewatchCandidate, lastActivityMillis, seriesId)
                }
            }
        }
    }

    return entries
        .sortedWith(
            compareByDescending<NextUpEntry> { it.lastWatched }
                .thenBy { it.seriesId }
        )
        .take(NEXT_UP_LIMIT)
        .map { it.episode }
}

/** (season, episode) order with nulls first — the one ordering every Next Up pass shares. */
private val seasonEpisodeOrder: Comparator<OfflineMediaItem> = compareBy(
    { it.seasonNumber ?: Int.MIN_VALUE },
    { it.episodeNumber ?: Int.MIN_VALUE },
)

/** True when [episode] sits strictly after [anchor] in (season, episode) order. */
private fun isAfter(
    episode: OfflineMediaItem,
    anchor: OfflineMediaItem?,
): Boolean = anchor == null || seasonEpisodeOrder.compare(episode, anchor) > 0

/** True when the item carries a playback position — the server's `IsResumable` position rule. */
private fun OfflineMediaItem.hasResumePosition(): Boolean = (playbackPositionTicks ?: 0L) > 0L

/** Milliseconds in one day — the `nextUpMaxDays` cutoff unit. */
private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Parses the `lastPlayedDate` forms the offline store carries into comparable
 * epoch millis: server-synced ISO stamps (offset / `Z`, up to nanosecond
 * fraction), local `OffsetDateTime.now().toString()` writes (host-zone
 * offset, variable precision) and bare local dates. Null when blank or
 * unparseable — callers treat null as "no activity".
 */
private fun isoEpochMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    val zone = java.time.ZoneId.systemDefault()
    return try {
        when {
            value.length == 10 -> // bare local date (`2026-01-05`)
                java.time.LocalDate.parse(value).atStartOfDay(zone).toInstant().toEpochMilli()
            ISO_OFFSET_SUFFIX.containsMatchIn(value) ->
                java.time.OffsetDateTime.parse(value).toInstant().toEpochMilli()
            else -> // bare local date-time, no offset
                java.time.LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
        }
    } catch (_: java.time.format.DateTimeParseException) {
        null
    }
}

/** Trailing `Z` / `±HH:mm` offset on a stored ISO timestamp. */
private val ISO_OFFSET_SUFFIX = Regex("(?:Z|[+-]\\d{2}:\\d{2})$")
