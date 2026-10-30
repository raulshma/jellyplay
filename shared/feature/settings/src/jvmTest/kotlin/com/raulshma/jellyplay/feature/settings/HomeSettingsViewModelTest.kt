package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.HomeLayoutRestoreFetch
import com.raulshma.jellyplay.core.data.repository.HomeLayoutSyncController
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.model.HomeLayoutConfig
import com.raulshma.jellyplay.core.model.HomeScreenPreferences
import io.mockk.coEvery
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Home settings screen's model (the module's mockk idiom, the
 * [JellyPlayYourWatchingViewModelTest] pattern): the manual upload/restore
 * verbs over the home-layout sync controller, and the pending-restore-offer
 * retention the confirmation dialog reads. The layout write POLICY lives in
 * the controller/store (pinned there) — this pins only the VM's routing and
 * the offer lifecycle.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeSettingsViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    private lateinit var projections: PreferenceProjections
    private lateinit var advancedSettings: AdvancedSettingsGate
    private lateinit var editor: PreferencesEditor
    private lateinit var homeLayoutSync: HomeLayoutSyncController

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        projections = mockk(relaxed = true)
        advancedSettings = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        homeLayoutSync = mockk(relaxed = true)
        every { projections.homeScreenPreferences } returns MutableStateFlow(HomeScreenPreferences())
        every { advancedSettings.showAdvancedSettings } returns MutableStateFlow(false)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = HomeSettingsViewModel(
        projections = projections,
        advancedSettings = advancedSettings,
        editor = editor,
        homeLayoutSync = homeLayoutSync,
    )

    @Test
    fun `no pending restore offer initially`() = runTest {
        assertNull(viewModel().pendingRestoreOffer)
    }

    @Test
    fun `fetchServerLayout retains an offer for the confirmation dialog`() = runTest {
        val offer = HomeLayoutRestoreFetch.Offer(
            config = HomeLayoutConfig(),
            savedAtEpochMillis = 1720000000000,
            schemaVersion = 1,
        )
        coEvery { homeLayoutSync.fetchServerLayout() } returns Result.success(offer)
        val viewModel = viewModel()

        var observed: Result<HomeLayoutRestoreFetch>? = null
        viewModel.fetchServerLayout { observed = it }
        advanceUntilIdle()

        assertEquals(offer, observed?.getOrNull())
        assertEquals(offer, viewModel.pendingRestoreOffer)
    }

    @Test
    fun `non-offer fetch outcomes leave no pending offer`() = runTest {
        // Empty (nothing stored) and Undecodable (corrupt blob) are answers,
        // not errors — but neither is restorable, so nothing is retained.
        coEvery { homeLayoutSync.fetchServerLayout() } returns Result.success(HomeLayoutRestoreFetch.Empty)
        val viewModel = viewModel()
        viewModel.fetchServerLayout { }
        advanceUntilIdle()
        assertNull(viewModel.pendingRestoreOffer)

        coEvery { homeLayoutSync.fetchServerLayout() } returns Result.failure(IllegalStateException("offline"))
        viewModel.fetchServerLayout { }
        advanceUntilIdle()
        assertNull(viewModel.pendingRestoreOffer)
    }

    @Test
    fun `clearPendingRestoreOffer drops the retained offer`() = runTest {
        val offer = HomeLayoutRestoreFetch.Offer(HomeLayoutConfig(), null, null)
        coEvery { homeLayoutSync.fetchServerLayout() } returns Result.success(offer)
        val viewModel = viewModel()
        viewModel.fetchServerLayout { }
        advanceUntilIdle()
        assertEquals(offer, viewModel.pendingRestoreOffer)

        viewModel.clearPendingRestoreOffer()

        assertNull(viewModel.pendingRestoreOffer)
    }

    @Test
    fun `uploadLayoutToServer reports the controller result`() = runTest {
        coEvery { homeLayoutSync.uploadNow() } returns Result.success(Unit)
        val viewModel = viewModel()

        var observed: Result<Unit>? = null
        viewModel.uploadLayoutToServer { observed = it }
        advanceUntilIdle()

        assertTrue(observed?.isSuccess == true)

        coEvery { homeLayoutSync.uploadNow() } returns Result.failure(IllegalStateException("offline"))
        viewModel.uploadLayoutToServer { observed = it }
        advanceUntilIdle()

        assertTrue(observed?.isFailure == true)
    }
}
