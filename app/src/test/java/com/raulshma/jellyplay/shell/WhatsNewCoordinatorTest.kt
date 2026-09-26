package com.raulshma.jellyplay.shell

import com.raulshma.jellyplay.core.data.whatsnew.WhatsNewRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.model.WhatsNewCategory
import com.raulshma.jellyplay.core.model.WhatsNewEntry
import com.raulshma.jellyplay.core.model.WhatsNewRelease
import com.raulshma.jellyplay.whatsnew.WhatsNewState
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Pins the launch-time show-once behavior of [WhatsNewCoordinator]: upgrade
 * with cached content presents instantly (offline-first), a version the cache
 * doesn't know gets exactly one remote chance (a SUCCESSFUL fetch that still
 * lacks it stamps silently; a failed fetch leaves the stamp so the next
 * launch retries), fresh installs stamp without presenting, and dismiss
 * stamps so the same release never re-presents.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class WhatsNewCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()

    private val repository = mockk<WhatsNewRepository>(relaxed = true)
    private val experimentalStore = mockk<ExperimentalStore>(relaxed = true)
    private val appRuntimeStateStore = mockk<AppRuntimeStateStore>(relaxed = true)

    private var installedVersion = "0.11.2"
    private var seenVersion: String? = "0.11.0"
    private var onboardingCompleted = true
    private val feedReleases = MutableStateFlow(listOf(release("0.11.2"), release("0.11.0")))
    private val capturedSeenStamps = mutableListOf<String?>()

    private lateinit var coordinator: WhatsNewCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.releases } returns feedReleases
        coEvery { repository.releasesSnapshot() } answers { feedReleases.value }
        coEvery { repository.refresh() } returns Result.success(Unit)
        every { experimentalStore.whatsNewSeenVersion } returns MutableStateFlow(seenVersion)
        coEvery { experimentalStore.setWhatsNewSeenVersion(any()) } answers {
            capturedSeenStamps += firstArg<String?>()
        }
        coEvery { appRuntimeStateStore.isOnboardingCompleted() } answers { onboardingCompleted }

        coordinator = WhatsNewCoordinator(
            whatsNewRepository = repository,
            experimentalStore = experimentalStore,
            appRuntimeStateStore = appRuntimeStateStore,
            currentVersionName = { installedVersion },
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `upgrade with cached content presents without waiting on the network`() = runTest(dispatcher) {
        coordinator.onSessionRestored()
        advanceUntilIdle()

        val state = coordinator.state.value
        assertTrue("expected Show, got $state", state is WhatsNewState.Show)
        assertEquals("0.11.2", (state as WhatsNewState.Show).release.version)
        // Presented WITHOUT waiting on the network first (offline-first).
        coVerify(exactly = 1) { repository.refresh() }
    }

    @Test
    fun `a failed fetch never stamps so the next launch retries`() = runTest(dispatcher) {
        installedVersion = "0.11.3"
        coEvery { repository.refresh() } returns Result.failure(java.io.IOException("offline"))

        // First launch: offline, no cached content for 0.11.3 — nothing
        // shown, and crucially nothing stamped.
        coordinator.onSessionRestored()
        advanceUntilIdle()

        assertEquals(WhatsNewState.Idle, coordinator.state.value)
        assertTrue("no stamp expected: $capturedSeenStamps", capturedSeenStamps.isEmpty())

        // The next launch retries (the stamp was left alone).
        coordinator.onSessionRestored()
        advanceUntilIdle()

        coVerify(exactly = 2) { repository.refresh() }
        assertTrue(capturedSeenStamps.isEmpty())
    }

    @Test
    fun `unknown version gets one remote chance then stamps silently`() = runTest(dispatcher) {
        installedVersion = "0.11.3"
        // Remote fetch does not bring the version either.
        coEvery { repository.refresh() } answers { Result.success(Unit) }

        coordinator.onSessionRestored()
        advanceUntilIdle()

        assertEquals(WhatsNewState.Idle, coordinator.state.value)
        coVerify(exactly = 1) { repository.refresh() }
        assertEquals(listOf("0.11.3"), capturedSeenStamps)
    }

    @Test
    fun `unknown version found by the remote fetch presents`() = runTest(dispatcher) {
        installedVersion = "0.11.3"
        // The refresh brings the missing release into the flow.
        coEvery { repository.refresh() } answers {
            feedReleases.value = listOf(release("0.11.3"), release("0.11.2"))
            Result.success(Unit)
        }

        coordinator.onSessionRestored()
        advanceUntilIdle()

        val state = coordinator.state.value
        assertTrue(state is WhatsNewState.Show)
        assertEquals("0.11.3", (state as WhatsNewState.Show).release.version)
    }

    @Test
    fun `fresh install stamps silently and never presents`() = runTest(dispatcher) {
        seenVersion = null
        onboardingCompleted = false
        every { experimentalStore.whatsNewSeenVersion } returns MutableStateFlow(null)

        coordinator.onSessionRestored()
        advanceUntilIdle()

        assertEquals(WhatsNewState.Idle, coordinator.state.value)
        assertEquals(listOf("0.11.2"), capturedSeenStamps)
    }

    @Test
    fun `already seen version stays idle`() = runTest(dispatcher) {
        seenVersion = "0.11.2"
        every { experimentalStore.whatsNewSeenVersion } returns MutableStateFlow("0.11.2")

        coordinator.onSessionRestored()
        advanceUntilIdle()

        assertEquals(WhatsNewState.Idle, coordinator.state.value)
        assertTrue("no stamp expected: $capturedSeenStamps", capturedSeenStamps.isEmpty())
        // Steady-state launch makes no network call — the Settings archive
        // refreshes itself on open.
        coVerify(exactly = 0) { repository.refresh() }
    }

    @Test
    fun `dismiss stamps the shown version so it never re-presents`() = runTest(dispatcher) {
        coordinator.onSessionRestored()
        advanceUntilIdle()
        assertTrue(coordinator.state.value is WhatsNewState.Show)

        coordinator.dismiss()
        advanceUntilIdle()

        assertEquals(WhatsNewState.Idle, coordinator.state.value)
        assertEquals(listOf("0.11.2"), capturedSeenStamps)
    }

    @Test
    fun `dismiss while idle writes nothing`() = runTest(dispatcher) {
        coordinator.dismiss()
        advanceUntilIdle()

        assertTrue(capturedSeenStamps.isEmpty())
    }

    companion object {
        private fun release(version: String) = WhatsNewRelease(
            version = version,
            entries = listOf(
                WhatsNewEntry(id = "e", category = WhatsNewCategory.NEW, title = "T", summary = "S"),
            ),
        )
    }
}
