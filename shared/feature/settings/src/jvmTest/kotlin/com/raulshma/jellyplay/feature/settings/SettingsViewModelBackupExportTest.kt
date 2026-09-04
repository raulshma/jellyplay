package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.PreferencesJson
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.search.SettingsRecentsStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tops up [SettingsViewModel] beyond [SettingsViewModelTest] (which pins the
 * staged-import classification and the v1 confirm): the EXPORT half of the
 * backup/restore pair (v2 envelope written to the [SettingsBackupIo] sink,
 * both failure degradations), the remaining confirm routes (v2 →
 * `restoreV2` with the security-sensitive gate forwarded verbatim, v0 bare
 * aggregate → the legacy restore path), the 30-second session auto-refresh
 * loop (virtual-time beat + explicit stop), the screensaver editor wiring,
 * and the clear/reset delegations.
 *
 * Stores/repositories are mockk'd with real [MutableStateFlow] stubs; the
 * main-dispatcher rule is inlined (StandardTestDispatcher + setMain/resetMain
 * — module jvmTest pattern).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelBackupExportTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsBackupIo: SettingsBackupIo
    private lateinit var preferencesStore: UserPreferencesStore
    private lateinit var projections: PreferenceProjections
    private lateinit var authRepository: AuthRepository
    private lateinit var seerrRepository: SeerrRepository
    private lateinit var adminRepository: AdminRepository
    private lateinit var editor: PreferencesEditor
    private lateinit var recentsStore: SettingsRecentsStore

    private val screenPrefs = MutableStateFlow(SettingsScreenPreferences())
    private val currentServer = MutableStateFlow<ServerInfo?>(null)
    private val currentUser = MutableStateFlow<UserInfo?>(null)
    private val currentServerUsers = MutableStateFlow<List<UserInfo>>(emptyList())
    private val pendingCount = MutableStateFlow(0)
    private val recents = MutableStateFlow<List<String>>(emptyList())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsBackupIo = mockk()
        preferencesStore = mockk(relaxed = true)
        projections = mockk(relaxed = true)
        authRepository = mockk(relaxed = true)
        seerrRepository = mockk(relaxed = true)
        adminRepository = mockk(relaxed = true)
        editor = mockk(relaxed = true)
        recentsStore = mockk(relaxed = true)

        every { projections.settingsScreenPreferences } returns screenPrefs
        every { authRepository.currentServer } returns currentServer
        every { authRepository.currentUser } returns currentUser
        every { authRepository.currentServerUsers } returns currentServerUsers
        every { seerrRepository.pendingRequestCount } returns pendingCount
        every { recentsStore.recents } returns recents
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Every VM created here; all cancelled in [vmTest]'s finally (see below). */
    private val createdViewModels = mutableListOf<SettingsViewModel>()

    /**
     * runTest wrapper that cancels every created VM in a `finally` INSIDE the
     * coroutine — before runTest's completion drain. The session auto-refresh
     * tests leave a live `while(true)` polling loop behind on a failed
     * assertion; a bare runTest drain then drives that loop's 30-second beat
     * for hundreds of thousands of virtual iterations (a near-permanent hang
     * that also OOMs the worker through mockk's per-invocation bookkeeping).
     */
    private fun vmTest(block: suspend TestScope.() -> Unit): Unit = runTest(testDispatcher) {
        try {
            block()
        } finally {
            createdViewModels.forEach { it.viewModelScope.cancel() }
            createdViewModels.clear()
        }
    }

    private fun viewModel(): SettingsViewModel {
        val vm = SettingsViewModel(
            settingsBackupIo = settingsBackupIo,
            preferencesStore = preferencesStore,
            projections = projections,
            authRepository = authRepository,
            seerrRepository = seerrRepository,
            adminRepository = adminRepository,
            editor = editor,
            recentsStore = recentsStore,
        )
        createdViewModels += vm
        return vm
    }

    private suspend fun TestScope.awaitUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            advanceUntilIdle()
            if (condition()) break
            assertTrue(
                System.currentTimeMillis() < deadline,
                "$description (timed out waiting for the VM's coroutine)",
            )
            withContext(Dispatchers.IO) { delay(10) }
        }
        advanceUntilIdle()
    }

    private fun adminUser() = UserInfo(
        id = "u1", name = "Admin", serverAddress = "http://x", accessToken = "t", isAdmin = true,
    )

    // ------------------------------------------------------------ export (v2)

    @Test
    fun `exportSettings writes the v2 envelope to the sink and reports success`() = vmTest {
        coEvery { preferencesStore.snapshotForBackup() } returns UserPreferencesStore.SettingsBackupSnapshot(
            slices = mapOf(BackupSliceKey.APPEARANCE to JsonPrimitive("stub-slice")),
            extras = AppRuntimeState(favoriteChannels = setOf("chan-1")),
        )
        val sink = ByteArrayOutputStream()
        coEvery { settingsBackupIo.openExportSink("backup:out") } returns sink
        val vm = viewModel()
        advanceUntilIdle()

        vm.exportSettings("backup:out")
        awaitUntil("the export completes") { vm.backupRestoreStatus != null }

        val decoded = PreferencesJson.import.decodeFromString(
            SettingsBackup.serializer(),
            sink.toString(),
        )
        assertEquals(SettingsBackup.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(JsonPrimitive("stub-slice"), decoded.slices[BackupSliceKey.APPEARANCE])
        assertEquals(setOf("chan-1"), decoded.extras.favoriteChannels)
        assertEquals("Settings exported successfully", vm.backupRestoreStatus)
    }

    @Test
    fun `exportSettings surfaces a failure when the sink cannot be opened`() = vmTest {
        coEvery { preferencesStore.snapshotForBackup() } returns UserPreferencesStore.SettingsBackupSnapshot(
            slices = emptyMap(),
            extras = AppRuntimeState(),
        )
        coEvery { settingsBackupIo.openExportSink("backup:dead") } returns null
        val vm = viewModel()
        advanceUntilIdle()

        vm.exportSettings("backup:dead")
        awaitUntil("the failure surfaces") { vm.backupRestoreStatus != null }

        assertTrue(
            "Export failed" in vm.backupRestoreStatus.orEmpty() && "Cannot open output stream" in vm.backupRestoreStatus.orEmpty(),
            "got: ${vm.backupRestoreStatus}",
        )
    }

    @Test
    fun `exportSettings surfaces a snapshot failure instead of crashing`() = vmTest {
        coEvery { preferencesStore.snapshotForBackup() } throws RuntimeException("store sealed")
        val vm = viewModel()
        advanceUntilIdle()

        vm.exportSettings("backup:any")
        awaitUntil("the failure surfaces") { vm.backupRestoreStatus != null }

        assertTrue("store sealed" in vm.backupRestoreStatus.orEmpty(), "got: ${vm.backupRestoreStatus}")
    }

    // ------------------------------------------------------- confirm: v2 / v0

    @Test
    fun `confirmImport on v2 fans to restoreV2 with the security gate forwarded`() = vmTest {
        val json = PreferencesJson.export.encodeToString(
            SettingsBackup.serializer(),
            SettingsBackup(slices = mapOf(BackupSliceKey.APPEARANCE to JsonPrimitive("stub-slice"))),
        )
        coEvery { settingsBackupIo.openImportSource("backup:v2") } answers {
            ByteArrayInputStream(json.toByteArray())
        }
        val vm = viewModel()
        advanceUntilIdle()
        vm.importSettings("backup:v2")
        awaitUntil("the import stages") { vm.pendingImport != null }

        vm.confirmImport(restoreSecuritySensitive = false)
        awaitUntil("the confirm completes") { vm.backupRestoreStatus != null }

        coVerify(exactly = 1) {
            preferencesStore.restoreV2(withArg { assertEquals(2, it.schemaVersion) }, false)
        }
        assertEquals("Settings imported successfully", vm.backupRestoreStatus)
        assertNull(vm.pendingImport)
    }

    @Test
    fun `confirmImport on v2 restores the device lock config only when opted in`() = vmTest {
        val json = PreferencesJson.export.encodeToString(
            SettingsBackup.serializer(),
            SettingsBackup(slices = emptyMap()),
        )
        coEvery { settingsBackupIo.openImportSource("backup:v2b") } answers {
            ByteArrayInputStream(json.toByteArray())
        }
        val vm = viewModel()
        advanceUntilIdle()
        vm.importSettings("backup:v2b")
        awaitUntil("the import stages") { vm.pendingImport != null }

        vm.confirmImport(restoreSecuritySensitive = true)
        awaitUntil("the confirm completes") { vm.backupRestoreStatus != null }

        coVerify(exactly = 1) { preferencesStore.restoreV2(any(), true) }
    }

    @Test
    fun `confirmImport on a bare v0 aggregate fans to the legacy restore path`() = vmTest {
        val json = PreferencesJson.export.encodeToString(
            UserPreferences.serializer(),
            UserPreferences(showAdvancedSettings = true),
        )
        coEvery { settingsBackupIo.openImportSource("backup:v0") } answers {
            ByteArrayInputStream(json.toByteArray())
        }
        val vm = viewModel()
        advanceUntilIdle()
        vm.importSettings("backup:v0")
        awaitUntil("the import stages") { vm.pendingImport != null }
        assertEquals(0, vm.pendingImport!!.schemaVersion)

        vm.confirmImport(restoreSecuritySensitive = false)
        awaitUntil("the confirm completes") { vm.backupRestoreStatus != null }

        coVerify(exactly = 1) {
            preferencesStore.restorePreferences(
                withArg { assertTrue(it.showAdvancedSettings) },
                false,
            )
        }
        coVerify(exactly = 0) { preferencesStore.restoreV2(any(), any()) }
    }

    // ------------------------------------------------- session auto-refresh

    @Test
    fun `session auto-refresh polls on the 30-second beat`() = vmTest {
        coEvery { adminRepository.getSessions() } returns Result.success(emptyList())
        val vm = viewModel()
        advanceUntilIdle()
        currentUser.value = adminUser()
        advanceUntilIdle()
        coVerify(exactly = 1) { adminRepository.getSessions() } // the init load

        // NOTE: no advanceUntilIdle from here on — the polling loop is a
        // self-rescheduling delay, which would spin the idle-advance forever.
        vm.startSessionAutoRefresh()
        advanceTimeBy(30_000)
        runCurrent()
        coVerify(exactly = 2) { adminRepository.getSessions() }

        advanceTimeBy(30_000)
        runCurrent()
        coVerify(exactly = 3) { adminRepository.getSessions() }
    }

    @Test
    fun `stopSessionAutoRefresh cancels the polling loop`() = vmTest {
        coEvery { adminRepository.getSessions() } returns Result.success(emptyList())
        val vm = viewModel()
        advanceUntilIdle()
        currentUser.value = adminUser()
        advanceUntilIdle()

        vm.startSessionAutoRefresh()
        vm.stopSessionAutoRefresh()
        advanceTimeBy(90_000)
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.getSessions() } // init load only — no beats
    }

    // ----------------------------------------------------- editor delegations

    @Test
    fun `dream setters route through the editor`() = vmTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setDreamShowTitle(true)
        vm.setDreamImageCategories(setOf(DreamImageCategory.MUSIC))
        vm.setDreamSlideshowIntervalMs(45_000L)
        vm.setDreamKenBurnsEnabled(true)
        vm.setDreamTransitionStyle(DreamTransitionStyle.CROSSFADE)

        verify(exactly = 5) { editor.edit(any()) }
    }

    @Test
    fun `clearAllPreferences delegates to the editor reset`() = vmTest {
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearAllPreferences()

        verify(exactly = 1) { editor.clearAllPreferences() }
    }

    @Test
    fun `clearBackupRestoreStatus resets the backup banner`() = vmTest {
        coEvery { preferencesStore.snapshotForBackup() } throws RuntimeException("boom")
        val vm = viewModel()
        advanceUntilIdle()
        vm.exportSettings("backup:any")
        awaitUntil("the failure surfaces") { vm.backupRestoreStatus != null }

        vm.clearBackupRestoreStatus()

        assertNull(vm.backupRestoreStatus)
    }
}
