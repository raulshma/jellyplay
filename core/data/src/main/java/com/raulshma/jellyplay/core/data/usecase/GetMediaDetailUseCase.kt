package com.raulshma.jellyplay.core.data.usecase

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.MediaDetail
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetMediaDetailUseCase @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
) {
    suspend operator fun invoke(itemId: String): Result<MediaDetail> =
        mediaRepository.getMediaDetail(itemId)
}
