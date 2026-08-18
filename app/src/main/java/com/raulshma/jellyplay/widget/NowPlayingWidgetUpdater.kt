package com.raulshma.jellyplay.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges [AudioPlaybackManager] state into the [NowPlayingWidget].
 *
 * Pushes partial updates whenever any of {title, artist, playing-state,
 * artwork} changes. Position pushes are driven by a 1 Hz ticker that runs
 * only while playing (the bounded paused-wait pattern used by the player's
 * position ticker): pausing cancels the ticker and sends one final update.
 *
 * Dormant while no widget is pinned: every push costs launcher binder IPC
 * (`getAppWidgetIds` + RemoteViews), so with zero widgets the collectors
 * don't run at all — [NowPlayingWidget.onEnabled] / [onDeleted] /
 * [onDisabled] / [onAppWidgetOptionsChanged] call [onWidgetPresenceChanged]
 * to (re)start or stop us.
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

    // Read/written from both the metadata and position collectors, which run
    // as separate coroutines on the Dispatchers.Default pool — volatile so a
    // position-tick thread always sees the metadata push that just landed.
    @Volatile private var lastPushFingerprint: PushFingerprint? = null

    fun start() {
        if (metadataJob?.isActive == true) return
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val componentName = ComponentName(context, NowPlayingWidget::class.java)
        val ids = appWidgetManager.getAppWidgetIds(componentName)
        if (ids.isEmpty()) {
            // Nothing pinned: stay dormant. The widget provider's
            // onEnabled/onAppWidgetOptionsChanged re-kicks us when one lands.
            return
        }
        for (id in ids) {
            NowPlayingWidget.updateAppWidget(context, appWidgetManager, id)
        }
        metadataJob = scope.launch { observeMetadata() }
        positionJob = scope.launch { observePosition() }
    }

    /**
     * Widget presence may have changed (pin added/removed). Restarts the
     * collectors when a widget exists, tears them down when the last one is
     * gone. Cheap no-op when presence didn't actually change.
     */
    fun onWidgetPresenceChanged() {
        scope.launch {
            val hasWidgets = AppWidgetManager.getInstance(context)
                .getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
                .isNotEmpty()
            if (hasWidgets) {
                start()
            } else {
                stop()
            }
        }
    }

    fun stop() {
        metadataJob?.cancel()
        positionJob?.cancel()
        metadataJob = null
        positionJob = null
        lastArtwork?.let { if (!it.isRecycled) it.recycle() }
        lastArtwork = null
        lastItemId = null
        lastPushFingerprint = null
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
                    lastArtwork?.let { if (!it.isRecycled) it.recycle() }
                    lastArtwork = null
                }
                val art = loadArtwork(snapshot.artUrl)
                if (art != null) {
                    lastArtwork?.let { if (!it.isRecycled) it.recycle() }
                    lastArtwork = art
                }
                pushUpdate(
                    title = snapshot.title,
                    subtitle = snapshot.artist.ifBlank { null },
                    isPlaying = snapshot.isPlaying,
                    albumArt = lastArtwork,
                    positionMs = audioPlaybackManager.currentPosition.value,
                    durationMs = audioPlaybackManager.duration.value,
                    isEmptyState = snapshot.itemId == null,
                )
            }
    }

    private suspend fun observePosition() = coroutineScope {
        // Bounded paused-wait: the 1 Hz ticker runs only while playing; a
        // pause cancels it (collectLatest) and sends one final update so the
        // bar and the "Paused ·" label settle. No clock-driven work — and no
        // binder IPC — while paused.
        launch {
            audioPlaybackManager.isPlaying.collectLatest { playing ->
                if (playing) {
                    while (true) {
                        pushPositionUpdate()
                        delay(POSITION_TICK_MS)
                    }
                } else {
                    pushPositionUpdate()
                }
            }
        }
        // A seek while paused moves the position without flipping isPlaying;
        // push those too so the bar doesn't go stale until playback resumes.
        launch {
            audioPlaybackManager.currentPosition.collect {
                if (!audioPlaybackManager.isPlaying.value) pushPositionUpdate()
            }
        }
    }

    private fun pushPositionUpdate() {
        // Position-only path: sends a partial RemoteViews (position label +
        // progress bar) instead of re-parceling the artwork bitmap and
        // re-wiring click intents at 1 Hz. Guarded by a fingerprint so no
        // redundant partial push crosses the binder.
        val title = audioPlaybackManager.title.value
        val subtitle = audioPlaybackManager.artist.value.ifBlank { null }
        val isPlaying = audioPlaybackManager.isPlaying.value
        val positionMs = audioPlaybackManager.currentPosition.value
        val durationMs = audioPlaybackManager.duration.value
        val isEmptyState = audioPlaybackManager.currentPlayingItemId.value == null

        val fingerprint = fingerprintOf(
            title = title,
            subtitle = subtitle,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            artUrl = audioPlaybackManager.albumArtUrl.value,
            isEmptyState = isEmptyState,
        )
        if (fingerprint == lastPushFingerprint) return
        lastPushFingerprint = fingerprint

        NowPlayingWidget.updateAllWidgetsPosition(
            context = context,
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
        )
    }

    private suspend fun loadArtwork(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return WidgetImageLoader.loadPoster(context, url, cornerRadiusDp = 12f)
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
        lastPushFingerprint = fingerprintOf(
            title = title,
            subtitle = subtitle,
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            artUrl = audioPlaybackManager.albumArtUrl.value,
            isEmptyState = isEmptyState,
        )
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

    private fun fingerprintOf(
        title: String,
        subtitle: String?,
        isPlaying: Boolean,
        positionMs: Long,
        durationMs: Long,
        artUrl: String?,
        isEmptyState: Boolean,
    ): PushFingerprint = PushFingerprint(
        title = title,
        subtitle = subtitle,
        isPlaying = isPlaying,
        positionBucketSec = positionMs / 1_000L,
        durationSec = durationMs / 1_000L,
        artUrl = artUrl,
        isEmptyState = isEmptyState,
    )

    /** Equality key for the last pushed widget render — see [pushPositionUpdate]. */
    private data class PushFingerprint(
        val title: String,
        val subtitle: String?,
        val isPlaying: Boolean,
        val positionBucketSec: Long,
        val durationSec: Long,
        val artUrl: String?,
        val isEmptyState: Boolean,
    )

    private data class MetadataSnapshot(
        val itemId: String?,
        val title: String,
        val artist: String,
        val artUrl: String?,
        val isPlaying: Boolean,
    )

    private companion object {
        /** Widget progress granularity — matches the player's 1 s ticker. */
        private const val POSITION_TICK_MS = 1_000L
    }
}
