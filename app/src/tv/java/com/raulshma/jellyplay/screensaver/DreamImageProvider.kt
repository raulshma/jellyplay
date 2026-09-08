package com.raulshma.jellyplay.screensaver

import android.content.Context
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import com.raulshma.jellyplay.core.concurrency.mapConcurrentCatching
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.DreamImage
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import kotlin.random.Random

class DreamImageProvider(
    private val mediaRepository: MediaRepository,
    private val imageUrlProvider: ImageUrlProvider,
    private val context: Context,
) {
    private val imageLoader: ImageLoader by lazy { SingletonImageLoader.get(context) }

    suspend fun fetchImages(
        categories: Set<DreamImageCategory>,
        count: Int = 50,
    ): List<DreamImage> = withContext(Dispatchers.IO) {
        val mediaTypes = categories.flatMap { it.toMediaTypes() }.distinct()
        if (mediaTypes.isEmpty()) return@withContext emptyList()

        val result = mediaRepository.getMediaItems(
            filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                mediaTypes = mediaTypes,
                sortBy = com.raulshma.jellyplay.core.model.SortOption.RANDOM,
            ),
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
                val backdropUrl = imageUrlProvider.getBackdropUrl(item.id, maxWidth = 1920)
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

    /**
     * Prefetches backdrop bitmaps with bounded concurrency. The screensaver
     * path runs on the project's explicitly targeted low-RAM TV sticks, where
     * 50 concurrent 1920×1080 hardware-bitmap fetches can each hold ~8 MB of
     * native texture. A small [Semaphore] gates peak parallelism — visual
     * behavior is identical, only peak memory is bounded.
     */
    suspend fun prefetchImages(urls: List<String>) {
        if (urls.isEmpty()) return
        withContext(Dispatchers.IO) {
            // mapConcurrentCatching = the documented drop-failed-item policy:
            // a failed decode is dropped from the prefetch, while a cancelling
            // one propagates (never masked as a drop). execute() suspends
            // until decode completes (or fails), unlike enqueue() which
            // fires-and-forgets — so the semaphore actually bounds in-flight
            // decodes.
            Semaphore(MAX_PREFETCH_CONCURRENCY).mapConcurrentCatching(urls) { url ->
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(Size(1920, 1080))
                    .allowHardware(true)
                    .build()
                imageLoader.execute(request)
            }
        }
    }

    private companion object {
        /** Bounds concurrent 1920×1080 hardware-bitmap decodes for the screensaver. */
        const val MAX_PREFETCH_CONCURRENCY = 4
    }

    private fun DreamImageCategory.toMediaTypes(): List<MediaType> = when (this) {
        DreamImageCategory.MOVIES -> listOf(MediaType.MOVIE)
        DreamImageCategory.SERIES -> listOf(MediaType.SERIES)
        DreamImageCategory.MUSIC -> listOf(MediaType.AUDIO, MediaType.ALBUM)
    }
}
