package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayUserDataRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlayUserRating
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The "My ratings" screen's model (the module's mockk idiom, the
 * [JellyPlayYourWatchingViewModelTest] pattern): the filter-tab query + state
 * shape over the [PluginTabLoader] dance (whose stale-guard/quiet/gate
 * invariants are pinned by [PluginTabLoaderTest]) — this pins the VM's own
 * half: the initial face, the select verb's state reset, and the gate arms.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlayUserRatingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var pluginApi: JellyPlayUserDataRoutes
    private lateinit var statusStore: JellyPlayPluginStatusStore

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        pluginApi = mockk(relaxed = true)
        statusStore = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** featureGate stays null: the probe-only fallback keeps the pre-toggle behavior. */
    private fun viewModel(gateOpen: Boolean) = JellyPlayUserRatingsViewModel(
        pluginApiClient = pluginApi,
        statusStore = statusStore,
    ).also {
        every { statusStore.status } returns MutableStateFlow(JellyPlayPluginStatus.AVAILABLE)
        every { statusStore.hasFeature(JellyPlayPluginFeatures.UserRatings) } returns gateOpen
    }

    @Test
    fun `initial state is the loading likes tab`() {
        val state = viewModel(gateOpen = true).uiState.value

        assertEquals(JellyPlayUserRatingsFilter.Likes, state.filter)
        assertEquals(emptyList(), state.ratings)
        assertTrue(state.isLoading)
    }

    @Test
    fun `selectFilter fetches the tab's wire filter and lands its rows`() = runTest {
        val rows = listOf(
            JellyPlayUserRating(itemId = "m1", name = "Movie One", likes = true),
            JellyPlayUserRating(itemId = "m2", name = "Movie Two", likes = true),
        )
        coEvery { pluginApi.getUserRatings("dislikes") } returns Result.success(rows)
        val viewModel = viewModel(gateOpen = true)

        viewModel.selectFilter(JellyPlayUserRatingsFilter.Dislikes)
        advanceUntilIdle()

        assertEquals(JellyPlayUserRatingsFilter.Dislikes, viewModel.uiState.value.filter)
        assertEquals(rows, viewModel.uiState.value.ratings)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 1) { pluginApi.getUserRatings("dislikes") }
    }

    @Test
    fun `same-filter reselect refetches through the loading face`() = runTest {
        coEvery { pluginApi.getUserRatings("likes") } returns Result.success(emptyList())
        val viewModel = viewModel(gateOpen = true)
        viewModel.selectFilter(JellyPlayUserRatingsFilter.Likes)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.selectFilter(JellyPlayUserRatingsFilter.Likes)

        // The select verb's own state reset — the tab never flashes stale rows.
        assertTrue(viewModel.uiState.value.isLoading)
        advanceUntilIdle()
        coVerify(exactly = 2) { pluginApi.getUserRatings("likes") }
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `failed fetch lands the quiet empty list`() = runTest {
        coEvery { pluginApi.getUserRatings(any()) } returns Result.failure(IllegalStateException("404"))
        val viewModel = viewModel(gateOpen = true)

        viewModel.selectFilter(JellyPlayUserRatingsFilter.Rated)
        advanceUntilIdle()

        assertEquals(JellyPlayUserRatingsFilter.Rated, viewModel.uiState.value.filter)
        assertEquals(emptyList(), viewModel.uiState.value.ratings)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `degraded gate never touches the api and lifts the loading face`() = runTest {
        val viewModel = viewModel(gateOpen = false)

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { pluginApi.getUserRatings(any()) }
    }
}
