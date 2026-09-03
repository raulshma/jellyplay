package com.raulshma.jellyplay.core.data.playback

import com.raulshma.jellyplay.core.database.dao.AudioQueueDao
import com.raulshma.jellyplay.core.database.entity.AudioQueueEntity
import com.raulshma.jellyplay.core.database.entity.AudioQueueStateEntity
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins [QueuePersistenceHelper]'s write-behind contract (the audio queue must
 * survive a process death without wearing the disk):
 *  1. every queue mutation persists the FULL list once (DELETE+INSERT via
 *     `replaceQueue`), an empty queue clearing the table instead;
 *  2. any current-index / playing / repeat / shuffle / speed change persists
 *     the composite state row in one write;
 *  3. playback-position ticks (≈4 Hz) are throttled through a 15 s sample —
 *     nothing is written inside the window, one write at its edge;
 *  4. `loadQueue`/`loadState` restore rows verbatim (a null artist column
 *     reads back as an empty string).
 */
class QueuePersistenceHelperTest {

    private lateinit var dao: AudioQueueDao
    private lateinit var helper: QueuePersistenceHelper

    private lateinit var queue: MutableStateFlow<List<AudioQueueItem>>
    private lateinit var currentIndex: MutableStateFlow<Int>
    private lateinit var currentPositionMs: MutableStateFlow<Long>
    private lateinit var isPlaying: MutableStateFlow<Boolean>
    private lateinit var repeatMode: MutableStateFlow<Int>
    private lateinit var shuffleEnabled: MutableStateFlow<Boolean>
    private lateinit var playbackSpeed: MutableStateFlow<Float>

    @BeforeTest
    fun setup() {
        dao = mockk(relaxed = true)
        helper = QueuePersistenceHelper(dao)
        queue = MutableStateFlow(emptyList())
        currentIndex = MutableStateFlow(-1)
        currentPositionMs = MutableStateFlow(0L)
        isPlaying = MutableStateFlow(false)
        repeatMode = MutableStateFlow(0)
        shuffleEnabled = MutableStateFlow(false)
        playbackSpeed = MutableStateFlow(1.0f)
    }

    private fun item(id: String) = AudioQueueItem(
        id = id,
        name = "Track $id",
        artist = "Artist",
        album = "Album",
        imageUrl = "https://s/$id",
        mediaSourceId = "ms-$id",
        durationMs = 200_000L,
        normalizationGain = null,
    )

    private fun kotlinx.coroutines.test.TestScope.startObserving() {
        helper.observeQueue(
            scope = backgroundScope,
            queue = queue,
            currentIndex = currentIndex,
            currentPositionMs = currentPositionMs,
            isPlaying = isPlaying,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            playbackSpeed = playbackSpeed,
        )
        runCurrent()
    }

    @Test
    fun `a queue mutation persists the full ordered list in one write`() = runTest {
        startObserving()

        queue.value = listOf(item("a"), item("b"))
        runCurrent()

        val slot = slot<List<AudioQueueEntity>>()
        coVerify(exactly = 1) { dao.replaceQueue(capture(slot)) }
        val entities = slot.captured
        assertEquals(2, entities.size)
        assertEquals(0, entities[0].position)
        assertEquals(1, entities[1].position)
        assertEquals("a", entities[0].id)
        assertEquals("Track b", entities[1].name)
        coVerify(exactly = 0) { dao.clearQueue() }
    }

    @Test
    fun `an emptied queue clears the table instead of writing rows`() = runTest {
        startObserving()
        queue.value = listOf(item("a"))
        runCurrent()

        queue.value = emptyList()
        runCurrent()

        coVerify(exactly = 1) { dao.clearQueue() }
        coVerify(exactly = 0) { dao.replaceQueue(emptyList()) }
    }

    @Test
    fun `an unchanged queue never writes`() = runTest {
        startObserving()
        advanceTimeBy(60_000)
        runCurrent()

        coVerify(exactly = 0) { dao.replaceQueue(any()) }
        coVerify(exactly = 0) { dao.clearQueue() }
    }

    @Test
    fun `a state-flip persists the composite state row`() = runTest {
        startObserving()
        queue.value = listOf(item("a"))
        currentIndex.value = 0
        currentPositionMs.value = 12_345L
        runCurrent()

        isPlaying.value = true
        runCurrent()

        val states = mutableListOf<AudioQueueStateEntity>()
        coVerify(atLeast = 1) { dao.saveState(capture(states)) }
        val persisted = states.last()
        assertEquals(1, persisted.id, "the state table is a single fixed row")
        assertEquals(0, persisted.currentIndex)
        assertEquals(12_345L, persisted.currentPositionMs)
        assertEquals(true, persisted.isPlaying)
        assertEquals(false, persisted.shuffleEnabled)
        assertEquals(1.0f, persisted.playbackSpeed)
    }

    @Test
    fun `position ticks are throttled to one write per 15 second window`() = runTest {
        startObserving()

        // ~4 Hz progress updates: nothing may hit the disk inside the window.
        repeat(10) {
            currentPositionMs.value += 250L
            advanceTimeBy(250)
            runCurrent()
        }
        coVerify(exactly = 0) { dao.saveState(any()) }

        advanceTimeBy(15_000)
        runCurrent()

        val states = mutableListOf<AudioQueueStateEntity>()
        coVerify(atLeast = 1) { dao.saveState(capture(states)) }
        assertEquals(2_500L, states.last().currentPositionMs)
    }

    @Test
    fun `loadQueue restores rows with positions and blank artists`() = runTest {
        coEvery { dao.getQueue() } returns listOf(
            AudioQueueEntity(id = "a", position = 0, name = "A", artist = null, album = null),
            AudioQueueEntity(id = "b", position = 1, name = "B", artist = "X", durationMs = 5L),
        )

        val restored = helper.loadQueue()

        assertEquals(2, restored.size)
        assertEquals("a", restored[0].id)
        assertEquals("", restored[0].artist, "a null artist column reads back as blank, never null")
        assertEquals("X", restored[1].artist)
        assertEquals(5L, restored[1].durationMs)
    }

    @Test
    fun `loadState passes the persisted row through`() = runTest {
        val state = AudioQueueStateEntity(currentIndex = 3, isPlaying = true)
        coEvery { dao.getState() } returns state

        assertEquals(state, helper.loadState())
    }

    @Test
    fun `loadState tolerates a missing row`() = runTest {
        coEvery { dao.getState() } returns null

        assertNull(helper.loadState())
    }

    @Test
    fun `the helper never persists before any mutation happens`() = runTest {
        startObserving()
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(queue.value.isEmpty())
        coVerify(exactly = 0) { dao.replaceQueue(any()) }
        coVerify(exactly = 0) { dao.clearQueue() }
        coVerify(exactly = 0) { dao.saveState(any()) }
    }
}
