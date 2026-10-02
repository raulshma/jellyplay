package com.raulshma.jellyplay.core.data.playback

import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.raulshma.jellyplay.core.concurrency.mapConcurrent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore

/**
 * The windowed queue→playlist mirror — the ONE owner of the shape invariant
 * and the window math behind [AudioPlaybackManager]'s adapter-side media3
 * plumbing (the chassis core stays player-agnostic). Every writer of the
 * player playlist that is not a direct per-row echo of a queue mutation
 * lives here: the play-path pre-warm ([prewarm]), the transition window
 * slide ([extend]), the crossfade re-mirror ([prewarmAround]) and the ONE
 * whole-playlist rebuild write ([rebuild] — the shuffle reorder/restore,
 * the undo restore and the out-of-window fallback).
 *
 * THE INVARIANT (what "mirrors" means): the player's playlist is an
 * index-aligned PREFIX of the chassis queue — rows `[0, mediaItemCount)` are
 * the queue's FIRST rows, so player index equals queue index for every
 * mirrored row. The crossfader's next-row read, the per-mutation
 * remove/move writes and [EngineDispatch.prepare]'s window seek all ride
 * that equality. Rows past the pre-warm lookahead are simply not mirrored
 * yet; [mirrorsQueue] is the predicate every caller checks before relying
 * on it.
 *
 * THE WINDOW MATH (one formula, [windowEnd], used by every writer here):
 * the mirror covers `[0, min(cursor + 1 + PREWARM_LOOKAHEAD_ITEMS,
 * queue.size))` — the full prefix below the cursor (the prepend
 * reconciliation keeps it full so the index equality above holds) plus the
 * bounded lookahead ahead of it. The gapless auto-advance / crossfade
 * horizon is the single next row (AudioCrossfader fades into
 * currentMediaItemIndex + 1, at most one 12 s fade before it), so the
 * lookahead is margin for the window extension on rapid transitions — and
 * must stay well inside the detail TtlCache so the window cannot thrash it
 * by itself. The default 25-entry [mediaItemCache] would still thrash under
 * the out-of-window fallbacks ([rebuild] resolves rows past the lookahead
 * on demand) — [prewarm] resizes the LRU to the queue.
 *
 * Everything is recomputed from the live queue, cursor and player at
 * trigger time — no window math survives a queue mutation. The Main-thread
 * writes re-check the player identity (and, for [extend], the queue
 * identity and the frontier) closest to the write, so a player swap or
 * queue mutation during the async build bails instead of writing stale
 * rows. This stays adapter-side androidMain plumbing: queue-state mutations
 * are NOT routed through EngineDispatch/AudioQueueStateCore here (the
 * recorded deferral — see the chassis core's declared divergence).
 *
 * @param scope the manager's playback scope (every build launches into it).
 * @param playerProvider the live engine read; the Main-write identity
 *   guards re-read it — it must reflect the manager's `exoPlayer` field,
 *   never a lazily-creating accessor.
 * @param queueProvider the chassis queue read.
 * @param cursorProvider the chassis cursor read.
 * @param writeCursor the chassis-cursor write the play-path prepend
 *   reconciliation makes — the mirror has no chassis cell of its own.
 * @param buildItem the item-builder path: one queue row → its playable
 *   [MediaItem] (null = unresolvable). The manager binds its
 *   [AudioLibraryBrowser.buildPlayableMediaItem] ladder here.
 */
internal class QueuePlaylistMirror(
    private val scope: CoroutineScope,
    private val playerProvider: () -> ExoPlayer?,
    private val queueProvider: () -> List<AudioQueueItem>,
    private val cursorProvider: () -> Int,
    private val writeCursor: (Int) -> Unit,
    private val buildItem: suspend (AudioQueueItem) -> MediaItem?,
) {

    companion object {
        // Queue pre-warm lookahead: queue rows mirrored ahead of the cursor
        // (the window shape it feeds is the class KDoc's invariant).
        internal const val PREWARM_LOOKAHEAD_ITEMS = 12
    }

    /** Window end for [cursor] over a queue of [size] rows — the one formula. */
    private fun windowEnd(cursor: Int, size: Int): Int =
        (cursor + 1 + PREWARM_LOOKAHEAD_ITEMS).coerceAtMost(size)

    private val preWarmPermits = Semaphore(8)
    private val mediaItemCache = android.util.LruCache<String, MediaItem>(25)

    /**
     * The play-path build's guard: queue mutations that would race an
     * in-flight mirror — [AudioPlaybackManager] reads [isLoading] before
     * removeFromQueue / the skips / undo / playFromQueue — park until the
     * build's Main write lands.
     */
    private var loadingJob: Job? = null
    private var windowJob: Job? = null

    /**
     * Set around a remove-of-the-current-row [AudioPlaybackManager.removeFromQueue]:
     * the chassis transition's player write is that caller's own
     * `removeMediaItem` (the shifted-in row then plays and its transition
     * echo reconciles), so [EngineDispatch.prepare] must not also
     * seek/rebuild on top of it.
     */
    var removingCurrentRow: Boolean = false

    /** True while a play-path mirror build is in flight (its guard, above). */
    val isLoading: Boolean get() = loadingJob != null

    /**
     * True while the player's playlist is an index-aligned prefix of the
     * chassis queue — the class KDoc's invariant.
     */
    fun mirrorsQueue(player: ExoPlayer): Boolean {
        val queue = queueProvider()
        val mirrored = player.mediaItemCount
        if (mirrored > queue.size) return false
        for (i in 0 until mirrored) {
            if (player.getMediaItemAt(i).mediaId != queue[i].id) return false
        }
        return true
    }

    /**
     * Builds [MediaItem]s for a queue segment concurrently (bounded by
     * [preWarmPermits], via [Semaphore.mapConcurrent]) while preserving
     * input order, so result order — and therefore the [mediaItemCache]
     * insertion order — matches the sequential `mapNotNull { ... }` loops
     * this replaces. Already-cached items short-circuit inside the transform
     * (the old ladder skipped their permit acquire via a completed deferred);
     * per-item failures cancel the siblings and propagate, exactly as the
     * old `coroutineScope { ... }` did.
     */
    private suspend fun buildMediaItemsFor(queueItems: List<AudioQueueItem>): List<MediaItem> =
        preWarmPermits.mapConcurrent(queueItems) { qi ->
            mediaItemCache.get(qi.id) ?: buildItem(qi)
        }.mapNotNull { it?.also { mediaItemCache.put(it.mediaId, it) } }

    /**
     * Builds the window's two halves concurrently — the rows after the
     * cursor through the window end, and the full prefix below it —
     * preserving the after-then-before await order the Main write consumes.
     */
    private suspend fun buildWindow(
        cursor: Int,
        queueItems: List<AudioQueueItem>,
    ): Pair<List<MediaItem>, List<MediaItem>> = coroutineScope {
        val afterJob = async { buildMediaItemsFor(queueItems.subList(cursor + 1, windowEnd(cursor, queueItems.size))) }
        val beforeJob = async { buildMediaItemsFor(queueItems.subList(0, cursor)) }
        afterJob.await() to beforeJob.await()
    }

    /**
     * The play-path pre-warm (the manager's `AudioPlayPath.afterLoad` seam —
     * Android's declared divergence from the desktop's next-item-only
     * prefetch): mirrors the window around the chassis cursor, cancelling
     * any in-flight builds first and resizing the LRU to the queue (see the
     * class KDoc's cache note).
     */
    fun prewarm(player: ExoPlayer) {
        val queueItems = queueProvider()
        val playIndex = cursorProvider()

        loadingJob?.cancel()
        windowJob?.cancel()
        mediaItemCache.resize(queueItems.size.coerceAtLeast(25))
        loadingJob = scope.launch(Dispatchers.IO) {
            val (mediaItemsAfter, mediaItemsBefore) = buildWindow(playIndex, queueItems)

            launch(Dispatchers.Main) {
                if (playerProvider() == player) {
                    if (mediaItemsAfter.isNotEmpty()) {
                        player.addMediaItems(mediaItemsAfter)
                    }
                    if (mediaItemsBefore.isNotEmpty()) {
                        player.addMediaItems(0, mediaItemsBefore)
                        // The prepend reconciliation: the chassis cursor is
                        // re-published so player index keeps equaling queue
                        // index under the shifted-in rows.
                        writeCursor(playIndex)
                    }
                }
                loadingJob = null
            }
        }
    }

    /**
     * The crossfade re-mirror: the same windowed mirror as [prewarm] — full
     * prefix below the crossfaded row, bounded lookahead ahead of it —
     * written to the [AudioCrossfader]-promoted secondary engine around its
     * already-committed cursor. No job guards, no cache resize and no
     * prepend reconciliation write: the crossfade teardown
     * (`AudioPlaybackManager.play`'s `crossfader.cancel()`) already
     * cancelled the play-path build whose guard this would disturb, the
     * play-path prewarm already sized the cache to the queue, and the
     * cursor was committed synchronously by the handoff before this runs.
     * Gated on a >1-row queue — a single row has no lookahead to mirror.
     */
    fun prewarmAround(player: ExoPlayer, cursor: Int) {
        val queueItems = queueProvider()
        if (queueItems.size <= 1) return
        scope.launch(Dispatchers.IO) {
            val (itemsAfter, itemsBefore) = buildWindow(cursor, queueItems)

            launch(Dispatchers.Main) {
                if (playerProvider() == player) {
                    if (itemsAfter.isNotEmpty()) {
                        player.addMediaItems(itemsAfter)
                    }
                    if (itemsBefore.isNotEmpty()) {
                        player.addMediaItems(0, itemsBefore)
                    }
                }
            }
        }
    }

    /**
     * Slides the pre-warm window forward after a media-item transition:
     * mirrors the queue rows that entered the [PREWARM_LOOKAHEAD_ITEMS]
     * lookahead since the last write. Everything is recomputed from the live
     * queue, cursor and player at trigger time — no window math survives a
     * queue mutation. Skipped while a build elsewhere owns the mirror (the
     * play-path pre-warm until its prepend restores alignment, the crossfade
     * re-mirror): appending under it would interleave out of queue order.
     */
    fun extend() {
        val player = playerProvider() ?: return
        val queueItems = queueProvider()
        val cursor = cursorProvider()
        if (cursor !in queueItems.indices) return
        if (!mirrorsQueue(player)) return
        val frontier = player.mediaItemCount
        val windowEnd = windowEnd(cursor, queueItems.size)
        if (windowEnd <= frontier) return
        val pending = queueItems.subList(frontier, windowEnd)
        // This cancel/restart (and [prewarm]'s) is what makes
        // SingleFlight.getOrFetch's retry-on-cancel recursion reachable — it
        // produced a fatal StackOverflowError on the first track-skip in
        // device testing. If the SingleFlight retry is not fixed first,
        // consider coalescing these restarts instead of cancelling in-flight builds.
        windowJob?.cancel()
        windowJob = scope.launch(Dispatchers.IO) {
            val mediaItems = buildMediaItemsFor(pending)
            launch(Dispatchers.Main) {
                if (playerProvider() == player && queueProvider() === queueItems && player.mediaItemCount == frontier) {
                    player.addMediaItems(mediaItems)
                }
                windowJob = null
            }
        }
    }

    /**
     * The ONE queue-rebuild write (the shuffle reorder/restore mirror and
     * the undo restore in [AudioPlaybackManager.toggleShuffle] /
     * [AudioPlaybackManager.undoLastQueueOperation] via the chassis, and the
     * out-of-window fallback via [EngineDispatch.prepare]): builds
     * [MediaItem]s for the index-aligned prefix window of [items] around
     * [targetIndex] — the same shape [prewarm] maintains, so rows past
     * [PREWARM_LOOKAHEAD_ITEMS] ahead of the target stay unmirrored until
     * [extend] slides over them — then replaces the player's playlist with
     * `setMediaItems(items, targetIndex, positionMs)` + prepare on Main.
     *
     * ONE canonical player-identity-check placement, chosen here: AFTER the
     * async build, on the Main thread, immediately before the write — the
     * check closest to the write is the only one that can actually close the
     * swap window (a check before the build would still race the swap that
     * happens while the build runs). Bail = no write, as in all pre-fold
     * copies (they only disagreed on where the check sat).
     *
     * [positionMs] is a provider evaluated at WRITE time on Main: the
     * shuffle arms read `player.currentPosition` there so playback that
     * continues during the build is not rewound, while the undo restore pins
     * the captured snapshot value. [targetIndex] is coerced into the BUILT
     * list's bounds — a partial build must not crash the write.
     */
    fun rebuild(
        items: List<AudioQueueItem>,
        targetIndex: Int,
        positionMs: () -> Long,
    ) {
        val player = playerProvider() ?: return
        if (items.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            val mediaItems = buildMediaItemsFor(items.subList(0, windowEnd(targetIndex, items.size)))
            launch(Dispatchers.Main) {
                if (mediaItems.isEmpty() || playerProvider() != player) return@launch
                player.setMediaItems(
                    mediaItems,
                    targetIndex.coerceIn(0, mediaItems.lastIndex),
                    positionMs(),
                )
                player.prepare()
            }
        }
    }
}
