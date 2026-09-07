package com.raulshma.jellyplay.core.data.util

import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.sync.Semaphore

/**
 * Prefetches child image URLs for photo-folder items in parallel with a bounded
 * [Semaphore]. Returns a map of `folderId → child image urls` for the folders
 * that were fetched, leaving it to the caller to merge the result into whatever
 * state holder it uses (MutableStateFlow, custom stateFlow wrapper, etc.).
 *
 * Folders already present in [alreadyFetched] are skipped so repeated calls
 * (e.g. on recomposition) don't refetch.
 *
 * Moved from the legacy :core:data Hilt graph at the V3 library conveyor move
 * (its sole consumer, LibraryViewModel, is Koin-owned) — the inert
 * @Singleton/@Inject pair was stripped per the DataKoinModule convention.
 */
class PhotoFolderPrefetcher(
    private val mediaRepository: MediaRepository,
) {
    suspend fun prefetch(
        items: List<MediaItem>,
        alreadyFetched: Set<String> = emptySet(),
        concurrency: Int = DEFAULT_CONCURRENCY,
    ): Map<String, List<String>> {
        val toFetch = items.filter {
            it.mediaType == MediaType.PHOTO_FOLDER && it.id !in alreadyFetched
        }
        if (toFetch.isEmpty()) return emptyMap()
        return Semaphore(concurrency).mapConcurrent(toFetch) { folder ->
            folder.id to mediaRepository.getPhotoFolderChildImageUrls(folder.id)
        }.toMap()
    }

    companion object {
        const val DEFAULT_CONCURRENCY = 4
    }
}
