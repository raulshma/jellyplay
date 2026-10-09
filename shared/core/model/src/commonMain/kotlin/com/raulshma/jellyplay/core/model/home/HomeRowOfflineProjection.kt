package com.raulshma.jellyplay.core.model.home

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.OfflineMediaItem

/**
 * The offline half of a [HomeRowModule] — how the row is computed from the
 * downloaded store. The module DESCRIBES the projection; the stores stay
 * injected: every member receives the offline library/episode lists (and the
 * current prefs' relevant slices) as parameters, so no store ownership moves
 * here. Online bodies live with the network layer's arm registry; this file
 * is the row's offline compute, absorbed from feature/home's offline mirror
 * so a row's rules cannot drift between its online and offline halves.
 *
 * Two shapes, matching the offline home's two layouts:
 *  - [resumeItems] / [nextUpItems] / [downloadedGroups] — the generic
 *    fallback compute (the locally derived rows when no cached online
 *    snapshot survives).
 *  - [mirrorRow] — the cached-layout mirror arm: what becomes of one
 *    snapshot row while offline ([OfflineMirrorRow]).
 *
 * Members a row doesn't own keep their inert defaults, so a registration
 * overrides exactly the offline behavior its row carries.
 */
open class HomeRowOfflineProjection {

    /**
     * Whether a mirrored row keeps the snapshot's [HomeSection.seedItem] —
     * the recommendations row's "Because you watched …" header seed is the
     * one identity fact the generic item filter must not strip.
     */
    protected open val keepsSeedItem: Boolean = false

    /**
     * The row's locally derived items (the generic-fallback compute) from the
     * mode-filtered offline library and its downloaded episodes. Empty = the
     * row is absent from the fallback layout. Only the resume rows own a
     * body (the locally derived Continue Watching / Continue Reading lists);
     * [hiddenItemIds] is the user's hidden-resume-rows set.
     */
    open fun resumeItems(
        library: List<OfflineMediaItem>,
        episodes: List<OfflineMediaItem>,
        hiddenItemIds: Set<String>,
    ): List<OfflineMediaItem> = emptyList()

    /**
     * The locally derived Next Up episodes — the offline mirror of the
     * server-side rule over the downloaded episodes. Parameters are the
     * current Next Up prefs the online fetch sends (`nextUpExcludedSeriesIds`,
     * the `nextUpDateCutoff` day count, the rewatching toggle).
     */
    open fun nextUpItems(
        episodes: List<OfflineMediaItem>,
        excludedSeriesIds: Set<String>,
        maxDays: Int,
        rewatching: Boolean,
    ): List<OfflineMediaItem> = emptyList()

    /**
     * The DOWNLOADED fallback partition of the offline library (recent /
     * movies / series / music) — the offline-only rows' compute. Null for
     * every other row (they have no downloaded partition).
     */
    open fun downloadedGroups(library: List<OfflineMediaItem>): OfflineDownloadedGroups? = null

    /**
     * The cached-layout mirror arm: what the offline home renders for one
     * snapshot row of this type. The default is the generic filter — drop
     * when the current prefs disable the (configurable) type or its
     * per-library override, otherwise keep the row's identity with its items
     * filtered to what is downloaded.
     */
    open fun mirrorRow(cached: HomeSection, ctx: OfflineMirrorContext): OfflineMirrorRow {
        // Configurable types honor the CURRENT enablement (the snapshot
        // reflects prefs at fetch time; a toggle made while offline wins).
        if (cached.type.isConfigurable && cached.type !in ctx.enabledSectionTypes) {
            return OfflineMirrorRow.Dropped
        }
        val libraryId = cached.libraryId
        if (libraryId != null && cached.type in ctx.libraryOverrides[libraryId].orEmpty()) {
            return OfflineMirrorRow.Dropped
        }
        val downloaded = cached.items.mapNotNull { ctx.itemsById[it.id] }
        if (downloaded.isEmpty()) return OfflineMirrorRow.Dropped
        return OfflineMirrorRow.Mirrored(downloaded, keepsSeedItem)
    }
}

/**
 * What the cached-layout mirror renders for one snapshot row — the outcome
 * data the offline render site turns into a [HomeSection] (the localized
 * titles and the section construction stay at the render site; they are the
 * hand-written emission this registry deliberately does not cover).
 */
sealed interface OfflineMirrorRow {

    /**
     * Replace the snapshot row with the locally derived items under the
     * offline resume-row identity ([sectionId] — the row's stable offline id,
     * e.g. `offline_continue_watching`). Local progress beats the snapshot.
     */
    data class Derived(
        val items: List<OfflineMediaItem>,
        val sectionId: String,
    ) : OfflineMirrorRow

    /**
     * Keep the snapshot row's identity (id/title/type/libraryId/
     * collectionType), items filtered to what is downloaded.
     * [keepSeedItem] preserves the snapshot's seed (recommendations).
     */
    data class Mirrored(
        val items: List<OfflineMediaItem>,
        val keepSeedItem: Boolean,
    ) : OfflineMirrorRow

    /** Drop the snapshot row. */
    data object Dropped : OfflineMirrorRow
}

/**
 * Everything the mirror arms read besides the snapshot row — the current
 * prefs' relevant slices and the locally derived resume rows (computed once
 * per emission by the offline home's orchestrator, shared by the fallback
 * layout and the mirror).
 */
class OfflineMirrorContext(
    /** All currently-enabled configurable section types (the global toggle filter). */
    val enabledSectionTypes: Set<HomeSectionType>,
    /** Per-library DISABLED types, keyed by library id — the online overrides' shape. */
    val libraryOverrides: Map<String, Set<HomeSectionType>>,
    /** Id → offline item across library + downloaded episodes. */
    val itemsById: Map<String, OfflineMediaItem>,
    /** The current Continue Watching toggle (the merged list can be non-empty with the toggle off when the merge pref folds Next Up in). */
    val continueWatchingEnabled: Boolean,
    /** The locally derived Continue Watching list (merge pref already applied). */
    val continueWatching: List<OfflineMediaItem>,
    /** The current Continue Reading toggle. */
    val continueReadingEnabled: Boolean,
    /** The locally derived Continue Reading books. */
    val continueReading: List<OfflineMediaItem>,
    /** The locally derived Next Up episodes. */
    val nextUp: List<OfflineMediaItem>,
    /** Whether the separate Next Up row renders (enabled, not merged, non-empty). */
    val showNextUpRow: Boolean,
)

/**
 * The DOWNLOADED rows' partition of the offline library — the four generic
 * fallback groups in their fixed tail order (recent by download date, then
 * movies / series / music). Built in one pass by the DOWNLOADED row's
 * projection.
 */
class OfflineDownloadedGroups(
    /** The newest items by download date (the "Recently Downloaded" row). */
    val recent: List<OfflineMediaItem>,
    val movies: List<OfflineMediaItem>,
    val series: List<OfflineMediaItem>,
    val music: List<OfflineMediaItem>,
)
