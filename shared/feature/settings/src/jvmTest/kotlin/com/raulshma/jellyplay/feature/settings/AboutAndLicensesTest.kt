package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.UpdateDismissPeriod
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Polls until [condition] holds, pumping the test scheduler between waits.
 * Needed because both ViewModels here hop to `Dispatchers.IO` for their
 * reads: the hop's resumption re-enters the test scheduler asynchronously,
 * so a bare `advanceUntilIdle` can drain the queue before the IO block
 * finishes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
private suspend fun TestScope.awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
    advanceUntilIdle()
    val deadline = System.currentTimeMillis() + timeoutMs
    while (!condition()) {
        assertTrue(System.currentTimeMillis() < deadline, "condition not met within ${timeoutMs}ms")
        withContext(Dispatchers.Default) { delay(10) }
        advanceUntilIdle()
    }
}

/**
 * Pins [AboutViewModel] (LibraryLayout jvmTest pattern: mockk collaborators +
 * real [MutableStateFlow]/Result stubs + inlined Main-dispatcher rule):
 * app info derives from the [AppMetaProvider] seam, server info joins
 * `authRepository.currentServer` with `adminRepository.getSystemInfo()`
 * (degrading, not throwing, on admin failure), the self-update toggles mirror
 * the experimental-store slice, and log collection clears its spinner even on
 * failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val experimentalState = MutableStateFlow(
        ExperimentalSlice(
            selfUpdateCheckEnabled = false,
            selfUpdateDownloadEnabled = true,
            updateDismissPeriod = UpdateDismissPeriod.NEVER,
        )
    )

    private lateinit var appMetaProvider: AppMetaProvider
    private lateinit var logCollector: LogCollector
    private lateinit var adminRepository: AdminRepository
    private lateinit var authRepository: AuthRepository
    private lateinit var experimentalStore: ExperimentalStore

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        appMetaProvider = mockk(relaxed = true)
        logCollector = mockk(relaxed = true)
        adminRepository = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        experimentalStore = mockk(relaxed = true)
        every { appMetaProvider.versionName } returns "2.3.4"
        every { appMetaProvider.isDebugBuild } returns true
        every { appMetaProvider.minSdk } returns 28
        every { appMetaProvider.targetSdk } returns 35
        every { experimentalStore.experimental } returns experimentalState
        every { authRepository.currentServer } returns MutableStateFlow(
            ServerInfo(id = "s1", name = "Media", address = "https://jelly.example")
        )
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = AboutViewModel(
        appMetaProvider, logCollector, adminRepository, authRepository, experimentalStore,
    )

    @Test
    fun `app info mirrors the platform seam`() {
        val viewModel = viewModel()

        assertEquals("2.3.4", viewModel.appVersion)
        assertEquals("Debug", viewModel.buildType)
        assertEquals("API 28", viewModel.minSdkInfo)
        assertEquals("API 35", viewModel.targetSdkInfo)
    }

    @Test
    fun `server info joins the auth and admin repositories`() = runTest {
        coEvery { adminRepository.getSystemInfo() } returns
            Result.success(SystemInfo(serverName = "Media Server", version = "10.10.0"))
        val viewModel = viewModel()

        awaitUntil { viewModel.serverName != null }

        assertEquals("Media Server", viewModel.serverName)
        assertEquals("10.10.0", viewModel.serverVersion)
        assertEquals("https://jelly.example", viewModel.serverAddress)
    }

    @Test
    fun `admin failure degrades the server info instead of throwing`() = runTest {
        coEvery { adminRepository.getSystemInfo() } returns
            Result.failure(RuntimeException("boom"))
        val viewModel = viewModel()

        awaitUntil { viewModel.serverAddress != null }

        assertNull(viewModel.serverName)
        assertNull(viewModel.serverVersion)
        assertEquals("https://jelly.example", viewModel.serverAddress)
    }

    @Test
    fun `self-update preferences mirror the experimental slice`() = runTest {
        val viewModel = viewModel()

        awaitUntil { !viewModel.selfUpdateCheckEnabled }

        assertFalse(viewModel.selfUpdateCheckEnabled)
        assertTrue(viewModel.selfUpdateDownloadEnabled)
        assertEquals(UpdateDismissPeriod.NEVER, viewModel.updateDismissPeriod)
    }

    @Test
    fun `self-update setters route to the experimental store`() = runTest {
        val viewModel = viewModel()

        viewModel.updateSelfUpdateCheckPref(true)
        viewModel.updateSelfUpdateDownloadPref(false)
        viewModel.updateDismissPeriodPref(UpdateDismissPeriod.WEEK_1)
        advanceUntilIdle()

        coVerify(exactly = 1) { experimentalStore.setSelfUpdateCheckEnabled(true) }
        coVerify(exactly = 1) { experimentalStore.setSelfUpdateDownloadEnabled(false) }
        coVerify(exactly = 1) { experimentalStore.setUpdateDismissPeriod(UpdateDismissPeriod.WEEK_1) }
    }

    @Test
    fun `sendAppLogs hands the collected file to the callback and clears the spinner`() = runTest {
        every { logCollector.collectLogs(any(), any(), any()) } returns "file:///logs.zip"
        val viewModel = viewModel()
        var result: String? = null
        var called = false

        viewModel.sendAppLogs { called = true; result = it }

        awaitUntil { called }
        assertEquals("file:///logs.zip", result)
        assertFalse(viewModel.isCollectingLogs)
    }

    @Test
    fun `sendAppLogs failure reports null instead of throwing`() = runTest {
        every { logCollector.collectLogs(any(), any(), any()) } throws RuntimeException("no logs")
        val viewModel = viewModel()
        var called = false
        var result: String? = "sentinel"

        viewModel.sendAppLogs { called = true; result = it }

        awaitUntil { called }
        assertNull(result)
        assertFalse(viewModel.isCollectingLogs)
    }
}

/**
 * Pins [LicensesViewModel] (same harness): a well-formed aboutlibraries
 * payload is parsed and sorted case-insensitively by name, and a malformed
 * payload degrades silently to an empty list (aboutlibraries' parser swallows
 * parse failures, so no error is surfaced) — either way the spinner settles.
 *
 * NOT covered here: the `read() == null` load-error path. It renders
 * `getString(Res.string.settings_licenses_load_error)`, and the Compose
 * resource bundle is not on the jvmTest runtime classpath (it is only packaged
 * into the jvmJar), so that branch cannot resolve its string in a unit test —
 * pinned by the platform screens instead.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LicensesViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()
    private lateinit var jsonSource: AboutLibrariesJsonSource

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        jsonSource = mockk(relaxed = true)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `parses libraries and sorts them case-insensitively by name`() = runTest {
        every { jsonSource.read() } returns """
            {"licenses":{},"libraries":[
                {"uniqueId":"com.zeta:zeta","name":"zeta lib"},
                {"uniqueId":"com.alpha:alpha","name":"Alpha lib"}
            ]}
        """.trimIndent()
        val viewModel = LicensesViewModel(jsonSource)

        awaitUntil { !viewModel.isLoading }

        assertEquals(listOf("Alpha lib", "zeta lib"), viewModel.libraries.map { it.name })
        assertNull(viewModel.error)
    }

    @Test
    fun `a malformed payload degrades to an empty list without an error`() = runTest {
        every { jsonSource.read() } returns "<not json>"
        val viewModel = LicensesViewModel(jsonSource)

        awaitUntil { !viewModel.isLoading }

        assertEquals(emptyList(), viewModel.libraries)
        assertNull(viewModel.error)
    }
}
