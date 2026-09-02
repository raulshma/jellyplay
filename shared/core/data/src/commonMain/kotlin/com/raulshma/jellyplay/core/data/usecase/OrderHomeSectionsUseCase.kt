package com.raulshma.jellyplay.core.data.usecase

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType

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
 *  * When [mergeContinueWatchingAndNextUp] is true, Next Up items are appended
 *    to Continue Watching (de-duplicated by item id) and the Next Up section is
 *    dropped. If Continue Watching is absent but Next Up is present, Next Up is
 *    relabelled as Continue Watching. When false, sections pass through ordered.
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

        val cw = ordered.firstOrNull { it.type == HomeSectionType.CONTINUE_WATCHING }
        val nextUp = ordered.firstOrNull { it.type == HomeSectionType.NEXT_UP }?.items.orEmpty()
        return if (cw != null) {
            val seen = cw.items.mapTo(mutableSetOf()) { it.id }
            val mergedItems = cw.items + nextUp.filter { seen.add(it.id) }
            ordered.mapNotNull { section ->
                when (section.type) {
                    HomeSectionType.CONTINUE_WATCHING -> section.copy(items = mergedItems)
                    HomeSectionType.NEXT_UP -> null
                    else -> section
                }
            }
        } else {
            val nextUpSection = ordered.firstOrNull { it.type == HomeSectionType.NEXT_UP }
            if (nextUpSection != null) {
                ordered.mapNotNull { section ->
                    when (section.type) {
                        HomeSectionType.NEXT_UP -> section.copy(type = HomeSectionType.CONTINUE_WATCHING)
                        else -> section
                    }
                }
            } else {
                ordered
            }
        }
    }
}
