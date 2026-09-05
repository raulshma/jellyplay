package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.LegacySettingsBackup
import com.raulshma.jellyplay.core.datastore.PreferencesJson
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.search.SettingsRecentsStore
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SessionNowPlayingItem
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream
import java.time.Instant
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
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pins the settings-root ViewModel's non-Composable surface: the cache-size
 * computation off the [SettingsBackupIo] seam, the staged
 * [SettingsViewModel.PendingImport] classification for the three backup
 * shapes (v2/v1/v0) with security-sensitive detection, the confirm path that
 * fans to the right restore call, the destructive guard rails
 * (no pending import → nothing restored), and the recent-settings tracking.
 *
 * Stores/repositories are mockk'd with real [MutableStateFlow] stubs for the
 * init-block collectors. Main-dispatcher rule inlined
 * (StandardTestDispatcher + setMain/resetMain — module jvmTest pattern).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

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

    private fun viewModel(): SettingsViewModel = SettingsViewModel(
        settingsBackupIo = settingsBackupIo,
        preferencesStore = preferencesStore,
        projections = projections,
        authRepository = authRepository,
        seerrRepository = seerrRepository,
        adminRepository = adminRepository,
        appearanceStore = mockk(relaxed = true),
        editor = editor,
        recentsStore = recentsStore,
    )

    /** Fresh stream per call — the VM re-reads the source on confirm. */
    private fun stubImportSource(uri: String, json: String) {
        coEvery { settingsBackupIo.openImportSource(uri) } answers {
            ByteArrayInputStream(json.toByteArray())
        }
    }

    /** Drains the scheduler until [condition] holds (pumps across real-IO hops). */
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

    // ------------------------------------------------------------ cache size

    @Test
    fun `refreshCacheSize converts the seam's byte estimate to whole mebibytes`() = runTest(testDispatcher) {
        // 5 MiB + 123 partial bytes must truncate, not round.
        coEvery { settingsBackupIo.estimateCacheSizeBytes() } returns 5L * 1024 * 1024 + 123
        val vm = viewModel()
        advanceUntilIdle()

        vm.refreshCacheSize()
        advanceUntilIdle()

        assertEquals(5L, vm.cacheSizeMb)
        assertNull(vm.cacheError)
    }

    // ------------------------------------------------------------ preferences collection

    @Test
    fun `projected screen preferences flow into the exposed state live`() = runTest(testDispatcher) {
        screenPrefs.value = SettingsScreenPreferences(showAdvancedSettings = true)
        val vm = viewModel()
        advanceUntilIdle()

        assertTrue(vm.preferences.showAdvancedSettings)

        screenPrefs.value = SettingsScreenPreferences(showAdvancedSettings = false)
        advanceUntilIdle()

        assertEquals(false, vm.preferences.showAdvancedSettings, "the collector must stay live")
    }

    // ------------------------------------------------------------ staged import

    private fun <T> sliceElement(serializer: KSerializer<T>, value: T): JsonElement =
        PreferencesJson.import.parseToJsonElement(
            PreferencesJson.import.encodeToString(serializer, value),
        )

    @Test
    fun `v2 backup stages a current-version pending import`() = runTest(testDispatcher) {
        val json = PreferencesJson.export.encodeToString(
            SettingsBackup.serializer(),
            SettingsBackup(slices = emptyMap()),
        )
        stubImportSource("backup:v2", json)
        val vm = viewModel()
        advanceUntilIdle()

        vm.importSettings("backup:v2")
        awaitUntil("the import stages") { vm.pendingImport != null }

        val pending = vm.pendingImport
        assertTrue(pending != null, "nothing is written until the user confirms — import must stage")
        assertEquals("backup:v2", pending!!.uri)
        assertEquals(2, pending.schemaVersion)
        assertEquals(false, pending.isLegacy)
        assertEquals(false, pending.versionMismatch)
        assertEquals(false, pending.hasSecuritySensitive)
        assertNull(vm.backupRestoreStatus)
    }

    @Test
    fun `v2 backup with lock config flags security-sensitive`() = runTest(testDispatcher) {
        val json = PreferencesJson.export.encodeToString(
            SettingsBackup.serializer(),
            SettingsBackup(
                slices = mapOf(
                    BackupSliceKey.SECURITY to sliceElement(
                        SecuritySlice.serializer(),
                        SecuritySlice(pinHash = "stored-hash"),
                    ),
                ),
            ),
        )
        stubImportSource("backup:v2sec", json)
        val vm = viewModel()
        advanceUntilIdle()

        vm.importSettings("backup:v2sec")
        awaitUntil("the import stages") { vm.pendingImport != null }

        assertEquals(true, vm.pendingImport!!.hasSecuritySensitive)
    }

    @Test
    fun `v1 envelope stages a legacy pending import`() = runTest(testDispatcher) {
        val json = PreferencesJson.export.encodeToString(
            LegacySettingsBackup.serializer(),
            LegacySettingsBackup(preferences = UserPreferences(pinLockEnabled = true)),
        )
        stubImportSource("backup:v1", json)
        val vm = viewModel()
        advanceUntilIdle()

        vm.importSettings("backup:v1")
        awaitUntil("the import stages") { vm.pendingImport != null }

        val pending = vm.pendingImport!!
        assertEquals(1, pending.schemaVersion)
        assertTrue(pending.isLegacy)
        assertTrue(pending.versionMismatch)
        assertTrue(pending.hasSecuritySensitive, "v0/v1 detection reads the aggregate's lock fields")
    }

    @Test
    fun `bare v0 aggregate stages with schema zero`() = runTest(testDispatcher) {
        val json = PreferencesJson.export.encodeToString(
            UserPreferences.serializer(),
            UserPreferences(showAdvancedSettings = true),
        )
        stubImportSource("backup:v0", json)
        val vm = viewModel()
        advanceUntilIdle()

        vm.importSettings("backup:v0")
        awaitUntil("the import stages") { vm.pendingImport != null }

        val pending = vm.pendingImport!!
        assertEquals(0, pending.schemaVersion)
        assertTrue(pending.isLegacy)
        assertTrue(pending.versionMismatch)
        assertFalse(pending.hasSecuritySensitive)
    }

    @Test
    fun `unopenable import source surfaces the failure and stages nothing`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.importSettings("backup:missing")
        awaitUntil("the failure surfaces") { vm.backupRestoreStatus != null }

        assertNull(vm.pendingImport)
        assertTrue("Import failed" in vm.backupRestoreStatus.orEmpty())
    }

    @Test
    fun `confirmImport on v1 fans to the legacy restore path`() = runTest(testDispatcher) {
        val json = PreferencesJson.export.encodeToString(
            LegacySettingsBackup.serializer(),
            LegacySettingsBackup(preferences = UserPreferences(themeMode = ThemeMode.DARK)),
        )
        stubImportSource("backup:v1", json)
        val vm = viewModel()
        advanceUntilIdle()
        vm.importSettings("backup:v1")
        awaitUntil("the import stages") { vm.pendingImport != null }

        vm.confirmImport(restoreSecuritySensitive = false)
        awaitUntil("the confirm completes") { vm.backupRestoreStatus != null }

        coVerify(exactly = 1) {
            preferencesStore.restorePreferences(
                withArg { assertEquals(ThemeMode.DARK, it.themeMode) },
                false,
            )
        }
        assertNull(vm.pendingImport, "a confirmed import clears the staged state")
        assertEquals("Settings imported successfully", vm.backupRestoreStatus)
    }

    @Test
    fun `confirmImport without a staged import touches nothing`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.confirmImport(restoreSecuritySensitive = true)
        advanceUntilIdle()

        coVerify(exactly = 0) { settingsBackupIo.openImportSource(any()) }
        coVerify(exactly = 0) { preferencesStore.restorePreferences(any(), any()) }
        coVerify(exactly = 0) { preferencesStore.restoreV2(any(), any()) }
    }

    @Test
    fun `failed confirm clears the staged import and reports the error`() = runTest(testDispatcher) {
        val json = PreferencesJson.export.encodeToString(
            LegacySettingsBackup.serializer(),
            LegacySettingsBackup(preferences = UserPreferences()),
        )
        stubImportSource("backup:v1", json)
        coEvery { preferencesStore.restorePreferences(any(), any()) } throws RuntimeException("disk full")
        val vm = viewModel()
        advanceUntilIdle()
        vm.importSettings("backup:v1")
        awaitUntil("the import stages") { vm.pendingImport != null }

        vm.confirmImport(restoreSecuritySensitive = false)
        awaitUntil("the failure surfaces") { vm.backupRestoreStatus != null }

        assertNull(vm.pendingImport, "a failed confirm must not leave the import staged")
        assertTrue("disk full" in vm.backupRestoreStatus.orEmpty())
    }

    @Test
    fun `cancelImport discards the staged import without restoring`() = runTest(testDispatcher) {
        val json = PreferencesJson.export.encodeToString(
            LegacySettingsBackup.serializer(),
            LegacySettingsBackup(preferences = UserPreferences()),
        )
        stubImportSource("backup:v1", json)
        val vm = viewModel()
        advanceUntilIdle()
        vm.importSettings("backup:v1")
        awaitUntil("the import stages") { vm.pendingImport != null }

        vm.cancelImport()

        assertNull(vm.pendingImport)
        coVerify(exactly = 0) { preferencesStore.restorePreferences(any(), any()) }
    }

    // ------------------------------------------------------------ session surface

    @Test
    fun `admin user loads only the live non-server sessions`() = runTest(testDispatcher) {
        val now = Instant.now().toString()
        val live = SessionInfo(id = "s1", client = "Jellyfin Web", deviceName = "Chrome", lastActivityDate = now, isActive = true)
        val staleIdle = SessionInfo(id = "s2", client = "Jellyfin Web", deviceName = "Tablet", lastActivityDate = "2020-01-01T00:00:00Z", isActive = true)
        val headless = SessionInfo(id = "s3", client = "Jellyfin Server", deviceName = "Server", lastActivityDate = now, isActive = true)
        val inactive = SessionInfo(id = "s4", client = "Jellyfin Web", deviceName = "Phone", lastActivityDate = now, isActive = false)
        val staleButPlaying = SessionInfo(
            id = "s5",
            client = "Jellyfin Web",
            deviceName = "TV",
            lastActivityDate = "2020-01-01T00:00:00Z",
            nowPlayingItem = SessionNowPlayingItem(id = "item-1"),
            isActive = true,
        )
        coEvery { adminRepository.getSessions() } returns Result.success(listOf(live, staleIdle, headless, inactive, staleButPlaying))
        val vm = viewModel()
        advanceUntilIdle()
        currentUser.value = UserInfo(id = "u1", name = "Admin", serverAddress = "http://x", accessToken = "t", isAdmin = true)
        advanceUntilIdle()

        coVerify(exactly = 1) { adminRepository.getSessions() }
        assertEquals(listOf("s1", "s5"), vm.activeSessions.map { it.id },
            "stale sessions drop out unless actively playing; headless/inactive entries never show")
        assertEquals(false, vm.isLoadingSessions)
    }

    @Test
    fun `non-admin user never polls sessions`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        currentUser.value = UserInfo(id = "u2", name = "User", serverAddress = "http://x", accessToken = "t", isAdmin = false)
        advanceUntilIdle()

        coVerify(exactly = 0) { adminRepository.getSessions() }
        assertTrue(vm.activeSessions.isEmpty())
    }

    @Test
    fun `sendMessageToSession posts success and failure events`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        coEvery { adminRepository.sendMessageToSession("s1", "h", "hello") } returns Result.success(Unit)
        vm.sendMessageToSession("s1", "h", "hello")
        advanceUntilIdle()
        assertEquals("Message sent successfully", vm.messageSentEvent)

        vm.clearMessageEvent()
        assertNull(vm.messageSentEvent)

        coEvery { adminRepository.sendMessageToSession("s2", "h", "hi") } returns Result.failure(RuntimeException("offline"))
        vm.sendMessageToSession("s2", "h", "hi")
        advanceUntilIdle()
        assertEquals("Failed to send message", vm.messageSentEvent)
    }

    @Test
    fun `server user list emission clears the loading flag`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        currentServerUsers.value = listOf(UserInfo(id = "u1", name = "A", serverAddress = "http://x", accessToken = "t"))
        advanceUntilIdle()

        assertEquals(1, vm.currentServerUsers.size)
        assertEquals(false, vm.isLoadingUsers)
    }

    // ------------------------------------------------------------ recents + editor

    @Test
    fun `recordSettingUsed delegates to the recents store`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.recordSettingUsed("theme_row")
        advanceUntilIdle()

        coVerify(exactly = 1) { recentsStore.addRecent("theme_row") }
    }

    @Test
    fun `clearRecentSettings delegates to the recents store`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.clearRecentSettings()
        advanceUntilIdle()

        coVerify(exactly = 1) { recentsStore.clearRecents() }
    }

    @Test
    fun `setShowAdvancedSettings routes through the editor`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.setShowAdvancedSettings(true)

        verify(exactly = 1) { editor.edit(any()) }
    }
}
