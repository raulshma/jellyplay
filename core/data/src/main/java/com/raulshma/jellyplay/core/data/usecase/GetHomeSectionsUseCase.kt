package com.raulshma.jellyplay.core.data.usecase

import com.raulshma.jellyplay.core.data.repository.HomeSectionQuery
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import javax.inject.Inject

/**
 * Wraps [MediaRepository.getHomeSections].
 *
 * Two entry points:
 *  * [invoke] with explicit params — retained for callers that pre-date the
 *    query bundling (TV Watch Next, sync workers, widget workers) and only
 *    need to override the enabled-section set.
 *  * [invoke] with [HomeSectionQuery] — the home screen path, where all seven
 *    inputs travel together from the preferences collector.
 */
class GetHomeSectionsUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(
        enabledSections: Set<HomeSectionType>,
        libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>>,
    ): Result<HomeSectionsResult> =
        mediaRepository.getHomeSections(enabledSections, libraryHomeSectionOverrides)

    suspend operator fun invoke(query: HomeSectionQuery): Result<HomeSectionsResult> =
        mediaRepository.getHomeSections(
            query.enabledSections,
            query.libraryHomeSectionOverrides,
            query.nextUpRewatching,
            query.nextUpMaxDays,
            query.nextUpExcludedSeriesIds,
            query.hiddenCwItemIds,
            query.pinnedSections,
        )
}
