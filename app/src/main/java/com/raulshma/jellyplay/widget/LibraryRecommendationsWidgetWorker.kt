package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.R
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

/**
 * Refreshes the cached items for the Library Recommendations widget.
 *
 * Resolution rules per [LibraryRecommendationsSource]:
 *   * [LibraryRecommendationsSource.SIMILAR_TO_RECENT] — uses the most
 *     recently resumed item from
 *     [com.raulshma.jellyplay.core.datastore.UserPreferencesStore.continueWatching]
 *     as the seed and asks the server for "similar" items. Falls back to
 *     [LibraryRecommendationsSource.LATEST] when no seed is available.
 *   * [LibraryRecommendationsSource.LATEST] — uses
 *     [MediaRepository.getMediaItems] with `sortBy=DateCreated, sortOrder=Descending`.
 *   * [LibraryRecommendationsSource.FAVORITES] — uses
 *     [MediaRepository.getFavorites].
 *   * [LibraryRecommendationsSource.SURPRISE_ME] — uses
 *     [MediaRepository.getMediaItems] with `sortBy=Random`.
 *
 * The result is capped to [MAX_ITEMS] and persisted to
 * [UserPreferencesStore.setLibraryWidgetItems] so the widget process can
 * render the grid without hitting the network.
 */
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
        android.util.Log.d("LibraryWidgetWorker", "doWork: Starting library widget sync")
        authRepository.restoreSession()
        val server = authRepository.currentServer.first()
        android.util.Log.d("LibraryWidgetWorker", "doWork: currentServer after restoreSession = $server")
        if (server == null) {
            android.util.Log.w("LibraryWidgetWorker", "doWork: Server is null, persisting empty items")
            persist(emptyList(), versionBumpOnly = true)
            return@runCatching
        }

        val config = userPreferencesStore.widgetConfig.first()
        android.util.Log.d("LibraryWidgetWorker", "doWork: widgetConfig source = ${config.librarySource}")
        val items: List<MediaItem> = when (config.librarySource) {
            LibraryRecommendationsSource.SIMILAR_TO_RECENT -> fetchSimilarToRecent()
                ?: fetchLatest()

            LibraryRecommendationsSource.LATEST -> fetchLatest()

            LibraryRecommendationsSource.FAVORITES -> fetchFavorites()

            LibraryRecommendationsSource.SURPRISE_ME -> fetchSurprise()
        }

        android.util.Log.d("LibraryWidgetWorker", "doWork: Fetched ${items.size} items")
        if (items.isEmpty()) {
            android.util.Log.w("LibraryWidgetWorker", "doWork: Items list is empty, persisting empty items")
            persist(emptyList(), versionBumpOnly = true)
            return@runCatching
        }

        val mapped = items.take(MAX_ITEMS).map { it.toWidgetItem() }
        android.util.Log.d("LibraryWidgetWorker", "doWork: Persisting ${mapped.size} mapped items")
        persist(mapped, versionBumpOnly = false)
    }.fold(
        onSuccess = { 
            android.util.Log.d("LibraryWidgetWorker", "doWork: Successfully finished library widget sync")
            Result.success() 
        },
        onFailure = { error ->
            android.util.Log.e("LibraryWidgetWorker", "doWork: Library widget sync failed with error", error)
            Result.retry() 
        },
    )

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

    private suspend fun persist(items: List<LibraryWidgetItem>, versionBumpOnly: Boolean) {
        val previous = userPreferencesStore.libraryWidgetItems.first()
        val previousVersion = userPreferencesStore.libraryWidgetVersion.first()
        val now = System.currentTimeMillis()
        val version = if (versionBumpOnly) previousVersion + 1L else now
        if (!versionBumpOnly && sameContent(previous, items)) {
            userPreferencesStore.setLibraryWidgetItems(items, previousVersion, now)
            return
        }
        userPreferencesStore.setLibraryWidgetItems(items, version, now)
        notifyWidgets()
    }

    private fun sameContent(
        previous: List<LibraryWidgetItem>,
        next: List<LibraryWidgetItem>,
    ): Boolean {
        if (previous.size != next.size) return false
        val prev = previous.map { it.itemId }.toSet()
        return next.all { it.itemId in prev }
    }

    private fun notifyWidgets() {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(
            applicationContext,
            LibraryRecommendationsWidget::class.java,
        )
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        for (id in ids) {
            LibraryRecommendationsWidget.updateAppWidget(applicationContext, manager, id)
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.lr_widget_grid)
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
        const val UNIQUE_PERIODIC_NAME = "library_recommendations_widget_periodic"
        const val UNIQUE_ONESHOT_NAME = "library_recommendations_widget_oneshot"
        private const val MAX_ITEMS = 9

        private val VIDEO_MEDIA_TYPES = listOf(MediaType.MOVIE, MediaType.SERIES)
        private val EMPTY_RESULT = SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0)
    }
}
