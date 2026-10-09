package com.raulshma.jellyplay.core.data.usecase

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.home.ContinueWatchingRowRule
import com.raulshma.jellyplay.core.model.home.HomeRowModules

/**
 * Orders the freshly fetched home sections to match the user's configured
 * [order], and optionally folds Next Up into Continue Watching.
 *
 * Extracted verbatim from `HomeViewModel.fetchAndUpdateSections` so the rule
 * is unit-testable without standing up the whole VM. Pure — no dispatcher hop;
 * the caller is responsible for any thread offload it needs.
 *
 * Rules:
 *  * Sections are sorted by their index in [order]; unknown types land last,
 *    preserving their original relative order.
 *  * When [mergeContinueWatchingAndNextUp] is true, the merge donor — the row
 *    whose [com.raulshma.jellyplay.core.model.home.HomeRowModule.mergesIntoContinueWatching]
 *    flag is set (NEXT_UP, read from the home row registry, never spelled
 *    here) — donates its items into the Continue Watching row (de-duplicated
 *    by item id through [ContinueWatchingRowRule.mergeCwNextUp] — the
 *    CW+NextUp merge's single owner, shared with the single-row refresh and
 *    the offline mirror) and the donor section is dropped. If Continue
 *    Watching is absent but a donor is present, the donor is relabelled as
 *    Continue Watching. When false, sections pass through ordered.
 */
class OrderHomeSectionsUseCase() {

    operator fun invoke(
        sections: List<HomeSection>,
        order: List<HomeSectionType>,
        mergeContinueWatchingAndNextUp: Boolean,
    ): List<HomeSection> {
        val orderIndex = order.withIndex().associate { it.value to it.index }
        val ordered = sections
            .mapIndexed { index, section -> index to section }
            .sortedWith(
                compareBy<Pair<Int, HomeSection>> {
                    orderIndex[it.second.type] ?: Int.MAX_VALUE
                }.thenBy { it.first },
            )
            .map { it.second }

        if (!mergeContinueWatchingAndNextUp) return ordered

        // The merge's anchor (Continue Watching) and donor (the registry's
        // mergesIntoContinueWatching flag — NEXT_UP) are the row registry's
        // facts; the fold mechanics stay ContinueWatchingRowRule's.
        val cw = ordered.firstOrNull { it.type == HomeSectionType.CONTINUE_WATCHING }
        val donor = ordered.firstOrNull { HomeRowModules[it.type].mergesIntoContinueWatching }
        val donorItems = donor?.items.orEmpty()
        return if (cw != null) {
            // The merge fold's single owner (ContinueWatchingRowRule) — the
            // single-row refresh rebuilds this exact call, and the offline
            // home mirror runs its offline twin.
            val mergedItems = ContinueWatchingRowRule.mergeCwNextUp(cw.items, donorItems)
            ordered.mapNotNull { section ->
                when {
                    HomeRowModules[section.type].mergesIntoContinueWatching -> null
                    section.type == HomeSectionType.CONTINUE_WATCHING -> section.copy(items = mergedItems)
                    else -> section
                }
            }
        } else {
            if (donor != null) {
                ordered.map { section ->
                    if (HomeRowModules[section.type].mergesIntoContinueWatching) {
                        section.copy(type = HomeSectionType.CONTINUE_WATCHING)
                    } else {
                        section
                    }
                }
            } else {
                ordered
            }
        }
    }
}
