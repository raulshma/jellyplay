package com.raulshma.jellyplay.screensaver

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import com.raulshma.jellyplay.core.model.DreamImage
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.random.Random

class DreamImageProvider(
    private val apiClient: JellyfinApiClient,
    private val context: Context,
) {
    private val imageLoader: ImageLoader by lazy { SingletonImageLoader.get(context) }

    suspend fun fetchImages(
        categories: Set<DreamImageCategory>,
        count: Int = 50,
    ): List<DreamImage> = withContext(Dispatchers.IO) {
        val mediaTypes = categories.flatMap { it.toMediaTypes() }.distinct()
        if (mediaTypes.isEmpty()) return@withContext emptyList()

        val result = apiClient.getMediaItems(
            mediaTypes = mediaTypes,
            sortBy = "Random",
            sortOrder = "Ascending",
            limit = count,
        )

        result.getOrNull()?.items.orEmpty()
            .filter { it.id.isNotBlank() }
            .mapNotNull { item ->
                val category = when (item.mediaType) {
                    MediaType.MOVIE -> DreamImageCategory.MOVIES
                    MediaType.SERIES -> DreamImageCategory.SERIES
                    MediaType.AUDIO, MediaType.ALBUM, MediaType.ARTIST -> DreamImageCategory.MUSIC
                    else -> return@mapNotNull null
                }
                val backdropUrl = apiClient.getBackdropImageUrl(item.id, maxWidth = 1920)
                if (backdropUrl.isBlank()) return@mapNotNull null
                DreamImage(
                    itemId = item.id,
                    backdropUrl = backdropUrl,
                    title = item.name,
                    type = category,
                )
            }
            .shuffled(Random)
    }

    fun prefetchImages(urls: List<String>) {
        urls.forEach { url ->
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(Size(1920, 1080))
                .allowHardware(true)
                .build()
            imageLoader.enqueue(request)
        }
    }

    private fun DreamImageCategory.toMediaTypes(): List<MediaType> = when (this) {
        DreamImageCategory.MOVIES -> listOf(MediaType.MOVIE)
        DreamImageCategory.SERIES -> listOf(MediaType.SERIES)
        DreamImageCategory.MUSIC -> listOf(MediaType.AUDIO, MediaType.ALBUM)
    }
}
