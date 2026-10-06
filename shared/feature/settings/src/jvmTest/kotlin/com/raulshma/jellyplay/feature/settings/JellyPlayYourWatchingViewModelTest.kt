package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.network.api.JellyPlayMyAnalytics
import com.raulshma.jellyplay.core.network.api.JellyPlayMyAnalyticsTotals
import com.raulshma.jellyplay.core.network.api.JellyPlayPluginApiClient
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
import kotlin.test.assertNull

/**
 * The "Your watching" screen's model (the mockk idiom, the
 * [JellyPlaySyncViewModelTest] pattern): one fetch per window chip, the
 * per-call analytics gate, and the quiet null degrade for the pre-wave 404 —
 * never an error state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class JellyPlayYourWatchingViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var pluginApi: JellyPlayPluginApiClient
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
    private fun viewModel(gateOpen: Boolean) = JellyPlayYourWatchingViewModel(
        pluginApiClient = pluginApi,
        statusStore = statusStore,
    ).also {
        // The probe-only fallback reads the WHOLE gate: AVAILABLE AND the
        // registry exposes the feature (JellyPlayFeatureGate's shared ladder).
        every { statusStore.status } returns MutableStateFlow(JellyPlayPluginStatus.AVAILABLE)
        every { statusStore.hasFeature(JellyPlayPluginFeatures.Analytics) } returns gateOpen
    }

    @Test
    fun selectWindow_fetchesThatWindow_days() = runTest {
        val payload = JellyPlayMyAnalytics(days = 90, totals = JellyPlayMyAnalyticsTotals(plays = 12))
        coEvery { pluginApi.getMyAnalytics(90) } returns Result.success(payload)

        val viewModel = viewModel(gateOpen = true)
        viewModel.selectWindow(JellyPlayYourWatchingWindow.Ninety)
        advanceUntilIdle()

        assertEquals(JellyPlayYourWatchingWindow.Ninety, viewModel.uiState.value.window)
        assertEquals(payload, viewModel.uiState.value.analytics)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 1) { pluginApi.getMyAnalytics(90) }
    }

    @Test
    fun notFound_degradesQuietlyToNull() = runTest {
        // The pre-wave 404: the route answers null (the api contract).
        coEvery { pluginApi.getMyAnalytics(any()) } returns Result.success(null)

        val viewModel = viewModel(gateOpen = true)
        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.analytics)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun degradedGate_neverTouchesTheApi() = runTest {
        val viewModel = viewModel(gateOpen = false)
        viewModel.refresh()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.analytics)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify(exactly = 0) { pluginApi.getMyAnalytics(any()) }
    }
}
