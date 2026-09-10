package com.raulshma.jellyplay.widget

import android.content.Context
import android.graphics.Bitmap
import com.raulshma.jellyplay.core.data.playback.AudioPlaybackManager
import com.raulshma.jellyplay.widget.skeleton.updateAllProviderWidgets
import com.raulshma.jellyplay.widget.skeleton.widgetIdsFor
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
 *
 * What to push, and what the last push retained, is decided by
 * [WidgetPushGate]; this class keeps the flow collection, the manager
 * re-reads and the RemoteViews submission.
 */
class NowPlayingWidgetUpdater (
    private val context: Context,
    private val audioPlaybackManager: AudioPlaybackManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var metadataJob: Job? = null
    private var positionJob: Job? = null
    private val pushGate = WidgetPushGate()

    fun start() {
        if (metadataJob?.isActive == true) return
        // The helper returns the ids it refreshed; the return is this
        // site's presence check — nothing pinned means stay dormant.
        val boundWidgetIds = updateAllProviderWidgets(
            context = context,
            providerClass = NowPlayingWidget::class.java,
            updateAppWidget = { manager, id ->
                NowPlayingWidget.updateAppWidget(context, manager, id)
            },
        )
        if (boundWidgetIds.isEmpty()) {
            // Nothing pinned: stay dormant. The widget provider's
            // onEnabled/onAppWidgetOptionsChanged re-kicks us when one lands.
            return
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
            val hasWidgets = widgetIdsFor(context, NowPlayingWidget::class.java).isNotEmpty()
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
        pushGate.reset()
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
                val art = loadArtwork(snapshot.artUrl)
                // The snapshot is re-read AFTER the artwork load — the manager
                // may have moved on while the poster downloaded, and the pushed
                // render wins over the metadata that triggered the load.
                val pushed = NowPlayingWidgetRenderer.readPushSnapshot(audioPlaybackManager)
                when (val decision = pushGate.decideOnMetadata(snapshot.itemId, art, pushed)) {
                    is WidgetPushGate.Decision.Full -> pushUpdate(pushed, decision.albumArt)
                    // The metadata source always pushes; Partial/Skip are the
                    // position ticker's answers.
                    else -> Unit
                }
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
        // re-wiring click intents at 1 Hz. The gate's
        // [WidgetPushGate.decideOnPositionTick] (over
        // [shouldPushPartialPosition]) holds the partial-vs-full race guard:
        // defer to the metadata collector's full push whenever the partial
        // couldn't re-render what moved, and suppress redundant pushes by
        // render equality.
        val snapshot = NowPlayingWidgetRenderer.readPushSnapshot(audioPlaybackManager)
        if (pushGate.decideOnPositionTick(snapshot) != WidgetPushGate.Decision.Partial) return

        NowPlayingWidget.updateAllWidgetsPosition(
            context = context,
            positionMs = snapshot.positionMs,
            durationMs = snapshot.durationMs,
            isPlaying = snapshot.isPlaying,
        )
    }

    private suspend fun loadArtwork(url: String?): Bitmap? {
        if (url.isNullOrBlank()) return null
        return WidgetImageLoader.loadPoster(context, url, cornerRadiusDp = 12f)
    }

    private fun pushUpdate(snapshot: WidgetPushSnapshot, albumArt: Bitmap?) {
        NowPlayingWidget.updateAllWidgets(
            context = context,
            snapshot = snapshot,
            albumArt = albumArt,
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
        /** Widget progress granularity — matches the player's 1 s ticker. */
        private const val POSITION_TICK_MS = 1_000L
    }
}
