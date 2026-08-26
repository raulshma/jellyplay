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
import com.raulshma.jellyplay.core.model.SeerrWidgetSource
import org.koin.mp.KoinPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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

class SeerrRecommendationsWidget : AppWidgetProvider() {

    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

    private fun triggerInitialRefresh(context: Context) {
        val pending = goAsync()
        refreshScope.launch {
            try {
                widgetScheduler(context).refreshSeerrNow()
            } finally {
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, SeerrRecommendationsWidget::class.java)
            val ids = appWidgetManager.getAppWidgetIds(componentName)
            appWidgetManager.notifyAppWidgetViewDataChanged(ids, R.id.sr_widget_grid)
            val pending = goAsync()
            refreshScope.launch {
                try {
                    widgetScheduler(context).refreshSeerrNow()
                } finally {
                    pending.finish()
                }
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
            val views = RemoteViews(context.packageName, R.layout.seerr_recommendations_widget)
            views.setTextViewText(R.id.sr_widget_subtitle, readSourceLabel(context, appWidgetId))

            // Apply responsive rules
            val dims = widgetDimensionsFromOptions(context, appWidgetManager.getAppWidgetOptions(appWidgetId), 250)
            if (dims != null) {
                val height = dims.height

                if (height < 130) {
                    views.setViewVisibility(R.id.sr_widget_header, android.view.View.GONE)
                } else {
                    views.setViewVisibility(R.id.sr_widget_header, android.view.View.VISIBLE)
                    if (height < 180) {
                        views.setViewVisibility(R.id.sr_widget_subtitle, android.view.View.GONE)
                        views.setViewVisibility(R.id.sr_widget_refresh, android.view.View.GONE)
                    } else {
                        views.setViewVisibility(R.id.sr_widget_subtitle, android.view.View.VISIBLE)
                        views.setViewVisibility(R.id.sr_widget_refresh, android.view.View.VISIBLE)
                    }
                }
            }

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

            val openApp = PendingIntent.getActivity(
                context,
                REQUEST_CODE_HEADER,
                WidgetDeepLinks.openAppIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.sr_widget_header_text_container, openApp)
            views.setOnClickPendingIntent(R.id.sr_widget_empty, openApp)

            val refreshIntent = Intent(context, SeerrRecommendationsWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            val refreshPending = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_REFRESH,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.sr_widget_refresh, refreshPending)

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
            views.setPendingIntentTemplate(R.id.sr_widget_grid, templatePending)

            val serviceIntent = Intent(context, SeerrRecommendationsWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.sr_widget_grid, serviceIntent)
            views.setEmptyView(R.id.sr_widget_grid, R.id.sr_widget_empty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
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
