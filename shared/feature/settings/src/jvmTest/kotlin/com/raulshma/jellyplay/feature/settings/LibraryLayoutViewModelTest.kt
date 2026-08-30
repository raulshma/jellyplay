package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
import kotlin.test.assertTrue

/**
 * Ported from the legacy Android unit test: the :core:testing
 * MainDispatcherRule is inlined (StandardTestDispatcher + setMain/resetMain —
 * downloads-conveyor jvmTest pattern) because jvmTest has no access to that
 * module.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryLayoutViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var editor: PreferencesEditor
    private lateinit var mediaRepository: MediaRepository

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        homeDiscoveryStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(HomeDiscoverySlice())
        coEvery { mediaRepository.getLibraryFolders() } returns Result.success(emptyList())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loadLibraryFolders exposes non-music libraries`() = runTest {
        coEvery { mediaRepository.getLibraryFolders() } returns Result.success(
            listOf(
                LibraryFolder(id = "movies", name = "Movies", collectionType = "movies"),
                LibraryFolder(id = "music", name = "Music", collectionType = "music"),
            )
        )
        val viewModel = LibraryLayoutViewModel(homeDiscoveryStore, editor, mediaRepository)
        advanceUntilIdle()

        val loaded = viewModel.libraryFolders.value

        assertEquals(listOf("movies"), loaded.map { it.id })
    }

    @Test
    fun `setLibrarySectionEnabled routes to the store section-prefs command`() = runTest {
        val viewModel = LibraryLayoutViewModel(homeDiscoveryStore, editor, mediaRepository)

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
        val viewModel = LibraryLayoutViewModel(homeDiscoveryStore, editor, mediaRepository)

        val json = viewModel.exportCurrentLayoutJson()

        assertTrue(json.contains("libraryHomeSectionOverrides"))
        assertTrue(json.contains("RECENTLY_ADDED"))
    }
}
