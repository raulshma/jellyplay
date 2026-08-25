package com.raulshma.jellyplay.shell

import android.content.Intent
import com.raulshma.jellyplay.core.data.update.ApkInstallBuilder
import com.raulshma.jellyplay.core.data.update.AppUpdateRepository
import com.raulshma.jellyplay.core.data.update.PendingAppUpdate
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.AppUpdateInfo
import com.raulshma.jellyplay.update.UpdateState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
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
 * Pins the update half of the session→update ordering invariant: the
 * launch-time work wired to [SessionCoordinator.start]'s `onSessionRestored`
 * callback lands in [UpdateCoordinator.onSessionRestored], which must restore
 * an already-downloaded APK *without* hitting the network, only fall through
 * to a network check when nothing pending exists, and stay completely quiet
 * when the user turned auto-checks off or dismissed that exact version.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class UpdateCoordinatorTest {

    private val dispatcher = StandardTestDispatcher()

    private val appUpdateRepository = mockk<AppUpdateRepository>(relaxed = true)
    private val experimentalStore = mockk<ExperimentalStore>(relaxed = true)
    private val experimental = MutableStateFlow(ExperimentalSlice())

    private lateinit var coordinator: UpdateCoordinator

    private val pendingInfo = updateInfo(latestVersion = "1.2.3")
    private val pendingApk = File("updates/jellyplay-1.2.3.apk")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { experimentalStore.experimental } returns experimental
        coEvery { appUpdateRepository.getPendingUpdate() } returns null
        coEvery { appUpdateRepository.checkForUpdate() } returns
            Result.success(updateInfo(latestVersion = "1.0.0", isAvailable = false))

        coordinator = UpdateCoordinator(
            appUpdateRepository = appUpdateRepository,
            apkInstallBuilder = ApkInstallBuilder { Intent(Intent.ACTION_VIEW) },
            experimentalStore = experimentalStore,
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `pending APK is restored without hitting the network`() = runTest(dispatcher) {
        coEvery { appUpdateRepository.getPendingUpdate() } returns
            PendingAppUpdate(pendingInfo, pendingApk)

        coordinator.onSessionRestored()
        advanceUntilIdle()

        val state = coordinator.updateState.value
        assertTrue(state is UpdateState.Downloaded)
        assertEquals(pendingApk, (state as UpdateState.Downloaded).file)
        coVerify(exactly = 0) { appUpdateRepository.checkForUpdate() }
    }

    @Test
    fun `nothing pending falls through to a network check and stays idle when up to date`() = runTest(dispatcher) {
        coordinator.onSessionRestored()
        advanceUntilIdle()

        coVerify(exactly = 1) { appUpdateRepository.checkForUpdate() }
        assertEquals(UpdateState.Idle, coordinator.updateState.value)
    }

    @Test
    fun `auto-check disabled stays completely quiet`() = runTest(dispatcher) {
        experimental.value = ExperimentalSlice(selfUpdateCheckEnabled = false)

        coordinator.onSessionRestored()
        advanceUntilIdle()

        assertEquals(UpdateState.Idle, coordinator.updateState.value)
        coVerify(exactly = 0) { appUpdateRepository.getPendingUpdate() }
        coVerify(exactly = 0) { appUpdateRepository.checkForUpdate() }
    }

    @Test
    fun `recently dismissed pending version falls through to the network check`() = runTest(dispatcher) {
        coEvery { appUpdateRepository.getPendingUpdate() } returns
            PendingAppUpdate(pendingInfo, pendingApk)
        experimental.value = ExperimentalSlice(
            dismissedUpdateVersion = "1.2.3",
            dismissedUpdateAtMs = System.currentTimeMillis() - 1_000,
        )

        coordinator.onSessionRestored()
        advanceUntilIdle()

        // The on-disk APK is suppressed for the dismissed version, so the
        // launch-time path must ask the network instead of surfacing it.
        coVerify(exactly = 1) { appUpdateRepository.checkForUpdate() }
        assertEquals(UpdateState.Idle, coordinator.updateState.value)
    }

    @Test
    fun `cancelDownload cancels active download and restores update available state`() = runTest(dispatcher) {
        val info = updateInfo("1.2.3")
        coEvery { appUpdateRepository.downloadUpdate(info, any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }

        coordinator.startUpdateDownload(info)
        testScheduler.advanceTimeBy(100)

        assertTrue(coordinator.updateState.value is UpdateState.Downloading)

        coordinator.cancelDownload()
        advanceUntilIdle()

        val state = coordinator.updateState.value
        assertTrue(state is UpdateState.UpdateAvailable)
        assertEquals("1.2.3", (state as UpdateState.UpdateAvailable).info.latestVersion)
    }

    @Test
    fun `dismissUpdate cancels active download and transitions to Idle`() = runTest(dispatcher) {
        val info = updateInfo("1.2.3")
        coEvery { appUpdateRepository.downloadUpdate(info, any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }

        coordinator.startUpdateDownload(info)
        testScheduler.advanceTimeBy(100)

        assertTrue(coordinator.updateState.value is UpdateState.Downloading)

        coordinator.dismissUpdate()
        advanceUntilIdle()

        assertEquals(UpdateState.Idle, coordinator.updateState.value)
    }

    private fun updateInfo(latestVersion: String, isAvailable: Boolean = true) = AppUpdateInfo(
        latestVersion = latestVersion,
        htmlUrl = "https://github.com/raulshma/JellyPlay/releases/tag/v$latestVersion",
        releaseNotes = "",
        isUpdateAvailable = isAvailable,
        downloadAssetUrl = "https://github.com/raulshma/JellyPlay/releases/download/v$latestVersion/app.apk",
        downloadAssetName = "app.apk",
        releaseSize = 1L,
    )
}
