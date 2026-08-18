package com.raulshma.jellyplay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.raulshma.jellyplay.MainActivity
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NowPlayingWidget : AppWidgetProvider() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun audioPlaybackManager(): AudioPlaybackManager
        fun widgetDataStore(): com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
        fun nowPlayingWidgetUpdater(): NowPlayingWidgetUpdater
    }

    /**
     * Hoisted out of [onAppWidgetOptionsChanged] so the orphaned `SupervisorJob`
     * graph is not rebuilt on every widget refresh broadcast (the updater can
     * fire many times per minute during position changes). Mirrors the sibling
     * `LibraryRecommendationsWidget.refreshScope` pattern — cancelled in
     * [onDisabled] when the last widget instance is removed.
     */
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    /**
     * First widget instance just got pinned — kick the updater out of its
     * dormant state (it starts dormant when nothing is pinned so the process
     * doesn't pay 1 Hz app-widget binder traffic with zero widgets).
     */
    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        notifyUpdaterPresenceChanged(context)
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        refreshScope.cancel()
        notifyUpdaterPresenceChanged(context)
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        notifyUpdaterPresenceChanged(context)
        if (context == null || appWidgetIds == null) return
        val store = try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            ).widgetDataStore()
        } catch (_: Exception) {
            return
        }
        val pending = goAsync()
        refreshScope.launch {
            try {
                for (id in appWidgetIds) {
                    store.removeWidgetConfigForId(id)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun notifyUpdaterPresenceChanged(context: Context?) {
        if (context == null) return
        try {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                WidgetEntryPoint::class.java,
            ).nowPlayingWidgetUpdater().onWidgetPresenceChanged()
        } catch (_: Exception) {
            // Updater not constructed yet (process start race) — Application's
            // start() call will pick the widget list up anyway.
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        // A resize can arrive while the updater is dormant (e.g. after a
        // restore where no onEnabled followed) — spec's named restart point.
        notifyUpdaterPresenceChanged(context)
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        val manager = entryPoint.audioPlaybackManager()
        val title = manager.title.value
        val artist = manager.artist.value
        val isPlaying = manager.isPlaying.value
        val position = manager.currentPosition.value
        val duration = manager.duration.value
        val itemId = manager.currentPlayingItemId.value

        val artUrl = manager.albumArtUrl.value
        val pending = goAsync()
        refreshScope.launch {
            try {
                val art = if (!artUrl.isNullOrBlank()) {
                    WidgetImageLoader.loadPoster(context.applicationContext, artUrl)
                } else null

                val mainHandler = android.os.Handler(context.mainLooper)
                mainHandler.post {
                    val views = RemoteViews(context.packageName, R.layout.now_playing_widget)
                    wireClickIntents(context, views)
                    bindState(
                        views = views,
                        title = title,
                        subtitle = artist.ifBlank { null },
                        isPlaying = isPlaying,
                        albumArt = art,
                        positionMs = position,
                        durationMs = duration,
                        isEmptyState = itemId == null,
                    )
                    applyResponsiveLayout(context, appWidgetManager, appWidgetId, views)
                    appWidgetManager.updateAppWidget(appWidgetId, views)
                    pending.finish()
                }
            } catch (_: Exception) {
                // Ensure the goAsync() window always closes even if poster load
                // fails — otherwise the system may ANR the widget host.
                pending.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        when (action) {
            ACTION_PLAY_PAUSE,
            ACTION_NEXT,
            ACTION_PREV,
            ACTION_REWIND,
            ACTION_FORWARD,
            -> handleTransport(context, action)

            ACTION_SEEK_TO -> {
                val percent = intent.getIntExtra(EXTRA_SEEK_PERCENT, -1)
                if (percent in 0..100) {
                    handleSeek(context, percent)
                }
            }
        }
    }

    private fun handleTransport(context: Context, action: String) {
        val pending = goAsync()
        val manager = resolveAudioManager(context)
        try {
            if (manager == null) return
            when (action) {
                ACTION_PLAY_PAUSE -> manager.togglePlayPause()
                ACTION_NEXT -> manager.skipToNext()
                ACTION_PREV -> manager.skipToPrevious()
                ACTION_REWIND -> manager.seekByDelta(-SEEK_DELTA_MS)
                ACTION_FORWARD -> manager.seekByDelta(SEEK_DELTA_MS)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Transport command failed: $action", e)
        } finally {
            pending.finish()
        }
    }

    private fun handleSeek(context: Context, percent: Int) {
        val pending = goAsync()
        val manager = resolveAudioManager(context)
        try {
            if (manager == null) return
            val duration = manager.duration.value
            if (duration <= 0L) return
            val target = (percent.toLong() * duration / 100L).coerceIn(0L, duration)
            manager.seekTo(target)
        } catch (e: Exception) {
            Log.w(TAG, "Seek command failed: $percent%", e)
        } finally {
            pending.finish()
        }
    }

    private fun resolveAudioManager(context: Context): AudioPlaybackManager? = try {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
        entryPoint.audioPlaybackManager()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to resolve AudioPlaybackManager", e)
        null
    }

    /**
     * Lazy process-wide memo table for click PendingIntents — get-map, build,
     * put. Concurrent access from widget broadcasts is safe; a race only ever
     * builds the same instance twice, which is benign.
     */
    private class PendingIntentCache<K : Any> {
        @Volatile private var map: MutableMap<K, PendingIntent>? = null

        fun getOrCreate(key: K, build: (K) -> PendingIntent): PendingIntent {
            map?.get(key)?.let { return it }
            val pending = build(key)
            (map ?: ConcurrentHashMap<K, PendingIntent>().also { map = it }).put(key, pending)
            return pending
        }
    }

    companion object {
        private const val TAG = "NowPlayingWidget"

        const val ACTION_PLAY_PAUSE = "com.raulshma.jellyplay.widget.ACTION_PLAY_PAUSE"
        const val ACTION_NEXT = "com.raulshma.jellyplay.widget.ACTION_NEXT"
        const val ACTION_PREV = "com.raulshma.jellyplay.widget.ACTION_PREV"
        const val ACTION_REWIND = "com.raulshma.jellyplay.widget.ACTION_REWIND"
        const val ACTION_FORWARD = "com.raulshma.jellyplay.widget.ACTION_FORWARD"
        const val ACTION_SEEK_TO = "com.raulshma.jellyplay.widget.ACTION_SEEK_TO"
        const val ACTION_UPDATE = "com.raulshma.jellyplay.widget.ACTION_UPDATE"

        const val EXTRA_SEEK_PERCENT = "extra_seek_percent"

        private const val SEEK_DELTA_MS = 10_000L

        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val views = RemoteViews(context.packageName, R.layout.now_playing_widget)
            wireClickIntents(context, views)
            val config = try {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java,
                ).widgetDataStore().getWidgetConfigForIdSync(appWidgetId)
            } catch (_: Exception) {
                com.raulshma.jellyplay.core.model.WidgetConfig()
            }
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    WidgetEntryPoint::class.java,
                )
                val manager = entryPoint.audioPlaybackManager()
                val title = manager.title.value
                val artist = manager.artist.value
                val isPlaying = manager.isPlaying.value
                val position = manager.currentPosition.value
                val duration = manager.duration.value
                val itemId = manager.currentPlayingItemId.value
                bindState(
                    views = views,
                    title = title,
                    subtitle = artist.ifBlank { null },
                    isPlaying = isPlaying,
                    albumArt = null,
                    positionMs = position,
                    durationMs = duration,
                    isEmptyState = itemId == null,
                )
            } catch (e: Exception) {
                bindState(
                    views = views,
                    title = null,
                    subtitle = null,
                    isPlaying = false,
                    albumArt = null,
                    positionMs = 0L,
                    durationMs = 0L,
                    isEmptyState = true,
                )
            }
            if (!config.nowPlayingShowArtwork) {
                views.setViewVisibility(R.id.widget_album_art, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_backdrop, android.view.View.GONE)
            }
            if (!config.nowPlayingShowProgress) {
                views.setViewVisibility(R.id.widget_progress_container, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_progress, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_position, android.view.View.GONE)
            }
            applyResponsiveLayout(context, appWidgetManager, appWidgetId, views)
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
            isEmptyState: Boolean = false,
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NowPlayingWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.now_playing_widget)
                wireClickIntents(context, views)
                bindState(
                    views = views,
                    title = title,
                    subtitle = subtitle,
                    isPlaying = isPlaying,
                    albumArt = albumArt,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    isEmptyState = isEmptyState,
                )
                applyResponsiveLayout(context, appWidgetManager, appWidgetId, views)
                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        /**
         * Position-only push for the 1 Hz ticker. Uses
         * [AppWidgetManager.partiallyUpdateAppWidget] so only the position
         * label and progress-bar actions cross the binder — no artwork bitmap
         * re-parceling, no responsive-layout re-application, and the existing
         * click PendingIntents are retained by the host. Rendered output is
         * identical to a full update; only the diff transport is cheaper.
         */
        fun updateAllWidgetsPosition(
            context: Context,
            positionMs: Long,
            durationMs: Long,
            isPlaying: Boolean,
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, NowPlayingWidget::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            if (appWidgetIds.isEmpty()) return

            for (appWidgetId in appWidgetIds) {
                val views = RemoteViews(context.packageName, R.layout.now_playing_widget)
                views.setTextViewText(
                    R.id.widget_position,
                    formatPosition(positionMs, durationMs, isPlaying),
                )
                if (durationMs > 0L) {
                    val progress = ((positionMs.toFloat() / durationMs) * 1_000f).toInt()
                        .coerceIn(0, 1_000)
                    views.setProgressBar(R.id.widget_progress, 1_000, progress, false)
                } else {
                    views.setProgressBar(R.id.widget_progress, 1_000, 0, false)
                }
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }
        }

        private fun applyResponsiveLayout(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            views: RemoteViews,
        ) {
            val options = appWidgetManager.getAppWidgetOptions(appWidgetId) ?: return
            val dims = widgetDimensionsFromOptions(context, options, 110) ?: return
            val width = dims.width
            val height = dims.height

            // Responsive width rules. Compact (<180dp) keeps prev/next so the
            // widget stays usable down to its 110dp min-resize width; only the
            // artwork, progress, subtitle, and the secondary seek buttons drop
            // out.
            if (width < 180) {
                views.setViewVisibility(R.id.widget_album_art, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_progress_container, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_position, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_subtitle, android.view.View.GONE)

                views.setViewVisibility(R.id.widget_rewind, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_forward, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_prev, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_next, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_play_pause, android.view.View.VISIBLE)
            } else if (width < 280) {
                views.setViewVisibility(R.id.widget_album_art, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_subtitle, android.view.View.VISIBLE)

                views.setViewVisibility(R.id.widget_rewind, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_forward, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_prev, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_next, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_play_pause, android.view.View.VISIBLE)
            } else {
                views.setViewVisibility(R.id.widget_album_art, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_subtitle, android.view.View.VISIBLE)

                views.setViewVisibility(R.id.widget_rewind, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_forward, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_prev, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_next, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_play_pause, android.view.View.VISIBLE)
            }

            // Responsive height rules
            if (height < 100) {
                views.setViewVisibility(R.id.widget_progress_container, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_position, android.view.View.GONE)
            } else {
                views.setViewVisibility(R.id.widget_progress_container, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_position, android.view.View.VISIBLE)
            }
            if (height < 70) {
                views.setViewVisibility(R.id.widget_subtitle, android.view.View.GONE)
            }
        }

        private fun wireClickIntents(context: Context, views: RemoteViews) {
            val app = context.applicationContext
            views.setOnClickPendingIntent(R.id.widget_container, cachedOpenAppPending(app))
            views.setOnClickPendingIntent(R.id.widget_album_art, cachedOpenAppPending(app))
            views.setOnClickPendingIntent(R.id.widget_backdrop, cachedOpenAppPending(app))
            views.setOnClickPendingIntent(R.id.widget_empty_state, cachedOpenAppPending(app))

            views.setOnClickPendingIntent(
                R.id.widget_play_pause,
                cachedBroadcastPending(app, ACTION_PLAY_PAUSE, REQ_PLAY_PAUSE),
            )
            views.setOnClickPendingIntent(
                R.id.widget_next,
                cachedBroadcastPending(app, ACTION_NEXT, REQ_NEXT),
            )
            views.setOnClickPendingIntent(
                R.id.widget_prev,
                cachedBroadcastPending(app, ACTION_PREV, REQ_PREV),
            )
            views.setOnClickPendingIntent(
                R.id.widget_rewind,
                cachedBroadcastPending(app, ACTION_REWIND, REQ_REWIND),
            )
            views.setOnClickPendingIntent(
                R.id.widget_forward,
                cachedBroadcastPending(app, ACTION_FORWARD, REQ_FORWARD),
            )

            val seekZoneIds = SEEK_ZONE_IDS
            for (i in seekZoneIds.indices) {
                val percent = SEEK_PERCENTS[i]
                views.setOnClickPendingIntent(
                    seekZoneIds[i],
                    cachedSeekPending(app, percent, REQ_SEEK_BASE + i),
                )
            }
        }

        // Click PendingIntents are process-stable (fixed request codes, fixed
        // intents) — memoized so full widget pushes don't force the system's
        // PendingIntent table rewrite (FLAG_UPDATE_CURRENT) on every rebuild.
        // Races only ever build the same instance twice, which is benign.
        @Volatile private var cachedOpenApp: PendingIntent? = null
        private val broadcastPendingCache = PendingIntentCache<String>()
        private val seekPendingCache = PendingIntentCache<Int>()

        private fun cachedOpenAppPending(context: Context): PendingIntent =
            cachedOpenApp ?: PendingIntent.getActivity(
                context, REQ_OPEN_APP, openAppIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ).also { cachedOpenApp = it }

        private fun openAppIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }

        private fun cachedBroadcastPending(
            context: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent = broadcastPendingCache.getOrCreate(action) {
            broadcastPending(context, action, requestCode)
        }

        private fun cachedSeekPending(
            context: Context,
            percent: Int,
            requestCode: Int,
        ): PendingIntent = seekPendingCache.getOrCreate(percent) {
            seekPending(context, percent, requestCode)
        }

        private fun broadcastPending(
            context: Context,
            action: String,
            requestCode: Int,
        ): PendingIntent {
            val intent = Intent(context, NowPlayingWidget::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun seekPending(
            context: Context,
            percent: Int,
            requestCode: Int,
        ): PendingIntent {
            val intent = Intent(context, NowPlayingWidget::class.java).apply {
                action = ACTION_SEEK_TO
                putExtra(EXTRA_SEEK_PERCENT, percent)
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun bindState(
            views: RemoteViews,
            title: String?,
            subtitle: String?,
            isPlaying: Boolean,
            albumArt: Bitmap?,
            positionMs: Long,
            durationMs: Long,
            isEmptyState: Boolean,
        ) {
            if (isEmptyState) {
                views.setViewVisibility(R.id.widget_empty_state, android.view.View.VISIBLE)
                views.setViewVisibility(R.id.widget_content, android.view.View.GONE)
                views.setViewVisibility(R.id.widget_backdrop, android.view.View.GONE)
                return
            }
            views.setViewVisibility(R.id.widget_empty_state, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_content, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_backdrop, android.view.View.VISIBLE)

            val displayTitle = title?.takeIf { it.isNotBlank() } ?: "—"
            views.setTextViewText(R.id.widget_title, displayTitle)
            views.setTextViewText(R.id.widget_subtitle, subtitle?.takeIf { it.isNotBlank() } ?: " ")
            views.setTextViewText(
                R.id.widget_position,
                formatPosition(positionMs, durationMs, isPlaying),
            )

            if (albumArt != null) {
                views.setImageViewBitmap(R.id.widget_album_art, albumArt)
                views.setImageViewBitmap(R.id.widget_backdrop, albumArt)
            } else {
                views.setImageViewResource(R.id.widget_album_art, R.drawable.widget_ic_music)
                views.setImageViewResource(R.id.widget_backdrop, R.drawable.widget_backdrop_placeholder)
            }
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play,
            )
            if (durationMs > 0L) {
                val progress = ((positionMs.toFloat() / durationMs) * 1_000f).toInt()
                    .coerceIn(0, 1_000)
                views.setProgressBar(R.id.widget_progress, 1_000, progress, false)
            } else {
                views.setProgressBar(R.id.widget_progress, 1_000, 0, false)
            }
        }

        private fun formatPosition(positionMs: Long, durationMs: Long, isPlaying: Boolean): String {
            if (durationMs <= 0L) return "—"
            val cur = formatMs(positionMs)
            val total = formatMs(durationMs)
            return if (isPlaying) "$cur / $total" else "Paused · $cur / $total"
        }

        private fun formatMs(ms: Long): String {
            val totalSec = (ms / 1000L).coerceAtLeast(0L)
            val m = totalSec / 60
            val s = totalSec % 60
            return "%d:%02d".format(m, s)
        }

        private const val REQ_OPEN_APP = 100
        private const val REQ_PLAY_PAUSE = 101
        private const val REQ_NEXT = 102
        private const val REQ_PREV = 103
        private const val REQ_REWIND = 104
        private const val REQ_FORWARD = 105
        private const val REQ_SEEK_BASE = 200

        private val SEEK_ZONE_IDS = intArrayOf(
            R.id.widget_seek_zone_0,
            R.id.widget_seek_zone_1,
            R.id.widget_seek_zone_2,
            R.id.widget_seek_zone_3,
            R.id.widget_seek_zone_4,
            R.id.widget_seek_zone_5,
            R.id.widget_seek_zone_6,
        )

        private val SEEK_PERCENTS = intArrayOf(0, 17, 33, 50, 67, 83, 100)
    }
}
