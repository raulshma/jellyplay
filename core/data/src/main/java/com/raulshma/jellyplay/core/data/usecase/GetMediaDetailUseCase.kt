package com.raulshma.jellyplay.core.data.usecase

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import javax.inject.Inject

class GetMediaDetailUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
) {
    suspend operator fun invoke(itemId: String): Result<MediaDetail> =
        mediaRepository.getMediaDetail(itemId)
}
