package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import javax.inject.Inject
import javax.inject.Singleton

interface ImageUrlProvider {
    fun getImageUrl(itemId: String, maxWidth: Int = DEFAULT_MAX_WIDTH): String

    companion object {
        const val DEFAULT_MAX_WIDTH = 400
        const val MUSIC_MAX_WIDTH = 300
    }
}

@Singleton
class ImageUrlProviderImpl @Inject constructor(
    private val playbackRepository: PlaybackRepository,
) : ImageUrlProvider {

    override fun getImageUrl(itemId: String, maxWidth: Int): String =
        playbackRepository.getImageUrl(itemId, maxWidth = maxWidth)
}
