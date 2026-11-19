package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.data.repository.LibraryFeed
import com.raulshma.jellyplay.core.data.repository.PlaylistRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Rule

/**
 * The shared MainDispatcherRule fixture installs a StandardTestDispatcher on
 * Main.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryLayoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var editor: PreferencesEditor
    private lateinit var libraryFeed: LibraryFeed
    private lateinit var libraryApiClient: LibraryApiClient
    private lateinit var playlistRepository: PlaylistRepository

    @BeforeTest
    fun setUp() {
        homeDiscoveryStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        libraryFeed = mockk(relaxed = true)
        libraryApiClient = mockk(relaxed = true)
        playlistRepository = mockk(relaxed = true)
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(HomeDiscoverySlice())
        coEvery { libraryFeed.getLibraryFolders(force = false) } returns Result.success(emptyList())
    }

    @Test
    fun `loadLibraryFolders exposes non-music libraries`() = runTest {
        coEvery { libraryFeed.getLibraryFolders(force = false) } returns Result.success(
            listOf(
                LibraryFolder(id = "movies", name = "Movies", collectionType = "movies"),
                LibraryFolder(id = "music", name = "Music", collectionType = "music"),
            )
        )
        val viewModel = LibraryLayoutViewModel(homeDiscoveryStore, editor, libraryFeed, libraryApiClient, playlistRepository)
        advanceUntilIdle()

        val loaded = viewModel.libraryFolders.value

        assertEquals(listOf("movies"), loaded.map { it.id })
    }

    @Test
    fun `setLibrarySectionEnabled routes to the store section-prefs command`() = runTest {
        val viewModel = LibraryLayoutViewModel(homeDiscoveryStore, editor, libraryFeed, libraryApiClient, playlistRepository)

        viewModel.setLibrarySectionEnabled("movies", HomeSectionType.LATEST_MEDIA, enabled = false)
        advanceUntilIdle()

        // The toggle/override POLICY lives in the store command (pinned by
        // HomeDiscoveryStoreTest + core/model's HomeSectionPrefsTest); this
        // pins only the routing.
        coVerify {
            homeDiscoveryStore.setLibrarySectionVisible("movies", HomeSectionType.LATEST_MEDIA, false)
        }
    }

    @Test
    fun `exportCurrentLayoutJson snapshots libraryHomeSectionOverrides`() = runTest {
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(
            HomeDiscoverySlice(libraryHomeSectionOverrides = mapOf("x" to setOf(HomeSectionType.RECENTLY_ADDED)))
        )
        val viewModel = LibraryLayoutViewModel(homeDiscoveryStore, editor, libraryFeed, libraryApiClient, playlistRepository)

        val json = viewModel.exportCurrentLayoutJson()

        assertTrue(json.contains("libraryHomeSectionOverrides"))
        assertTrue(json.contains("RECENTLY_ADDED"))
    }
}
