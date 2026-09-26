package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pure state-machine coverage for [AudioRadioController] over MutableStateFlow
 * fakes: the refill trigger (threshold), the dedupe (queued/played ids never
 * re-appended), the batch cap, the failure burnout, and stop semantics.
 */
class AudioRadioControllerTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var queue: MutableStateFlow<List<AudioQueueItem>>
    private lateinit var currentIndex: MutableStateFlow<Int>
    private lateinit var enqueued: List<List<MediaItem>>
    private var mixResult: Result<List<MediaItem>> = Result.success(emptyList())
    private var mixFetches = 0

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        queue = MutableStateFlow(emptyList())
        currentIndex = MutableStateFlow(0)
        enqueued = emptyList()
        mixFetches = 0
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun track(id: String) = MediaItem(id = id, name = id, mediaType = MediaType.AUDIO)

    private fun queued(id: String) = AudioQueueItem(
        id = id, name = id, artist = "", album = null, imageUrl = null,
        mediaSourceId = null, durationMs = 0L, normalizationGain = null,
    )

    private fun controller(): AudioRadioController = AudioRadioController(
        scope = kotlinx.coroutines.CoroutineScope(dispatcher),
        queueFlow = queue,
        currentIndexFlow = currentIndex,
        fetchMix = {
            mixFetches++
            mixResult
        },
        enqueue = { tracks -> enqueued = enqueued + listOf(tracks) },
    )

    private fun AudioRadioController.appendedIds(): List<String> =
        enqueued.flatten().map { it.id }

    @Test
    fun `no refill while remaining tracks exceed the threshold`() = runTest(dispatcher) {
        val c = controller()
        c.start("seed")
        queue.value = (1..10).map { queued("t$it") }
        currentIndex.value = 0
        advanceUntilIdle()

        assertEquals(0, mixFetches)
        c.stop()
    }

    @Test
    fun `refills when the queue is within the threshold and dedupes queued ids`() = runTest(dispatcher) {
        val c = controller()
        c.start("seed")
        queue.value = listOf(queued("a"), queued("b"), queued("c"), queued("current"))
        currentIndex.value = 3 // 0 remaining → at threshold
        mixResult = Result.success(listOf(track("a"), track("d"), track("b"), track("e")))
        advanceUntilIdle()

        // Only the ids not already queued get appended (batch-capped).
        assertEquals(listOf("d", "e"), c.appendedIds())
        assertEquals(1, c.state.value.refillCount)
        c.stop()
    }

    @Test
    fun `empty mix batch does not crash the radio and re-fires on the next advance`() = runTest(dispatcher) {
        val c = controller()
        c.start("seed")
        queue.value = listOf(queued("current"))
        currentIndex.value = 0
        mixResult = Result.success(listOf(track("current"))) // all duplicates
        advanceUntilIdle()

        assertEquals(1, mixFetches)
        assertTrue(c.state.value.active)
        assertTrue(c.appendedIds().isEmpty())

        // A fresh emission (the user added something; the queue snapshot is a
        // new, unequal list) re-triggers the refill; the fresh track appends.
        mixResult = Result.success(listOf(track("fresh")))
        queue.value = listOf(queued("current"), queued("later"))
        advanceUntilIdle()
        assertEquals(listOf("fresh"), c.appendedIds())
        c.stop()
    }

    @Test
    fun `three consecutive failed fetches deactivate the radio`() = runTest(dispatcher) {
        val c = controller()
        c.start("seed")
        queue.value = listOf(queued("current"))
        currentIndex.value = 0
        mixResult = Result.failure(IllegalStateException("offline"))
        advanceUntilIdle()
        // Each failed refill re-arms on the NEXT queue emission (a track
        // advance / user mutation) — simulate three of them.
        repeat(2) { i ->
            queue.value = listOf(queued("current"), queued("extra$i"))
            advanceUntilIdle()
        }

        assertFalse(c.state.value.active, "radio must burn out after consecutive failures")
        assertEquals(3, mixFetches)
    }

    @Test
    fun `stop clears the state and later emissions trigger nothing`() = runTest(dispatcher) {
        val c = controller()
        c.start("seed")
        queue.value = listOf(queued("current"))
        c.stop()
        val fetchesBefore = mixFetches
        currentIndex.value = 0
        advanceUntilIdle()

        assertEquals(fetchesBefore, mixFetches)
        assertFalse(c.state.value.active)
        assertEquals(0, c.state.value.refillCount)
    }

    @Test
    fun `refill is capped when the queue reaches MAX_QUEUE`() = runTest(dispatcher) {
        val c = controller()
        c.start("seed")
        queue.value = (1..AudioRadioController.MAX_QUEUE).map { queued("t$it") }
        currentIndex.value = AudioRadioController.MAX_QUEUE - 1
        mixResult = Result.success(listOf(track("new")))
        advanceUntilIdle()

        assertEquals(0, mixFetches, "no fetch once the cap is reached")
        c.stop()
    }
}
