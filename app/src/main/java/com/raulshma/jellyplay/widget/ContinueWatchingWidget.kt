package com.raulshma.jellyplay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
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

class ContinueWatchingWidget : AppWidgetProvider() {

    private val widgetScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var updateJob: kotlinx.coroutines.Job? = null

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun preferencesStore(): UserPreferencesStore
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context,
            WidgetEntryPoint::class.java,
        )
        val store = entryPoint.preferencesStore()

        updateJob?.cancel()
        updateJob = widgetScope.launch {
            val items = store.continueWatching.first()
            appWidgetIds.forEach { appWidgetId ->
                updateWidget(context, appWidgetManager, appWidgetId, items)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, ContinueWatchingWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            onUpdate(context, appWidgetManager, appWidgetIds)
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
        const val ACTION_REFRESH = "com.raulshma.jellyplay.widget.ACTION_REFRESH_CONTINUE_WATCHING"

        fun triggerUpdate(context: Context) {
            val intent = Intent(context, ContinueWatchingWidget::class.java).apply {
                action = ACTION_REFRESH
            }
            context.sendBroadcast(intent)
        }

        fun updateWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            items: List<com.raulshma.jellyplay.core.model.MediaItem>,
        ) {
            val views = RemoteViews(context.packageName, R.layout.continue_watching_widget)

            // Launch app on header click
            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPending = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header, launchPending)

            // Clear previous item views (set empty text for up to 3 slots)
            listOf(R.id.item_1, R.id.item_2, R.id.item_3).forEach { id ->
                views.setTextViewText(id, "")
            }

            items.take(3).forEachIndexed { index, item ->
                val viewId = when (index) {
                    0 -> R.id.item_1
                    1 -> R.id.item_2
                    2 -> R.id.item_3
                    else -> return@forEachIndexed
                }
                val progress = item.runTimeTicks?.let { total ->
                    item.playbackPositionTicks?.let { pos ->
                        (pos * 100 / total).toInt()
                    }
                }
                val text = buildString {
                    append(item.name)
                    if (progress != null) append(" • ${progress}%")
                }
                views.setTextViewText(viewId, text)
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
