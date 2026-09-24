package com.raulshma.jellyplay.feature.syncplay

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.raulshma.jellyplay.core.data.repository.SyncPlayRepository
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.designsystem.theme.JellyPlayTheme
import com.raulshma.jellyplay.core.model.NetworkStatus
import com.raulshma.jellyplay.core.ui.components.LocalNetworkStatus
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith
import org.koin.compose.viewmodel.dsl.viewModel
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

@RunWith(AndroidJUnit4::class)
class SyncPlayScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // The screen's default `viewModel: SyncPlayViewModel = koinViewModel()` resolves
    // from the running Koin app (on device :app's Application starts it; the host
    // lane starts a minimal one here) backed by mockk doubles, mirroring the
    // jvmTest SyncPlayViewModelTest setup.
    private val syncPlayRepository = mockk<SyncPlayRepository>()
    private val syncPlaySession = mockk<SyncPlaySession>()
    private val syncPlayCastStore = mockk<SyncPlayCastStore>()

    @Before
    fun setUpKoin() {
        coEvery { syncPlayRepository.getSyncPlayGroups() } returns Result.success(emptyList())
        every { syncPlaySession.activeGroupId } returns null
        every { syncPlaySession.lastReconnectMs } returns 0L
        every { syncPlaySession.events } returns emptyFlow()
        every { syncPlayCastStore.syncPlayCast } returns MutableStateFlow(SyncPlayCastSlice())

        startKoin {
            modules(
                module {
                    viewModel { SyncPlayViewModel(syncPlayRepository, syncPlaySession, syncPlayCastStore) }
                },
            )
        }
    }

    @After
    fun tearDownKoin() {
        // stopKoin() resets the GlobalContext only when it is running (a failed
        // @Before leaves nothing to tear down).
        if (GlobalContext.getKoinApplicationOrNull() != null) stopKoin()
    }

    private fun setSyncPlayScreenContent() {
        composeTestRule.setContent {
            // LocalNetworkStatus is provided at the app level in production
            // (JellyPlayApp); the host lane provides it directly.
            JellyPlayTheme(dynamicColor = false) {
                CompositionLocalProvider(
                    LocalNetworkStatus provides MutableStateFlow(NetworkStatus.Online),
                ) {
                    SyncPlayScreen(onBack = {})
                }
            }
        }
    }

    @Test
    fun syncPlayScreen_showsEmptyState() {
        setSyncPlayScreenContent()

        composeTestRule.onNodeWithText("SyncPlay").assertIsDisplayed()
        composeTestRule.onNodeWithText("No active SyncPlay groups").assertIsDisplayed()
        composeTestRule.onNodeWithText("Create a group to watch together").assertIsDisplayed()
    }

    @Test
    fun syncPlayScreen_showsBackButton() {
        setSyncPlayScreenContent()

        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
    }

    @Test
    fun syncPlayScreen_showsCreateFab() {
        setSyncPlayScreenContent()

        composeTestRule.onNodeWithContentDescription("Create group").assertIsDisplayed()
    }

    @Test
    fun syncPlayScreen_showsCreateDialogOnClick() {
        setSyncPlayScreenContent()

        composeTestRule.onNodeWithContentDescription("Create group").performClick()
        composeTestRule.onNodeWithText("Create SyncPlay Group").assertIsDisplayed()
    }
}
