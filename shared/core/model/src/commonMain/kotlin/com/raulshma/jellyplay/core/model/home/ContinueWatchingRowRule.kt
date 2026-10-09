package com.raulshma.jellyplay.core.model.home

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * THE single owner of the Continue Watching row's rule (the GLOSSARY's "home
 * row module" seed): the played-row exclusion (#157), the hidden-item drop,
 * Next Up eligibility, and the Continue Watching + Next Up merge with id
 * dedupe. Online and offline both consume it from here — the network fetch
 * path, the single-row refresh, the ordering use case, and the offline home
 * mirror call these members instead of keeping private copies, so the rule
 * cannot drift between them.
 *
 * Everything here is a pure client-side fold over already-fetched items; the
 * server-request shaping (the `/Items/Resume` and `/Shows/NextUp` query
 * filters) stays where it is, in the network layer. The module operates on
 * [MediaItem] directly and, through the accessor-lambda members, on any row
 * type that can project `id` / `mediaType` / `seriesId` / a played notion —
 * the offline mirror's `OfflineMediaItem` maps its local playstate into the
 * `isPlayed` parameter instead of running a parallel rule (its local "counts
 * as watched" test is `isPlayed || isFinishedOffline`, the local rendering of
 * the same #157 fact).
 *
 * Members are deliberately small and composable — the sites combine them in
 * different orders (the network resume rows fold parental + distinct + rollup
 * drops around [filterResumable]; the offline rows fold a position floor
 * around it) and those compositions stay with the sites. Only the shared
 * atoms live here.
 */
object ContinueWatchingRowRule {

    // ── #157: the played-row exclusion ──────────────────────────────────────

    /**
     * The video resume row's #157 fold: a played row never occupies Continue
     * Watching, and BOOK rows are excluded outright (books surface in their
     * own Continue Reading row, [filterReadingResumable]). /Items/Resume
     * filters on position > 0 only and does NOT exclude played items — a
     * position report landing on an already-played item leaves it resumable
     * forever server-side, so the client drops it here.
     *
     * [isPlayed] is the caller's played notion (the plain [MediaItem.isPlayed]
     * flag online; the offline mirror passes its local
     * `isPlayed || isFinishedOffline`), [mediaType] the item's content type.
     */
    fun <T> filterResumable(
        items: List<T>,
        isPlayed: (T) -> Boolean,
        mediaType: (T) -> MediaType,
    ): List<T> = items.filter { !isPlayed(it) && mediaType(it) != MediaType.BOOK }

    /** [MediaItem] convenience overload — the online fetch path's shape. */
    fun filterResumable(items: List<MediaItem>): List<MediaItem> =
        filterResumable(items, isPlayed = { it.isPlayed }, mediaType = { it.mediaType })

    /**
     * The books half of the #157 rule — the exact complement of
     * [filterResumable]'s book exclusion: keep only BOOK items, under the same
     * played-row drop (a finished book's lingering position must not occupy
     * Continue Reading forever).
     */
    fun <T> filterReadingResumable(
        items: List<T>,
        isPlayed: (T) -> Boolean,
        mediaType: (T) -> MediaType,
    ): List<T> = items.filter { !isPlayed(it) && mediaType(it) == MediaType.BOOK }

    /** [MediaItem] convenience overload — the online fetch path's shape. */
    fun filterReadingResumable(items: List<MediaItem>): List<MediaItem> =
        filterReadingResumable(items, isPlayed = { it.isPlayed }, mediaType = { it.mediaType })

    // ── The hidden-item drop ────────────────────────────────────────────────

    /**
     * Drops the items the user hid from the resume rows (`hiddenCwItemIds`)
     * from a Continue Watching / Continue Reading list. Books ride the same
     * per-item affordance — one hidden set covers both rows.
     */
    fun <T> excludingHiddenItems(
        items: List<T>,
        hiddenItemIds: Set<String>,
        id: (T) -> String,
    ): List<T> = items.filter { id(it) !in hiddenItemIds }

    /** [MediaItem] convenience overload — the home fetch/refresh paths' shape. */
    fun excludingHiddenItems(items: List<MediaItem>, hiddenItemIds: Set<String>): List<MediaItem> =
        excludingHiddenItems(items, hiddenItemIds, id = { it.id })

    // ── The Continue Watching id capture ────────────────────────────────────

    /**
     * The Continue Watching id set the Next Up / Recently Added CW-overlap
     * drops key on — derived from the HIDDEN-FILTERED CW list
     * ([excludingHiddenItems]), so an item hidden from Continue Watching stays
     * eligible for Next Up (its series' next episode being the intended
     * resume path). Callers pass their own CW list (the batch fetch's list, a
     * single-row refresh's fresh read) and the rule applies once.
     */
    fun <T> continueWatchingFilterIds(
        continueWatchingItems: List<T>,
        hiddenItemIds: Set<String>,
        id: (T) -> String,
    ): Set<String> = continueWatchingItems
        .let { excludingHiddenItems(it, hiddenItemIds, id) }
        .mapTo(mutableSetOf(), id)

    /** [MediaItem] convenience overload — the home fetch/refresh paths' shape. */
    fun continueWatchingFilterIds(
        continueWatchingItems: List<MediaItem>,
        hiddenItemIds: Set<String>,
    ): Set<String> = continueWatchingFilterIds(continueWatchingItems, hiddenItemIds, id = { it.id })

    // ── Next Up eligibility ─────────────────────────────────────────────────

    /**
     * The Next Up row's eligibility fold: the CW-overlap drop (keyed on
     * [continueWatchingFilterIds]'s HIDDEN-FILTERED id set) plus the
     * "remove from Next Up" series blocklist (an item without a series is
     * always eligible). Shared verbatim by the batch assembler's NEXT_UP arm,
     * the single-row refresh's NEXT_UP and merged-Continue-Watching arms, and
     * the offline Next Up row's series exclusion.
     */
    fun <T> nextUpEligible(
        items: List<T>,
        continueWatchingIds: Set<String>,
        excludedSeriesIds: Set<String>,
        id: (T) -> String,
        seriesId: (T) -> String?,
    ): List<T> = items
        .filter { id(it) !in continueWatchingIds }
        .filter { seriesId(it) == null || seriesId(it) !in excludedSeriesIds }

    /** [MediaItem] convenience overload — the home fetch/refresh paths' shape. */
    fun nextUpEligible(
        items: List<MediaItem>,
        continueWatchingIds: Set<String>,
        excludedSeriesIds: Set<String>,
    ): List<MediaItem> = nextUpEligible(
        items,
        continueWatchingIds,
        excludedSeriesIds,
        id = { it.id },
        seriesId = { it.seriesId },
    )

    // ── The Continue Watching + Next Up merge ───────────────────────────────

    /**
     * The CW + Next Up merge, id-deduped: [cw] passes through VERBATIM (its
     * order is the row's order), then the [nextUp] items join on FIRST SIGHT
     * of their id — an id already present in cw, or already joined from an
     * earlier nextUp entry, is skipped. CW first, then the unseen Next Up
     * tail, in Next Up's own order.
     *
     * Input precondition, recorded once instead of re-litigated per site: the
     * cw list must not carry internal duplicate ids (the online fetch path's
     * resume fold is id-distinct before this runs; the offline rows are
     * per-item unique by construction). Under that precondition this fold is
     * exactly the distinct-by-id of the concatenation — the shape the
     * single-row refresh used to hand-roll.
     */
    fun <T> mergeCwNextUp(cw: List<T>, nextUp: List<T>, id: (T) -> String): List<T> {
        val seen = cw.mapTo(mutableSetOf(), id)
        return cw + nextUp.filter { seen.add(id(it)) }
    }

    /** [MediaItem] convenience overload — the merge's online callers' shape. */
    fun mergeCwNextUp(cw: List<MediaItem>, nextUp: List<MediaItem>): List<MediaItem> =
        mergeCwNextUp(cw, nextUp, id = { it.id })
}
