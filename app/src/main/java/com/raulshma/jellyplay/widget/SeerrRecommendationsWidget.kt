package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import com.raulshma.jellyplay.widget.skeleton.GridWidgetRequestCodes
import com.raulshma.jellyplay.widget.skeleton.GridWidgetUi
import com.raulshma.jellyplay.widget.skeleton.WidgetProviderSkeleton
import com.raulshma.jellyplay.widget.skeleton.updateRecommendationGridWidget
import kotlinx.coroutines.cancel
import org.koin.mp.KoinPlatform

/**
 * Home-screen widget that surfaces Seerr (Jellyseerr/Overseerr)
 * recommendations. Renders a poster grid; tap routes to the in-app
 * Seerr detail screen via a `jellyplay://seerr/{tmdbId}/{mediaType}`
 * deep link.
 *
 * Data flow:
 *   * [SeerrRecommendationsWidgetWorker] refreshes the cached list every
 *     6h and on user-initiated refresh.
 *   * [SeerrRecommendationsWidgetService] is bound as the grid's remote
 *     adapter and reads from
 *     [com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore.seerrWidgetItems] — no network in the widget
 *     process.
 *
 * The refresh-scope/goAsync choreography and the `updateAppWidget` wiring
 * are shared with the Library grid via the widget skeleton (`WidgetProviderSkeleton`
 * and `updateRecommendationGridWidget`, parameterized by this widget's
 * 7_500_0xx request-code namespace); the Seerr-only empty-state texts ride
 * the template's extra-binding hook.
 */
/**
 * Koin accessors (wave 8B — Hilt removal): resolved straight from the
 * application container, same try/catch shape the EntryPoint call used.
 */
private fun koinWidgetDataStore(): com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore =
    KoinPlatform.getKoin()!!.get()

private fun koinSeerrPreferencesStore(): com.raulshma.jellyplay.core.datastore.SeerrPreferencesStore =
    KoinPlatform.getKoin()!!.get()

private fun koinWidgetWorkScheduler(): WidgetWorkScheduler =
    KoinPlatform.getKoin()!!.get()

class SeerrRecommendationsWidget : WidgetProviderSkeleton() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // The server-configured check reads a widget-independent pref — read
        // it once for the whole batch instead of per widget ID.
        val isServerConfigured = hasServerConfigured(context)
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id, isServerConfigured)
        }
        triggerInitialRefresh(context)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateAppWidget(context, appWidgetManager, appWidgetId)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.sr_widget_grid)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        triggerInitialRefresh(context)
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        refreshScope.cancel()
    }

    private fun triggerInitialRefresh(context: Context) = launchWithPendingResult {
        widgetScheduler(context).refreshSeerrNow()
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SeerrRecommendationsWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.sr_widget_grid)
            launchWithPendingResult {
                widgetScheduler(context).refreshSeerrNow()
            }
        }
    }

    private fun widgetScheduler(context: Context): WidgetWorkScheduler =
        koinWidgetWorkScheduler()

    companion object {
        const val ACTION_REFRESH = "com.raulshma.jellyplay.widget.ACTION_REFRESH_SEERR"

        const val REQUEST_CODE_HEADER = 7_500_010
        const val REQUEST_CODE_REFRESH = 7_500_011
        const val REQUEST_CODE_ITEM = 7_500_012

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            isServerConfigured: Boolean = hasServerConfigured(context),
        ) {
            updateRecommendationGridWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                subtitleText = readSourceLabel(context, appWidgetId),
                ui = GridWidgetUi(
                    layoutRes = R.layout.seerr_recommendations_widget,
                    headerViewId = R.id.sr_widget_header,
                    headerTextContainerViewId = R.id.sr_widget_header_text_container,
                    subtitleViewId = R.id.sr_widget_subtitle,
                    refreshViewId = R.id.sr_widget_refresh,
                    gridViewId = R.id.sr_widget_grid,
                    emptyViewId = R.id.sr_widget_empty,
                    refreshAction = ACTION_REFRESH,
                    refreshBroadcastTarget = SeerrRecommendationsWidget::class.java,
                    serviceClass = SeerrRecommendationsWidgetService::class.java,
                    requestCodes = GridWidgetRequestCodes(
                        header = REQUEST_CODE_HEADER,
                        refresh = REQUEST_CODE_REFRESH,
                        item = REQUEST_CODE_ITEM,
                    ),
                ),
                bindExtraContent = { views ->
                    if (isServerConfigured) {
                        views.setTextViewText(
                            R.id.sr_widget_empty_title,
                            context.getString(R.string.widget_seerr_no_recommendations)
                        )
                        views.setTextViewText(
                            R.id.sr_widget_empty_subtitle,
                            context.getString(R.string.widget_seerr_no_recommendations_subtitle)
                        )
                    } else {
                        views.setTextViewText(
                            R.id.sr_widget_empty_title,
                            context.getString(R.string.widget_seerr_recommendations_empty)
                        )
                        views.setTextViewText(
                            R.id.sr_widget_empty_subtitle,
                            context.getString(R.string.widget_seerr_recommendations_empty_subtitle)
                        )
                    }
                },
            )
        }

        private fun readSourceLabel(context: Context, appWidgetId: Int): String = runCatching {
            // Sync snapshot accessor — `onUpdate`/`onAppWidgetOptionsChanged` run
            // on the main thread, so a blocking DataStore read is not acceptable.
            koinWidgetDataStore().getWidgetConfigForIdSync(appWidgetId).seerrSource.displayName
        }.getOrDefault(SeerrWidgetSource.TRENDING.displayName)

        // Widget-independent server-configured read (serverUrl pref is set) —
        // callers looping over widget IDs should hoist this out of the loop.
        // `preferences` is an eagerly-started StateFlow, so `.value` is a
        // memory read safe for the main thread.
        private fun hasServerConfigured(context: Context): Boolean {
            return runCatching {
                koinSeerrPreferencesStore().preferences.value.serverUrl.isNotBlank()
            }.getOrDefault(false)
        }
    }
}
