package com.raulshma.jellyplay.widget

import android.content.Context
import android.util.Log
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.buildBackdropUrl
import com.raulshma.jellyplay.core.model.seerr.buildPosterUrl
import com.raulshma.jellyplay.widget.skeleton.RecommendationWorkerSkeleton
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Plain CoroutineWorker constructed by [AppWidgetWorkerFactory] (wave 8B —
 * Hilt removal: the former Hilt worker assisted-injection ctor became this
 * explicit constructor; deps resolve from the Koin container). A thin adapter
 * over [RecommendationWorkerSkeleton], which owns the guard → fetch →
 * empty-keep → persist → retry-fold chassis; this class supplies the Seerr
 * seams (server-configured guard, source-routed discover fetches, the
 * TMDB-url mapper, the persist call) and the permanent-only logging arm.
 */
class SeerrRecommendationsWidgetWorker(
    appContext: Context,
    params: WorkerParameters,
    private val widgetDataStore: WidgetDataStore,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val seerrRepository: SeerrRepository,
) : RecommendationWorkerSkeleton<SeerrSearchItem, SeerrWidgetItem>(
    appContext = appContext,
    params = params,
    maxItems = MAX_ITEMS,
) {

    // Resolved once by the guard (the preferences snapshot it already read)
    // and consumed by fetchItems — the single historical preferences read.
    private var discoverRegion: String = "US"

    override suspend fun skipFetch(): Boolean {
        val seerrPrefs = seerrPreferencesStore.preferences.first()
        discoverRegion = seerrPrefs.discoverRegion.ifBlank { "US" }
        // No Seerr server configured: leave existing cached items intact
        // so the widget keeps showing the last good snapshot.
        return seerrPrefs.serverUrl.isBlank()
    }

    override suspend fun fetchItems(): List<SeerrSearchItem> {
        val config = widgetDataStore.widgetConfig.first()
        val response = fetch(config.seerrSource, discoverRegion).getOrNull()
        return response?.results.orEmpty()
    }

    override fun mapItem(raw: SeerrSearchItem): SeerrWidgetItem = raw.toWidgetItem()

    override suspend fun persist(items: List<SeerrWidgetItem>) {
        WidgetPersistHelper.persistSeerrItems(applicationContext, widgetDataStore, items)
    }

    override fun logFailure(error: Throwable) {
        if (isPermanentWidgetFailure(error)) {
            Log.w(TAG, "Permanent failure, not retrying", error)
        }
    }

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
        private const val TAG = "SeerrRecWidgetWorker"
        const val UNIQUE_PERIODIC_NAME = "seerr_recommendations_widget_periodic"
        const val UNIQUE_ONESHOT_NAME = "seerr_recommendations_widget_oneshot"
        const val WORK_TAG = "widget"
        private const val MAX_ITEMS = 9

        private val ISO_DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
