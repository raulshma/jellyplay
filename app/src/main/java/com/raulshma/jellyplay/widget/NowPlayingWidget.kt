package com.raulshma.jellyplay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.R

class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREV -> {
                context.sendBroadcast(
                    Intent(intent.action).setPackage(context.packageName)
                )
                val appWidgetManager = AppWidgetManager.getInstance(context)
                val componentName = ComponentName(context, NowPlayingWidget::class.java)
                val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
                onUpdate(context, appWidgetManager, appWidgetIds)
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE = "com.raulshma.jellyplay.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.raulshma.jellyplay.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.raulshma.jellyplay.widget.ACTION_PREV"
        const val ACTION_UPDATE = "com.raulshma.jellyplay.widget.ACTION_UPDATE"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_SUBTITLE = "extra_subtitle"
        const val EXTRA_IS_PLAYING = "extra_is_playing"
        const val EXTRA_ALBUM_ART = "extra_album_art"
        const val EXTRA_POSITION_MS = "extra_position_ms"
        const val EXTRA_DURATION_MS = "extra_duration_ms"

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.now_playing_widget)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val launchPendingIntent = PendingIntent.getActivity(
                context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, launchPendingIntent)

            val playPauseIntent = Intent(context, NowPlayingWidget::class.java).apply {
                action = ACTION_PLAY_PAUSE
            }
            val playPausePendingIntent = PendingIntent.getBroadcast(
                context, 1, playPauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_play_pause, playPausePendingIntent)

            val nextIntent = Intent(context, NowPlayingWidget::class.java).apply {
                action = ACTION_NEXT
            }
            val nextPendingIntent = PendingIntent.getBroadcast(
                context, 2, nextIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_next, nextPendingIntent)

            val prevIntent = Intent(context, NowPlayingWidget::class.java).apply {
                action = ACTION_PREV
            }
            val prevPendingIntent = PendingIntent.getBroadcast(
                context, 3, prevIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_prev, prevPendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(
            context: Context,
            title: String?,
            subtitle: String?,
            isPlaying: Boolean,
            albumArt: Bitmap? = null,
            positionMs: Long = 0L,
            durationMs: Long = 0L,
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NowPlayingWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.now_playing_widget)
                views.setTextViewText(R.id.widget_title, title ?: context.getString(R.string.app_name))
                views.setTextViewText(R.id.widget_subtitle, subtitle ?: "Not playing")
                views.setImageViewResource(
                    R.id.widget_play_pause,
                    if (isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play
                )
                if (albumArt != null) {
                    views.setImageViewBitmap(R.id.widget_album_art, albumArt)
                } else {
                    views.setImageViewResource(
                        R.id.widget_album_art,
                        R.drawable.widget_ic_music
                    )
                }
                if (durationMs > 0) {
                    val progress = ((positionMs.toFloat() / durationMs) * 1000).toInt().coerceIn(0, 1000)
                    views.setProgressBar(R.id.widget_progress, 1000, progress, false)
                } else {
                    views.setProgressBar(R.id.widget_progress, 1000, 0, false)
                }
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }
        }

        fun sendUpdateBroadcast(
            context: Context,
            title: String?,
            subtitle: String?,
            isPlaying: Boolean,
            albumArt: Bitmap? = null,
            positionMs: Long = 0L,
            durationMs: Long = 0L,
        ) {
            val intent = Intent(context, NowPlayingWidget::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_SUBTITLE, subtitle)
                putExtra(EXTRA_IS_PLAYING, isPlaying)
                putExtra(EXTRA_ALBUM_ART, albumArt)
                putExtra(EXTRA_POSITION_MS, positionMs)
                putExtra(EXTRA_DURATION_MS, durationMs)
            }
            intent.setPackage(context.packageName)
            context.sendBroadcast(intent)
        }
    }
}
