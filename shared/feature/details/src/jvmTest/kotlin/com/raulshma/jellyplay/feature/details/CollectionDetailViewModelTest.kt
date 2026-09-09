package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.UserDataChange
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import org.junit.Assert.assertSame
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CollectionDetailViewModelTest {

    // Legacy :core:testing MainDispatcherRule, inlined (conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var userDataMutator: FakeUserDataMutator
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: CollectionDetailViewModel

    /** Driven by the deferred-refresh tests; collected by the VM for its lifetime. */
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        userDataMutator = FakeUserDataMutator()
        imageUrlProvider = mockk(relaxed = true)
        every { imageUrlProvider.getImageUrl(any()) } returns "img"
        every { imageUrlProvider.getBackdropUrl(any()) } returns "backdrop"

        viewModel = CollectionDetailViewModel(
            mediaRepository,
            userDataMutator,
            imageUrlProvider,
            mockk<com.raulshma.jellyplay.core.data.download.MediaDownloadActions>(relaxed = true),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadCollection success emits Success with detail and items`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val detail = MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION))
        val items = listOf(
            MediaItem(id = "m1", name = "Movie 1", mediaType = MediaType.MOVIE),
            MediaItem(id = "m2", name = "Movie 2", mediaType = MediaType.MOVIE),
        )
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(detail)
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns Result.success(
            SearchResult(items = items, totalRecordCount = 2, startIndex = 0),
        )

        viewModel.loadCollection("c1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CollectionDetailUiState.Success)
        val success = state as CollectionDetailUiState.Success
        assertEquals("c1", success.detail.item.id)
        assertEquals(items, success.items)
    }

    @Test
    fun `loadCollection detail failure emits Error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.failure(RuntimeException("boom"))
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )

        viewModel.loadCollection("c1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CollectionDetailUiState.Error)
        assertEquals("boom", (state as CollectionDetailUiState.Error).message)
    }

    @Test
    fun `loadCollection items failure surfaces error even if detail succeeded`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION)),
        )
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns Result.failure(RuntimeException("items boom"))

        viewModel.loadCollection("c1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is CollectionDetailUiState.Error)
        assertEquals("items boom", (state as CollectionDetailUiState.Error).message)
    }

    @Test
    fun `loadCollection resets to Loading before resolving`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        // No stubs → relaxed mock returns success(null) which getOrThrow would fail;
        // stub a slow-ish path so we can observe the initial Loading emission.
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION)),
        )
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )

        viewModel.loadCollection("c1")

        assertEquals(CollectionDetailUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `loadCollection on an already-successful collection is a no-op`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        stubCollectionFetch()
        viewModel.loadCollection("c1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CollectionDetailUiState.Success)

        // Back-stack re-entry re-runs the screen's LaunchedEffect; a second
        // loud load must not refetch (or flash Loading) on top of the deferred
        // refresh's silent regeneration.
        viewModel.loadCollection("c1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("c1") }
        coVerify(exactly = 1) { mediaRepository.getCollectionItems("c1", any(), any(), any()) }
        assertTrue(viewModel.uiState.value is CollectionDetailUiState.Success)
    }

    @Test
    fun `getImageUrl and getBackdropUrl delegate to ImageUrlProvider`() {
        viewModel.getImageUrl("x")
        viewModel.getBackdropUrl("y")

        io.mockk.verify(exactly = 1) { imageUrlProvider.getImageUrl("x") }
        io.mockk.verify(exactly = 1) { imageUrlProvider.getBackdropUrl("y") }
    }

    @Test
    fun `initial state is Loading`() {
        assertEquals(CollectionDetailUiState.Loading, viewModel.uiState.value)
    }

    /**
     * The one container-adapter test (plan 03): a successful optimistic
     * mutation flips ONLY the matching item — resume zeroed by the resolved
     * patch — and leaves non-matching items referentially equal.
     */
    @Test
    fun `markItemPlayed flips only the matching item in the success items`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val withProgress = MediaItem(
            id = "m1",
            name = "Movie 1",
            mediaType = MediaType.MOVIE,
            playbackPositionTicks = 5_000_000_000L,
        )
        val untouched = MediaItem(id = "m2", name = "Movie 2", mediaType = MediaType.MOVIE)
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION)),
        )
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns Result.success(
            SearchResult(items = listOf(withProgress, untouched), totalRecordCount = 2, startIndex = 0),
        )
        viewModel.loadCollection("c1")
        advanceUntilIdle()

        viewModel.markItemPlayed(withProgress, played = true)
        advanceUntilIdle()

        assertEquals(listOf(Triple("m1", true, null as String?)), userDataMutator.playedCalls)
        val items = (viewModel.uiState.value as CollectionDetailUiState.Success).items
        assertTrue(items.first { it.id == "m1" }.isPlayed)
        assertEquals(0L, items.first { it.id == "m1" }.playbackPositionTicks)
        assertSame(untouched, items.last())
    }

    @Test
    fun `failure with null message falls back to generic error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.failure(RuntimeException())
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )

        viewModel.loadCollection("c1")
        advanceUntilIdle()

        val state = viewModel.uiState.value as CollectionDetailUiState.Error
        assertEquals("Failed to load collection", state.message)
    }

    // ── Deferred refresh (user-data changes while off-screen) ───────────────
    //
    // These build a LOCAL ViewModel after stubbing `userDataChanges`: the
    // suite's setUp-built VM starts its refresher collector before this test
    // can stub the flow, and a relaxed-mock Flow's collect completes
    // immediately — killing the collector before the first emit.

    @Test
    fun `userData change while inactive defers a silent reload to the next entry`() = runTest {
        every { mediaRepository.userDataChanges } returns userDataEvents
        stubCollectionFetch()
        val viewModel = collectionViewModel()

        viewModel.loadCollection("c1")
        advanceUntilIdle()
        coVerify(exactly = 1) { mediaRepository.getCollectionItems("c1", any(), any(), any()) }

        // A write confirmed while the screen is NOT on screen only marks stale.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("m1")))
        advanceUntilIdle()
        coVerify(exactly = 1) { mediaRepository.getCollectionItems("c1", any(), any(), any()) }

        // Re-entry fires the single deferred reload — silently: Success is
        // never dropped back to Loading on the way.
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        coVerify(exactly = 2) { mediaRepository.getCollectionItems("c1", any(), any(), any()) }
        assertTrue(viewModel.uiState.value is CollectionDetailUiState.Success)
    }

    @Test
    fun `a silent reload failure keeps the last success instead of flashing error`() = runTest {
        every { mediaRepository.userDataChanges } returns userDataEvents
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION))
        ) andThen Result.failure(RuntimeException("offline blip"))
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )
        val viewModel = collectionViewModel()

        viewModel.loadCollection("c1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CollectionDetailUiState.Success)

        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("m1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // The silent refresh DID run (second fetch) and its detail read
        // failed — yet serve-stale-while-revalidate keeps the last Success
        // instead of flashing an Error screen over the old content.
        coVerify(exactly = 2) { mediaRepository.getCollectionItems("c1", any(), any(), any()) }
        assertTrue(viewModel.uiState.value is CollectionDetailUiState.Success)
    }

    @Test
    fun `deferred refresh does not stack a silent twin on an in-flight loud load`() = runTest {
        every { mediaRepository.userDataChanges } returns userDataEvents
        // The first loud load fails (Error state → re-entry loud-loads again);
        // every later detail read parks on [gate] so a load can be held
        // IN FLIGHT across the next step.
        val gate = CompletableDeferred<Unit>()
        var detailCalls = 0
        coEvery { mediaRepository.getMediaDetail("c1") } coAnswers {
            if (++detailCalls == 1) {
                Result.failure(RuntimeException("offline"))
            } else {
                gate.await()
                Result.success(MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION)))
            }
        }
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )
        val viewModel = collectionViewModel()

        viewModel.loadCollection("c1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CollectionDetailUiState.Error)

        // Armed while off-screen...
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("m1")))
        advanceUntilIdle()

        // ...then re-entry: the loud load starts (Error does not no-op) and
        // parks mid-fetch; the deferred effect consumes the flag while that
        // load is in flight and must NOT launch a silent twin on top of it —
        // the loud load is already the regeneration.
        viewModel.loadCollection("c1")
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // Two fetches total (initial + in-flight loud), not three.
        coVerify(exactly = 2) { mediaRepository.getCollectionItems("c1", any(), any(), any()) }
    }

    @Test
    fun `a silent reload skipped by an in-flight loud load re-arms for the next re-entry`() = runTest {
        every { mediaRepository.userDataChanges } returns userDataEvents
        // The first loud load fails (Error state → re-entry loud-loads again);
        // every later detail read parks on [gate] so a load can be held
        // IN FLIGHT across the next step.
        val gate = CompletableDeferred<Unit>()
        var detailCalls = 0
        coEvery { mediaRepository.getMediaDetail("c1") } coAnswers {
            if (++detailCalls == 1) {
                Result.failure(RuntimeException("offline"))
            } else {
                gate.await()
                Result.success(MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION)))
            }
        }
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )
        val viewModel = collectionViewModel()

        viewModel.loadCollection("c1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is CollectionDetailUiState.Error)

        // Armed while off-screen; the parked loud load dispatched BEFORE the
        // change landed, so when the deferred effect consumes the flag and
        // skips itself, the consumed flag must survive the skip.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("m1")))
        advanceUntilIdle()
        viewModel.loadCollection("c1")
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        coVerify(exactly = 2) { mediaRepository.getCollectionItems("c1", any(), any(), any()) }

        gate.complete(Unit)
        advanceUntilIdle()

        // The next re-entry fires the silent reload the skip promised.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        coVerify(exactly = 3) { mediaRepository.getCollectionItems("c1", any(), any(), any()) }
        assertTrue(viewModel.uiState.value is CollectionDetailUiState.Success)
    }

    @Test
    fun `deferred silent reload forces the collection items read`() = runTest {
        every { mediaRepository.userDataChanges } returns userDataEvents
        stubCollectionFetch()
        val viewModel = collectionViewModel()

        viewModel.loadCollection("c1")
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("m1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // The loud load goes through the cache; the silent regeneration must
        // bypass it — a member flip never evicts the collection's page key,
        // so a non-forced reload would re-serve the pre-flip badges.
        coVerify(exactly = 1) { mediaRepository.getCollectionItems("c1", any(), any(), force = false) }
        coVerify(exactly = 1) { mediaRepository.getCollectionItems("c1", any(), any(), force = true) }
    }

    private fun stubCollectionFetch() {
        coEvery { mediaRepository.getMediaDetail("c1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "c1", name = "Collection", mediaType = MediaType.COLLECTION))
        )
        coEvery { mediaRepository.getCollectionItems("c1", any(), any(), any()) } returns Result.success(
            SearchResult(items = emptyList(), totalRecordCount = 0, startIndex = 0),
        )
    }

    private fun collectionViewModel() = CollectionDetailViewModel(
        mediaRepository,
        userDataMutator,
        imageUrlProvider,
        mockk<com.raulshma.jellyplay.core.data.download.MediaDownloadActions>(relaxed = true),
    )
}
