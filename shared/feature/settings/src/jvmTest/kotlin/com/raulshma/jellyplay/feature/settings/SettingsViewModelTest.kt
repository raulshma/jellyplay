package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.search.SettingsRecentsStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.SessionNowPlayingItem
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.time.Instant
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
 * Pins the settings-root ViewModel's non-Composable surface: the cache-size
 * computation off the [SettingsBackupIo] seam, the import stage-and-navigate
 * signal (uri staging only — decoding/classification/the restore live on the
 * import preview path, pinned by [ImportPreviewViewModelTest]), the
 * destructive guard rails (cancel → nothing restored), and the recent-settings
 * tracking.
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
        editor = editor,
        recentsStore = recentsStore,
    )

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

    @Test
    fun `importSettings stages the uri without reading or writing anything`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.importSettings("backup:any")
        advanceUntilIdle()

        assertEquals("backup:any", vm.stagedImportUri, "the stage-and-navigate signal carries the picked uri")
        assertNull(vm.backupRestoreStatus)
        // The dead twin used to read + classify here; the surviving path owns
        // all of that — staging must not touch the file or any store.
        coVerify(exactly = 0) { settingsBackupIo.openImportSource(any()) }
        coVerify(exactly = 0) { preferencesStore.restorePreferences(any(), any()) }
        coVerify(exactly = 0) { preferencesStore.restoreV2(any(), any()) }
    }

    @Test
    fun `cancelImport discards the staged import without restoring`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()
        vm.importSettings("backup:any")

        vm.cancelImport()

        assertNull(vm.stagedImportUri)
        coVerify(exactly = 0) { preferencesStore.restorePreferences(any(), any()) }
        coVerify(exactly = 0) { preferencesStore.restoreV2(any(), any()) }
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

    // ------------------------------------------------------------ recents

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
}