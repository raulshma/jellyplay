package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.widget.RemoteViews
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.widget.skeleton.WidgetProviderSkeleton
import com.raulshma.jellyplay.widget.skeleton.widgetIdsFor
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.mp.KoinPlatform

/**
 * Koin accessors (wave 8B — Hilt removal): each call site resolves its
 * dependency straight from the application container, wrapped in the same
 * try/catch the former EntryPointAccessors call used (process-start race →
 * the caller's empty/fallback state, never a crash from the broadcast).
 */
private fun koinAudioPlaybackManager(): AudioPlaybackManager =
    KoinPlatform.getKoin()!!.get()

private fun koinNowPlayingWidgetUpdater(): NowPlayingWidgetUpdater =
    KoinPlatform.getKoin()!!.get()

class NowPlayingWidget : WidgetProviderSkeleton() {

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
        launchWidgetConfigCleanup(context, appWidgetIds)
    }

    private fun notifyUpdaterPresenceChanged(context: Context?) {
        if (context == null) return
        try {
            koinNowPlayingWidgetUpdater().onWidgetPresenceChanged()
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
        // The manager state is read once up front (same thread the former
        // inline reads used); the render itself goes through the shared
        // renderer, inside the main-handler post below.
        val manager = koinAudioPlaybackManager()
        val snapshot = NowPlayingWidgetRenderer.readPushSnapshot(manager)

        // Owns its goAsync() window inline (finish inside the posted main
        // handler, not a finally) rather than [launchWithPendingResult] —
        // the widget push happens on the main thread after the poster load.
        val pending = goAsync()
        refreshScope.launch {
            try {
                val art = if (!snapshot.artUrl.isNullOrBlank()) {
                    WidgetImageLoader.loadPoster(context.applicationContext, snapshot.artUrl)
                } else null

                val mainHandler = android.os.Handler(context.mainLooper)
                mainHandler.post {
                    NowPlayingWidgetRenderer.renderFullPush(
                        context = context,
                        appWidgetManager = appWidgetManager,
                        appWidgetId = appWidgetId,
                        snapshot = snapshot,
                        albumArt = art,
                    )
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
                if (isValidSeekPercent(percent)) {
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
            val target = seekTargetMs(percent, manager.duration.value) ?: return
            manager.seekTo(target)
        } catch (e: Exception) {
            Log.w(TAG, "Seek command failed: $percent%", e)
        } finally {
            pending.finish()
        }
    }

    private fun resolveAudioManager(context: Context): AudioPlaybackManager? = try {
        koinAudioPlaybackManager()
    } catch (e: Exception) {
        Log.w(TAG, "Failed to resolve AudioPlaybackManager", e)
        null
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

        /**
         * The bind the provider's onUpdate, the updater's dormant restart and
         * the config activity's save share: read the manager once (empty
         * state when it cannot be resolved — the process-start race) and
         * render through the shared pipeline. No artwork load on this path;
         * the updater's push owns the bitmap.
         */
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
        ) {
            val snapshot = try {
                NowPlayingWidgetRenderer.readPushSnapshot(koinAudioPlaybackManager())
            } catch (_: Exception) {
                EMPTY_STATE_SNAPSHOT
            }
            NowPlayingWidgetRenderer.renderFullPush(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                snapshot = snapshot,
                albumArt = null,
            )
        }

        /**
         * The updater's full push: render [snapshot] — already read through
         * [NowPlayingWidgetRenderer.readPushSnapshot] on the updater's
         * collector thread — onto every bound instance. Per-widget config
         * visibility is applied inside the renderer.
         */
        internal fun updateAllWidgets(
            context: Context,
            snapshot: WidgetPushSnapshot,
            albumArt: Bitmap? = null,
        ) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = widgetIdsFor(context, NowPlayingWidget::class.java)
            if (appWidgetIds.isEmpty()) return

            for (appWidgetId in appWidgetIds) {
                NowPlayingWidgetRenderer.renderFullPush(
                    context = context,
                    appWidgetManager = appWidgetManager,
                    appWidgetId = appWidgetId,
                    snapshot = snapshot,
                    albumArt = albumArt,
                )
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
            val appWidgetIds = widgetIdsFor(context, NowPlayingWidget::class.java)
            if (appWidgetIds.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.now_playing_widget)
            views.setTextViewText(
                R.id.widget_position,
                formatPosition(positionMs, durationMs, isPlaying),
            )
            views.setProgressBar(
                R.id.widget_progress,
                1_000,
                progressPerMille(positionMs, durationMs),
                false,
            )
            for (appWidgetId in appWidgetIds) {
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            }
        }

        /**
         * Fallback render when the playback manager cannot be resolved: the
         * empty-state branch of the bind (title/subtitle are never read
         * there, so blanks stand in for the nulls the former inline fallback
         * passed).
         */
        private val EMPTY_STATE_SNAPSHOT = WidgetPushSnapshot(
            title = "",
            subtitle = null,
            isPlaying = false,
            positionMs = 0L,
            durationMs = 0L,
            artUrl = null,
            isEmptyState = true,
        )
    }
}
