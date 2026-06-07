package com.raulshma.jellyplay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.model.LibraryRecommendationsSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

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
 *     [UserPreferencesStore.libraryWidgetItems] — no network in the
 *     widget process.
 *   * Tapping a cell launches [MainActivity] with a `jellyplay://media/{id}`
 *     deep link, which is parsed by
 *     [com.raulshma.jellyplay.deeplink.DeepLinkHandler] and routed to
 *     the media detail screen.
 */
class LibraryRecommendationsWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun userPreferencesStore(): UserPreferencesStore
    }

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
                WidgetWorkScheduler.refreshLibraryNow(context.applicationContext)
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
                    WidgetWorkScheduler.refreshLibraryNow(context.applicationContext)
                } finally {
                    pending.finish()
                }
            }
        }
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
            val views = RemoteViews(context.packageName, R.layout.library_recommendations_widget)
            views.setTextViewText(R.id.lr_widget_subtitle, readSourceLabel(context))

            val openApp = PendingIntent.getActivity(
                context,
                REQUEST_CODE_HEADER,
                WidgetDeepLinks.openAppIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.lr_widget_header, openApp)
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
            views.setRemoteAdapter(
                R.id.lr_widget_grid,
                Intent(context, LibraryRecommendationsWidgetService::class.java),
            )
            views.setEmptyView(R.id.lr_widget_grid, R.id.lr_widget_empty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun readSourceLabel(context: Context): String = runCatching {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            )
            runBlocking {
                entryPoint.userPreferencesStore().widgetConfig.first()
            }.librarySource.displayName
        }.getOrDefault(LibraryRecommendationsSource.SIMILAR_TO_RECENT.displayName)
    }
}
