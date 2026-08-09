package com.raulshma.jellyplay.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
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
    private val widgetDataStore: WidgetDataStore,
    private val mediaRepository: MediaRepository,
    private val playbackRepository: PlaybackRepository,
    private val authRepository: AuthRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        // Best-effort session restore. We must NOT fail the whole worker if
        // this returns a failure (e.g. transient DB or api-client error): the
        // `currentServer` check below decides whether we have enough state to
        // fetch. Failing here leaves the widget stuck on the empty state
        // because no persist (and therefore no notify) ever happens.
        authRepository.restoreSession()
        val server = authRepository.currentServer.first()
        if (server == null) {
            // No server: leave existing cached items intact so the widget
            // keeps showing the last good snapshot instead of going blank
            // during the window before the app restores the session.
            return@runCatching
        }

        val config = widgetDataStore.widgetConfig.first()
        val items: List<MediaItem> = when (config.librarySource) {
            LibraryRecommendationsSource.SIMILAR_TO_RECENT -> fetchSimilarToRecent()
                ?: fetchLatest()

            LibraryRecommendationsSource.LATEST -> fetchLatest()

            LibraryRecommendationsSource.FAVORITES -> fetchFavorites()

            LibraryRecommendationsSource.SURPRISE_ME -> fetchSurprise()
        }

        if (items.isEmpty()) {
            // Keep existing data instead of clearing the widget.
            return@runCatching
        }

        val mapped = items.take(MAX_ITEMS).map { it.toWidgetItem() }
        WidgetPersistHelper.persistLibraryItems(applicationContext, widgetDataStore, mapped, versionBumpOnly = false)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { e ->
            Log.e(TAG, "Worker execution failed", e)
            if (isPermanentWidgetFailure(e)) {
                Result.failure()
            } else {
                Result.retry()
            }
        },
    )

    private suspend fun fetchSimilarToRecent(): List<MediaItem>? {
        val seed = widgetDataStore.continueWatching.first().firstOrNull() ?: return null
        val result = mediaRepository.getSimilarItems(seed.id, MAX_ITEMS).getOrNull() ?: return null
        if (result.isEmpty()) return null
        return result
    }

    private suspend fun fetchLatest(): List<MediaItem> {
        val sectionsResult = mediaRepository.getHomeSections(
            com.raulshma.jellyplay.core.data.repository.HomeSectionQuery(
                enabledSections = setOf(
                    com.raulshma.jellyplay.core.model.HomeSectionType.LATEST_MEDIA,
                    com.raulshma.jellyplay.core.model.HomeSectionType.RECENTLY_ADDED,
                ),
            ),
        )
        val items = sectionsResult.getOrNull()?.sections.orEmpty()
            .flatMap { it.items }
            .distinctBy { it.id }
            .take(MAX_ITEMS)
        if (items.isNotEmpty()) return items
        return mediaRepository.getMediaItems(
            filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                mediaTypes = VIDEO_MEDIA_TYPES,
                sortBy = com.raulshma.jellyplay.core.model.SortOption.DATE_ADDED,
            ),
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
            filters = com.raulshma.jellyplay.core.model.LibraryFilters(
                mediaTypes = VIDEO_MEDIA_TYPES,
                sortBy = com.raulshma.jellyplay.core.model.SortOption.RANDOM,
            ),
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
        const val WORK_TAG = "widget"
        private const val MAX_ITEMS = 9

        private val VIDEO_MEDIA_TYPES = listOf(MediaType.MOVIE, MediaType.SERIES)
        private val EMPTY_RESULT = SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0)
    }
}
