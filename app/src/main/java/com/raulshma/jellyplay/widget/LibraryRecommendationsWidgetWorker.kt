package com.raulshma.jellyplay.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import com.raulshma.jellyplay.core.model.LibraryWidgetItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class LibraryRecommendationsWidgetWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val userPreferencesStore: UserPreferencesStore,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val authRepository: AuthRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        authRepository.restoreSession()
        val server = authRepository.currentServer.first()
        if (server == null) {
            WidgetPersistHelper.persistLibraryItems(applicationContext, userPreferencesStore, emptyList(), versionBumpOnly = true)
            return@runCatching
        }

        val config = userPreferencesStore.widgetConfig.first()
        val items: List<MediaItem> = when (config.librarySource) {
            LibraryRecommendationsSource.SIMILAR_TO_RECENT -> fetchSimilarToRecent()
                ?: fetchLatest()

            LibraryRecommendationsSource.LATEST -> fetchLatest()

            LibraryRecommendationsSource.FAVORITES -> fetchFavorites()

            LibraryRecommendationsSource.SURPRISE_ME -> fetchSurprise()
        }

        if (items.isEmpty()) {
            WidgetPersistHelper.persistLibraryItems(applicationContext, userPreferencesStore, emptyList(), versionBumpOnly = true)
            return@runCatching
        }

        val mapped = items.take(MAX_ITEMS).map { it.toWidgetItem() }
        WidgetPersistHelper.persistLibraryItems(applicationContext, userPreferencesStore, mapped, versionBumpOnly = false)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { e ->
            if (isPermanentFailure(e)) {
                Log.w(TAG, "Permanent failure, not retrying", e)
                Result.failure()
            } else {
                Result.retry()
            }
        },
    )

    private fun isPermanentFailure(throwable: Throwable): Boolean {
        val message = throwable.message ?: return false
        return message.contains("401") || message.contains("403") ||
               message.contains("404") || message.contains("Unauthorized") ||
               message.contains("Forbidden") || message.contains("Not Found")
    }

    private suspend fun fetchSimilarToRecent(): List<MediaItem>? {
        val seed = userPreferencesStore.continueWatching.first().firstOrNull() ?: return null
        val result = mediaRepository.getSimilarItems(seed.id, MAX_ITEMS).getOrNull() ?: return null
        if (result.isEmpty()) return null
        return result
    }

    private suspend fun fetchLatest(): List<MediaItem> {
        val sectionsResult = mediaRepository.getHomeSections(
            enabledSections = setOf(
                com.raulshma.jellyplay.core.model.HomeSectionType.LATEST_MEDIA,
                com.raulshma.jellyplay.core.model.HomeSectionType.RECENTLY_ADDED,
            ),
        )
        val items = sectionsResult.getOrNull().orEmpty()
            .flatMap { it.items }
            .distinctBy { it.id }
            .take(MAX_ITEMS)
        if (items.isNotEmpty()) return items
        return mediaRepository.getMediaItems(
            mediaTypes = VIDEO_MEDIA_TYPES,
            sortBy = "DateCreated",
            sortOrder = "Descending",
            limit = MAX_ITEMS,
        ).getOrDefault(EMPTY_RESULT).items
    }

    private suspend fun fetchFavorites(): List<MediaItem> {
        return mediaRepository.getFavorites(
            mediaTypes = VIDEO_MEDIA_TYPES,
            limit = MAX_ITEMS,
        ).getOrDefault(EMPTY_RESULT).items
    }

    private suspend fun fetchSurprise(): List<MediaItem> {
        return mediaRepository.getMediaItems(
            mediaTypes = VIDEO_MEDIA_TYPES,
            sortBy = "Random",
            sortOrder = "Ascending",
            limit = MAX_ITEMS,
        ).getOrDefault(EMPTY_RESULT).items
    }

    private fun MediaItem.toWidgetItem(): LibraryWidgetItem {
        val imageId = seriesId ?: id
        val poster = runCatching { playbackRepository.getImageUrl(imageId, maxWidth = 400) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
        return LibraryWidgetItem(
            itemId = id,
            name = name,
            mediaType = mediaType,
            year = year,
            communityRating = communityRating,
            seriesName = seriesName,
            posterUrl = poster,
            backdropUrl = null,
            isFavorite = isFavorite,
            seedItemName = seriesName,
        )
    }

    companion object {
        private const val TAG = "LibraryRecWidgetWorker"
        const val UNIQUE_PERIODIC_NAME = "library_recommendations_widget_periodic"
        const val UNIQUE_ONESHOT_NAME = "library_recommendations_widget_oneshot"
        private const val MAX_ITEMS = 9

        private val VIDEO_MEDIA_TYPES = listOf(MediaType.MOVIE, MediaType.SERIES)
        private val EMPTY_RESULT = SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0)
    }
}
