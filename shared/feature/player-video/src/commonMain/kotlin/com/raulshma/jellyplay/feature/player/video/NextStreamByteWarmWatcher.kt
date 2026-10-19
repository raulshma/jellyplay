package com.raulshma.jellyplay.feature.player.video

import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.playback.isSessionKeyedUrl
import com.raulshma.jellyplay.core.model.PlayMethod
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.feature.player.video.engine.EnginePlaybackState
import com.raulshma.jellyplay.feature.player.video.engine.MediaEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Same log tag the VM's Up-Next mirror logs under — one logcat filter for the queue. */
private const val UP_NEXT_LOG_TAG = "UpNext"

/**
 * The next-item stream-cache pre-warm's head bound: ~3–8 s of media at
 * typical direct-play bitrates — enough to absorb the advance's connection
 * setup + first segment reads (the inter-item rebuffer) without pulling the
 * whole file into the LRU ahead of the playing item's own cache fills.
 */
private const val NEXT_STREAM_WARM_BYTES = 8L * 1024 * 1024

/**
 * The combined gate snapshot behind the next-stream pre-warm's
 * `collectLatest` block ([NextStreamByteWarmWatcher.install]): one
 * immutable fold of the warm slot, the Up Next card, the engine's
 * playing-on-a-READY-engine fact, and the session's current item, so every
 * cancellation trigger (queue change, playback stop, engine swap, handover)
 * rides a single emission that replaces — and thereby cancels — the
 * in-flight warm.
 */
private data class NextStreamWarmGate(
    val warm: WarmNextStream?,
    val cardItemId: String?,
    val enginePlaying: Boolean,
    val currentItemId: String?,
)

/**
 * The next-item pre-warm eligibility gate: mirrors the engine's own
 * stream-cache scoping (ExoPlayerEngine.isStreamCacheEligible) for the NEXT
 * item's resolved URL, composed from the same shared predicates that gate
 * enforces — [PlayMethod] (direct play / direct stream only: transcode URLs
 * are session-keyed and must never be pre-warmed or cached), the session-
 * param rejection ([isSessionKeyedUrl], the same `PlaySessionId`/
 * `TranscodingJobId`/`LiveStreamId` set), and the HLS inference (server
 * container hint or `.m3u8` URL — the same check the engine applies to VOD
 * requests, whose MIME is inferred from the URL).
 *
 * Declared divergences, both non-behavioral:
 *  - the DRM arm is omitted: `EngineConfig.drmSessionManagerProvider` is an
 *    extension slot no production construction in this app populates, so the
 *    engine's arm is vacuous here;
 *  - an added [PlayerType.EXO_PLAYER] requirement: the byte cache is only
 *    read back by the Exo engine's CacheDataSource layer, so warming for an
 *    mpv/VLC-preferred session would be pure bandwidth waste;
 *  - the HTTP(S) scheme check runs against the resolved [WarmNextStream.streamUrl]
 *    (offline downloads never reach the warm slot — they resolve to file
 *    URIs, rejected here before any byte flows).
 */
private fun isNextStreamWarmEligible(warm: WarmNextStream): Boolean {
    if (warm.playerType != PlayerType.EXO_PLAYER) return false
    if (!warm.streamUrl.startsWith("http", ignoreCase = true)) return false
    if (warm.playMethod != PlayMethod.DIRECT_PLAY && warm.playMethod != PlayMethod.DIRECT_STREAM) return false
    if (warm.container == "hls" || warm.streamUrl.contains(".m3u8", ignoreCase = true)) return false
    if (isSessionKeyedUrl(warm.streamUrl)) return false
    return true
}

/**
 * The next-item stream-cache pre-warm trigger, extracted from
 * [VideoPlayerViewModel] (its extracted-module size guard): when the
 * Up-Next card's item has a settled PlaybackInfo resolution (the session's
 * warm slot — the SAME trigger moment the resolve warm uses, so no extra
 * server traffic pattern) and that resolution passes the engine-mirroring
 * eligibility gate, stream the item's first [NEXT_STREAM_WARM_BYTES] into
 * the video stream cache so the eventual advance skips the inter-item
 * rebuffer. collectLatest IS the cancellation + single-flight: exactly
 * one warm runs, and any gate input change (queue/order via the card,
 * playback stop/pause, engine swap, handover to the target, warm-slot
 * reset) replaces the block — cancelling the in-flight bounded read,
 * whose already-written spans stay in the LRU cache for the real load.
 */
internal class NextStreamByteWarmWatcher(
    private val scope: CoroutineScope,
    private val warmNextStream: StateFlow<WarmNextStream?>,
    private val upNextCard: StateFlow<UpNextCard?>,
    private val engineFlow: StateFlow<MediaEngine?>,
    private val sessionState: StateFlow<PlayerSessionState>,
    private val isInSyncPlaySession: () -> Boolean,
    private val warmer: NextStreamByteWarmer?,
) {
    fun install() {
        scope.launch {
            combine(
                warmNextStream,
                upNextCard,
                // The current item must be playing on a READY engine — not
                // itself loading/buffering — for background warming to be
                // worth its bandwidth; an engine swap's null window drops
                // the gate for the swap's duration.
                engineFlow.flatMapLatest { engine ->
                    if (engine == null) {
                        flowOf(false)
                    } else {
                        combine(engine.isPlaying, engine.playbackState) { playing, state ->
                            playing && state == EnginePlaybackState.READY
                        }.distinctUntilChanged()
                    }
                },
                sessionState
                    .map { it.currentItemId }
                    .distinctUntilChanged(),
            ) { warm, card, enginePlaying, currentItemId ->
                NextStreamWarmGate(
                    warm = warm,
                    cardItemId = card?.itemId,
                    enginePlaying = enginePlaying,
                    currentItemId = currentItemId,
                )
            }.collectLatest { gate ->
                val warmer = warmer ?: return@collectLatest
                val warm = gate.warm ?: return@collectLatest
                if (!gate.enginePlaying) return@collectLatest
                // The warm target must still BE the card's item — a queue
                // edit or a refreshed adjacent episode makes this snapshot
                // stale and the emission replacing it cancels the read.
                if (gate.cardItemId != warm.itemId) return@collectLatest
                // Handover: the target became current — the real load is
                // (about to be) reading these very spans.
                if (gate.currentItemId == warm.itemId) return@collectLatest
                // SyncPlay belt-and-braces: the card projection already
                // nulls under a group session (the group owns the order).
                if (isInSyncPlaySession()) return@collectLatest
                if (!isNextStreamWarmEligible(warm)) return@collectLatest
                Log.d(UP_NEXT_LOG_TAG, "next-stream warm start item=${warm.itemId}")
                runCatchingRethrowingCancellation {
                    warmer.warmStreamHead(warm.streamUrl, NEXT_STREAM_WARM_BYTES)
                }.onFailure {
                    Log.d(UP_NEXT_LOG_TAG, "next-stream warm failed (ignored): ${it.message}")
                }
            }
        }
    }
}
