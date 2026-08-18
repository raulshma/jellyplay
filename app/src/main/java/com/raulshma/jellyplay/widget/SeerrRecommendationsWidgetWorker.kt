package com.raulshma.jellyplay.widget

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.SeerrWidgetItem
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.buildBackdropUrl
import com.raulshma.jellyplay.core.model.seerr.buildPosterUrl
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltWorker
class SeerrRecommendationsWidgetWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val widgetDataStore: WidgetDataStore,
    private val seerrPreferencesStore: SeerrPreferencesStore,
    private val seerrRepository: SeerrRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        val seerrPrefs = seerrPreferencesStore.preferences.first()
        if (seerrPrefs.serverUrl.isBlank()) {
            // No Seerr server configured: leave existing cached items intact
            // so the widget keeps showing the last good snapshot.
            return@runCatching
        }

        val config = widgetDataStore.widgetConfig.first()
        val region = seerrPrefs.discoverRegion.ifBlank { "US" }
        val response = fetch(config.seerrSource, region).getOrNull()
        val items = response?.results.orEmpty().take(MAX_ITEMS)
        if (items.isEmpty()) {
            // Keep existing data instead of clearing the widget.
            return@runCatching
        }
        val mapped = items.map { it.toWidgetItem() }
        WidgetPersistHelper.persistSeerrItems(applicationContext, widgetDataStore, mapped, versionBumpOnly = false)
    }.fold(
        onSuccess = { Result.success() },
        onFailure = { e ->
            if (isPermanentWidgetFailure(e)) {
                Log.w(TAG, "Permanent failure, not retrying", e)
                Result.failure()
            } else {
                Result.retry()
            }
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
