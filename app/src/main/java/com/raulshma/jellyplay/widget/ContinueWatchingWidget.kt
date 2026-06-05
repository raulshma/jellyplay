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
import com.raulshma.jellyplay.deeplink.DeepLinkHandler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ContinueWatchingWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun preferencesStore(): UserPreferencesStore
    }

    private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: kotlinx.coroutines.Job? = null

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId)
        }
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

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        updateJob?.cancel()
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        updateJob?.cancel()
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

            // Header click opens the continue-watching newsletter list.
            val headerUri = Uri.parse(
                "${DeepLinkHandler.SCHEME_CUSTOM}://newsletter/CONTINUE_WATCHING",
            )
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
            val templateUri = Uri.parse("${DeepLinkHandler.SCHEME_CUSTOM}://media/")
            val templateIntent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = templateUri
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
            val serviceIntent = Intent(context, ContinueWatchingWidgetService::class.java)
            views.setRemoteAdapter(R.id.cw_widget_list, serviceIntent)
            views.setEmptyView(R.id.cw_widget_list, R.id.cw_widget_empty)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private const val REQUEST_CODE_HEADER = 7_300_010
        private const val REQUEST_CODE_ITEM = 7_300_011
    }
}
