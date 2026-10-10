package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.home.ContinueWatchingRowRule

/**
 * The per-type shaping rules the home batch path ([assembleHomeSections])
 * and the single-row refresh ([HomeSectionsFetcher.refreshSection]) must
 * apply identically — this file is THE single home of each rule, and both
 * paths route through it so they cannot drift (the same one-shape-per-rule
 * policy as [filterNextUpEligible]).
 *
 * The continue-watching-shaped rules (the hidden-item drop, the Continue
 * Watching id capture) are owned by
 * [com.raulshma.jellyplay.core.model.home.ContinueWatchingRowRule] — the
 * single owner the online fetch, the single-row refresh, the ordering use
 * case and the offline mirror all consume; the extensions here are this
 * module's named handles on it. [distinctByIdExcluding] is the Recently
 * Added row's own fold and stays local — it is not part of the
 * continue-watching rule.
 */

/**
 * The hidden-resume-rows filter — drops the items the user hid from the
 * resume rows (`hiddenCwItemIds`) from the Continue Watching and Continue
 * Reading lists. Routes through [ContinueWatchingRowRule.excludingHiddenItems],
 * the rule's single owner: the assembler's CW/CR arms and
 * [HomeSectionsFetcher.refreshSection]'s CW/CR arms all route through it.
 *
 * Books ride the same per-item "hide from resume rows" affordance as
 * Continue Watching — one `hiddenCwItemIds` set covers both rows.
 */
internal fun List<MediaItem>.excludingHiddenItemIds(hiddenItemIds: Set<String>): List<MediaItem> =
    ContinueWatchingRowRule.excludingHiddenItems(this, hiddenItemIds)

/**
 * The Continue Watching id set that seeds the Next Up / Recently Added
 * CW-overlap drops ([filterNextUpEligible] / [distinctByIdExcluding]) —
 * derived from the HIDDEN-FILTERED CW list ([excludingHiddenItemIds]), so an
 * item hidden from Continue Watching stays eligible for Next Up / Recently
 * Added (its series' next episode being the intended resume path). Routes
 * through [ContinueWatchingRowRule.continueWatchingFilterIds], the rule's
 * single owner: the assembler derives it from its batch-fetched CW list,
 * [HomeSectionsFetcher.refreshSection] from its fresh CW read — each passes
 * its own list and the rule applies once.
 */
internal fun continueWatchingFilterIds(
    continueWatchingItems: List<MediaItem>,
    hiddenItemIds: Set<String>,
): Set<String> = ContinueWatchingRowRule.continueWatchingFilterIds(continueWatchingItems, hiddenItemIds)

/**
 * The Recently Added aggregate's shaping fold — id-distinct (the
 * per-library latest fan-out can report the same item twice) plus the
 * CW-overlap drop keyed on [continueWatchingFilterIds]'s set. THE single
 * home of that fold: the assembler's aggregate arm and
 * [HomeSectionsFetcher.refreshSection]'s RECENTLY_ADDED arm both route
 * through it; the caller keeps the zero-items-is-not-rendered policy around
 * the result.
 */
internal fun List<MediaItem>.distinctByIdExcluding(itemIds: Set<String>): List<MediaItem> =
    distinctBy { it.id }.filter { it.id !in itemIds }
