package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges [AudioPlaybackManager] state into the [NowPlayingWidget].
 *
 * Pushes partial updates whenever any of {title, artist, playing-state,
 * artwork} changes. Position is sampled at 1Hz to avoid hammering the
 * app-widget IPC channel during playback.
 */
@Singleton
class NowPlayingWidgetUpdater @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val audioPlaybackManager: AudioPlaybackManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var metadataJob: Job? = null
    private var positionJob: Job? = null
    private var lastArtwork: Bitmap? = null
    private var lastItemId: String? = null

    fun start() {
        if (metadataJob?.isActive == true) return
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, NowPlayingWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        for (id in ids) {
            NowPlayingWidget.updateAppWidget(context, appWidgetManager, id)
        }
        metadataJob = scope.launch { observeMetadata() }
        positionJob = scope.launch { observePosition() }
    }

    fun stop() {
        metadataJob?.cancel()
        positionJob?.cancel()
        metadataJob = null
        positionJob = null
        lastArtwork = null
        lastItemId = null
    }

    private suspend fun observeMetadata() {
        combine(
            audioPlaybackManager.currentPlayingItemId,
            audioPlaybackManager.title,
            audioPlaybackManager.artist,
            audioPlaybackManager.albumArtUrl,
            audioPlaybackManager.isPlaying,
        ) { itemId, title, artist, artUrl, isPlaying ->
            MetadataSnapshot(itemId, title, artist, artUrl, isPlaying)
        }
            .distinctUntilChanged { old, new ->
                old.itemId == new.itemId &&
                    old.title == new.title &&
                    old.artist == new.artist &&
                    old.artUrl == new.artUrl &&
                    old.isPlaying == new.isPlaying
            }
            .collectLatest { snapshot ->
                if (snapshot.itemId != lastItemId) {
                    lastItemId = snapshot.itemId
                    lastArtwork = null
                }
                val art = loadArtwork(snapshot.artUrl)
                if (art != null) {
                    lastArtwork = art
                }
                pushUpdate(
                    title = snapshot.title,
                    subtitle = snapshot.artist.ifBlank { null },
                    isPlaying = snapshot.isPlaying,
                    albumArt = lastArtwork,
                    isEmptyState = snapshot.itemId == null,
                )
            }
    }

    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private suspend fun observePosition() {
        // Throttle to ~1 update/sec while playing. When paused, send a single
        // final update with the latest position so the bar stays put.
        audioPlaybackManager.currentPosition
            .sample(1_000L)
            .collect { pos ->
                val playing = audioPlaybackManager.isPlaying.value
                val duration = audioPlaybackManager.duration.value
                pushUpdate(
                    title = audioPlaybackManager.title.value,
                    subtitle = audioPlaybackManager.artist.value.ifBlank { null },
                    isPlaying = playing,
                    albumArt = lastArtwork,
                    positionMs = pos,
                    durationMs = duration,
                    isEmptyState = audioPlaybackManager.currentPlayingItemId.value == null,
                )
            }
    }

    private suspend fun loadArtwork(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .size(ARTWORK_SIZE)
                    .allowHardware(false)
                    .build()
                val result = context.imageLoader.execute(request)
                val bitmap = result.image?.toBitmap()
                if (bitmap != null) {
                    val density = context.resources.displayMetrics.density
                    val cornerRadiusPx = 12f * density
                    getRoundedCornerBitmap(bitmap, cornerRadiusPx)
                } else {
                    null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun getRoundedCornerBitmap(bitmap: Bitmap, pixels: Float): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val paint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = 0xff424242.toInt()
        }
        val rect = android.graphics.Rect(0, 0, bitmap.width, bitmap.height)
        val rectF = android.graphics.RectF(rect)
        canvas.drawARGB(0, 0, 0, 0)
        canvas.drawRoundRect(rectF, pixels, pixels, paint)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, rect, rect, paint)
        return output
    }

    private fun pushUpdate(
        title: String,
        subtitle: String?,
        isPlaying: Boolean,
        albumArt: Bitmap?,
        positionMs: Long = 0L,
        durationMs: Long = 0L,
        isEmptyState: Boolean = false,
    ) {
        NowPlayingWidget.updateAllWidgets(
            context = context,
            title = title,
            subtitle = subtitle,
            isPlaying = isPlaying,
            albumArt = albumArt,
            positionMs = positionMs,
            durationMs = durationMs,
            isEmptyState = isEmptyState,
        )
    }

    private data class MetadataSnapshot(
        val itemId: String?,
        val title: String,
        val artist: String,
        val artUrl: String?,
        val isPlaying: Boolean,
    )

    private companion object {
        const val ARTWORK_SIZE = 768
    }
}
