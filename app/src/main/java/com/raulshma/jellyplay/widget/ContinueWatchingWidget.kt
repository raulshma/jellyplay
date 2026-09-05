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
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import org.koin.mp.KoinPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Koin accessor (wave 8B — Hilt removal): resolved straight from the
 * application container, same try/catch shape the EntryPoint call used.
 */
private fun koinWidgetDataStore(): com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore =
    KoinPlatform.getKoin()!!.get()

class ContinueWatchingWidget : AppWidgetProvider() {

    /**
     * Runs [onDeleted] config cleanup off the main thread — mirrors the
     * sibling `SeerrRecommendationsWidget.refreshScope` goAsync() pattern.
     */
    private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        if (context == null || appWidgetIds == null) return
        val store = try {
            koinWidgetDataStore()
        } catch (_: Exception) {
            return
        }
        val pending = goAsync()
        cleanupScope.launch {
            try {
                for (id in appWidgetIds) {
                    store.removeWidgetConfigForId(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.cw_widget_list)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ContinueWatchingWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.cw_widget_list)
        }
    }

    companion object {
        const val ACTION_REFRESH =
            "com.raulshma.jellyplay.widget.ACTION_REFRESH_CONTINUE_WATCHING"

        fun triggerUpdate(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ContinueWatchingWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isNotEmpty()) {
                appWidgetManager.notifyAppWidgetViewDataChanged(
                    appWidgetIds,
                    R.id.cw_widget_list,
                )
            }
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.continue_watching_widget)

            // Apply responsive rules
            val dims = widgetDimensionsFromOptions(context, appWidgetManager.getAppWidgetOptions(appWidgetId), 220)
            if (dims != null) {
                val width = dims.width
                val height = dims.height

                if (height < 150) {
                    views.setViewVisibility(R.id.cw_widget_header, android.view.View.GONE)
                } else {
                    views.setViewVisibility(R.id.cw_widget_header, android.view.View.VISIBLE)
                }

                if (width < 220) {
                    views.setViewVisibility(R.id.cw_widget_see_all, android.view.View.GONE)
                } else {
                    views.setViewVisibility(R.id.cw_widget_see_all, android.view.View.VISIBLE)
                }
            }

            // Header click opens the continue-watching newsletter list.
            val headerUri = Uri.parse(DeepLinkGrammar.continueWatchingLink())
            val headerIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = headerUri
                addCategory(Intent.CATEGORY_DEFAULT)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val headerPending = PendingIntent.getActivity(
                context,
                REQUEST_CODE_HEADER,
                headerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.cw_widget_header, headerPending)
            views.setOnClickPendingIntent(R.id.cw_widget_empty, headerPending)

            // Item click template: each fillInIntent adds a per-item deep link
            // and the extras get merged into the template.
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
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_MUTABLE,
            )
            views.setPendingIntentTemplate(R.id.cw_widget_list, templatePending)

            // Bind the list adapter.
            val serviceIntent = Intent(context, ContinueWatchingWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.cw_widget_list, serviceIntent)
            views.setEmptyView(R.id.cw_widget_list, R.id.cw_widget_empty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private const val REQUEST_CODE_HEADER = 7_300_010
        private const val REQUEST_CODE_ITEM = 7_300_011
    }
}
