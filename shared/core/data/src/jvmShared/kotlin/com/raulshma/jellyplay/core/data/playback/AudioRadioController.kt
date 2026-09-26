package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
 * **Threading.** The observer runs on the injected scope's dispatcher; the
 * [enqueue] lambda owns its own main-thread hop (the facade's
 * `enqueueTracks` pipeline), so this class never touches the queue contract
 * directly. Pure decisions — flows and lambdas in, no platform types —
 * so the whole state machine is unit-testable with MutableStateFlow fakes.
 */
class AudioRadioController(
    private val scope: CoroutineScope,
    queueFlow: StateFlow<List<AudioQueueItem>>,
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

    /** UI-visible radio status (now-playing surfaces show an active chip). */
    data class RadioState(
        val active: Boolean = false,
        val seedItemId: String? = null,
        val isRefilling: Boolean = false,
        /** Batches appended this session — a cheap "how alive is this" signal. */
        val refillCount: Int = 0,
    )

    private val _state = MutableStateFlow(RadioState())
    val state: StateFlow<RadioState> = _state.asStateFlow()

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
        refillJob?.cancel()
        consecutiveFailures = 0
        _state.value = RadioState(active = true, seedItemId = seedItemId)
    }

    /** Deactivates the radio (manual stop or failure burnout). */
    fun stop() {
        refillJob?.cancel()
        refillJob = null
        _state.value = RadioState()
    }

    private fun maybeRefill(queue: List<AudioQueueItem>, currentIndex: Int) {
        val current = _state.value
        if (!current.active || current.isRefilling || refillJob?.isActive == true) return
        if (queue.size >= MAX_QUEUE) return
        val remaining = queue.size - (currentIndex + 1)
        if (remaining > REFILL_THRESHOLD) return
        val seed = current.seedItemId ?: return

        refillJob = scope.launch {
            _state.update { it.copy(isRefilling = true) }
            try {
                fetchMix(seed).fold(
                    onSuccess = { tracks ->
                        val queuedIds = queue.mapTo(HashSet(queue.size)) { it.id }
                        val fresh = tracks.filter { it.id !in queuedIds }.take(REFILL_BATCH)
                        if (fresh.isNotEmpty()) {
                            enqueue(fresh)
                            _state.update { it.copy(refillCount = it.refillCount + 1) }
                        }
                        consecutiveFailures = 0
                    },
                    onFailure = {
                        consecutiveFailures++
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) stop()
                    },
                )
            } finally {
                _state.update { it.copy(isRefilling = false) }
            }
        }
    }
}
