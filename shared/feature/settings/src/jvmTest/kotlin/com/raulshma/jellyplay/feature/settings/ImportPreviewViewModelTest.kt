package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.LegacySettingsBackup
import com.raulshma.jellyplay.core.datastore.PreferencesJson
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceSlice
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioSlice
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheSlice
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsSlice
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsSlice
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineSlice
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalSlice
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoverySlice
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibrarySlice
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationSlice
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineSlice
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationSlice
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackSlice
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverSlice
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.PinLockoutState
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.ByteArrayInputStream
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
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The import-preview diff/preview gate that runs BEFORE a destructive backup
 * restore: three backup shapes are classified (v2 per-slice / v1 enveloped /
 * v0 bare) plus the forward-compat future shape, the incoming snapshot never
 * touches the stores until the user confirms, and each import route
 * (all / single category / extras) fans to the right store call.
 * Regression-critical because confirming overwrites every preference.
 *
 * Stores are mockk'd (final DataStore-backed classes) with REAL
 * [MutableStateFlow] + default-slice stubs so the init-block snapshot
 * collectors read real values (relaxed child-mock flows would hang `.first()`).
 * Main-dispatcher rule inlined (StandardTestDispatcher + setMain/resetMain —
 * ServerManagementViewModelTest pattern).
 *
 * Timing note: `loadIncoming` hops through `Dispatchers.IO`, which does NOT
 * run on the virtual scheduler — its continuation re-enters Main some real
 * microseconds later. Tests therefore pump the scheduler via [awaitUntil]
 * instead of a single `advanceUntilIdle()`; the import stubs must also hand
 * back a FRESH stream per call (the VM re-reads the backup on confirm).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ImportPreviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var settingsBackupIo: SettingsBackupIo
    private lateinit var userPreferencesStore: UserPreferencesStore
    private lateinit var playbackStore: PlaybackStore
    private lateinit var appearanceStore: AppearanceStore
    private lateinit var videoPlayerStore: VideoPlayerStore
    private lateinit var downloadsStore: DownloadsStore
    private lateinit var engineStore: PlayerEngineStore
    private lateinit var homeDiscoveryStore: HomeDiscoveryStore
    private lateinit var audioStore: AudioStore
    private lateinit var audioEffectsStore: AudioEffectsStore
    private lateinit var audioCacheStore: AudioCacheStore
    private lateinit var libraryStore: LibraryStore
    private lateinit var navigationStore: NavigationStore
    private lateinit var networkOfflineStore: NetworkOfflineStore
    private lateinit var notificationStore: NotificationStore
    private lateinit var screensaverStore: ScreensaverStore
    private lateinit var securityStore: SecurityStore
    private lateinit var subtitleLanguageStore: SubtitleLanguageStore
    private lateinit var syncPlayCastStore: SyncPlayCastStore
    private lateinit var experimentalStore: ExperimentalStore
    private lateinit var appRuntimeStateStore: AppRuntimeStateStore
    private lateinit var pinRateLimiter: PinRateLimiter

    private val runtimeState = MutableStateFlow(AppRuntimeState())

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        settingsBackupIo = mockk()
        userPreferencesStore = mockk(relaxed = true)
        playbackStore = mockk(relaxed = true)
        appearanceStore = mockk(relaxed = true)
        videoPlayerStore = mockk(relaxed = true)
        downloadsStore = mockk(relaxed = true)
        engineStore = mockk(relaxed = true)
        homeDiscoveryStore = mockk(relaxed = true)
        audioStore = mockk(relaxed = true)
        audioEffectsStore = mockk(relaxed = true)
        audioCacheStore = mockk(relaxed = true)
        libraryStore = mockk(relaxed = true)
        navigationStore = mockk(relaxed = true)
        networkOfflineStore = mockk(relaxed = true)
        notificationStore = mockk(relaxed = true)
        screensaverStore = mockk(relaxed = true)
        securityStore = mockk(relaxed = true)
        subtitleLanguageStore = mockk(relaxed = true)
        syncPlayCastStore = mockk(relaxed = true)
        experimentalStore = mockk(relaxed = true)
        appRuntimeStateStore = mockk(relaxed = true)
        pinRateLimiter = mockk(relaxed = true)

        // Real flows with default slices — the init-block snapshot reads each
        // store's StateFlow via `.first()`.
        every { playbackStore.playback } returns MutableStateFlow(PlaybackSlice())
        every { videoPlayerStore.videoPlayer } returns MutableStateFlow(VideoPlayerSlice())
        every { engineStore.playerEngine } returns MutableStateFlow(PlayerEngineSlice())
        every { subtitleLanguageStore.subtitle } returns MutableStateFlow(SubtitleSlice())
        every { audioStore.audio } returns MutableStateFlow(AudioSlice())
        every { audioEffectsStore.audioEffects } returns MutableStateFlow(AudioEffectsSlice())
        every { audioCacheStore.audioCache } returns MutableStateFlow(AudioCacheSlice())
        every { appearanceStore.appearance } returns MutableStateFlow(AppearanceSlice())
        every { homeDiscoveryStore.homeDiscovery } returns MutableStateFlow(HomeDiscoverySlice())
        every { libraryStore.library } returns MutableStateFlow(LibrarySlice())
        every { navigationStore.navigation } returns MutableStateFlow(NavigationSlice())
        every { downloadsStore.downloads } returns MutableStateFlow(DownloadsSlice())
        every { networkOfflineStore.networkOffline } returns MutableStateFlow(NetworkOfflineSlice())
        every { notificationStore.notification } returns MutableStateFlow(NotificationSlice())
        every { screensaverStore.screensaver } returns MutableStateFlow(ScreensaverSlice())
        every { securityStore.security } returns MutableStateFlow(SecuritySlice())
        every { syncPlayCastStore.syncPlayCast } returns MutableStateFlow(SyncPlayCastSlice())
        every { experimentalStore.experimental } returns MutableStateFlow(ExperimentalSlice())
        every { appRuntimeStateStore.state } returns runtimeState
        every { pinRateLimiter.getPinLockoutState() } returns PinLockoutState.NOT_LOCKED
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Drains the scheduler until [condition] holds, pumping across real-IO hops. */
    private suspend fun TestScope.awaitUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            advanceUntilIdle()
            if (condition()) break
            assertTrue(
                System.currentTimeMillis() < deadline,
                "$description (timed out waiting for the VM's IO-hop continuation)",
            )
            withContext(Dispatchers.IO) { delay(10) }
        }
        advanceUntilIdle()
    }

    // ------------------------------------------------------------ JSON fixture builders

    /** Encodes a slice value as the [JsonElement] stored in the v2 slices map. */
    private fun <T> sliceElement(serializer: KSerializer<T>, value: T): JsonElement =
        PreferencesJson.import.parseToJsonElement(
            PreferencesJson.import.encodeToString(serializer, value),
        )

    private fun v2Json(
        slices: Map<String, JsonElement> = emptyMap(),
        extras: AppRuntimeState = AppRuntimeState(),
        schemaVersion: Int = SettingsBackup.CURRENT_SCHEMA_VERSION,
    ): String = PreferencesJson.export.encodeToString(
        SettingsBackup.serializer(),
        SettingsBackup(schemaVersion = schemaVersion, slices = slices, extras = extras),
    )

    private fun appearanceBackupJson(themeMode: ThemeMode = ThemeMode.DARK): String = v2Json(
        slices = mapOf(
            BackupSliceKey.APPEARANCE to
                sliceElement(AppearanceSlice.serializer(), AppearanceSlice(themeMode = themeMode)),
        ),
    )

    private fun v1Json(preferences: UserPreferences): String =
        PreferencesJson.export.encodeToString(
            LegacySettingsBackup.serializer(),
            LegacySettingsBackup(preferences = preferences),
        )

    private fun v0Json(preferences: UserPreferences): String =
        PreferencesJson.export.encodeToString(UserPreferences.serializer(), preferences)

    /** Fresh stream per call — the VM re-reads the source on every import. */
    private fun stubImport(uri: String, json: String) {
        coEvery { settingsBackupIo.openImportSource(uri) } answers {
            ByteArrayInputStream(json.toByteArray())
        }
    }

    private fun viewModel(): ImportPreviewViewModel = ImportPreviewViewModel(
        settingsBackupIo = settingsBackupIo,
        userPreferencesStore = userPreferencesStore,
        playbackStore = playbackStore,
        appearanceStore = appearanceStore,
        videoPlayerStore = videoPlayerStore,
        downloadsStore = downloadsStore,
        engineStore = engineStore,
        homeDiscoveryStore = homeDiscoveryStore,
        audioStore = audioStore,
        audioEffectsStore = audioEffectsStore,
        audioCacheStore = audioCacheStore,
        libraryStore = libraryStore,
        navigationStore = navigationStore,
        networkOfflineStore = networkOfflineStore,
        notificationStore = notificationStore,
        screensaverStore = screensaverStore,
        securityStore = securityStore,
        subtitleLanguageStore = subtitleLanguageStore,
        syncPlayCastStore = syncPlayCastStore,
        experimentalStore = experimentalStore,
        appRuntimeStateStore = appRuntimeStateStore,
        pinRateLimiter = pinRateLimiter,
    )

    private suspend fun TestScope.loadedWith(json: String, uri: String = "backup:v2"): ImportPreviewViewModel {
        stubImport(uri, json)
        val vm = viewModel().also { it.loadBackup(uri) }
        awaitUntil("backup preview for $uri") { vm.incomingPrefs != null || vm.error != null }
        return vm
    }

    // ---------------------------------------------------------------- load / classify

    @Test
    fun `v2 backup loads the incoming snapshot with current-version flags`() = runTest(testDispatcher) {
        val vm = loadedWith(appearanceBackupJson())

        assertFalse(vm.isLoading, "loading must finish once the backup is parsed")
        assertEquals(2, vm.schemaVersion)
        assertFalse(vm.isLegacy, "v2 is the current format")
        assertFalse(vm.versionMismatch)
        assertEquals(ThemeMode.DARK, vm.incomingPrefs?.themeMode, "incoming snapshot must mirror the backup slice")
        assertEquals(ThemeMode.SYSTEM, vm.currentPrefs.themeMode, "current snapshot stays on the live stores")
        assertNull(vm.legacyIncoming, "v2 has no legacy aggregate")
        assertTrue(vm.rawBackup != null, "v2 keeps the raw envelope for importAll")
        assertNull(vm.error)
    }

    @Test
    fun `v2 security slice flags the backup as security-sensitive`() = runTest(testDispatcher) {
        val json = v2Json(
            slices = mapOf(
                BackupSliceKey.SECURITY to sliceElement(
                    SecuritySlice.serializer(),
                    SecuritySlice(pinLockEnabled = true, pinHash = "imported-hash"),
                ),
            ),
        )
        val vm = loadedWith(json)

        assertTrue(vm.hasSecuritySensitive, "a backup carrying lock config must be opt-in")
    }

    @Test
    fun `v2 backup without a security slice is not security-sensitive`() = runTest(testDispatcher) {
        val vm = loadedWith(appearanceBackupJson())

        assertFalse(vm.hasSecuritySensitive)
    }

    @Test
    fun `v1 envelope is classified legacy with a version warning`() = runTest(testDispatcher) {
        val vm = loadedWith(v1Json(UserPreferences(themeMode = ThemeMode.DARK)), uri = "backup:v1")

        assertEquals(1, vm.schemaVersion)
        assertTrue(vm.isLegacy)
        assertTrue(vm.versionMismatch, "legacy imports must warn before overwriting")
        assertEquals(ThemeMode.DARK, vm.incomingPrefs?.themeMode)
        assertTrue(vm.legacyIncoming != null, "v1 keeps the legacy aggregate")
        assertTrue(vm.rawBackup == null, "v1 has no per-slice envelope")
    }

    @Test
    fun `bare v0 aggregate is classified legacy with schema zero`() = runTest(testDispatcher) {
        val vm = loadedWith(
            v0Json(UserPreferences(themeMode = ThemeMode.DARK, favoriteChannels = setOf("chan-9"))),
            uri = "backup:v0",
        )

        assertEquals(0, vm.schemaVersion)
        assertTrue(vm.isLegacy)
        assertTrue(vm.versionMismatch)
        assertEquals(setOf("chan-9"), vm.incomingExtras?.favoriteChannels, "v0 extras are lifted from the aggregate")
    }

    @Test
    fun `future schema version flags a mismatch but still previews`() = runTest(testDispatcher) {
        val vm = loadedWith(
            v2Json(schemaVersion = SettingsBackup.CURRENT_SCHEMA_VERSION + 1),
            uri = "backup:future",
        )

        assertFalse(vm.isLegacy)
        assertTrue(vm.versionMismatch, "a newer backup must warn before overwriting")
        assertTrue(vm.incomingPrefs != null)
    }

    @Test
    fun `unopenable backup file surfaces an error and clears loading`() = runTest(testDispatcher) {
        // A null stream is the dead-SAF-stream shape the seam documents.
        coEvery { settingsBackupIo.openImportSource("backup:missing") } returns null
        val vm = viewModel()
        advanceUntilIdle()
        vm.loadBackup("backup:missing")

        awaitUntil("the load failure surfaces") { vm.error != null }

        assertEquals("Cannot open backup file", vm.error)
        assertFalse(vm.isLoading)
        assertNull(vm.incomingPrefs, "nothing may be staged from an unreadable file")
    }

    @Test
    fun `same uri is not re-read but a different uri reloads`() = runTest(testDispatcher) {
        val vm = loadedWith(appearanceBackupJson(), uri = "backup:a")

        vm.loadBackup("backup:a")
        advanceUntilIdle()

        coVerify(exactly = 1) { settingsBackupIo.openImportSource("backup:a") }

        stubImport("backup:b", v1Json(UserPreferences()))
        vm.loadBackup("backup:b")
        awaitUntil("the second file replaces the preview") { vm.isLegacy }

        coVerify(exactly = 1) { settingsBackupIo.openImportSource("backup:b") }
        assertEquals(1, vm.schemaVersion, "the second file replaces the preview")
    }

    // ---------------------------------------------------------------- import all

    @Test
    fun `importAll on a v2 backup fans to restoreV2 without security opt-in`() = runTest(testDispatcher) {
        val vm = loadedWith(appearanceBackupJson())
        var done = false

        vm.importAll(restoreSecuritySensitive = false) { done = true }
        awaitUntil("importAll completes") { vm.importEvent != null }

        assertTrue(done, "onDone must fire after a successful import")
        assertIs<ImportPreviewViewModel.ImportEvent.AllImported>(vm.importEvent)
        coVerify(exactly = 1) {
            userPreferencesStore.restoreV2(withArg { assertEquals(2, it.schemaVersion) }, false)
        }
        coVerify(exactly = 0) { appRuntimeStateStore.restore(any(), any()) }
    }

    @Test
    fun `importAll on a legacy backup fans to restorePreferences plus runtime restore`() = runTest(testDispatcher) {
        val vm = loadedWith(v1Json(UserPreferences(themeMode = ThemeMode.DARK)), uri = "backup:v1")

        vm.importAll(restoreSecuritySensitive = false) { }
        awaitUntil("importAll completes") { vm.importEvent != null }

        assertIs<ImportPreviewViewModel.ImportEvent.AllImported>(vm.importEvent)
        coVerify(exactly = 1) {
            userPreferencesStore.restorePreferences(
                withArg { assertEquals(ThemeMode.DARK, it.themeMode) },
                false,
            )
        }
        coVerify(exactly = 1) { appRuntimeStateStore.restore(any(), clearNullIds = true) }
    }

    @Test
    fun `importAll without a loaded backup is a silent no-op`() = runTest(testDispatcher) {
        val vm = viewModel()
        advanceUntilIdle()

        vm.importAll(restoreSecuritySensitive = false) { }
        advanceUntilIdle()

        assertNull(vm.importEvent)
        coVerify(exactly = 0) { userPreferencesStore.restorePreferences(any(), any()) }
        coVerify(exactly = 0) { userPreferencesStore.restoreV2(any(), any()) }
    }

    @Test
    fun `importAll surfaces a Failed event when the store rejects the restore`() = runTest(testDispatcher) {
        val vm = loadedWith(appearanceBackupJson())
        coEvery { userPreferencesStore.restoreV2(any(), any()) } throws RuntimeException("db locked")

        vm.importAll(restoreSecuritySensitive = false) { }
        awaitUntil("the failure surfaces") { vm.importEvent != null }

        val failed = assertIs<ImportPreviewViewModel.ImportEvent.Failed>(vm.importEvent)
        assertTrue("db locked" in failed.message, "the store's message must reach the UI")
        assertEquals("Import failed: db locked", vm.importStatus, "legacy String API mirrors the event")
    }

    // ---------------------------------------------------------------- import category

    @Test
    fun `importCategory on v2 restores exactly the picked category`() = runTest(testDispatcher) {
        val vm = loadedWith(appearanceBackupJson())

        vm.importCategory(PreferenceResetCategory.APPEARANCE, restoreSecuritySensitive = false) { }
        awaitUntil("category import completes") { vm.importEvent != null }

        assertIs<ImportPreviewViewModel.ImportEvent.CategoryImported>(vm.importEvent)
        coVerify(exactly = 1) {
            userPreferencesStore.restoreV2Categories(
                withArg { assertEquals(2, it.schemaVersion) },
                eq(setOf(PreferenceResetCategory.APPEARANCE)),
                eq(false),
                eq(false),
            )
        }
    }

    @Test
    fun `legacy appearance import merges only the appearance fields`() = runTest(testDispatcher) {
        val vm = loadedWith(
            v1Json(UserPreferences(themeMode = ThemeMode.DARK, oledMode = true, pinLockEnabled = true)),
            uri = "backup:v1",
        )

        vm.importCategory(PreferenceResetCategory.APPEARANCE, restoreSecuritySensitive = false) { }
        awaitUntil("category import completes") { vm.importEvent != null }

        coVerify(exactly = 1) {
            userPreferencesStore.restorePreferences(
                withArg { merged ->
                    // Theme + OLED land; the security field must NOT ride along
                    // on an appearance-category import.
                    assertEquals(UserPreferences(themeMode = ThemeMode.DARK, oledMode = true), merged)
                },
                false,
            )
        }
    }

    @Test
    fun `legacy security import without opt-in keeps the device lock config`() = runTest(testDispatcher) {
        val vm = loadedWith(
            v1Json(UserPreferences(pinLockEnabled = true, pinHash = "stolen-hash", remoteControlEnabled = false)),
            uri = "backup:v1",
        )

        vm.importCategory(PreferenceResetCategory.SECURITY, restoreSecuritySensitive = false) { }
        awaitUntil("category import completes") { vm.importEvent != null }

        coVerify(exactly = 1) {
            userPreferencesStore.restorePreferences(
                withArg { merged ->
                    assertEquals(
                        UserPreferences(remoteControlEnabled = false),
                        merged,
                        "only the non-sensitive remote-control switch may move without opt-in",
                    )
                },
                false,
            )
        }
    }

    @Test
    fun `legacy security import with opt-in restores the whole lock config`() = runTest(testDispatcher) {
        val vm = loadedWith(
            v1Json(
                UserPreferences(
                    pinLockEnabled = true,
                    pinHash = "imported-hash",
                    biometricLockEnabled = true,
                    usePinForPlayerLock = true,
                    autoLockTimerMs = 99_999L,
                    remoteControlEnabled = false,
                ),
            ),
            uri = "backup:v1",
        )

        vm.importCategory(PreferenceResetCategory.SECURITY, restoreSecuritySensitive = true) { }
        awaitUntil("category import completes") { vm.importEvent != null }

        coVerify(exactly = 1) {
            userPreferencesStore.restorePreferences(
                withArg { merged ->
                    assertEquals(
                        UserPreferences(
                            pinLockEnabled = true,
                            pinHash = "imported-hash",
                            biometricLockEnabled = true,
                            usePinForPlayerLock = true,
                            autoLockTimerMs = 99_999L,
                            remoteControlEnabled = false,
                        ),
                        merged,
                    )
                },
                true,
            )
        }
    }

    // ---------------------------------------------------------------- import extras

    @Test
    fun `importExtras on v2 delegates to the store's extras restore`() = runTest(testDispatcher) {
        val vm = loadedWith(v2Json(extras = AppRuntimeState(favoriteChannels = setOf("chan-1"))))

        vm.importExtras { }
        awaitUntil("extras import completes") { vm.importEvent != null }

        assertIs<ImportPreviewViewModel.ImportEvent.ExtrasImported>(vm.importEvent)
        coVerify(exactly = 1) {
            userPreferencesStore.restoreExtras(
                withArg { assertEquals(setOf("chan-1"), it.extras.favoriteChannels) },
            )
        }
    }

    @Test
    fun `importExtras on legacy restores the lifted runtime state`() = runTest(testDispatcher) {
        val vm = loadedWith(v0Json(UserPreferences(onboardingCompleted = true)), uri = "backup:v0")

        vm.importExtras { }
        awaitUntil("extras import completes") { vm.importEvent != null }

        assertIs<ImportPreviewViewModel.ImportEvent.ExtrasImported>(vm.importEvent)
        coVerify(exactly = 1) {
            appRuntimeStateStore.restore(
                withArg { assertTrue(it.onboardingCompleted) },
                clearNullIds = true,
            )
        }
    }

    // ---------------------------------------------------------------- misc state

    @Test
    fun `clearImportEvent resets the posted event`() = runTest(testDispatcher) {
        val vm = loadedWith(appearanceBackupJson())
        vm.importAll(restoreSecuritySensitive = false) { }
        awaitUntil("importAll completes") { vm.importEvent != null }

        vm.clearImportEvent()

        assertNull(vm.importEvent)
        assertNull(vm.importStatus)
    }

    @Test
    fun `init failure surfaces an error without staging anything`() = runTest(testDispatcher) {
        // A collaborator blowing up during the init snapshot must not crash —
        // the VM posts the message and clears the spinner.
        coEvery { pinRateLimiter.getPinLockoutState() } throws RuntimeException("lockout read failed")
        val vm = viewModel()

        awaitUntil("the init failure surfaces") { vm.error != null }

        assertEquals("lockout read failed", vm.error)
        assertFalse(vm.isLoading)
    }
}
