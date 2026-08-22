package com.raulshma.jellyplay.core.data.sync

import com.raulshma.jellyplay.core.data.offline.OfflineModeManager
import com.raulshma.jellyplay.core.data.repository.OfflineFirstItemResolver
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEntry
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxEventType
import com.raulshma.jellyplay.core.data.repository.PlaybackOutboxRepository
import com.raulshma.jellyplay.core.data.repository.ResolvedMediaRef
import com.raulshma.jellyplay.core.data.worker.PlaybackSyncScheduler
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.model.OfflineMode
import com.raulshma.jellyplay.core.model.PlayMethod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.BeforeTest
import kotlin.test.Test

/**
 * Direct [SyncStatusStateHolder] tests — the five behaviors migrated from
 * HomeViewModelTest (sync-now gating, the entries flow, per-id resolution
 * caching) plus direct pins for the drain gate that the VM tests previously
 * exercised only through the offline→online transition. Plain kotlin.test +
 * inline Main dispatcher (the legacy suite's MainDispatcherRule, inlined —
 * jvmTest has no access to :core:testing) + MockK; the holder runs on its own
 * scope over the test scheduler, mirroring the production viewModelScope
 * hand-off (HomeRefresherTest pattern).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SyncStatusStateHolderTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var playbackOutboxRepository: PlaybackOutboxRepository
    private lateinit var playbackSyncScheduler: PlaybackSyncScheduler
    private lateinit var offlineFirstItemResolver: OfflineFirstItemResolver
    private lateinit var offlineModeManager: OfflineModeManager
    private var holderScope: CoroutineScope? = null

    private val offlineModeFlow = MutableStateFlow(OfflineMode.ONLINE)
    private val networkStatusFlow = MutableStateFlow(NetworkStatus.Online)
    private val outboxCountFlow = MutableStateFlow(0)
    private val outboxEntriesFlow = MutableStateFlow<List<PlaybackOutboxEntry>>(emptyList())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        playbackOutboxRepository = mockk(relaxed = true)
        playbackSyncScheduler = mockk(relaxed = true)
        offlineFirstItemResolver = mockk(relaxed = true)
        offlineModeManager = mockk(relaxed = true)

        every { offlineModeManager.offlineMode } returns offlineModeFlow
        every { offlineModeManager.networkStatus } returns networkStatusFlow
        every { playbackOutboxRepository.countFlow() } returns outboxCountFlow
        every { playbackOutboxRepository.getAllFlow() } returns outboxEntriesFlow
        coEvery { offlineFirstItemResolver.resolveMediaRef(any()) } returns
            ResolvedMediaRef(item = null, posterUrl = "http://server/img")
    }

    @AfterTest
    fun stopHolder() {
        holderScope?.cancel()
        Dispatchers.resetMain()
    }

    private fun TestScope.buildHolder(): SyncStatusStateHolder {
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        holderScope = scope
        return SyncStatusStateHolder(
            scope = scope,
            playbackOutboxRepository = playbackOutboxRepository,
            playbackSyncScheduler = playbackSyncScheduler,
            offlineFirstItemResolver = offlineFirstItemResolver,
            offlineModeManager = offlineModeManager,
        )
    }

    @Test
    fun syncNow_whenOnline_enqueuesDrain() = runTest {
        val holder = buildHolder()

        holder.syncNow()
        // The drain worker must be enqueued exactly once; the worker itself
        // carries the NetworkType.CONNECTED constraint, but the holder gate
        // also short-circuits while offline.
        verify(exactly = 1) { playbackSyncScheduler.enqueueNow() }
    }

    @Test
    fun syncNow_whenOffline_skipsEnqueue() = runTest {
        offlineModeFlow.value = OfflineMode.OFFLINE_MANUAL
        val holder = buildHolder()

        holder.syncNow()

        verify(exactly = 0) { playbackSyncScheduler.enqueueNow() }
    }

    @Test
    fun pendingSyncEntries_emitsWhatRepositoryProduces() = runTest {
        val entry = PlaybackOutboxEntry(
            id = "e1",
            itemId = "item-1",
            eventType = PlaybackOutboxEventType.PROGRESS,
            sessionId = "s1",
            positionTicks = 10_000_000L,
            isPaused = false,
            playMethod = PlayMethod.DIRECT_PLAY,
            mediaSourceId = null,
            recordedAt = 1L,
            createdAt = 1L,
        )
        outboxEntriesFlow.value = listOf(entry)
        val holder = buildHolder()
        // pendingSyncEntries is stateIn(WhileSubscribed) — needs a live
        // subscriber to pull, then its .value reflects the upstream emission.
        val job = launch { holder.pendingSyncEntries.collect { } }
        runCurrent()

        assertEquals(listOf(entry), holder.pendingSyncEntries.value)
        job.cancel()
    }

    @Test
    fun ensurePendingItemDetails_mapsResolverResultIntoState() = runTest {
        // The offline-first fork itself is pinned by OfflineFirstItemResolverTest
        // in :core:data; here we only pin that the holder caches the resolver's
        // answer per outbox id.
        coEvery { offlineFirstItemResolver.resolveMediaRef("item-1") } returns ResolvedMediaRef(
            item = MediaItem(id = "item-1", name = "Offline Movie", mediaType = MediaType.MOVIE),
            posterUrl = "file:///offline/poster.jpg",
        )
        val holder = buildHolder()

        holder.ensurePendingItemDetails(listOf("item-1"))
        runCurrent()

        val resolved = holder.pendingItemDetails.value["item-1"]
        assertEquals("Offline Movie", resolved?.item?.name)
        assertEquals("file:///offline/poster.jpg", resolved?.posterUrl)
    }

    @Test
    fun ensurePendingItemDetails_prunesStaleKeys_andDedupesInFlight() = runTest {
        val holder = buildHolder()

        holder.ensurePendingItemDetails(listOf("a", "b"))
        runCurrent()
        assertEquals(setOf("a", "b"), holder.pendingItemDetails.value.keys)

        // Second call with overlapping ids must not re-launch resolves for
        // already-resolved keys (dedup), and ids dropped from the input are
        // pruned from the map.
        holder.ensurePendingItemDetails(listOf("b", "c"))
        runCurrent()

        assertEquals(setOf("b", "c"), holder.pendingItemDetails.value.keys)
        coVerify(exactly = 1) { offlineFirstItemResolver.resolveMediaRef("b") }
    }

    @Test
    fun ensurePendingItemDetails_resolverFailure_releasesDedupSlotAndRetries() = runTest {
        // The resolver absorbs domain failures itself; an exception here means
        // infrastructure trouble (e.g. Room). The holder must neither crash
        // its scope nor wedge the id as permanently in-flight.
        var attempts = 0
        coEvery { offlineFirstItemResolver.resolveMediaRef("item-1") } coAnswers {
            attempts++
            if (attempts == 1) throw RuntimeException("room broke")
            ResolvedMediaRef(item = null, posterUrl = "http://server/img")
        }
        val holder = buildHolder()

        holder.ensurePendingItemDetails(listOf("item-1"))
        runCurrent()
        assertTrue(holder.pendingItemDetails.value.isEmpty())

        // The second ensure re-launches the resolve only because the first
        // attempt released the dedup slot on its failure exit path.
        holder.ensurePendingItemDetails(listOf("item-1"))
        runCurrent()

        coVerify(exactly = 2) { offlineFirstItemResolver.resolveMediaRef("item-1") }
        assertEquals(1, holder.pendingItemDetails.value.size)
    }

    @Test
    fun awaitOutboxDrained_returnsImmediately_whenNothingPending() = runTest {
        coEvery { playbackOutboxRepository.count() } returns 0
        // countFlow() is invoked eagerly by the holder's constructor
        // (pendingSyncCount is stateIn(WhileSubscribed) — only collected while
        // a subscriber exists), so "never collected" is pinned with an
        // onSubscription counter rather than a MockK call count.
        var drainCollections = 0
        every { playbackOutboxRepository.countFlow() } returns
            outboxCountFlow.onSubscription { drainCollections++ }
        val holder = buildHolder()

        holder.awaitOutboxDrained()

        // Zero pending: the short-circuit returns without collecting the live count.
        assertEquals(0, drainCollections)
    }

    @Test
    fun awaitOutboxDrained_returnsWhenCountReachesZero() = runTest {
        coEvery { playbackOutboxRepository.count() } returns 2
        // The live count flow reports the drain immediately.
        every { playbackOutboxRepository.countFlow() } returns MutableStateFlow(2).apply { value = 0 }
        val holder = buildHolder()

        val start = testScheduler.currentTime
        holder.awaitOutboxDrained()

        // Drained without waiting out the 8s cap.
        assertEquals(0L, testScheduler.currentTime - start)
    }

    @Test
    fun awaitOutboxDrained_givesUpAfterTimeout() = runTest {
        coEvery { playbackOutboxRepository.count() } returns 2
        // Never drains: the live count stays > 0 and never completes, so only
        // the 8s cap can end the wait.
        every { playbackOutboxRepository.countFlow() } returns MutableStateFlow(2)
        val holder = buildHolder()

        val start = testScheduler.currentTime
        holder.awaitOutboxDrained()

        // Returned exactly at the cap, not immediately and not never.
        assertEquals(8_000L, testScheduler.currentTime - start)
    }
}
