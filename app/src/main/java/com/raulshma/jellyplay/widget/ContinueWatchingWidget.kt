package com.raulshma.jellyplay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.RemoteViews
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.model.deeplink.DeepLinkGrammar
import com.raulshma.jellyplay.widget.skeleton.WidgetProviderSkeleton
import com.raulshma.jellyplay.widget.skeleton.continueWatchingChromeVisibility
import com.raulshma.jellyplay.widget.skeleton.toViewVisibility

class ContinueWatchingWidget : WidgetProviderSkeleton() {

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        // Config cleanup runs off the main thread inside the provider
        // skeleton's shared goAsync() scope.
        launchWidgetConfigCleanup(context, appWidgetIds)
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
            val dims = widgetDimensionsFromOptions(
                context,
                appWidgetManager.getAppWidgetOptions(appWidgetId),
                WidgetLayoutThresholds.CONTINUE_WATCHING_DEFAULT_HEIGHT_DP,
            )
            if (dims != null) {
                val chrome = continueWatchingChromeVisibility(dims.width, dims.height)
                views.setViewVisibility(R.id.cw_widget_header, chrome.showHeader.toViewVisibility())
                views.setViewVisibility(R.id.cw_widget_see_all, chrome.showSeeAll.toViewVisibility())
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
