package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import com.raulshma.jellyplay.widget.skeleton.GridWidgetRequestCodes
import com.raulshma.jellyplay.widget.skeleton.GridWidgetUi
import com.raulshma.jellyplay.widget.skeleton.updateRecommendationGridWidget
import org.koin.mp.KoinPlatform

/**
 * Home-screen widget that surfaces personalized recommendations from
 * the active Jellyfin server.
 *
 * Data flow:
 *   * [LibraryRecommendationsWidgetWorker] (scheduled in
 *     [com.raulshma.jellyplay.JellyPlayApplication.onCreate]) refreshes
 *     the cached list every 6h and on user-initiated refresh.
 *   * [LibraryRecommendationsWidgetService] is bound as the grid's
 *     remote adapter and reads the cached list straight from
 *     [com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore.libraryWidgetItems] — no network in the
 *     widget process.
 *   * Tapping a cell launches the app with a `jellyfin://media/{id}`
 *     deep link, which is parsed by
 *     [com.raulshma.jellyplay.deeplink.DeepLinkHandler] and routed to
 *     the media detail screen.
 *
 * The refresh-scope/goAsync choreography, the lifecycle overrides and the
 * `onReceive` refresh fold are shared with the Seerr grid via
 * [GridWidgetProvider]; the `updateAppWidget` wiring rides the widget
 * skeleton (`updateRecommendationGridWidget`, parameterized by this widget's
 * 7_400_0xx request-code namespace).
 */
/**
 * Koin accessors (wave 8B — Hilt removal): resolved straight from the
 * application container, same try/catch shape the EntryPoint call used.
 */
private fun koinWidgetDataStore(): com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore =
    KoinPlatform.getKoin()!!.get()

private fun koinWidgetWorkScheduler(): WidgetWorkScheduler =
    KoinPlatform.getKoin()!!.get()

class LibraryRecommendationsWidget : GridWidgetProvider() {

    override val gridViewId: Int = R.id.lr_widget_grid

    override val refreshAction: String = ACTION_REFRESH

    override fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
    ) {
        updateAppWidget(context, appWidgetManager, appWidgetId)
    }

    override suspend fun refreshNow(context: Context) {
        koinWidgetWorkScheduler().refreshLibraryNow()
    }

    companion object {
        const val ACTION_REFRESH = "com.raulshma.jellyplay.widget.ACTION_REFRESH_LIBRARY"

        const val REQUEST_CODE_HEADER = 7_400_010
        const val REQUEST_CODE_REFRESH = 7_400_011
        const val REQUEST_CODE_ITEM = 7_400_012

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            updateRecommendationGridWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                subtitleText = readSourceLabel(context, appWidgetId),
                ui = GridWidgetUi(
                    layoutRes = R.layout.library_recommendations_widget,
                    headerViewId = R.id.lr_widget_header,
                    headerTextContainerViewId = R.id.lr_widget_header_text_container,
                    subtitleViewId = R.id.lr_widget_subtitle,
                    refreshViewId = R.id.lr_widget_refresh,
                    gridViewId = R.id.lr_widget_grid,
                    emptyViewId = R.id.lr_widget_empty,
                    refreshAction = ACTION_REFRESH,
                    refreshBroadcastTarget = LibraryRecommendationsWidget::class.java,
                    serviceClass = LibraryRecommendationsWidgetService::class.java,
                    requestCodes = GridWidgetRequestCodes(
                        header = REQUEST_CODE_HEADER,
                        refresh = REQUEST_CODE_REFRESH,
                        item = REQUEST_CODE_ITEM,
                    ),
                ),
            )
        }

        private fun readSourceLabel(context: Context, appWidgetId: Int): String = runCatching {
            koinWidgetDataStore().getWidgetConfigForIdSync(appWidgetId)
                .librarySource.displayName
        }.getOrDefault(LibraryRecommendationsSource.SIMILAR_TO_RECENT.displayName)
    }
}
