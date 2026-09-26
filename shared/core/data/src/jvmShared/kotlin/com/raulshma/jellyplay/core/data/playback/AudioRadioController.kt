package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/**
 * Keeps an "endless radio" seeded off one item alive: observes the queue and
 * appends a fresh instant-mix batch whenever the user nears its end, forever
 * (a bounded-cap queue guard aside), so playback never blocks at the
 * RepeatNone tail while radio is active.
 *
 * **Refill rule.** When `queue.size - (currentIndex + 1) <= REFILL_THRESHOLD`,
 * fetch a mix for the seed and append up to [REFILL_BATCH] tracks the queue
 * does not already contain (both the played prefix and the pending tail
 * count as "already heard/queued" — the queue grows monotonically while
 * radio is active, so the full snapshot is the heard set). If a batch comes
 * back entirely duplicated the refill is a no-op this round — the next
 * advance re-triggers — and [MAX_CONSECUTIVE_FAILURES] failed fetches in a
 * row deactivate the radio (offline servers must not spin).
 *
 * **Queue hygiene.** No prefix trimming: [MAX_QUEUE] bounds the total size
 * (the persistence layer rewrites the full list per emission, and index
 * stability matters more than unbounded runtime) — after the cap the radio
 * simply stops appending.
 *
 * **Threading.** The observer runs on the injected scope's dispatcher while
 * [start]/[stop] are called from the caller's thread — every touch of
 * `refillJob` / `consecutiveFailures` / the active-vs-launch decision goes
 * through [lock], so a stop can never lose a race against a launch that is
 * already past its active check (a post-stop refill would otherwise keep
 * enqueuing into a stopped radio). An in-flight refill re-asserts its
 * claim right before appending ([mayAppend]): a stop, a re-start, or a
 * queue replaced by a fresh play aborts the append rather than splicing
 * the fetched batch into a queue it wasn't fetched for. The [enqueue]
 * lambda owns its own main-thread hop (the facade's `enqueueTracks` pipeline), so this class
 * never touches the queue contract directly. Pure decisions — flows and
 * lambdas in, no platform types — so the whole state machine is
 * unit-testable with MutableStateFlow fakes.
 */
class AudioRadioController(
    private val scope: CoroutineScope,
    private val queueFlow: StateFlow<List<AudioQueueItem>>,
    currentIndexFlow: StateFlow<Int>,
    private val fetchMix: suspend (seedItemId: String) -> Result<List<MediaItem>>,
    private val enqueue: suspend (tracks: List<MediaItem>) -> Unit,
) {
    companion object {
        /** Refill once the user is this many tracks from the end of the queue. */
        const val REFILL_THRESHOLD = 3

        /** Max tracks appended per refill (the mix endpoint returns up to 100). */
        const val REFILL_BATCH = 20

        /** Hard cap on the radio-grown queue (see the class KDoc's hygiene note). */
        const val MAX_QUEUE = 300

        /** Failed mixes in a row before the radio deactivates itself. */
        const val MAX_CONSECUTIVE_FAILURES = 3
    }

    private val _state = MutableStateFlow(RadioState())
    val state: StateFlow<RadioState> = _state.asStateFlow()

    /** Serializes refillJob/consecutiveFailures/state-active transitions (see Threading). */
    private val lock = Any()
    private var refillJob: Job? = null
    private var consecutiveFailures = 0

    init {
        scope.launch {
            combine(queueFlow, currentIndexFlow) { queue, index -> queue to index }
                .collect { (queue, index) -> maybeRefill(queue, index) }
        }
    }

    /** Arms the radio for [seedItemId] (call after the seed queue started playing). */
    fun start(seedItemId: String) {
        synchronized(lock) {
            refillJob?.cancel()
            refillJob = null
            consecutiveFailures = 0
            _state.value = RadioState(active = true, seedItemId = seedItemId)
        }
    }

    /** Deactivates the radio (manual stop or failure burnout). */
    fun stop() {
        synchronized(lock) {
            refillJob?.cancel()
            refillJob = null
            _state.value = RadioState()
        }
    }

    private fun maybeRefill(queue: List<AudioQueueItem>, currentIndex: Int) {
        synchronized(lock) {
            val current = _state.value
            if (!current.active || current.isRefilling || refillJob?.isActive == true) return
            if (queue.size >= MAX_QUEUE) return
            val remaining = queue.size - (currentIndex + 1)
            if (remaining > REFILL_THRESHOLD) return
            val seed = current.seedItemId ?: return

            refillJob = scope.launch { refill(seed, queue) }
        }
    }

    private suspend fun refill(seed: String, queue: List<AudioQueueItem>) {
        val self = coroutineContext.job
        synchronized(lock) {
            // A stop/start already replaced this job before the body ran.
            if (refillJob !== self) return
            _state.update { it.copy(isRefilling = true) }
        }
        try {
            fetchMix(seed).fold(
                onSuccess = { tracks ->
                    val queuedIds = queue.mapTo(HashSet(queue.size)) { it.id }
                    val fresh = tracks.filter { it.id !in queuedIds }.take(REFILL_BATCH)
                    if (fresh.isNotEmpty() && mayAppend(self, queue)) {
                        enqueue(fresh)
                        _state.update { it.copy(refillCount = it.refillCount + 1) }
                    }
                    synchronized(lock) { consecutiveFailures = 0 }
                },
                onFailure = {
                    val burnout = synchronized(lock) {
                        consecutiveFailures++
                        consecutiveFailures >= MAX_CONSECUTIVE_FAILURES
                    }
                    if (burnout) stop()
                },
            )
        } finally {
            synchronized(lock) {
                // A stop/start that replaced this job already reset the
                // state; only the owning job clears the refilling flag.
                if (refillJob === self) {
                    refillJob = null
                    _state.update { it.copy(isRefilling = false) }
                }
            }
        }
    }

    /**
     * Whether this refill job may still append. It must still own [refillJob]
     * and the radio must still be active (a stop or a re-start cancels its
     * claim), and the queue it snapshotted must still BE the queue — every
     * queue mutation swaps in a new list instance, so an identity mismatch
     * means the snapshot went stale against a replaced queue (a fresh play),
     * and appending would splice old-seed tracks into an unrelated queue.
     */
    private fun mayAppend(self: Job, snapshot: List<AudioQueueItem>): Boolean = synchronized(lock) {
        refillJob === self && _state.value.active && queueFlow.value === snapshot
    }
}

/**
 * UI-visible radio status (now-playing surfaces show an active chip).
 * Top-level beside [AudioRadioController] because the facade's
 * [AudioQueueFacade.radioState] exposes it as part of its own contract —
 * callers should not reach into the controller's nesting for it.
 */
data class RadioState(
    val active: Boolean = false,
    val seedItemId: String? = null,
    val isRefilling: Boolean = false,
    /** Batches appended this session — a cheap "how alive is this" signal. */
    val refillCount: Int = 0,
)
