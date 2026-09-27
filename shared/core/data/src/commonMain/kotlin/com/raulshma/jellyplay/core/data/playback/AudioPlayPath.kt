package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.data.repository.PlaybackRepository
import com.raulshma.jellyplay.core.model.PlaybackStartInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The play-path payload [AudioPlayPath.resolve] yields — everything the
 * shared choreography needs from a resolved item, and nothing more. Each
 * adapter fills it from its own resolution source (Android: the media-detail
 * round-trip, or the queue-only local-file fallback; desktop: its
 * [AudioTrackResolver] track).
 *
 * [startPositionMs] arrives PRE-COMPUTED (server resume ticks / 10_000, or
 * the local-fallback zero): the resume ladder reads adapter-owned state, so
 * it belongs to the resolve seam, not the skeleton. Android's historical
 * restored-current-item override is dead code on the shared shape exactly
 * as it was dead on the hand-written one — `play()` claims
 * `currentItemId = itemId` SYNCHRONOUSLY before the async resolve runs, so
 * the "nothing loaded yet" clause can never hold (the recorded desktop
 * parity note; both managers always resumed from server ticks only).
 *
 * [title]/[artist]/[artistId]/[album] feed the now-playing detail publish
 * and the out-of-queue append row; [lyricArtistName]/[lyricTrackName]/
 * [lyricDurationSec] feed the lyrics fetch (Android's detail arm passes the
 * albumArtist chain nullable where the publish coalesces it to "").
 *
 * [uri] is the DESKTOP load input (the resolver track's uri, also Android's
 * local-fallback file uri); Android's media3 detail path builds its
 * `MediaItem`s through its browser ladder and leaves it null there.
 *
 * [reportsToServer] is false ONLY on Android's queue-only local-file
 * fallback arm — see [AudioPlayPath]'s reduced-choreography note.
 */
internal class AudioPlayTrack(
    val itemId: String,
    val startPositionMs: Long,
    val mediaSourceId: String?,
    /** Now-playing publish payload (the detail arm's resolved metadata). */
    val title: String,
    val artist: String,
    val artistId: String?,
    val album: String?,
    /** Lyric fetch payload (the detail arm's artist/track/duration triple). */
    val lyricArtistName: String?,
    val lyricTrackName: String,
    val lyricDurationSec: Double?,
    /** The resolved item's normalization gain (ReplayGain apply sites read it). */
    val normalizationGain: Float?,
    /** Queue-row duration (the resolved item's run time, in ms). */
    val durationMs: Long = 0L,
    /** Desktop load input; Android builds MediaItems adapter-side. */
    val uri: String? = null,
    /**
     * False = the REDUCED choreography arm (Android's queue-only local-file
     * fallback): publish + append + load + position ticker run, but the
     * server start report, the lyrics fetch, the ReplayGain apply and the
     * progress-reporter start DO NOT — the fallback's historical shape. Also
     * leaves an already-published load error standing (the fallback never
     * cleared it — a preserved quirk).
     */
    val reportsToServer: Boolean = true,
)

/**
 * The ONE user-play choreography skeleton both audio managers run — the
 * ~90-line mirror `play(itemId)` bodies (Android `AudioPlaybackManager`
 * androidMain / desktop `DesktopAudioQueueManager` jvmMain) folded into
 * commonMain beside the [AudioQueueStateCore] chassis they write.
 *
 * ## What [start] runs, in order (the load-bearing sequence)
 *
 *  SYNCHRONOUS prefix (on the caller's thread — the managers' main thread):
 *  1. [progressReporter].`reportStopped()` — the no-arg stop report
 *     (session id rotates synchronously inside).
 *  2. [clearTrackScopedState] — the adapter drops state scoped to the
 *     outgoing track (BOTH managers clear the A→B loop; the desktop also
 *     drops its next-item prefetch).
 *  3. Item claim + loading-flag arm (`state.beginItemLoad`;
 *     [onLoadingItemChanged] mirrors the adapter's fast-path flag).
 *  4. [acquireEngine] — the adapter creates/obtains its engine BEFORE the
 *     async body, exactly where both hand-written plays did.
 *
 *  ASYNC body (launched on [scope]):
 *  5. [resolve] → an [AudioPlayTrack], or null → the failure arm: publish
 *     [loadFailureText] onto the chassis error flow and stop. Android's
 *     resolve folds the detail+local ladder (killing the former intra-file
 *     copy of the append/build/load choreography).
 *  6. Error clear — ONLY on the server-reporting arm (the local fallback
 *     never cleared the load error it fell back from; preserved quirk).
 *  7. [publishDetail] — the ONE all-six-fields now-playing publish (the
 *     adapter branches detail vs local-file shape here).
 *  8. Out-of-queue append: the row the adapter builds via [appendQueueItem]
 *     joins the tail through the chassis ([AudioQueueStateCore.appendPlayedItem]).
 *  9. Under the (possibly just-moved) cursor: [beforeLoad] → [loadIntoEngine]
 *     → the server-reporting tail (start report with the *10_000 resume
 *     ticks math → [fetchLyrics] → [afterReporting]) → [startPositionTracking]
 *     → `progressReporter.start()`.
 * 10. Loading-flag disarm (mirror first, then the chassis cell — the
 *     hand-written order; NOT in a `finally`: an exception mid-body left
 *     the flag armed in both originals, and the same-item fast path relied
 *     on that arming to suppress re-entry).
 *
 * ## Declared divergences that stay adapter-side hooks
 *
 *  - PLAY-ON CAST ROUTING (Android's pre-skeleton early-return to a remote
 *    session) and the SAME-ITEM/loading fast-path guards stay in the
 *    adapters' `play()` — they read platform player state.
 *  - CROSSFADE CANCEL (Android, before the stop report) — adapter prefix.
 *  - QUEUE PRE-WARM vs NEXT-ITEM PREFETCH: Android's whole-queue
 *    `mapConcurrent` MediaItem build runs in [afterLoad] (its historical
 *    slot, right after the load); the desktop's next-item-only prefetch
 *    runs in [afterReporting] (its historical slot, after the lyrics
 *    fetch). Both default no-op.
 *  - REPLAYGAIN APPLY POSITION: the desktop applies the incoming row's
 *    context in [beforeLoad] (before the engine load — its af chain
 *    re-push); Android applies the resolved detail's gain in
 *    [afterReporting] (after the lyrics fetch). The two placements were
 *    ALREADY divergent in the hand-written bodies; the skeleton keeps both
 *    expressible instead of picking one.
 *  - The REDUCED arm ([AudioPlayTrack.reportsToServer] = false) runs only
 *    steps 7–9's publish/append/load/ticker — Android's local-file
 *    fallback, which never reported to the server, fetched lyrics, applied
 *    ReplayGain or started the progress reporter.
 *
 * Main-thread confined by contract (the [AudioQueueManager] thread
 * contract): adapters assert their platform main thread in `play()` before
 * calling [start]; the launched body inherits the caller's dispatcher.
 */
internal class AudioPlayPath(
    /** Launch target for the async resolve/load body. */
    private val scope: CoroutineScope,
    private val playbackRepository: PlaybackRepository,
    private val progressReporter: AudioProgressReporter,
    /** The queue-state chassis this path writes (same module — internal cells). */
    private val state: AudioQueueStateCore,
    /** Item resolution: the adapter's detail/local ladder or track resolver. */
    private val resolve: suspend (itemId: String) -> AudioPlayTrack?,
    /**
     * The failure arm's error text, or null when the resolver has ALREADY
     * published the failure message itself (Android: the specific
     * detail-failure text goes out before its local-file probe, the
     * historical order, and re-publishing a captured copy would need
     * out-of-band mutable state — so its resolver owns the publish and
     * returns null here). Non-null: the skeleton publishes it (the desktop
     * keeps the constant default — its resolution seam folds detail+local
     * into one call, so a null result has no finer message — its recorded
     * divergence).
     */
    private val loadFailureText: () -> String? = { "Failed to load track" },
    /** Drops outgoing-track-scoped state (both managers clear the A→B loop). */
    private val clearTrackScopedState: () -> Unit,
    /** Mirrors the loading flag onto the adapter's fast-path cell. */
    private val onLoadingItemChanged: (Boolean) -> Unit,
    /** Creates/obtains the engine (Android getOrCreatePlayer / desktop twin). */
    private val acquireEngine: () -> Unit,
    /** The now-playing detail publish (adapter branches detail vs local file). */
    private val publishDetail: (track: AudioPlayTrack) -> Unit,
    /** Builds the out-of-queue append row (reads its just-published tracker values). */
    private val appendQueueItem: (track: AudioPlayTrack) -> AudioQueueItem,
    /** Desktop's pre-load ReplayGain apply; no-op on Android. */
    private val beforeLoad: (track: AudioPlayTrack, clickedItem: AudioQueueItem) -> Unit = { _, _ -> },
    /**
     * The engine load (Android setMediaItem/prepare/playWhenReady; desktop
     * load()). Suspend-typed: Android's ladder resolves its media3 MediaItem
     * through the suspend browser before loading; the desktop's non-suspend
     * body coerces.
     */
    private val loadIntoEngine: suspend (track: AudioPlayTrack, clickedItem: AudioQueueItem, startPositionMs: Long) -> Unit,
    /** Android's whole-queue pre-warm; no-op on desktop. */
    private val afterLoad: (track: AudioPlayTrack, clickedItem: AudioQueueItem) -> Unit = { _, _ -> },
    /** The lyric fetch (both arms' historical argument shapes differ). */
    private val fetchLyrics: (track: AudioPlayTrack, clickedItem: AudioQueueItem) -> Unit,
    /** Android's post-lyrics ReplayGain apply; desktop's next-item prefetch. */
    private val afterReporting: (track: AudioPlayTrack, clickedItem: AudioQueueItem) -> Unit = { _, _ -> },
    /** The adapter's position-ticker start (engine-specific plumbing). */
    private val startPositionTracking: () -> Unit,
) {

    /**
     * Runs the shared play choreography for [itemId] — see the class KDoc
     * for the ordered invariant list. The adapter's `play()` keeps only its
     * platform prefix (main-thread assert, Play-On routing, same-item
     * guard, crossfade cancel) and delegates here.
     */
    fun start(itemId: String) {
        progressReporter.reportStopped()
        clearTrackScopedState()
        state.beginItemLoad(itemId)
        onLoadingItemChanged(true)
        acquireEngine()

        scope.launch {
            val track = resolve(itemId)
            if (track == null) {
                loadFailureText()?.let(state::setLoadError)
            } else {
                // Error clear on the server arm only — the local fallback
                // never cleared the load error it fell back from (the
                // historical arm shape, preserved).
                if (track.reportsToServer) {
                    state.setLoadError(null)
                }
                publishDetail(track)

                val q = state._queue.value
                val currentIdx = state.currentIndex.value
                val isInQueue = currentIdx >= 0 && q.getOrNull(currentIdx)?.id == itemId
                if (!isInQueue) {
                    state.appendPlayedItem(appendQueueItem(track))
                }

                val clickedItem = state._queue.value.getOrNull(state.currentIndex.value)
                if (clickedItem != null) {
                    beforeLoad(track, clickedItem)
                    loadIntoEngine(track, clickedItem, track.startPositionMs)
                    if (track.reportsToServer) {
                        afterLoad(track, clickedItem)
                        playbackRepository.reportPlaybackStart(
                            PlaybackStartInfo(
                                itemId = itemId,
                                sessionId = state.playSessionId,
                                mediaSourceId = track.mediaSourceId,
                                startPositionTicks = if (track.startPositionMs > 0) {
                                    track.startPositionMs * 10_000
                                } else {
                                    null
                                },
                            )
                        )
                        fetchLyrics(track, clickedItem)
                        afterReporting(track, clickedItem)
                    }
                    startPositionTracking()
                    if (track.reportsToServer) {
                        progressReporter.start()
                    }
                }
            }
            // Mirror first, then the chassis cell — the hand-written order
            // (and deliberately not a finally: a mid-body exception left
            // the flags armed in both originals).
            onLoadingItemChanged(false)
            state.endItemLoad()
        }
    }
}
