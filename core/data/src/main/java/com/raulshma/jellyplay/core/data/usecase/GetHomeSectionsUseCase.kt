package com.raulshma.jellyplay.core.data.usecase

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import javax.inject.Inject

class GetHomeSectionsUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(
        enabledSections: Set<HomeSectionType>,
        hiddenLibraryIds: Set<String>,
    ): Result<List<HomeSection>> =
        mediaRepository.getHomeSections(enabledSections, hiddenLibraryIds)
}
