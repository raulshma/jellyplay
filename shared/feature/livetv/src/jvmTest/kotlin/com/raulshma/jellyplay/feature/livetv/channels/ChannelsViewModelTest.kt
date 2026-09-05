package com.raulshma.jellyplay.feature.livetv.channels

import com.raulshma.jellyplay.core.data.playback.VideoMiniPlayerState
import com.raulshma.jellyplay.core.data.repository.LiveTvRepository
import com.raulshma.jellyplay.core.data.util.ImageUrlProvider
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.LiveTvChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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

@OptIn(ExperimentalCoroutinesApi::class)
class ChannelsViewModelTest {

    // The legacy suite's MainDispatcherRule (:core:testing), inlined — jvmTest
    // has no access to that module (search/music conveyor port pattern).
    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var mediaRepository: LiveTvRepository
    private lateinit var imageUrlProvider: ImageUrlProvider

    /** Backing flow behind the mocked store's `state` — the fake runtime store. */
    private lateinit var runtimeState: MutableStateFlow<AppRuntimeState>

    /** Backing flow behind the mocked mini-player's `itemId`. */
    private lateinit var miniPlayerItemId: MutableStateFlow<String?>

    private lateinit var appRuntimeStateStore: AppRuntimeStateStore
    private lateinit var videoMiniPlayerState: VideoMiniPlayerState

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        mediaRepository = mockk()
        imageUrlProvider = mockk()

        // AppRuntimeStateStore is a final DataStore-backed class, so the fake
        // is a mock whose `state` reads our MutableStateFlow and whose
        // setFavoriteChannels writes it back — mimicking the real round-trip.
        runtimeState = MutableStateFlow(AppRuntimeState())
        appRuntimeStateStore = mockk()
        every { appRuntimeStateStore.state } returns runtimeState
        coEvery { appRuntimeStateStore.setFavoriteChannels(any()) } coAnswers {
            runtimeState.value = runtimeState.value.copy(favoriteChannels = firstArg())
        }

        // VideoMiniPlayerState's itemId is only mutable through the playback
        // engine path, so the test drives it through a mocked holder backed by
        // the same MutableStateFlow the VM's init collector observes.
        miniPlayerItemId = MutableStateFlow(null)
        videoMiniPlayerState = mockk()
        every { videoMiniPlayerState.itemId } returns miniPlayerItemId

        every { imageUrlProvider.getImageUrl(any()) } returns "http://img/chan"

        coEvery { mediaRepository.getLiveTvChannels(any(), any(), any(), any(), any()) } returns
            Result.success(sampleChannels())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun sampleChannels() = listOf(
        LiveTvChannel(id = "chan-a", name = "Alpha"),
        LiveTvChannel(id = "chan-b", name = "Bravo"),
        LiveTvChannel(id = "chan-c", name = "Charlie"),
        LiveTvChannel(id = "chan-d", name = "Delta"),
    )

    private fun seedFavorites(favorites: Set<String>) {
        runtimeState.value = AppRuntimeState(favoriteChannels = favorites)
    }

    private fun newViewModel() = ChannelsViewModel(
        mediaRepository = mediaRepository,
        imageUrlProvider = imageUrlProvider,
        appRuntimeStateStore = appRuntimeStateStore,
        videoMiniPlayerState = videoMiniPlayerState,
    )

    // ── loadChannels: favorites-first ordering ────────────────────────────

    @Test
    fun loadChannels_sorts_favorites_first_keeping_their_relative_order() = runTest(mainDispatcher) {
        // Server order a,b,c,d; favorites {a, c} → a and c lead, in server
        // order (sortedByDescending is stable), then the rest.
        seedFavorites(setOf("chan-c", "chan-a"))
        val viewModel = newViewModel()
        advanceUntilIdle()

        val channels = viewModel.uiState.value.channels
        assertEquals(
            listOf("chan-a", "chan-c", "chan-b", "chan-d"),
            channels.map { it.id },
        )
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun loadChannels_without_favorites_keeps_the_server_order() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf("chan-a", "chan-b", "chan-c", "chan-d"),
            viewModel.uiState.value.channels.map { it.id },
        )
    }

    @Test
    fun loadChannels_failure_sets_the_error_and_clears_loading() = runTest(mainDispatcher) {
        coEvery { mediaRepository.getLiveTvChannels(any(), any(), any(), any(), any()) } returns
            Result.failure(RuntimeException("guide offline"))
        val viewModel = newViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("guide offline", state.error)
        assertFalse(state.isLoading)
        assertTrue(state.channels.isEmpty())
    }

    // ── toggleFavorite: add/remove + resort ───────────────────────────────

    @Test
    fun toggleFavorite_adds_the_channel_resorts_and_persists() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.toggleFavorite("chan-c")
        advanceUntilIdle()

        coVerify { appRuntimeStateStore.setFavoriteChannels(setOf("chan-c")) }
        assertEquals(setOf("chan-c"), runtimeState.value.favoriteChannels)
        assertEquals(
            listOf("chan-c", "chan-a", "chan-b", "chan-d"),
            viewModel.uiState.value.channels.map { it.id },
        )
    }

    @Test
    fun toggleFavorite_removing_the_last_favorite_persists_and_keeps_the_current_order() = runTest(mainDispatcher) {
        seedFavorites(setOf("chan-d"))
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(
            listOf("chan-d", "chan-a", "chan-b", "chan-c"),
            viewModel.uiState.value.channels.map { it.id },
        )

        viewModel.toggleFavorite("chan-d")
        advanceUntilIdle()

        coVerify { appRuntimeStateStore.setFavoriteChannels(emptySet()) }
        assertEquals(emptySet(), runtimeState.value.favoriteChannels)
        // The VM only re-sorts while favorites remain non-empty — with none
        // left it keeps the current ordering (it never remembers the server
        // order), so the just-unfavorited channel still leads.
        assertEquals(
            listOf("chan-d", "chan-a", "chan-b", "chan-c"),
            viewModel.uiState.value.channels.map { it.id },
        )
    }

    // ── now-playing mirror of the mini player ─────────────────────────────

    @Test
    fun nowPlayingChannelId_mirrors_the_mini_player_item_id_flow() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()
        assertEquals(null, viewModel.nowPlayingChannelId.value)

        miniPlayerItemId.value = "chan-9"
        advanceUntilIdle()
        assertEquals("chan-9", viewModel.nowPlayingChannelId.value)

        miniPlayerItemId.value = null
        advanceUntilIdle()
        assertEquals(null, viewModel.nowPlayingChannelId.value)
    }

    @Test
    fun setNowPlayingChannelId_overrides_the_mirror_directly() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.setNowPlayingChannelId("chan-3")
        assertEquals("chan-3", viewModel.nowPlayingChannelId.value)

        viewModel.setNowPlayingChannelId(null)
        assertEquals(null, viewModel.nowPlayingChannelId.value)
    }

    // ── favoriteChannelIds projection ─────────────────────────────────────

    @Test
    fun favoriteChannelIds_projects_the_runtime_state_favorite_set() = runTest(mainDispatcher) {
        val viewModel = newViewModel()
        val observed = mutableListOf<Set<String>>()
        val collector: Job = launch { viewModel.favoriteChannelIds.collect { observed += it } }
        advanceUntilIdle()
        assertEquals(setOf<String>(), observed.first())

        runtimeState.value = AppRuntimeState(favoriteChannels = setOf("chan-b"))
        advanceUntilIdle()
        assertEquals(setOf("chan-b"), viewModel.favoriteChannelIds.value)
        assertEquals(setOf<String>(), observed.first())
        assertEquals(setOf("chan-b"), observed.last())

        collector.cancel()
    }

    // ── getImageUrl tag quirk ─────────────────────────────────────────────

    @Test
    fun getImageUrl_without_a_tag_returns_empty_without_touching_the_provider() {
        val viewModel = newViewModel()

        assertEquals("", viewModel.getImageUrl("chan-a", null))
        verify(exactly = 0) { imageUrlProvider.getImageUrl(any()) }
    }

    /**
     * Pins the (quirky) contract: a non-null tag selects the provider path but
     * the tag value itself is dropped on the floor — the provider only ever
     * sees the item id.
     */
    @Test
    fun getImageUrl_with_a_tag_delegates_to_the_provider_ignoring_the_tag() {
        val viewModel = newViewModel()

        assertEquals("http://img/chan", viewModel.getImageUrl("chan-a", "tag-xyz"))
        verify(exactly = 1) { imageUrlProvider.getImageUrl("chan-a") }
    }
}
