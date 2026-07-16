package com.raulshma.jellyplay.core.data.usecase

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import javax.inject.Inject

class GetHomeSectionsUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(
        enabledSections: Set<HomeSectionType>,
        libraryHomeSectionOverrides: Map<String, Set<HomeSectionType>>,
    ): Result<HomeSectionsResult> =
        mediaRepository.getHomeSections(enabledSections, libraryHomeSectionOverrides)
}
