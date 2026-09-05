package com.raulshma.jellyplay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import org.koin.mp.KoinPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
 *   * Tapping a cell launches [MainActivity] with a `jellyfin://media/{id}`
 *     deep link, which is parsed by
 *     [com.raulshma.jellyplay.deeplink.DeepLinkHandler] and routed to
 *     the media detail screen.
 */
/**
 * Koin accessors (wave 8B — Hilt removal): resolved straight from the
 * application container, same try/catch shape the EntryPoint call used.
 */
private fun koinWidgetDataStore(): com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore =
    KoinPlatform.getKoin()!!.get()

private fun koinWidgetWorkScheduler(): WidgetWorkScheduler =
    KoinPlatform.getKoin()!!.get()

class LibraryRecommendationsWidget : AppWidgetProvider() {

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (id in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, id)
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
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.lr_widget_grid)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        triggerInitialRefresh(context)
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        refreshScope.cancel()
    }

    private fun triggerInitialRefresh(context: Context) {
        val pending = goAsync()
        refreshScope.launch {
            try {
                widgetScheduler(context).refreshLibraryNow()
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, LibraryRecommendationsWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.lr_widget_grid)
            val pending = goAsync()
            refreshScope.launch {
                try {
                    widgetScheduler(context).refreshLibraryNow()
                } finally {
                    pending.finish()
                }
            }
        }
    }

    private fun widgetScheduler(context: Context): WidgetWorkScheduler =
        koinWidgetWorkScheduler()

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
            val views = RemoteViews(context.packageName, R.layout.library_recommendations_widget)
            views.setTextViewText(R.id.lr_widget_subtitle, readSourceLabel(context, appWidgetId))

            // Apply responsive rules
            val dims = widgetDimensionsFromOptions(context, appWidgetManager.getAppWidgetOptions(appWidgetId), 250)
            if (dims != null) {
                val height = dims.height

                if (height < 130) {
                    views.setViewVisibility(R.id.lr_widget_header, android.view.View.GONE)
                } else {
                    views.setViewVisibility(R.id.lr_widget_header, android.view.View.VISIBLE)
                    if (height < 180) {
                        views.setViewVisibility(R.id.lr_widget_subtitle, android.view.View.GONE)
                        views.setViewVisibility(R.id.lr_widget_refresh, android.view.View.GONE)
                    } else {
                        views.setViewVisibility(R.id.lr_widget_subtitle, android.view.View.VISIBLE)
                        views.setViewVisibility(R.id.lr_widget_refresh, android.view.View.VISIBLE)
                    }
                }
            }

            val openApp = openAppPendingIntent(context, REQUEST_CODE_HEADER)
            views.setOnClickPendingIntent(R.id.lr_widget_header_text_container, openApp)
            views.setOnClickPendingIntent(R.id.lr_widget_empty, openApp)

            val refreshIntent = Intent(context, LibraryRecommendationsWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REFRESH,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.lr_widget_refresh, refreshPending)

            val templateIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                addCategory(Intent.CATEGORY_DEFAULT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val templatePending = PendingIntent.getActivity(
                context,
                REQUEST_CODE_ITEM,
                templateIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.lr_widget_grid, templatePending)

            val serviceIntent = Intent(context, LibraryRecommendationsWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.lr_widget_grid, serviceIntent)
            views.setEmptyView(R.id.lr_widget_grid, R.id.lr_widget_empty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun readSourceLabel(context: Context, appWidgetId: Int): String = runCatching {
            koinWidgetDataStore().getWidgetConfigForIdSync(appWidgetId)
                .librarySource.displayName
        }.getOrDefault(LibraryRecommendationsSource.SIMILAR_TO_RECENT.displayName)
    }
}
