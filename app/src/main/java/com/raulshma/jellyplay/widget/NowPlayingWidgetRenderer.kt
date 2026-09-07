package com.raulshma.jellyplay.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.raulshma.jellyplay.R
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.core.datastore.widget.WidgetDataStore
import com.raulshma.jellyplay.core.model.WidgetConfig
import com.raulshma.jellyplay.widget.skeleton.toViewVisibility
import java.util.concurrent.ConcurrentHashMap
import org.koin.mp.KoinPlatform

/**
 * The Now Playing widget's single render pipeline. Every FULL push — the
 * provider's onUpdate/options-changed, the updater's metadata push, and the
 * config activity's save — reads the manager state once through
 * [readPushSnapshot] and binds through [renderFullPush], so the three former
 * hand-copied read→bind paths cannot drift. The per-widget config
 * visibility ([nowPlayingConfigFold]) applies on every full push; before the
 * fold, the updater's push skipped it and could resurrect artwork/progress
 * the user had disabled.
 *
 * The renderer is synchronous and owns no threading — callers keep their
 * shells (the options-changed path's goAsync/Handler dance around the poster
 * load, the updater's Dispatchers.Default collectors). The 1 Hz
 * partial-position push deliberately does NOT route through here; it stays
 * with [NowPlayingWidget.updateAllWidgetsPosition] per the
 * [WidgetPushSnapshot.sameRenderAs] machinery.
 */
internal object NowPlayingWidgetRenderer {

    /**
     * Everything a full widget push renders, read from the manager in one
     * pass. Also the sole input to the render-equality guards, so those
     * guards and the pushes can never disagree about which values were
     * observed.
     */
    fun readPushSnapshot(manager: AudioPlaybackManager): WidgetPushSnapshot = WidgetPushSnapshot(
        title = manager.title.value,
        subtitle = manager.artist.value.ifBlank { null },
        isPlaying = manager.isPlaying.value,
        positionMs = manager.currentPosition.value,
        durationMs = manager.duration.value,
        artUrl = manager.albumArtUrl.value,
        isEmptyState = manager.currentPlayingItemId.value == null,
    )

    /**
     * Builds and binds one widget instance's full RemoteViews: click wiring,
     * state bind, responsive ladder + config fold visibility, then the
     * manager bind. The config is resolved per widget id here so no caller
     * can forget the visibility pass.
     */
    fun renderFullPush(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        snapshot: WidgetPushSnapshot,
        albumArt: Bitmap?,
    ) {
        val views = RemoteViews(context.packageName, R.layout.now_playing_widget)
        wireClickIntents(context, views)
        bindState(views, snapshot, albumArt)
        applyVisibility(context, appWidgetManager, appWidgetId, views, resolveWidgetConfig(appWidgetId), snapshot.isEmptyState)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    /** Koin accessor with the process-start race the provider paths carry. */
    private fun resolveWidgetConfig(appWidgetId: Int): WidgetConfig = try {
        KoinPlatform.getKoin()!!.get<WidgetDataStore>().getWidgetConfigForIdSync(appWidgetId)
    } catch (_: Exception) {
        WidgetConfig()
    }

    /**
     * The responsive ladder re-decides the size-driven views (skipped
     * wholesale when the widget's options are unavailable — the XML defaults
     * stand), then the config fold forces the artwork/progress row per the
     * user's toggles. Fold last: a disabled row stays GONE at every size.
     */
    private fun applyVisibility(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        views: RemoteViews,
        config: WidgetConfig,
        isEmptyState: Boolean,
    ) {
        val options = appWidgetManager.getAppWidgetOptions(appWidgetId)
        val dims = options?.let {
            widgetDimensionsFromOptions(context, it, WidgetLayoutThresholds.NOW_PLAYING_DEFAULT_HEIGHT_DP)
        }
        val layout = dims?.let { responsiveNowPlayingLayout(widthDp = it.width, heightDp = it.height) }
        if (layout != null) {
            views.setViewVisibility(R.id.widget_subtitle, layout.showSubtitle.toViewVisibility())
            views.setViewVisibility(R.id.widget_rewind, layout.showRewind.toViewVisibility())
            views.setViewVisibility(R.id.widget_forward, layout.showForward.toViewVisibility())
            views.setViewVisibility(R.id.widget_prev, layout.showPrev.toViewVisibility())
            views.setViewVisibility(R.id.widget_next, layout.showNext.toViewVisibility())
            views.setViewVisibility(R.id.widget_play_pause, layout.showPlayPause.toViewVisibility())
        }
        val fold = nowPlayingConfigFold(layout, config, isEmptyState)
        views.setViewVisibility(R.id.widget_album_art, fold.showAlbumArt.toViewVisibility())
        views.setViewVisibility(R.id.widget_backdrop, fold.showBackdrop.toViewVisibility())
        views.setViewVisibility(R.id.widget_progress_container, fold.showProgressContainer.toViewVisibility())
        views.setViewVisibility(R.id.widget_progress, fold.showProgressBar.toViewVisibility())
        views.setViewVisibility(R.id.widget_position, fold.showPosition.toViewVisibility())
    }

    private fun wireClickIntents(context: Context, views: RemoteViews) {
        val app = context.applicationContext
        views.setOnClickPendingIntent(R.id.widget_container, cachedOpenAppPending(app))
        views.setOnClickPendingIntent(R.id.widget_album_art, cachedOpenAppPending(app))
        views.setOnClickPendingIntent(R.id.widget_backdrop, cachedOpenAppPending(app))
        views.setOnClickPendingIntent(R.id.widget_empty_state, cachedOpenAppPending(app))

        views.setOnClickPendingIntent(
            R.id.widget_play_pause,
            cachedBroadcastPending(app, NowPlayingWidget.ACTION_PLAY_PAUSE, REQ_PLAY_PAUSE),
        )
        views.setOnClickPendingIntent(
            R.id.widget_next,
            cachedBroadcastPending(app, NowPlayingWidget.ACTION_NEXT, REQ_NEXT),
        )
        views.setOnClickPendingIntent(
            R.id.widget_prev,
            cachedBroadcastPending(app, NowPlayingWidget.ACTION_PREV, REQ_PREV),
        )
        views.setOnClickPendingIntent(
            R.id.widget_rewind,
            cachedBroadcastPending(app, NowPlayingWidget.ACTION_REWIND, REQ_REWIND),
        )
        views.setOnClickPendingIntent(
            R.id.widget_forward,
            cachedBroadcastPending(app, NowPlayingWidget.ACTION_FORWARD, REQ_FORWARD),
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

    private fun bindState(
        views: RemoteViews,
        snapshot: WidgetPushSnapshot,
        albumArt: Bitmap?,
    ) {
        if (snapshot.isEmptyState) {
            views.setViewVisibility(R.id.widget_empty_state, android.view.View.VISIBLE)
            views.setViewVisibility(R.id.widget_content, android.view.View.GONE)
            views.setViewVisibility(R.id.widget_backdrop, android.view.View.GONE)
            return
        }
        views.setViewVisibility(R.id.widget_empty_state, android.view.View.GONE)
        views.setViewVisibility(R.id.widget_content, android.view.View.VISIBLE)
        views.setViewVisibility(R.id.widget_backdrop, android.view.View.VISIBLE)

        views.setTextViewText(R.id.widget_title, widgetDisplayTitle(snapshot.title))
        views.setTextViewText(R.id.widget_subtitle, widgetDisplaySubtitle(snapshot.subtitle))
        views.setTextViewText(
            R.id.widget_position,
            formatPosition(snapshot.positionMs, snapshot.durationMs, snapshot.isPlaying),
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
            if (snapshot.isPlaying) R.drawable.widget_ic_pause else R.drawable.widget_ic_play,
        )
        views.setProgressBar(
            R.id.widget_progress,
            1_000,
            progressPerMille(snapshot.positionMs, snapshot.durationMs),
            false,
        )
    }

    // Click PendingIntents are process-stable (fixed request codes, fixed
    // intents) — memoized so full widget pushes don't force the system's
    // PendingIntent table rewrite (FLAG_UPDATE_CURRENT) on every rebuild.
    // Request codes are unique across open-app (100), transports (101–105)
    // and seek zones (200+), so one table keyed by request code covers all
    // of them. Races only ever build the same instance twice, which is
    // benign.
    private val pendingIntents = ConcurrentHashMap<Int, PendingIntent>()

    private fun cachedOpenAppPending(context: Context): PendingIntent =
        pendingIntents.computeIfAbsent(REQ_OPEN_APP) {
            PendingIntent.getActivity(
                context, REQ_OPEN_APP, openAppIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

    private fun cachedBroadcastPending(
        context: Context,
        action: String,
        requestCode: Int,
    ): PendingIntent = pendingIntents.computeIfAbsent(requestCode) {
        broadcastPending(context, action, requestCode)
    }

    private fun cachedSeekPending(
        context: Context,
        percent: Int,
        requestCode: Int,
    ): PendingIntent = pendingIntents.computeIfAbsent(requestCode) {
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
            action = NowPlayingWidget.ACTION_SEEK_TO
            putExtra(NowPlayingWidget.EXTRA_SEEK_PERCENT, percent)
        }
        return PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
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
}
