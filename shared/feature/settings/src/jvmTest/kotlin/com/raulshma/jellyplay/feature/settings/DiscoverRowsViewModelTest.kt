package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.datastore.volume.VolumeProfileStore
import com.raulshma.jellyplay.core.model.DiscoverRowConfig
import com.raulshma.jellyplay.core.model.DiscoverRowSource
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.PlayedStatus
import com.raulshma.jellyplay.core.model.SeerrRowMedia
import com.raulshma.jellyplay.core.model.SeerrRowSort
import com.raulshma.jellyplay.core.model.SortOption
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the discover-row editor ViewModel: the draft lifecycle (startNew /
 * startEdit / closeEditor / saveDraft's blank-title guard), the
 * duplicate-with-"(2)" rule, the DEBOUNCED preview (a burst of updateRow
 * calls costs one fetch), the stale-result CONTENT guard (an older fetch
 * landing after a chip mutation must not apply), and the quick-start
 * templates' pre-filled defaults.
 *
 * The editor is a REAL [PreferencesEditor] over a mocked
 * [PreferencesEditScope] (StorageSettingsViewModelTest pattern — the only way
 * `edit { … }` blocks actually run, so store upserts are observable).
 * Main-dispatcher rule inlined (StandardTestDispatcher + setMain/resetMain)
 * and shared with runTest via `runTest(testDispatcher)` so one virtual clock
 * advances both the ViewModel jobs and the test body.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiscoverRowsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var homeDiscovery: HomeDiscoveryStore
    private lateinit var mediaRepository: MediaRepository
    private lateinit var editor: PreferencesEditor

    private val homeDiscoverySlice = MutableStateFlow(HomeDiscoverySlice())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        homeDiscovery = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        every { homeDiscovery.homeDiscovery } returns homeDiscoverySlice
        coEvery { mediaRepository.getLibraryFolders() } returns Result.success(emptyList())
        coEvery { mediaRepository.getGenres() } returns Result.success(emptyList())
        coEvery { mediaRepository.getStudios() } returns Result.success(emptyList())
        coEvery { mediaRepository.getTags(any(), any(), any()) } returns Result.success(emptyList())
        coEvery { mediaRepository.getDiscoverRowItems(any()) } returns Result.success(emptyList())
        editor = PreferencesEditor(
            scope = CoroutineScope(testDispatcher + Job()),
            editScope = PreferencesEditScope(
                playback = mockk<PlaybackStore>(relaxed = true),
                appearance = mockk<AppearanceStore>(relaxed = true),
                videoPlayer = mockk<VideoPlayerStore>(relaxed = true),
                downloads = mockk<DownloadsStore>(relaxed = true),
                engine = mockk<PlayerEngineStore>(relaxed = true),
                homeDiscovery = homeDiscovery,
                audio = mockk<AudioStore>(relaxed = true),
                audioEffects = mockk<AudioEffectsStore>(relaxed = true),
                audioCache = mockk<AudioCacheStore>(relaxed = true),
                library = mockk<LibraryStore>(relaxed = true),
                navigation = mockk<NavigationStore>(relaxed = true),
                networkOffline = mockk<NetworkOfflineStore>(relaxed = true),
                notification = mockk<NotificationStore>(relaxed = true),
                screensaver = mockk<ScreensaverStore>(relaxed = true),
                security = mockk<SecurityStore>(relaxed = true),
                subtitle = mockk<SubtitleLanguageStore>(relaxed = true),
                syncPlayCast = mockk<SyncPlayCastStore>(relaxed = true),
                experimental = mockk<ExperimentalStore>(relaxed = true),
                volumeProfile = mockk<VolumeProfileStore>(relaxed = true),
                appRuntimeState = mockk<AppRuntimeStateStore>(relaxed = true),
            ),
            store = mockk<UserPreferencesStore>(relaxed = true),
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): DiscoverRowsViewModel =
        DiscoverRowsViewModel(homeDiscovery, editor, mediaRepository)

    private fun item(name: String): MediaItem =
        MediaItem(id = name.lowercase().replace(' ', '-'), name = name, mediaType = MediaType.MOVIE)

    // ---------------------------------------------------------------- draft lifecycle

    @Test
    fun `startNew opens a blank draft with the editor defaults`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.startNew()

        val draft = vm.draft.value
        assertNotNull(draft)
        assertEquals("", draft.row.title)
        assertTrue(draft.row.id.isNotBlank(), "a new draft rolls a fresh row id")
        assertEquals(DiscoverRowConfig.DEFAULT_LIMIT, draft.row.limit)
        assertEquals(SortOption.RANDOM, draft.row.filters.sortBy)
        assertTrue(draft.previewLoading, "the preview flag latches on immediately (the fetch itself is debounced)")
    }

    @Test
    fun `startNew with a template seeds the draft from it`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        val template = DiscoverRowTemplates.highlyRated.create("Picks")
        vm.startNew(template)

        assertEquals(template, vm.draft.value?.row)
    }

    @Test
    fun `startEdit copies the persisted row into the draft`() = runTest(testDispatcher) {
        val row = DiscoverRowConfig(id = "row-1", title = "Weekend")
        homeDiscoverySlice.value = HomeDiscoverySlice(discoverRows = listOf(row))
        val vm = viewModel()
        advanceUntilIdle()

        vm.startEdit("row-1")

        assertEquals(row, vm.draft.value?.row)
    }

    @Test
    fun `startEdit with an unknown row id keeps the editor closed`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.startEdit("missing")

        assertNull(vm.draft.value)
    }

    @Test
    fun `closeEditor drops the draft`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.startNew()

        vm.closeEditor()

        assertNull(vm.draft.value)
    }

    // ---------------------------------------------------------------- save / duplicate

    @Test
    fun `saveDraft upserts the row through the store and closes the editor`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.startNew()
        vm.updateRow { it.copy(title = "Weekend Picks") }

        vm.saveDraft()
        advanceUntilIdle()

        assertNull(vm.draft.value, "saving closes the editor")
        coVerify(exactly = 1) { homeDiscovery.upsertDiscoverRow(match { it.title == "Weekend Picks" }) }
    }

    @Test
    fun `saveDraft refuses a blank title and keeps the editor open`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.startNew()
        vm.updateRow { it.copy(title = "   ") }

        vm.saveDraft()
        advanceUntilIdle()

        assertNotNull(vm.draft.value, "a blank title must not save NOR close the editor")
        coVerify(exactly = 0) { homeDiscovery.upsertDiscoverRow(any()) }
    }

    @Test
    fun `duplicateDraft saves a copy with a fresh id and the (2) suffix`() = runTest(testDispatcher) {
        homeDiscoverySlice.value = HomeDiscoverySlice(
            discoverRows = listOf(DiscoverRowConfig(id = "row-1", title = "Weekend Picks")),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.startEdit("row-1")

        vm.duplicateDraft()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            homeDiscovery.upsertDiscoverRow(match { it.id != "row-1" && it.title == "Weekend Picks (2)" })
        }
        assertEquals("row-1", vm.draft.value?.row?.id, "the editor stays open on the ORIGINAL row")
    }

    @Test
    fun `duplicateDraft refuses a blank title`() = runTest(testDispatcher) {
        homeDiscoverySlice.value = HomeDiscoverySlice(
            discoverRows = listOf(DiscoverRowConfig(id = "row-1", title = " ")),
        )
        val vm = viewModel()
        advanceUntilIdle()
        vm.startEdit("row-1")

        vm.duplicateDraft()
        advanceUntilIdle()

        coVerify(exactly = 0) { homeDiscovery.upsertDiscoverRow(any()) }
    }

    // ---------------------------------------------------------------- preview: debounce + stale guard

    @Test
    fun `a burst of draft updates costs one debounced preview fetch for the last state`() = runTest(testDispatcher) {
        coEvery { mediaRepository.getDiscoverRowItems(any()) } returns Result.success(listOf(item("Only Fetch")))
        val vm = viewModel()
        advanceUntilIdle()

        vm.startNew()
        repeat(5) { i -> vm.updateRow { it.copy(title = "burst $i") } }
        advanceUntilIdle()

        coVerify(exactly = 1) { mediaRepository.getDiscoverRowItems(any()) }
        coVerify { mediaRepository.getDiscoverRowItems(match { it.title == "burst 4" && it.limit == 12 }) }
        assertEquals(listOf("Only Fetch"), vm.draft.value?.previewItems?.map { it.name })
        assertFalse(vm.draft.value?.previewLoading == true)
    }

    @Test
    fun `updateRow drops the previous query's preview items while the debounced refetch is pending`() = runTest(testDispatcher) {
        coEvery { mediaRepository.getDiscoverRowItems(any()) } returns Result.success(listOf(item("Old")))
        val vm = viewModel()
        advanceUntilIdle()
        vm.startNew()
        advanceUntilIdle()
        assertEquals(listOf("Old"), vm.draft.value?.previewItems?.map { it.name })

        vm.updateRow { it.copy(title = "changed") }

        val pending = vm.draft.value
        assertNotNull(pending)
        assertEquals(emptyList(), pending.previewItems, "a draft mutation drops the previous query's items immediately")
        assertNull(pending.previewError)
    }

    @Test
    fun `a stale fetch landing after the replacement fetch does not apply to the mutated draft`() = runTest(testDispatcher) {
        // The older query parks inside the "repository"; a non-cooperative
        // implementation (runCatching-wrapped IO) lets its answer return
        // NORMALLY into the already-cancelled coroutine — the exact leak the
        // content guard exists for. The replacement query answers instantly.
        val staleAnswer = CompletableDeferred<Unit>()
        coEvery { mediaRepository.getDiscoverRowItems(match { it.filters.minRating == 6f }) } coAnswers {
            try {
                withContext(NonCancellable) { staleAnswer.await() }
            } catch (_: CancellationException) {
                // swallowed at the repository boundary
            }
            Result.success(listOf(item("Stale")))
        }
        coEvery { mediaRepository.getDiscoverRowItems(match { it.filters.minRating == 7f }) } returns
            Result.success(listOf(item("Fresh")))

        val vm = viewModel()
        advanceUntilIdle()
        vm.startNew()
        vm.updateRow { it.copy(filters = it.filters.withMinRating(6f)) }
        advanceUntilIdle() // the minRating=6 fetch is parked inside the repository

        vm.updateRow { it.copy(filters = it.filters.withMinRating(7f)) }
        advanceUntilIdle() // the fresh 7.0 fetch resolves and applies
        assertEquals(listOf("Fresh"), vm.draft.value?.previewItems?.map { it.name })

        staleAnswer.complete(Unit) // the older answer finally lands — AFTER its replacement
        advanceUntilIdle()

        val draft = vm.draft.value
        assertNotNull(draft)
        assertEquals(listOf("Fresh"), draft.previewItems.map { it.name }, "the stale 6.0 items must not land on the 7.0 draft")
        assertFalse(draft.previewLoading)
    }

    // ---------------------------------------------------------------- quick-start templates

    @Test
    fun `templates pre-fill their advertised filter mixes under the resolved title`() {
        val unwatched = DiscoverRowTemplates.unwatchedMovies.create("Unwatched Movies")
        assertEquals(listOf(MediaType.MOVIE), unwatched.filters.mediaTypes)
        assertEquals(SortOption.RANDOM, unwatched.filters.sortBy)

        val rated = DiscoverRowTemplates.highlyRated.create("Highly Rated Gems")
        assertEquals(7.5f, rated.filters.minRating)
        assertEquals(PlayedStatus.UNPLAYED, rated.filters.playedStatus)
        assertEquals(SortOption.RATING, rated.filters.sortBy)

        val fresh = DiscoverRowTemplates.newThisMonth.create("New This Month")
        assertEquals(SortOption.DATE_ADDED, fresh.filters.sortBy)
        assertEquals(30, fresh.addedWithinDays)

        val surprise = DiscoverRowTemplates.randomSurprise.create("Random Surprise")
        assertEquals(DiscoverRowConfig(id = surprise.id, title = "Random Surprise"), surprise)

        val trending = DiscoverRowTemplates.trendingSeerr.create("Trending on Seerr")
        assertEquals(DiscoverRowSource.SEERR, trending.source)
        assertEquals(SeerrRowMedia.MOVIE, trending.seerrFilters.media)
        assertEquals(SeerrRowSort.POPULARITY, trending.seerrFilters.sort)
    }

    @Test
    fun `every create call rolls a fresh id and honors the given title`() {
        val a = DiscoverRowTemplates.unwatchedMovies.create("T")
        val b = DiscoverRowTemplates.unwatchedMovies.create("T")

        assertTrue(a.id != b.id, "every use of a template adds a row with its own id")
        assertEquals("T", a.title)
    }

    @Test
    fun `template titles are five distinct localized resources`() {
        val titles = listOf(
            DiscoverRowTemplates.unwatchedMovies,
            DiscoverRowTemplates.highlyRated,
            DiscoverRowTemplates.newThisMonth,
            DiscoverRowTemplates.randomSurprise,
            DiscoverRowTemplates.trendingSeerr,
        ).map { it.title }

        assertEquals(5, titles.toSet().size, "UiText.Resource equality is per-resource — all five templates must differ")
    }
}
