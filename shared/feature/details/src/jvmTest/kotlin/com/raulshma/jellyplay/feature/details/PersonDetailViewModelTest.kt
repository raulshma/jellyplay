package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PersonInfo
import com.raulshma.jellyplay.core.model.PersonRef
import com.raulshma.jellyplay.core.model.UserDataChange
import com.raulshma.jellyplay.core.testfixtures.FakeUserDataMutator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import org.junit.Assert.assertSame
import kotlin.test.assertTrue
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonDetailViewModelTest {

    // Legacy :core:testing MainDispatcherRule, inlined (conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: MediaRepository
    private lateinit var libraryApiClient: LibraryApiClient
    private lateinit var userDataMutator: FakeUserDataMutator
    private lateinit var imageUrlProvider: ImageUrlProvider
    private lateinit var viewModel: PersonDetailViewModel

    /** Driven by the deferred-refresh tests; collected by the VM for its lifetime. */
    private val userDataEvents = MutableSharedFlow<UserDataChange>(extraBufferCapacity = 16)

    /** Builds a VM over fresh preference stubs — the D18/D20 gate seam. */
    private fun buildViewModel(
        detailPreferences: DetailPreferences = DetailPreferences(),
    ): PersonDetailViewModel {
        val projections = mockk<PreferenceProjections>(relaxed = true)
        every { projections.detailPreferences } returns MutableStateFlow(detailPreferences)
        return PersonDetailViewModel(
            mediaRepository,
            libraryApiClient,
            userDataMutator,
            imageUrlProvider,
            mockk<com.raulshma.jellyplay.core.data.download.MediaDownloadActions>(relaxed = true),
            projections,
        )
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk(relaxed = true)
        libraryApiClient = mockk(relaxed = true)
        userDataMutator = FakeUserDataMutator()
        imageUrlProvider = mockk(relaxed = true)
        // The D20 leg runs behind a relaxed mock otherwise (mockk cannot shape
        // a Result return) — every test here starts co-star-silent.
        coEvery { libraryApiClient.getPeople(any(), any(), any()) } returns Result.success(emptyList())
        viewModel = buildViewModel()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Loading`() {
        assertEquals(PersonDetailUiState.Loading, viewModel.uiState.value)
    }

    /**
     * The one container-adapter test (plan 03): a successful optimistic
     * mutation flips ONLY the matching filmography item — resume zeroed by the
     * resolved patch — and leaves non-matching items referentially equal.
     */
    @Test
    fun `markItemPlayed flips only the matching item in the filmography`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN, overview = "An actor.")
        val withProgress = MediaItem(
            id = "m1",
            name = "Movie 1",
            mediaType = MediaType.MOVIE,
            playbackPositionTicks = 5_000_000_000L,
        )
        val untouched = MediaItem(id = "m2", name = "Movie 2", mediaType = MediaType.MOVIE)
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { libraryApiClient.getItemsByPerson("p1", any()) } returns Result.success(listOf(withProgress, untouched))

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        viewModel.markItemPlayed(withProgress, played = true)
        advanceUntilIdle()

        assertEquals(listOf(Triple("m1", true, null as String?)), userDataMutator.playedCalls)
        val filmography = (viewModel.uiState.value as PersonDetailUiState.Success).filmography
        assertTrue(filmography.first { it.id == "m1" }.isPlayed)
        assertEquals(0L, filmography.first { it.id == "m1" }.playbackPositionTicks)
        assertSame(untouched, filmography.last())
    }

    @Test
    fun `loadPerson success emits Success with name filmography biography and profile image`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN, overview = "An actor.")
        val filmography = listOf(
            MediaItem(id = "m1", name = "Movie 1", mediaType = MediaType.MOVIE),
        )
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(filmography)
        every { imageUrlProvider.getImageUrl("p1") } returns "profile-url"

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PersonDetailUiState.Success)
        val success = state as PersonDetailUiState.Success
        assertEquals("Person One", success.name)
        assertEquals(filmography, success.filmography)
        assertEquals("An actor.", success.biography)
        assertEquals("profile-url", success.profileImageUrl)
    }

    @Test
    fun `loadPerson null biography is not emitted`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN, overview = "")
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(emptyList())

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        val success = viewModel.uiState.value as PersonDetailUiState.Success
        assertNull(success.biography)
    }

    // ── D18: role credits off the filmography's projected People ────────────

    @Test
    fun `filmography People resolve the person's role credits into the state`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN)
        val acting = PersonInfo(id = "p1", name = "Person One", role = "John Smith", type = "Actor")
        val credited = MediaItem(
            id = "m1",
            name = "Movie 1",
            mediaType = MediaType.MOVIE,
            people = listOf(PersonInfo(id = "someone-else", name = "Other", type = "Actor"), acting),
        )
        val uncredited = MediaItem(id = "m2", name = "Movie 2", mediaType = MediaType.MOVIE)
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(listOf(credited, uncredited))

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        val success = viewModel.uiState.value as PersonDetailUiState.Success
        assertEquals(acting, success.filmographyCredits["m1"])
        assertFalse(success.filmographyCredits.containsKey("m2"))
    }

    // ── D20: co-star enrichment leg ─────────────────────────────────────────

    @Test
    fun `co-stars anchor on the most-recent filmography entry and exclude the person`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN)
        val older = MediaItem(id = "m1", name = "Old Movie", mediaType = MediaType.MOVIE, year = 2000)
        val newest = MediaItem(id = "m2", name = "New Movie", mediaType = MediaType.MOVIE, year = 2020)
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(listOf(older, newest))
        coEvery { libraryApiClient.getPeople(any(), any(), any()) } returns Result.success(
            listOf(
                PersonRef(id = "p1", name = "Person One"),
                PersonRef(id = "c1", name = "Co Star One"),
                PersonRef(id = "c2", name = "Co Star Two"),
            )
        )

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        // The appearsIn query runs against the newest title, not the first listed.
        coVerify(exactly = 1) { libraryApiClient.getPeople(null, any(), "m2") }
        val success = viewModel.uiState.value as PersonDetailUiState.Success
        assertEquals(
            listOf(PersonRef(id = "c1", name = "Co Star One"), PersonRef(id = "c2", name = "Co Star Two")),
            success.costars,
        )
    }

    @Test
    fun `showFrequentCostars off skips the co-star query`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN)
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(
            listOf(MediaItem(id = "m1", name = "Movie 1", mediaType = MediaType.MOVIE, year = 2020))
        )
        val viewModel = buildViewModel(detailPreferences = DetailPreferences(showFrequentCostars = false))

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        coVerify(exactly = 0) { libraryApiClient.getPeople(any(), any(), any()) }
        assertTrue((viewModel.uiState.value as PersonDetailUiState.Success).costars.isEmpty())
    }

    @Test
    fun `empty filmography skips the co-star query`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN)
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(emptyList())

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        // No anchor entry exists, so the enrichment never runs — the gate the
        // row-hidden contract needs.
        coVerify(exactly = 0) { libraryApiClient.getPeople(any(), any(), any()) }
        assertTrue((viewModel.uiState.value as PersonDetailUiState.Success).costars.isEmpty())
    }

    @Test
    fun `a failed co-star query keeps the Success state with no costars`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN)
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(
            listOf(MediaItem(id = "m1", name = "Movie 1", mediaType = MediaType.MOVIE, year = 2020))
        )
        coEvery { libraryApiClient.getPeople(any(), any(), any()) } returns Result.failure(
            RuntimeException("persons boom")
        )

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        // Failure-tolerant enrichment: the loud content survives, the row hides.
        val success = viewModel.uiState.value as PersonDetailUiState.Success
        assertTrue(success.costars.isEmpty())
        assertEquals("Person One", success.name)
    }

    @Test
    fun `loadPerson blank profile url is not emitted`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        val person = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN, overview = "bio")
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(MediaDetail(item = person))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(emptyList())
        every { imageUrlProvider.getImageUrl("p1") } returns ""

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        val success = viewModel.uiState.value as PersonDetailUiState.Success
        assertNull(success.profileImageUrl)
    }

    @Test
    fun `loadPerson detail failure emits Error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.failure(RuntimeException("detail boom"))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(emptyList())

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is PersonDetailUiState.Error)
        assertEquals("detail boom", (state as PersonDetailUiState.Error).message)
        // Retry is owned by the data layer; this layer must not re-issue calls.
        io.mockk.coVerify(exactly = 1) { mediaRepository.getMediaDetail("p1") }
        io.mockk.coVerify(exactly = 1) { libraryApiClient.getItemsByPerson("p1") }
    }

    @Test
    fun `loadPerson items failure emits Error preferring items message`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.failure(RuntimeException("detail boom"))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.failure(RuntimeException("items boom"))

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        // itemsError is preferred (first in the ?: chain) when both fail.
        assertEquals("items boom", (viewModel.uiState.value as PersonDetailUiState.Error).message)
    }

    @Test
    fun `loadPerson failure with null messages falls back to generic error`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.failure(RuntimeException())
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.failure(RuntimeException())

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        assertEquals("Failed to load", (viewModel.uiState.value as PersonDetailUiState.Error).message)
    }

    @Test
    fun `a thrown repo failure on the loud load surfaces Error instead of stranding Loading`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("p1") } throws IllegalStateException("engine blew up")
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(emptyList())

        viewModel.loadPerson("p1")
        advanceUntilIdle()

        // The coordinator swallows the throw (re-arm + no uncaught handler);
        // without the error hook this screen would sit on Loading forever.
        val state = viewModel.uiState.value
        assertTrue(state is PersonDetailUiState.Error)
        assertEquals("engine blew up", (state as PersonDetailUiState.Error).message)
    }

    @Test
    fun `loadPerson on an already-successful person is a no-op`() = runTest(mainDispatcher) {
        backgroundScope.launch { viewModel.uiState.collect { /* warm */ } }
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN))
        )
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(emptyList())

        viewModel.loadPerson("p1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PersonDetailUiState.Success)

        // Back-stack re-entry re-runs the screen's LaunchedEffect; a second
        // loud load must not refetch (or flash Loading) on top of the deferred
        // refresh's silent regeneration.
        viewModel.loadPerson("p1")
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getMediaDetail("p1") }
        coVerify(exactly = 1) { libraryApiClient.getItemsByPerson("p1") }
        assertTrue(viewModel.uiState.value is PersonDetailUiState.Success)
    }

    @Test
    fun `getImageUrl delegates to ImageUrlProvider`() {
        viewModel.getImageUrl("p1")
        io.mockk.verify(exactly = 1) { imageUrlProvider.getImageUrl("p1") }
    }

    // ── Deferred refresh (user-data changes while off-screen) ───────────────
    //
    // Builds a LOCAL ViewModel after stubbing `userDataChanges`: the suite's
    // setUp-built VM starts its refresher collector before this test can stub
    // the flow, and a relaxed-mock Flow's collect completes immediately —
    // killing the collector before the first emit.

    @Test
    fun `userData change while inactive defers a silent reload to the next entry`() = runTest {
        every { mediaRepository.userDataChanges } returns userDataEvents
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN))
        )
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(emptyList())
        val viewModel = buildViewModel()

        viewModel.loadPerson("p1")
        advanceUntilIdle()
        coVerify(exactly = 1) { libraryApiClient.getItemsByPerson("p1") }

        // A write confirmed while the screen is NOT on screen only marks stale.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("m1")))
        advanceUntilIdle()
        coVerify(exactly = 1) { libraryApiClient.getItemsByPerson("p1") }

        // Re-entry fires the single deferred reload — silently: Success is
        // never dropped back to Loading on the way.
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        coVerify(exactly = 2) { libraryApiClient.getItemsByPerson("p1") }
        assertTrue(viewModel.uiState.value is PersonDetailUiState.Success)
    }

    // The no-stack/skip-re-arm choreography this suite used to re-pin
    // (in-flight loud loads holding a gated repo read) is module behaviour
    // now — DeferredFetchCoordinatorTest owns that table. What stays here is
    // the host surface: the guard's failure half, expressed through
    // loadPerson's public state.

    @Test
    fun `loadPerson after an Error reloads instead of no-oping`() = runTest {
        every { mediaRepository.userDataChanges } returns userDataEvents
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.failure(RuntimeException("offline"))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(emptyList())
        val viewModel = buildViewModel()

        viewModel.loadPerson("p1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PersonDetailUiState.Error)

        // Back-stack re-entry re-runs the screen's LaunchedEffect: a failed
        // loud load re-arms the guard, so re-entry reloads and heals.
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN))
        )
        viewModel.loadPerson("p1")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value is PersonDetailUiState.Success)
        coVerify(exactly = 2) { mediaRepository.getMediaDetail("p1") }
    }

    @Test
    fun `a silent reload failure keeps the last success and re-arms for the next re-entry`() = runTest {
        every { mediaRepository.userDataChanges } returns userDataEvents
        coEvery { mediaRepository.getMediaDetail("p1") } returns Result.success(
            MediaDetail(item = MediaItem(id = "p1", name = "Person One", mediaType = MediaType.UNKNOWN))
        ) andThen Result.failure(RuntimeException("offline blip"))
        coEvery { libraryApiClient.getItemsByPerson("p1") } returns Result.success(emptyList())
        val viewModel = buildViewModel()

        viewModel.loadPerson("p1")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value is PersonDetailUiState.Success)

        viewModel.deferredRefresher.onScreenActiveChanged(false)
        userDataEvents.emit(UserDataChange("user-1", listOf("m1")))
        advanceUntilIdle()
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()

        // The silent reload DID run (second fetch) and its detail read
        // failed — serve-stale-while-revalidate keeps the last Success.
        coVerify(exactly = 2) { libraryApiClient.getItemsByPerson("p1") }
        assertTrue(viewModel.uiState.value is PersonDetailUiState.Success)

        // The failed silent fetch re-arms the deferred refresh: the next
        // re-entry retries instead of trusting the consumed flag.
        viewModel.deferredRefresher.onScreenActiveChanged(false)
        viewModel.deferredRefresher.onScreenActiveChanged(true)
        advanceUntilIdle()
        coVerify(exactly = 3) { libraryApiClient.getItemsByPerson("p1") }
        assertTrue(viewModel.uiState.value is PersonDetailUiState.Success)
    }
}
