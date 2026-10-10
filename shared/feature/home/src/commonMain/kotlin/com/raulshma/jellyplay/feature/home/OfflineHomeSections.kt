package com.raulshma.jellyplay.feature.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.home.generated.resources.Res
import com.raulshma.jellyplay.feature.home.generated.resources.home_continue_reading
import com.raulshma.jellyplay.feature.home.generated.resources.home_continue_watching
import com.raulshma.jellyplay.feature.home.generated.resources.home_movies
import com.raulshma.jellyplay.feature.home.generated.resources.home_music
import com.raulshma.jellyplay.feature.home.generated.resources.home_next_up
import com.raulshma.jellyplay.feature.home.generated.resources.home_recently_downloaded
import com.raulshma.jellyplay.feature.home.generated.resources.home_series
import com.raulshma.jellyplay.core.model.HomeMode
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.OfflineMediaTypeGroup
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.home.ContinueWatchingRowRule
import com.raulshma.jellyplay.core.model.home.HomeRowModules
import com.raulshma.jellyplay.core.model.home.OfflineDownloadedGroups
import com.raulshma.jellyplay.core.model.home.OfflineMirrorContext
import com.raulshma.jellyplay.core.model.home.OfflineMirrorRow
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.model.typeGroup

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
    val continueReading: String,
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
        continueReading = stringResource(Res.string.home_continue_reading),
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
    val continueReadingEnabled: Boolean = true,
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
 *     Continue Reading / Next Up keep their sections but render the locally
 *     derived lists (local playback progress is fresher than the snapshot).
 *     Rows the user has since disabled via the CURRENT prefs drop out, and
 *     the surviving rows re-sort by the user's CURRENT section order — the
 *     snapshot is persisted in fetch order (pre-ordering), so its raw order is
 *     the network build order, not the user's layout.
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
 *    items, capped at the module's Continue Watching row cap.
 *  - Continue Reading — downloaded books with reading progress (the same
 *    rules and ordering), capped at the module's Continue Reading row cap.
 *  - Next Up — the local mirror of Jellyfin's server-side rule over the
 *    downloaded episodes (the NEXT_UP module's offline projection): per series with watch
 *    activity, the first unplayed episode strictly after the highest played
 *    one; mid-watch (resumable) episodes stay in Continue Watching instead.
 *    When [OfflineHomeSectionPrefs.mergeCwAndNextUp] is set, Next Up items
 *    are appended into the Continue Watching row instead (deduplicated),
 *    mirroring the online
 *    [com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase].
 *  - Recently Downloaded (newest items by download date), then
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

    // The per-type compute lives on the row modules' offline projections
    // (core/model/home — see HomeRowOfflineRules): the library partition into
    // the DOWNLOADED rows' groups, and the locally derived resume rows whose
    // rules route through ContinueWatchingRowRule. This orchestrator owns the
    // prefs plumbing and the once-per-emission derived values only.
    val downloaded = HomeRowModules[HomeSectionType.DOWNLOADED].offline
        .downloadedGroups(library) ?: OfflineDownloadedGroups(emptyList(), emptyList(), emptyList(), emptyList())

    // Continue Watching mirrors the server's resume query
    // (ItemsController.GetResumeItems → IsResumable): non-folder items with a
    // playback position, sorted DatePlayed-desc — the module's projection.
    val continueWatching = if (prefs.continueWatchingEnabled) {
        HomeRowModules[HomeSectionType.CONTINUE_WATCHING].offline
            .resumeItems(library, episodes, prefs.hiddenCwItemIds)
    } else {
        emptyList()
    }

    // Continue Reading: the books half of the same resume query — the
    // module's projection (the identical position > 0 / played / hidden-item
    // rules, no percentage floor).
    val continueReading = if (prefs.continueReadingEnabled) {
        HomeRowModules[HomeSectionType.CONTINUE_READING].offline
            .resumeItems(library, episodes, prefs.hiddenCwItemIds)
    } else {
        emptyList()
    }

    val nextUp = if (prefs.nextUpEnabled) {
        HomeRowModules[HomeSectionType.NEXT_UP].offline.nextUpItems(
            episodes,
            excludedSeriesIds = prefs.nextUpExcludedSeriesIds,
            maxDays = prefs.nextUpMaxDays,
            rewatching = prefs.nextUpRewatching,
        )
    } else {
        emptyList()
    }

    // Merge mode folds Next Up into the Continue Watching row (deduped) and
    // drops the separate row — the offline mirror of the online merge pref,
    // through the same fold owner as the online paths
    // (ContinueWatchingRowRule.mergeCwNextUp; the offline item type maps in
    // through the id accessor).
    val mergedContinueWatching = if (prefs.mergeCwAndNextUp && nextUp.isNotEmpty()) {
        ContinueWatchingRowRule.mergeCwNextUp(continueWatching, nextUp, id = { it.id })
    } else {
        continueWatching
    }
    val showNextUpRow = prefs.nextUpEnabled && !prefs.mergeCwAndNextUp && nextUp.isNotEmpty()

    val sections = buildList {
        if (mergedContinueWatching.isNotEmpty()) {
            add(
                offlineResumeSection(
                    id = "offline_continue_watching",
                    title = titles.continueWatching,
                    type = HomeSectionType.CONTINUE_WATCHING,
                    items = mergedContinueWatching,
                )
            )
        }
        if (continueReading.isNotEmpty()) {
            add(
                offlineResumeSection(
                    id = "offline_continue_reading",
                    title = titles.continueReading,
                    type = HomeSectionType.CONTINUE_READING,
                    items = continueReading,
                )
            )
        }
        if (showNextUpRow) {
            add(
                offlineResumeSection(
                    id = "offline_next_up",
                    title = titles.nextUp,
                    type = HomeSectionType.NEXT_UP,
                    items = nextUp,
                )
            )
        }
        if (downloaded.recent.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_recently_downloaded",
                    title = titles.recentlyDownloaded,
                    type = HomeSectionType.DOWNLOADED,
                    items = downloaded.recent.map { it.toMediaItem() },
                )
            )
        }
        if (downloaded.movies.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_movies",
                    title = titles.movies,
                    type = HomeSectionType.DOWNLOADED,
                    items = downloaded.movies.map { it.toMediaItem() },
                )
            )
        }
        if (downloaded.series.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_series",
                    title = titles.series,
                    type = HomeSectionType.DOWNLOADED,
                    items = downloaded.series.map { it.toMediaItem() },
                )
            )
        }
        if (downloaded.music.isNotEmpty()) {
            add(
                HomeSection(
                    id = "offline_music",
                    title = titles.music,
                    type = HomeSectionType.DOWNLOADED,
                    items = downloaded.music.map { it.toMediaItem() },
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
            DerivedCwNextUp(mergedContinueWatching, showNextUpRow, nextUp, continueReading),
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
    /** The locally derived Continue Reading books (local progress beats the snapshot). */
    val continueReading: List<OfflineMediaItem>,
)

/**
 * The ONE construction of the offline resume rows (Continue Watching /
 * Continue Reading / Next Up) — shared by the generic fallback layout and
 * the cached-layout mirror, so the two sites cannot drift on the row ids,
 * titles, types, or the item lift. The gating `if`s stay at the call sites
 * (they differ: the fallback keys on item presence, the mirror also on the
 * current prefs).
 */
private fun offlineResumeSection(
    id: String,
    title: String,
    type: HomeSectionType,
    items: List<OfflineMediaItem>,
): HomeSection = HomeSection(
    id = id,
    title = title,
    type = type,
    items = items.map { it.toMediaItem() },
)

/**
 * Mirrors the cached online layout onto the offline home: each snapshot row
 * is dispatched to ITS row module's offline projection
 * ([HomeRowModules]/offline/mirrorRow — the per-type arms: the locally
 * derived resume rows swap in, LIVE TV drops unplayable, DOWNLOADED never
 * appears in a snapshot, everything else takes the generic filter), and the
 * outcome is turned into the rendered section here — the ids/titles/types
 * and the item lift are the hand-written emission this site owns. Rows drop
 * when the CURRENT prefs disable their type (or the per-library override for
 * a LATEST_MEDIA row), when nothing in them is downloaded, or when
 * unplayable offline (LIVE_TV). Row order is re-normalized by the caller
 * against the user's CURRENT section order — the snapshot is persisted in
 * fetch order (before
 * [com.raulshma.jellyplay.core.data.usecase.OrderHomeSectionsUseCase] runs
 * online), so same-type rows keep their snapshot relative order via the
 * stable sort, but the rows themselves do NOT keep the snapshot's raw order.
 *
 * Coverage: rows whose content the mirror cannot surface are not lost —
 * [buildOfflineHomeSections] appends the generic fallback rows for any item
 * id the mirrored rows do not already show, so every download stays
 * reachable while offline even when its snapshot row dropped or its type was
 * absent from the snapshot.
 */
private fun mirrorCachedLayoutSections(
    cachedLayout: List<HomeSection>,
    itemsById: Map<String, OfflineMediaItem>,
    prefs: OfflineHomeSectionPrefs,
    titles: OfflineHomeSectionTitles,
    cwNextUp: DerivedCwNextUp,
): List<HomeSection> {
    val ctx = OfflineMirrorContext(
        enabledSectionTypes = prefs.enabledSectionTypes,
        libraryOverrides = prefs.libraryOverrides,
        itemsById = itemsById,
        continueWatchingEnabled = prefs.continueWatchingEnabled,
        continueWatching = cwNextUp.mergedContinueWatching,
        continueReadingEnabled = prefs.continueReadingEnabled,
        continueReading = cwNextUp.continueReading,
        nextUp = cwNextUp.nextUp,
        showNextUpRow = cwNextUp.showNextUpRow,
    )
    return buildList {
        for (cached in cachedLayout) {
            when (val outcome = HomeRowModules[cached.type].offline.mirrorRow(cached, ctx)) {
                // Locally derived rows: local progress beats the snapshot —
                // the offline resume identity (module-supplied id), localized
                // title, the row's own type.
                is OfflineMirrorRow.Derived -> add(
                    offlineResumeSection(
                        id = outcome.sectionId,
                        title = offlineDerivedTitle(cached.type, titles),
                        type = cached.type,
                        items = outcome.items,
                    )
                )
                // Generic filter: the snapshot row's identity (id / title /
                // type / libraryId / collectionType) with items filtered to
                // what is downloaded.
                is OfflineMirrorRow.Mirrored -> add(
                    HomeSection(
                        id = "offline_${cached.id}",
                        title = cached.title,
                        type = cached.type,
                        items = outcome.items.map { it.toMediaItem() },
                        seedItem = if (outcome.keepSeedItem) cached.seedItem else null,
                        libraryId = cached.libraryId,
                        collectionType = cached.collectionType,
                    )
                )
                OfflineMirrorRow.Dropped -> Unit
            }
        }
    }
}

/**
 * The localized header for a derived offline resume row — the title
 * emission stays at this layer (strings are composable resources; the row
 * module carries no UI strings).
 */
private fun offlineDerivedTitle(
    type: HomeSectionType,
    titles: OfflineHomeSectionTitles,
): String = when (type) {
    HomeSectionType.CONTINUE_WATCHING -> titles.continueWatching
    HomeSectionType.CONTINUE_READING -> titles.continueReading
    HomeSectionType.NEXT_UP -> titles.nextUp
    else -> error("No derived offline title for $type")
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
