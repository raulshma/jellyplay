package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.network.seerr.buildBackdropUrl
import com.raulshma.jellyplay.core.network.seerr.buildPosterUrl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Refreshes the cached items for the Seerr Recommendations widget.
 *
 * The chosen [SeerrWidgetSource] is persisted in
 * [UserPreferencesStore.widgetConfig]. Results are capped to
 * [MAX_ITEMS] and image URLs are pre-built using TMDB's public CDN
 * helpers ([buildPosterUrl], [buildBackdropUrl]) so the widget process
 * never has to authenticate to render artwork.
 */
@HiltWorker
class SeerrRecommendationsWidgetWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val userPreferencesStore: UserPreferencesStore,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val seerrRepository: SeerrRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        android.util.Log.d("SeerrWidgetWorker", "doWork: Starting Seerr widget sync")
        val seerrPrefs = seerrPreferencesStore.preferences.first()
        android.util.Log.d("SeerrWidgetWorker", "doWork: serverUrl = ${seerrPrefs.serverUrl}, hasApiKey = ${seerrPrefs.apiKey.isNotBlank()}")
        if (seerrPrefs.serverUrl.isBlank() || seerrPrefs.apiKey.isBlank()) {
            android.util.Log.w("SeerrWidgetWorker", "doWork: Seerr not configured (URL/API Key blank), persisting empty items")
            persist(emptyList(), versionBumpOnly = true)
            return@runCatching
        }

        val config = userPreferencesStore.widgetConfig.first()
        android.util.Log.d("SeerrWidgetWorker", "doWork: widgetConfig source = ${config.seerrSource}")
        val region = seerrPrefs.discoverRegion.ifBlank { "US" }
        val responseResult = fetch(config.seerrSource, region)
        android.util.Log.d("SeerrWidgetWorker", "doWork: fetch result = $responseResult")
        val response = responseResult.getOrNull()
        val items = response?.results.orEmpty().take(MAX_ITEMS)
        android.util.Log.d("SeerrWidgetWorker", "doWork: Fetched ${items.size} items")
        if (items.isEmpty()) {
            android.util.Log.w("SeerrWidgetWorker", "doWork: Items list is empty, persisting empty items")
            persist(emptyList(), versionBumpOnly = true)
            return@runCatching
        }
        val mapped = items.map { it.toWidgetItem() }
        android.util.Log.d("SeerrWidgetWorker", "doWork: Persisting ${mapped.size} mapped items")
        persist(mapped, versionBumpOnly = false)
    }.fold(
        onSuccess = { 
            android.util.Log.d("SeerrWidgetWorker", "doWork: Successfully finished Seerr widget sync")
            Result.success() 
        },
        onFailure = { error ->
            android.util.Log.e("SeerrWidgetWorker", "doWork: Seerr widget sync failed with error", error)
            Result.retry() 
        },
    )

    private suspend fun fetch(source: SeerrWidgetSource, region: String) = when (source) {
        SeerrWidgetSource.TRENDING -> seerrRepository.getTrending(page = 1)
        SeerrWidgetSource.POPULAR_MOVIES -> seerrRepository.getDiscoverMovies(page = 1)
        SeerrWidgetSource.POPULAR_TV -> seerrRepository.getDiscoverTv(page = 1)
        SeerrWidgetSource.UPCOMING_MOVIES -> seerrRepository.getDiscoverMovies(
            page = 1,
            primaryReleaseDateGte = todayIso(),
        )
        SeerrWidgetSource.UPCOMING_TV -> seerrRepository.getDiscoverTv(
            page = 1,
            firstAirDateGte = todayIso(),
        )
    }.map { it.copy(results = it.results.filter { item -> item.posterPath != null }) }

    private fun todayIso(): String = ISO_DATE_FORMAT.format(Date())

    private suspend fun persist(items: List<SeerrWidgetItem>, versionBumpOnly: Boolean) {
        val previous = userPreferencesStore.seerrWidgetItems.first()
        val previousVersion = userPreferencesStore.seerrWidgetVersion.first()
        val now = System.currentTimeMillis()
        val version = if (versionBumpOnly) previousVersion + 1L else now
        if (!versionBumpOnly && sameContent(previous, items)) {
            userPreferencesStore.setSeerrWidgetItems(items, previousVersion, now)
            return
        }
        userPreferencesStore.setSeerrWidgetItems(items, version, now)
        notifyWidgets()
    }

    private fun sameContent(
        previous: List<SeerrWidgetItem>,
        next: List<SeerrWidgetItem>,
    ): Boolean {
        if (previous.size != next.size) return false
        val prev = previous.map { it.tmdbId }.toSet()
        return next.all { it.tmdbId in prev }
    }

    private fun notifyWidgets() {
        val manager = AppWidgetManager.getInstance(applicationContext)
        val component = ComponentName(
            applicationContext,
            SeerrRecommendationsWidget::class.java,
        )
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return
        for (id in ids) {
            SeerrRecommendationsWidget.updateAppWidget(applicationContext, manager, id)
        }
        manager.notifyAppWidgetViewDataChanged(ids, R.id.sr_widget_grid)
    }

    private fun SeerrSearchItem.toWidgetItem(): SeerrWidgetItem {
        val title = displayName.ifBlank { "Untitled" }
        val subtitle = when (mediaType.lowercase(Locale.ROOT)) {
            "movie" -> "Movie"
            "tv" -> "TV Series"
            else -> mediaType.replaceFirstChar { it.titlecase(Locale.ROOT) }
        }
        return SeerrWidgetItem(
            tmdbId = id,
            mediaType = mediaType,
            title = title,
            subtitle = subtitle,
            year = year,
            voteAverage = voteAverage,
            overview = overview,
            posterUrl = buildPosterUrl(posterPath),
            backdropUrl = buildBackdropUrl(backdropPath),
        )
    }

    companion object {
        const val UNIQUE_PERIODIC_NAME = "seerr_recommendations_widget_periodic"
        const val UNIQUE_ONESHOT_NAME = "seerr_recommendations_widget_oneshot"
        private const val MAX_ITEMS = 9

        private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
