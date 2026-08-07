package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.testing.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryLayoutViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var editor: PreferencesEditor
    private lateinit var mediaRepository: MediaRepository

    @Before
    fun setUp() {
        homeDiscoveryStore = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        mediaRepository = mockk(relaxed = true)
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(HomeDiscoverySlice())
        coEvery { mediaRepository.getLibraryFolders() } returns Result.success(emptyList())
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
    fun `setLibrarySectionEnabled false adds type to overrides`() = runTest {
        val viewModel = LibraryLayoutViewModel(homeDiscoveryStore, editor, mediaRepository)

        viewModel.setLibrarySectionEnabled("movies", HomeSectionType.LATEST_MEDIA, enabled = false)

        verify {
            editor.setLibraryHomeSectionOverrides(match { it["movies"] == setOf(HomeSectionType.LATEST_MEDIA) })
        }
    }

    @Test
    fun `setLibrarySectionEnabled true removes type and drops empty key`() = runTest {
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(
            HomeDiscoverySlice(libraryHomeSectionOverrides = mapOf("movies" to setOf(HomeSectionType.LATEST_MEDIA)))
        )
        val viewModel = LibraryLayoutViewModel(homeDiscoveryStore, editor, mediaRepository)

        viewModel.setLibrarySectionEnabled("movies", HomeSectionType.LATEST_MEDIA, enabled = true)

        verify { editor.setLibraryHomeSectionOverrides(emptyMap()) }
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
