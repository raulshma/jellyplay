package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository
import com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore
import com.raulshma.jellyplay.core.datastore.BackupSecrets
import com.raulshma.jellyplay.core.datastore.BackupSecretsCodec
import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.PreferencesJson
import com.raulshma.jellyplay.core.datastore.SettingsBackup
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
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentity
import com.raulshma.jellyplay.core.datastore.identity.ServerIdentityStore
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
import com.raulshma.jellyplay.core.datastore.settings.PreferenceSnapshotReader
import com.raulshma.jellyplay.core.datastore.settings.PreferenceStores
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastSlice
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerSlice
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.model.PinLockoutState
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.model.ThemeMode
import com.raulshma.jellyplay.core.network.api.JellyPlayAppliedSetting
import com.raulshma.jellyplay.core.network.api.JellyPlayRejectedSetting
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsBatchResult
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsEntry
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSyncRoutes
import com.raulshma.jellyplay.core.network.api.JellyPlaySnapshot
import com.raulshma.jellyplay.core.network.api.JellyPlaySnapshotContent
import com.raulshma.jellyplay.core.network.api.JellyPlaySnapshotProfile
import com.raulshma.jellyplay.core.network.api.JellyPlaySettingWrite
import com.raulshma.jellyplay.core.ui.message.UserMessageBus
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_other
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_quota
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_reject_bucket_stale_write
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_safety_snapshot_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.wizard_summary_rejected
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkObject
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The unified restore wizard's model gate (Wave 5+6): source loading + degrade,
 * the file diff (the retired import preview's ported coverage), the snapshot
 * diff classification (changed/added/removed + prefs sub-grouping incl. the
 * "Other" bucket), the two-tier selection consistency, apply-path branching
 * (file vs snapshot vs old-plugin full restore), the reject-reason mapping,
 * cross-account detection, the best-effort pre-apply capture (Wave 6 — a miss
 * warns and never blocks), and the Wave-3 secrets unlock/apply port.
 *
 * Harness = the retired `ImportPreviewViewModelTest` idiom (stores mockk'd with
 * REAL `MutableStateFlow` slices, a REAL [PreferenceSnapshotReader] over the
 * mock bundle, StandardTestDispatcher + setMain/resetMain, `awaitUntil` pumping
 * across the real-thread hops) crossed with the [JellyPlaySyncViewModelTest]
 * api-mockk idiom. Wizard texts resolve through an injected seam so the
 * bus-message assertions stay deterministic without a resource loader.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestoreWizardViewModelTest {

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

    private lateinit var pluginApi: JellyPlaySettingsSyncRoutes
    private lateinit var statusStore: JellyPlayPluginStatusStore
    private lateinit var syncRepository: ProfileSyncRepository
    private lateinit var serverIdentityStore: ServerIdentityStore

    private val pluginStatus = MutableStateFlow(JellyPlayPluginStatus.UNAVAILABLE)
    private val identity = MutableStateFlow(ServerIdentity(activeServerId = "srv-1", activeUserId = "user-1"))

    /** The real bus under test — its messages are drained by a collector. */
    private lateinit var bus: UserMessageBus
    private val busMessages = mutableListOf<String>()

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
        every { appRuntimeStateStore.state } returns MutableStateFlow(AppRuntimeState())
        every { pinRateLimiter.getPinLockoutState() } returns PinLockoutState.NOT_LOCKED

        pluginApi = mockk(relaxed = true)
        statusStore = mockk(relaxed = true)
        syncRepository = mockk(relaxed = true)
        serverIdentityStore = mockk(relaxed = true)
        every { statusStore.status } returns pluginStatus
        every { statusStore.hasFeature(JellyPlayPluginFeatures.SettingsSync) } returns false
        every { syncRepository.state } returns MutableStateFlow(ProfileSyncRepository.SyncState())
        every { serverIdentityStore.identity } returns identity
        coEvery { syncRepository.currentDeviceId() } returns "device-a"
        coEvery { pluginApi.getSnapshots() } returns Result.success(null)
        coEvery { pluginApi.getSnapshotContent(any()) } returns Result.success(null)
        coEvery { pluginApi.exportSettings() } returns Result.success(null)

        bus = UserMessageBus()
        busMessages.clear()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Drains the scheduler until [condition] holds, pumping across real-thread hops. */
    private suspend fun TestScope.awaitUntil(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
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

    // ------------------------------------------------------------ fixture builders

    private fun <T> sliceElement(serializer: KSerializer<T>, value: T): JsonElement =
        PreferencesJson.import.parseToJsonElement(
            PreferencesJson.import.encodeToString(serializer, value),
        )

    private fun v2Json(
        slices: Map<String, JsonElement> = emptyMap(),
        extras: AppRuntimeState = AppRuntimeState(),
        schemaVersion: Int = SettingsBackup.CURRENT_SCHEMA_VERSION,
        secrets: com.raulshma.jellyplay.core.datastore.SecretsEnvelope? = null,
        originUserId: String? = null,
        originServerId: String? = null,
    ): String = PreferencesJson.export.encodeToString(
        SettingsBackup.serializer(),
        SettingsBackup(
            schemaVersion = schemaVersion,
            slices = slices,
            extras = extras,
            secrets = secrets,
            originUserId = originUserId,
            originServerId = originServerId,
        ),
    )

    private fun appearanceBackupJson(themeMode: ThemeMode = ThemeMode.DARK): String = v2Json(
        slices = mapOf(
            BackupSliceKey.APPEARANCE to
                sliceElement(AppearanceSlice.serializer(), AppearanceSlice(themeMode = themeMode)),
        ),
    )

    private fun stubImport(uri: String, json: String) {
        coEvery { settingsBackupIo.readImportPayload(uri) } returns json
    }

    /**
     * Deterministic wizard text: each resolved resource gets a stable
     * "res#N" name (compose resource objects toString with hash suffixes, so
     * the bus-message assertions pin on this registry instead).
     */
    private val resName = java.util.concurrent.ConcurrentHashMap<org.jetbrains.compose.resources.StringResource, String>()
    private val testText: suspend (org.jetbrains.compose.resources.StringResource, List<Any>) -> String =
        { res, args ->
            resName.computeIfAbsent(res) { "res#${resName.size}" } +
                if (args.isEmpty()) "" else " [" + args.joinToString(",") + "]"
        }

    /** The stable name [testText] renders for [res] (must have been resolved once). */
    private fun nameOf(res: org.jetbrains.compose.resources.StringResource): String =
        resName[res] ?: error("resource never resolved in this test: $res")

    private fun viewModel(assembler: SecretsBackupAssembler? = null): RestoreWizardViewModel {
        val stores = PreferenceStores(
            playback = playbackStore,
            videoPlayer = videoPlayerStore,
            engine = engineStore,
            subtitle = subtitleLanguageStore,
            audio = audioStore,
            audioEffects = audioEffectsStore,
            audioCache = audioCacheStore,
            appearance = appearanceStore,
            homeDiscovery = homeDiscoveryStore,
            library = libraryStore,
            navigation = navigationStore,
            downloads = downloadsStore,
            networkOffline = networkOfflineStore,
            notification = notificationStore,
            syncPlayCast = syncPlayCastStore,
            security = securityStore,
            experimental = experimentalStore,
            screensaver = screensaverStore,
            volumeProfile = mockk(relaxed = true),
        )
        return RestoreWizardViewModel(
            settingsBackupIo = settingsBackupIo,
            userPreferencesStore = userPreferencesStore,
            snapshotReader = PreferenceSnapshotReader(stores, appRuntimeStateStore, pinRateLimiter),
            secretsBackupAssembler = assembler,
            pluginApiClient = pluginApi,
            statusStore = statusStore,
            syncRepository = syncRepository,
            serverIdentityStore = serverIdentityStore,
            messageBus = bus,
            diffLabelResolver = { _ -> { res -> res.toString() } },
            textResolver = testText,
        )
    }

    /** Starts the bus collector BEFORE any action posts (one-shot channel semantics). */
    private fun TestScope.collectBus() {
        backgroundScope.launch { bus.messages.collect { busMessages += it.toString() } }
    }

    private suspend fun TestScope.loadedWithFile(
        json: String,
        uri: String = "backup:v2",
        assembler: SecretsBackupAssembler? = null,
    ): RestoreWizardViewModel {
        stubImport(uri, json)
        val vm = viewModel(assembler).also { it.loadBackupFile(uri) }
        awaitUntil("file diff for $uri") { vm.uiState.file != null || vm.uiState.loadError }
        return vm
    }

    /** The probe AVAILABLE + `settings-sync` registry state the server arm reads. */
    private fun gateOpen() {
        pluginStatus.value = JellyPlayPluginStatus.AVAILABLE
        every { statusStore.hasFeature(JellyPlayPluginFeatures.SettingsSync) } returns true
    }

    /** Gate open AND a succeeding safety capture — the happy pre-apply path. */
    private fun gateCaptureOk() {
        gateOpen()
        coEvery { pluginApi.createSnapshot() } returns Result.success(
            com.raulshma.jellyplay.core.network.api.JellyPlaySnapshotCreated(0L),
        )
    }

    private fun entry(
        ns: String,
        key: String,
        value: String,
        profile: String = "",
        updatedAt: Long = 0,
    ): JellyPlaySettingsEntry =
        JellyPlaySettingsEntry(
            ns = ns,
            key = key,
            schemaVersion = 1,
            updatedAt = updatedAt,
            profile = profile,
            value = JsonPrimitive(value),
        )

    /** Builds an export-bundle body carrying [rows] as its only profile's rows. */
    private fun exportBundle(rows: List<JellyPlaySettingsEntry>, profile: String = ""): String =
        buildJsonObject {
            put("profiles", buildJsonArray {
                add(
                    buildJsonObject {
                        put("profile", profile)
                        put("settings", buildJsonArray {
                            rows.forEach { row ->
                                add(
                                    PreferencesJson.export.encodeToJsonElement(
                                        JellyPlaySettingsEntry.serializer(),
                                        row,
                                    ),
                                )
                            }
                        })
                    },
                )
            })
        }.toString()

    /**
     * Loads the shared snapshot fixture and stages its diff. Snapshot rows:
     * a changed appearance key, an undeclared (Other) key, a per-user
     * namespaced home key, an added search key, a cw key. Live rows: the
     * appearance key at another value, two prefs keys the snapshot lacks,
     * a cw key the snapshot lacks.
     */
    private suspend fun TestScope.openSnapshotVm(): RestoreWizardViewModel {
        gateOpen()
        coEvery { pluginApi.getSnapshotContent("s1") } returns Result.success(
            JellyPlaySnapshotContent(
                id = 1L,
                profiles = listOf(
                    JellyPlaySnapshotProfile(
                        settings = listOf(
                            entry("prefs", "theme_mode", "dark"),
                            entry("prefs", "mystery_key", "m"),
                            entry("prefs", "u_user-1::home_mode", "cozy"),
                            entry("search", "recents_k1", "x"),
                            entry("cw", "row_a", "y"),
                        ),
                    ),
                ),
            ),
        )
        coEvery { pluginApi.exportSettings() } returns Result.success(
            exportBundle(
                listOf(
                    entry("prefs", "theme_mode", "light"),
                    entry("prefs", "removed_live_key", "v"),
                    entry("prefs", "gone_other", "v"),
                    entry("cw", "row_b", "z"),
                ),
            ),
        )
        val vm = viewModel()
        vm.start(uri = null, snapshotId = "s1")
        awaitUntil("the snapshot diff stages") { vm.uiState.snapshot != null }
        return vm
    }

    // ---------------------------------------------------------------- source loading + degrade

    @Test
    fun `gate closed degrades the server arm quietly without touching the api`() = runTest(testDispatcher) {
        val vm = viewModel()
        vm.start(uri = null, snapshotId = null)
        advanceUntilIdle()

        assertFalse(vm.uiState.serverAvailable)
        assertTrue(vm.uiState.snapshotsUnavailable, "the empty state must explain the closed gate")
        coVerify(exactly = 0) { pluginApi.getSnapshots() }
    }

    @Test
    fun `gate open loads the restore points and a pre-wave 404 degrades to unavailable`() = runTest(testDispatcher) {
        gateOpen()
        coEvery { pluginApi.getSnapshots() } returns Result.success(
            listOf(JellyPlaySnapshot(id = 1L, createdAt = 1_700_000_000_000, origin = "manual", keys = 4)),
        )
        val vm = viewModel()
        vm.start(uri = null, snapshotId = null)
        awaitUntil("the restore points load") { vm.uiState.snapshots.isNotEmpty() }

        assertTrue(vm.uiState.serverAvailable)
        assertFalse(vm.uiState.snapshotsUnavailable)
        assertEquals(1, vm.uiState.snapshots.size)

        // The pre-wave 404 (null payload) degrades to the same quiet face.
        coEvery { pluginApi.getSnapshots() } returns Result.success(null)
        vm.refreshSource()
        awaitUntil("the pre-wave 404 degrades") { vm.uiState.snapshotsUnavailable }
    }

    @Test
    fun `a nav-carried snapshot id jumps straight into the snapshot diff`() = runTest(testDispatcher) {
        val vm = openSnapshotVm()

        assertEquals(WizardStep.DIFF, vm.uiState.step)
        assertNull(vm.uiState.file)
        assertTrue(vm.uiState.fullRestore == null)
        assertTrue(vm.uiState.snapshot!!.groups.totalGroups > 0)
    }

    // ---------------------------------------------------------------- file diff (ported preview coverage)

    @Test
    fun `v2 backup loads the file diff with current-version flags`() = runTest(testDispatcher) {
        val vm = loadedWithFile(appearanceBackupJson())

        assertFalse(vm.uiState.loadError)
        val file = vm.uiState.file!!
        assertEquals(2, file.schemaVersion)
        assertFalse(file.versionMismatch)
        assertFalse(file.crossAccount)
        assertEquals(
            PreferenceResetCategory.entries.size,
            file.categories.size,
            "every declared category gets a selectable group",
        )
        val appearance = file.categories.first { it.category == PreferenceResetCategory.APPEARANCE }
        assertTrue(appearance.changed.isNotEmpty(), "the DARK backup must diff against the live SYSTEM default")
        assertTrue(appearance.changed.any { it.currentValue != it.factoryValue })
        assertTrue(vm.uiState.step == WizardStep.DIFF)
    }

    @Test
    fun `v2 security slice flags the lock-config opt-in`() = runTest(testDispatcher) {
        val json = v2Json(
            slices = mapOf(
                BackupSliceKey.SECURITY to sliceElement(
                    SecuritySlice.serializer(),
                    SecuritySlice(pinLockEnabled = true, pinHash = "imported-hash"),
                ),
            ),
        )
        val vm = loadedWithFile(json)

        assertTrue(vm.uiState.file!!.hasSecuritySensitive)
    }

    @Test
    fun `future schema version flags a mismatch but still previews`() = runTest(testDispatcher) {
        val vm = loadedWithFile(
            v2Json(schemaVersion = SettingsBackup.CURRENT_SCHEMA_VERSION + 1),
            uri = "backup:future",
        )

        val file = vm.uiState.file!!
        assertTrue(file.versionMismatch)
        assertEquals(SettingsBackup.CURRENT_SCHEMA_VERSION + 1, file.schemaVersion)
    }

    @Test
    fun `unopenable backup file surfaces the inline load error and allows a retry`() = runTest(testDispatcher) {
        coEvery { settingsBackupIo.readImportPayload("backup:missing") } returns null
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadBackupFile("backup:missing")
        awaitUntil("the load failure surfaces") { vm.uiState.loadError }

        assertNull(vm.uiState.file, "nothing may be staged from an unreadable file")
        assertEquals("Cannot open backup file", vm.lastLoadErrorMessage)

        // A retry with the SAME uri re-reads (the stage was cleared on failure).
        stubImport("backup:missing", appearanceBackupJson())
        vm.loadBackupFile("backup:missing")
        awaitUntil("the retry stages") { vm.uiState.file != null }
        assertFalse(vm.uiState.loadError)
    }

    @Test
    fun `legacy v0 backups are rejected by the parser into the load error`() = runTest(testDispatcher) {
        stubImport("backup:v0", """{"schemaVersion":0}""")
        val vm = viewModel()
        vm.loadBackupFile("backup:v0")
        awaitUntil("the rejection surfaces") { vm.uiState.loadError }

        assertNull(vm.uiState.file)
        assertTrue(vm.lastLoadErrorMessage!!.contains("schemaVersion"))
    }

    // ---------------------------------------------------------------- wave-2 slices + extras

    @Test
    fun `wave-2 slices in the backup stage external cards with human labels`() = runTest(testDispatcher) {
        val currentIntegrations = buildJsonObject { put("seerr_server_url", "https://old.local") }
        val incomingIntegrations = buildJsonObject { put("seerr_server_url", "https://new.local") }
        val incomingPlaylists = buildJsonObject { put("smart/pl-1", buildJsonObject { put("name", "Night") }) }
        coEvery { userPreferencesStore.externalSliceSnapshot() } returns mapOf(
            BackupSliceKey.INTEGRATIONS to currentIntegrations,
            BackupSliceKey.PLAYLISTS to null,
        )
        val json = v2Json(
            slices = mapOf(
                BackupSliceKey.INTEGRATIONS to incomingIntegrations,
                BackupSliceKey.PLAYLISTS to incomingPlaylists,
            ),
        )

        val vm = loadedWithFile(json)

        val cards = vm.uiState.file!!.externalSlices
        assertEquals(
            listOf(BackupSliceKey.INTEGRATIONS, BackupSliceKey.PLAYLISTS),
            cards.map { it.key },
            "only slices the backup carries get cards",
        )
        val integrations = cards.first { it.key == BackupSliceKey.INTEGRATIONS }
        assertEquals(1, integrations.changed.size)
        assertEquals("https://old.local", integrations.changed.single().currentValue)
        assertEquals("https://new.local", integrations.changed.single().factoryValue)

        // The "All" affordance over the external-slice cards: a full restore
        // of every offered slice stays one tap (partial → all → clear).
        val offered = listOf(BackupSliceKey.INTEGRATIONS, BackupSliceKey.PLAYLISTS)
        vm.toggleSlice(BackupSliceKey.INTEGRATIONS)
        var selection = vm.uiState.file!!.selection
        assertEquals(setOf(BackupSliceKey.INTEGRATIONS), selection.slices)
        assertFalse(selection.allSlicesSelected(offered))

        vm.toggleAllSlices()
        selection = vm.uiState.file!!.selection
        assertTrue(selection.allSlicesSelected(offered))
        assertEquals(offered.toSet(), selection.slices)

        vm.toggleAllSlices()
        assertTrue(vm.uiState.file!!.selection.slices.isEmpty())
    }

    @Test
    fun `extras card diffs the app-runtime block`() = runTest(testDispatcher) {
        val vm = loadedWithFile(
            v2Json(extras = AppRuntimeState(favoriteChannels = setOf("chan-1"))),
        )

        val extras = vm.uiState.file!!.extras!!
        assertTrue(extras.changed.isNotEmpty(), "the incoming favorite channel must diff against the empty live block")
        assertTrue(extras.changed.any { it.factoryValue.contains("chan-1") })
    }

    // ---------------------------------------------------------------- selection consistency

    @Test
    fun `selection toggles keep the parent tier consistent`() = runTest(testDispatcher) {
        val vm = loadedWithFile(appearanceBackupJson())
        val declared = PreferenceCategoryViews.map { it.category }

        // Child toggle lands in the selection…
        vm.toggleCategory(PreferenceResetCategory.APPEARANCE)
        var selection = vm.uiState.file!!.selection
        assertTrue(PreferenceResetCategory.APPEARANCE in selection.categories)
        assertFalse(selection.allCategoriesSelected(declared))

        // …the parent toggle selects ALL children from a partial state…
        vm.toggleAllCategories()
        selection = vm.uiState.file!!.selection
        assertTrue(selection.allCategoriesSelected(declared))
        assertEquals(declared.toSet(), selection.categories)

        // …and clears them all from the fully-selected state.
        vm.toggleAllCategories()
        selection = vm.uiState.file!!.selection
        assertTrue(selection.categories.isEmpty())

        // Extras + slices toggle independently; the confirm count follows.
        vm.toggleExtras()
        vm.toggleSlice(BackupSliceKey.WIDGET)
        vm.toggleSlice(BackupSliceKey.WIDGET)
        selection = vm.uiState.file!!.selection
        assertTrue(selection.extras)
        assertTrue(BackupSliceKey.WIDGET !in selection.slices)
        assertEquals(1, vm.selectedGroupCount())
    }

    @Test
    fun `snapshot selection keeps the prefs parent aligned with its domains`() = runTest(testDispatcher) {
        val vm = openSnapshotVm()

        vm.togglePrefDomain("appearance")
        var selection = vm.uiState.snapshot!!.selection
        assertTrue("appearance" in selection.domains)
        assertFalse(selection.allDomainsSelected(vm.uiState.snapshot!!.groups.prefDomains))

        vm.togglePrefs()
        selection = vm.uiState.snapshot!!.selection
        assertTrue(selection.allDomainsSelected(vm.uiState.snapshot!!.groups.prefDomains))

        vm.togglePrefs()
        selection = vm.uiState.snapshot!!.selection
        assertTrue(selection.domains.isEmpty())

        vm.toggleNamespace("search")
        assertTrue("search" in vm.uiState.snapshot!!.selection.namespaces)
    }

    // ---------------------------------------------------------------- file apply (Wave 6 hook #1 inside)

    @Test
    fun `file apply fans to the store seams per selection and syncs after`() = runTest(testDispatcher) {
        gateCaptureOk()
        val vm = loadedWithFile(appearanceBackupJson())
        collectBus()

        vm.toggleCategory(PreferenceResetCategory.APPEARANCE)
        vm.toggleSlice(BackupSliceKey.WIDGET)
        vm.toggleExtras()
        vm.restoreSelected()
        awaitUntil("the file apply completes") { vm.uiState.completed }

        coVerify(exactly = 1) {
            userPreferencesStore.restoreV2Categories(
                withArg { assertEquals(2, it.schemaVersion) },
                eq(setOf(PreferenceResetCategory.APPEARANCE)),
                eq(false),
                eq(false),
            )
        }
        coVerify(exactly = 1) {
            userPreferencesStore.restoreExternalSlice(any(), BackupSliceKey.WIDGET)
        }
        coVerify(exactly = 1) { userPreferencesStore.restoreExtras(any()) }
        coVerify(exactly = 1) { pluginApi.createSnapshot() }
        coVerify(exactly = 1) { syncRepository.requestSync() }
        awaitUntil("the summary lands on the bus") { busMessages.isNotEmpty() }
        assertEquals(1, busMessages.size, "the summary lands on the bus")
        assertFalse(vm.uiState.applying)
    }

    @Test
    fun `empty selection applies nothing and never completes`() = runTest(testDispatcher) {
        gateCaptureOk()
        val vm = loadedWithFile(appearanceBackupJson())

        vm.restoreSelected()
        advanceUntilIdle()

        coVerify(exactly = 0) { userPreferencesStore.restoreV2Categories(any(), any(), any(), any()) }
        coVerify(exactly = 1) { pluginApi.createSnapshot() }
        assertFalse(vm.uiState.completed)
    }

    @Test
    fun `pre-apply capture failure warns on the bus and never blocks the apply`() = runTest(testDispatcher) {
        gateOpen()
        coEvery { pluginApi.createSnapshot() } returns Result.failure(IllegalStateException("500"))
        val vm = loadedWithFile(appearanceBackupJson())
        collectBus()

        vm.toggleCategory(PreferenceResetCategory.APPEARANCE)
        vm.restoreSelected()
        awaitUntil("the apply still completes") { vm.uiState.completed }

        coVerify(exactly = 1) {
            userPreferencesStore.restoreV2Categories(any(), any(), any(), any())
        }
        awaitUntil("the capture miss warns") { busMessages.isNotEmpty() }
        assertTrue(
            busMessages.any { it.contains(nameOf(Res.string.wizard_safety_snapshot_failed)) },
            "the capture miss must warn through the bus",
        )
    }

    @Test
    fun `closed gate skips the capture entirely`() = runTest(testDispatcher) {
        // Defaults: probe UNAVAILABLE — the gate is closed.
        val vm = loadedWithFile(appearanceBackupJson())
        vm.toggleCategory(PreferenceResetCategory.APPEARANCE)

        vm.restoreSelected()
        awaitUntil("the apply completes") { vm.uiState.completed }

        coVerify(exactly = 0) { pluginApi.createSnapshot() }
    }

    @Test
    fun `security opt-in rides the categories restore`() = runTest(testDispatcher) {
        gateCaptureOk()
        val json = v2Json(
            slices = mapOf(
                BackupSliceKey.SECURITY to sliceElement(
                    SecuritySlice.serializer(),
                    SecuritySlice(pinLockEnabled = true, pinHash = "imported-hash"),
                ),
            ),
        )
        val vm = loadedWithFile(json)

        vm.toggleCategory(PreferenceResetCategory.SECURITY)
        vm.toggleRestoreSecuritySensitive(true)
        vm.restoreSelected()
        awaitUntil("the apply completes") { vm.uiState.completed }

        coVerify(exactly = 1) {
            userPreferencesStore.restoreV2Categories(any(), any(), eq(true), eq(false))
        }
    }

    // ---------------------------------------------------------------- cross-account

    @Test
    fun `origin ids from another account warn, own or absent stay silent`() = runTest(testDispatcher) {
        val foreign = loadedWithFile(
            v2Json(originUserId = "user-2", originServerId = "srv-1"),
            uri = "backup:foreign",
        )
        assertTrue(foreign.uiState.file!!.crossAccount)

        val own = loadedWithFile(
            v2Json(originUserId = "user-1", originServerId = "srv-1"),
            uri = "backup:own",
        )
        assertFalse(own.uiState.file!!.crossAccount)

        val unstamped = loadedWithFile(appearanceBackupJson(), uri = "backup:unstamped")
        assertFalse(unstamped.uiState.file!!.crossAccount)
    }

    // ---------------------------------------------------------------- secrets (Wave 3 port)

    /** Real-codec fixture: one KDF per encrypt, counts pinned by the asserts below. */
    private fun secretsEnvelope(passphrase: String) = BackupSecretsCodec.encrypt(
        BackupSecrets(
            arrServers = listOf(
                com.raulshma.jellyplay.core.datastore.ArrServerSecret(
                    id = "arr-1", baseUrl = "https://arr.local", apiKey = "key", name = "Arr",
                    kind = com.raulshma.jellyplay.core.model.arr.ArrServiceKind.RADARR,
                ),
            ),
            subtitleCredentials = emptyList(),
            seerr = null,
            servers = listOf(
                com.raulshma.jellyplay.core.datastore.ServerEntrySecret("srv-1", "Home", "https://home.local"),
                com.raulshma.jellyplay.core.datastore.ServerEntrySecret("srv-2", "Away", "https://away.local"),
            ),
        ),
        passphrase = passphrase.toCharArray(),
    )

    @Test
    fun `backup with secrets stages the locked card and unlock surfaces the counts`() = runTest(testDispatcher) {
        val assembler = mockk<SecretsBackupAssembler>(relaxed = true)
        every { assembler.summarize(any()) } returns SecretsRestoreSummary(
            servers = 2, arrServers = 1, subtitleProviders = 0, hasSeerr = false,
        )
        val vm = loadedWithFile(
            v2Json(secrets = secretsEnvelope("open-sesame-9")),
            uri = "backup:secrets",
            assembler = assembler,
        )
        val file = vm.uiState.file!!
        assertTrue(file.hasSecrets)
        assertNull(file.secretsUnlocked, "locked until the passphrase unlocks it")

        vm.unlockSecrets("open-sesame-9".toCharArray())
        awaitUntil("the unlock settles") { vm.uiState.file?.secretsUnlocked != null }

        assertEquals(2, vm.uiState.file?.secretsUnlocked?.servers)
        assertEquals(1, vm.uiState.file?.secretsUnlocked?.arrServers)
        coVerify(exactly = 1) { assembler.summarize(any()) }
    }

    @Test
    fun `unlock with the wrong passphrase flags the inline error and stays locked`() = runTest(testDispatcher) {
        val vm = loadedWithFile(
            v2Json(secrets = secretsEnvelope("open-sesame-9")),
            uri = "backup:secrets",
        )

        vm.unlockSecrets("not-the-passphrase".toCharArray())
        awaitUntil("the inline error surfaces") { vm.uiState.file?.secretsError == true }

        assertNull(vm.uiState.file?.secretsUnlocked, "a failed unlock must not stage anything")
    }

    @Test
    fun `applySecrets fans the unlocked payload through the assembler and a locked apply is a no-op`() = runTest(testDispatcher) {
        val assembler = mockk<SecretsBackupAssembler>(relaxed = true)
        coEvery { assembler.apply(any()) } returns SecretsApplyResult(1, 0, false, 2)
        val vm = loadedWithFile(
            v2Json(secrets = secretsEnvelope("open-sesame-9")),
            uri = "backup:secrets",
            assembler = assembler,
        )

        // Locked: nothing to apply.
        vm.applySecrets()
        advanceUntilIdle()
        coVerify(exactly = 0) { assembler.apply(any()) }

        vm.unlockSecrets("open-sesame-9".toCharArray())
        awaitUntil("the unlock settles") { vm.uiState.file?.secretsUnlocked != null }
        collectBus()
        vm.applySecrets()
        awaitUntil("the secrets apply posts") { busMessages.isNotEmpty() }

        coVerify(exactly = 1) { assembler.apply(any()) }

        // A successful apply SPENDS the payload: a second tap is a no-op,
        // never a second write of the decrypted copy.
        vm.applySecrets()
        advanceUntilIdle()
        coVerify(exactly = 1) { assembler.apply(any()) }
        assertFalse(vm.uiState.applying)
    }

    @Test
    fun `unlockSecrets zeroes the passphrase material`() = runTest(testDispatcher) {
        val vm = loadedWithFile(
            v2Json(secrets = secretsEnvelope("open-sesame-9")),
            uri = "backup:secrets",
        )

        val passphrase = "open-sesame-9".toCharArray()
        vm.unlockSecrets(passphrase)
        awaitUntil("the unlock settles") {
            vm.uiState.file?.secretsUnlocked != null || vm.uiState.file?.secretsError == true
        }

        assertTrue(passphrase.all { it == '\u0000' }, "the passphrase must be zeroed after the attempt")
    }

    @Test
    fun `unlockSecrets zeroes the passphrase on the early returns`() = runTest(testDispatcher) {
        // Early return 1: the staged file carries NO secrets envelope.
        val vm = loadedWithFile(appearanceBackupJson())
        val noEnvelope = "zero-me-now-1".toCharArray()
        vm.unlockSecrets(noEnvelope)

        assertTrue(noEnvelope.all { it == '\u0000' }, "the no-envelope return zeroes the passphrase")
        assertFalse(vm.uiState.file!!.secretsUnlocking)

        // Early return 2: an unlock is already in flight (parked inside the
        // gated codec call).
        val entryGate = CompletableDeferred<Unit>()
        mockkObject(BackupSecretsCodec)
        try {
            coEvery { BackupSecretsCodec.decrypt(any(), any()) } coAnswers {
                entryGate.await()
                callOriginal()
            }
            stubImport("backup:again", v2Json(secrets = secretsEnvelope("open-sesame-9")))
            vm.loadBackupFile("backup:again")
            awaitUntil("the secrets file stages") { vm.uiState.file?.hasSecrets == true }

            val inFlight = "open-sesame-9".toCharArray()
            vm.unlockSecrets(inFlight)
            val refused = "zero-me-now-2".toCharArray()
            vm.unlockSecrets(refused)

            assertTrue(refused.all { it == '\u0000' }, "the already-unlocking return zeroes the passphrase")
            entryGate.complete(Unit)
            awaitUntil("the in-flight unlock settles") { inFlight.all { it == '\u0000' } }
        } finally {
            unmockkObject(BackupSecretsCodec)
        }
    }

    @Test
    fun `a rapid double confirmation queues only one KDF run`() = runTest(testDispatcher) {
        val entries = java.util.concurrent.atomic.AtomicInteger()
        val entryGate = CompletableDeferred<Unit>()
        mockkObject(BackupSecretsCodec)
        try {
            coEvery { BackupSecretsCodec.decrypt(any(), any()) } coAnswers {
                entries.incrementAndGet()
                entryGate.await()
                callOriginal()
            }
            val vm = loadedWithFile(
                v2Json(secrets = secretsEnvelope("open-sesame-9")),
                uri = "backup:twice",
            )

            val first = "open-sesame-9".toCharArray()
            val second = "wrong-passphrase".toCharArray()
            vm.unlockSecrets(first)
            // The second confirmation must be refused synchronously — the busy
            // flag is already up — not queue a second 600k KDF run.
            vm.unlockSecrets(second)

            assertTrue(second.all { it == '\u0000' }, "the refused passphrase is zeroed before returning")
            awaitUntil("the first KDF enters") { entries.get() == 1 }

            entryGate.complete(Unit)
            awaitUntil("the single unlock settles") { first.all { it == '\u0000' } }
            assertEquals(1, entries.get(), "two rapid confirmations must not queue two KDF runs")
            assertTrue(vm.uiState.file!!.secretsUnlocked != null)
        } finally {
            unmockkObject(BackupSecretsCodec)
        }
    }

    @Test
    fun `an unlock settling after a re-stage is discarded - no summary and its apply is a no-op`() =
        runTest(testDispatcher) {
            val assembler = mockk<SecretsBackupAssembler>(relaxed = true)
            val envelopeA = secretsEnvelope("open-sesame-9")
            val entryGate = CompletableDeferred<Unit>()
            mockkObject(BackupSecretsCodec)
            try {
                coEvery { BackupSecretsCodec.decrypt(any(), any()) } coAnswers {
                    entryGate.await()
                    callOriginal()
                }
                stubImport("backup:A", v2Json(secrets = envelopeA))
                val vm = viewModel(assembler).also { it.loadBackupFile("backup:A") }
                awaitUntil("file A stages") { vm.uiState.file?.hasSecrets == true }

                val passA = "open-sesame-9".toCharArray()
                vm.unlockSecrets(passA)

                // While A's decrypt is parked, re-stage a DIFFERENT source:
                // file B carries no secrets at all.
                stubImport("backup:B", appearanceBackupJson())
                vm.loadBackupFile("backup:B")
                awaitUntil("file B stages") { vm.uiState.file?.hasSecrets == false }
                assertFalse(vm.uiState.file!!.secretsUnlocking)

                entryGate.complete(Unit)
                awaitUntil("the parked decrypt settles") { passA.all { it == '\u0000' } }

                val file = vm.uiState.file!!
                assertFalse(file.hasSecrets, "no secrets card for the discarded envelope")
                assertNull(file.secretsUnlocked, "the stale unlock must not resurrect a summary for A")
                assertFalse(file.secretsError)

                vm.applySecrets()
                advanceUntilIdle()
                coVerify(exactly = 0) { assembler.apply(any()) }
            } finally {
                unmockkObject(BackupSecretsCodec)
            }
        }

    // ---------------------------------------------------------------- snapshot diff + apply

    @Test
    fun `snapshot diff classifies changed, added and removed and sub-groups prefs by domain`() = runTest(testDispatcher) {
        val vm = openSnapshotVm()

        val groups = vm.uiState.snapshot!!.groups
        // Non-prefs namespaces sort by ns: cw first, then search.
        assertEquals(listOf("cw", "search"), groups.namespaces.map { it.id })
        val search = groups.namespaces.first { it.id == "search" }
        assertEquals(1, search.added, "the snapshot-only row classifies ADDED")
        assertEquals("recents_k1", search.rows.single().key)

        // The cw pair: the snapshot's row_a is live-absent (ADDED) and the
        // live row_b the snapshot lacks is REMOVED.
        val cw = groups.namespaces.first { it.id == "cw" }
        assertTrue(cw.rows.any { it.key == "row_b" && it.kind == SnapshotRowKind.REMOVED })
        assertTrue(cw.rows.any { it.key == "row_a" && it.kind == SnapshotRowKind.ADDED })

        // Prefs sub-grouping: the appearance key lands in its domain, the
        // undeclared key lands in "Other", the live-only keys are REMOVED.
        val appearance = groups.prefDomains.first { it.id == "appearance" }
        assertTrue(appearance.rows.any { it.key == "theme_mode" && it.kind == SnapshotRowKind.CHANGED })
        assertTrue(appearance.rows.none { it.kind == SnapshotRowKind.REMOVED })

        val other = groups.prefDomains.last { it.id == PrefsDomainCatalog.OTHER_DOMAIN }
        assertTrue(other.rows.any { it.key == "mystery_key" && it.kind == SnapshotRowKind.ADDED })
        assertTrue(other.rows.any { it.key == "removed_live_key" && it.kind == SnapshotRowKind.REMOVED })
        assertTrue(other.rows.any { it.key == "gone_other" && it.kind == SnapshotRowKind.REMOVED })

        // The namespaced per-user key still matches its canonical domain.
        val home = groups.prefDomains.first { it.id == "home" }
        assertTrue(home.rows.single().key.endsWith("home_mode"))
    }

    @Test
    fun `snapshot apply builds one base-profile batch with fresh stamps, tombstones, and syncs after`() = runTest(testDispatcher) {
        gateCaptureOk()
        coEvery { pluginApi.applySettings(any(), any(), any()) } returns Result.success(
            JellyPlaySettingsBatchResult(
                applied = listOf(
                    JellyPlayAppliedSetting("search", "recents_k1", 1, 1),
                    JellyPlayAppliedSetting("prefs", "theme_mode", 1, 2),
                ),
            ),
        )
        val vm = openSnapshotVm()
        collectBus()

        vm.toggleNamespace("search")
        vm.togglePrefDomain("appearance")
        vm.restoreSelected()
        awaitUntil("the snapshot apply completes") { vm.uiState.completed }

        // The fixture's rows all originate from the base profile: exactly ONE
        // call, riding profile = null (the base profile's wire form).
        val writesSlot = slot<List<JellyPlaySettingWrite>>()
        coVerify(exactly = 1) {
            pluginApi.applySettings(profile = null, deviceId = "device-a", capture(writesSlot))
        }
        val writes = writesSlot.captured
        val searchWrite = writes.single { it.ns == "search" }
        assertFalse(searchWrite.deleted)
        assertTrue(searchWrite.updatedAt > 0, "value writes carry fresh stamps")
        assertEquals(JsonPrimitive("x"), searchWrite.value)

        val appearanceWrite = writes.single { it.key == "theme_mode" }
        assertFalse(appearanceWrite.deleted)

        coVerify(exactly = 1) { syncRepository.requestSync() }
        awaitUntil("the snapshot summary lands") { busMessages.isNotEmpty() }
        assertEquals(1, busMessages.size)
    }

    @Test
    fun `snapshot apply fans one applySettings per origin profile - base rides profile null`() = runTest(testDispatcher) {
        gateCaptureOk()
        coEvery { pluginApi.getSnapshotContent("s-mp") } returns Result.success(
            JellyPlaySnapshotContent(
                id = 2L,
                profiles = listOf(
                    // The SAME ns/key under two profiles: two distinct diff rows.
                    JellyPlaySnapshotProfile(
                        settings = listOf(entry("search", "shared_k", "base-value", updatedAt = 5_000)),
                    ),
                    JellyPlaySnapshotProfile(
                        profile = "tv",
                        settings = listOf(entry("search", "shared_k", "tv-value")),
                    ),
                ),
            ),
        )
        coEvery { pluginApi.exportSettings() } returns Result.success(exportBundle(emptyList()))
        coEvery { pluginApi.applySettings(any(), any(), any()) } returns Result.success(
            JellyPlaySettingsBatchResult(applied = listOf(JellyPlayAppliedSetting("search", "shared_k", 1, 1))),
        )
        val vm = viewModel()
        vm.start(uri = null, snapshotId = "s-mp")
        awaitUntil("the multi-profile diff stages") { vm.uiState.snapshot != null }
        collectBus()

        assertEquals(
            2,
            vm.uiState.snapshot!!.groups.namespaces.single().rows.size,
            "base and overlay rows sharing an ns/key stay distinct diff rows",
        )
        vm.toggleNamespace("search")
        vm.restoreSelected()
        awaitUntil("the snapshot apply completes") { vm.uiState.completed }

        // One batch per ORIGIN profile: base rows under profile = null, the
        // overlay rows under their own profile — never collapsed into one.
        val baseSlot = slot<List<JellyPlaySettingWrite>>()
        coVerify(exactly = 1) {
            pluginApi.applySettings(profile = null, deviceId = "device-a", capture(baseSlot))
        }
        assertEquals("shared_k", baseSlot.captured.single().key)

        val tvSlot = slot<List<JellyPlaySettingWrite>>()
        coVerify(exactly = 1) {
            pluginApi.applySettings(profile = "tv", deviceId = "device-a", capture(tvSlot))
        }
        assertEquals("shared_k", tvSlot.captured.single().key)
        assertEquals(JsonPrimitive("tv-value"), tvSlot.captured.single().value)
    }

    @Test
    fun `snapshot apply chunks at the page limit and aggregates the summary`() = runTest(testDispatcher) {
        gateCaptureOk()
        val rows = (1..250).map { entry("search", "k$it", "v$it") }
        coEvery { pluginApi.getSnapshotContent("s-big") } returns Result.success(
            JellyPlaySnapshotContent(id = 3L, profiles = listOf(JellyPlaySnapshotProfile(settings = rows))),
        )
        coEvery { pluginApi.exportSettings() } returns Result.success(exportBundle(emptyList()))
        val chunkSizes = mutableListOf<Int>()
        coEvery { pluginApi.applySettings(any(), any(), any()) } coAnswers {
            val writes = thirdArg<List<JellyPlaySettingWrite>>()
            chunkSizes += writes.size
            Result.success(
                JellyPlaySettingsBatchResult(applied = writes.map { JellyPlayAppliedSetting(it.ns, it.key, 1, 1) }),
            )
        }
        val vm = viewModel()
        vm.start(uri = null, snapshotId = "s-big")
        awaitUntil("the big diff stages") { vm.uiState.snapshot != null }
        collectBus()

        vm.toggleNamespace("search")
        vm.restoreSelected()
        awaitUntil("the chunked apply completes") { vm.uiState.completed }

        assertEquals(listOf(200, 50), chunkSizes, "250 selected writes split at the 200-write page limit")
        coVerify(exactly = 2) { pluginApi.applySettings(any(), any(), any()) }
        awaitUntil("the summary lands") { busMessages.isNotEmpty() }
        assertTrue(
            busMessages.single().contains("250"),
            "applied counts aggregate across chunks: ${busMessages.single()}",
        )
    }

    @Test
    fun `snapshot apply failure surfaces the error face and never claims success`() = runTest(testDispatcher) {
        gateCaptureOk()
        coEvery { pluginApi.applySettings(any(), any(), any()) } returns Result.failure(RuntimeException("socket boom"))
        val vm = openSnapshotVm()

        vm.toggleNamespace("search")
        vm.restoreSelected()
        awaitUntil("the failure surfaces") { vm.uiState.applyError != null }

        assertEquals("socket boom", vm.uiState.applyError)
        assertFalse(vm.uiState.applying, "a failed apply releases the busy face")
        assertFalse(vm.uiState.completed, "a failed apply must never claim success")
        coVerify(exactly = 0) { syncRepository.requestSync() }
    }

    @Test
    fun `a second restoreSelected while applying is refused - exactly one batch`() = runTest(testDispatcher) {
        gateCaptureOk()
        val applyGate = CompletableDeferred<Unit>()
        coEvery { pluginApi.applySettings(any(), any(), any()) } coAnswers {
            applyGate.await()
            Result.success(
                JellyPlaySettingsBatchResult(applied = listOf(JellyPlayAppliedSetting("search", "recents_k1", 1, 1))),
            )
        }
        val vm = openSnapshotVm()
        vm.toggleNamespace("search")

        vm.restoreSelected()
        vm.restoreSelected() // the first apply is still parked — refused synchronously
        assertTrue(vm.uiState.applying)

        applyGate.complete(Unit)
        awaitUntil("the apply completes") { vm.uiState.completed }
        coVerify(exactly = 1) { pluginApi.applySettings(any(), any(), any()) }
    }

    @Test
    fun `a failed live export read is a load error - never an empty diff`() = runTest(testDispatcher) {
        gateOpen()
        coEvery { pluginApi.getSnapshotContent("s1") } returns Result.success(
            JellyPlaySnapshotContent(
                id = 1L,
                profiles = listOf(
                    JellyPlaySnapshotProfile(settings = listOf(entry("search", "recents_k1", "x"))),
                ),
            ),
        )
        coEvery { pluginApi.exportSettings() } returns Result.failure(RuntimeException("socket"))
        val vm = viewModel()
        vm.start(uri = null, snapshotId = "s1")
        awaitUntil("the load error surfaces") { vm.uiState.loadError }

        assertEquals(WizardStep.SOURCE, vm.uiState.step)
        assertNull(vm.uiState.snapshot, "no diff may stage from an unreadable live state")
        assertNull(vm.uiState.fullRestore)
        assertTrue(vm.lastLoadErrorIsServer, "the server arm's own failure face")
        assertTrue(vm.lastLoadErrorMessage!!.contains("socket"))
    }

    @Test
    fun `a failed snapshot content read is a load error - never the degrade face`() = runTest(testDispatcher) {
        gateOpen()
        coEvery { pluginApi.getSnapshotContent("s-broken") } returns Result.failure(RuntimeException("500"))
        val vm = viewModel()
        vm.start(uri = null, snapshotId = "s-broken")
        awaitUntil("the load error surfaces") { vm.uiState.loadError }

        assertNull(vm.uiState.fullRestore, "a FAILED read is a load error, not the old-plugin degrade")
        assertNull(vm.uiState.snapshot)
        assertEquals(WizardStep.SOURCE, vm.uiState.step)
        assertTrue(vm.lastLoadErrorIsServer)
    }

    @Test
    fun `partial prefs selection never tombstones the unselected domains`() = runTest(testDispatcher) {
        gateCaptureOk()
        val vm = openSnapshotVm()

        coEvery { pluginApi.applySettings(any(), any(), any()) } returns Result.success(
            JellyPlaySettingsBatchResult(applied = listOf(JellyPlayAppliedSetting("prefs", "theme_mode", 1, 1))),
        )
        // Only "appearance" is selected — the removed live keys live in Other.
        vm.togglePrefDomain("appearance")
        vm.restoreSelected()
        awaitUntil("the snapshot apply completes") { vm.uiState.completed }

        val writesSlot = slot<List<JellyPlaySettingWrite>>()
        coVerify(exactly = 1) {
            pluginApi.applySettings(profile = null, deviceId = any(), capture(writesSlot))
        }
        val writes = writesSlot.captured
        assertTrue(writes.none { it.deleted }, "no tombstones may escape the selected scope")
        assertTrue(writes.all { it.ns == "prefs" })
    }

    @Test
    fun `old-plugin null content degrades to full restore only`() = runTest(testDispatcher) {
        gateOpen()
        coEvery { pluginApi.getSnapshotContent("s-gone") } returns Result.success(null)
        val vm = viewModel()
        vm.start(uri = null, snapshotId = "s-gone")
        awaitUntil("the degrade face stages") { vm.uiState.fullRestore != null }

        assertNull(vm.uiState.snapshot)
        gateCaptureOk()
        coEvery { pluginApi.restoreSnapshot("s-gone") } returns Result.success(
            JellyPlaySettingsBatchResult(applied = listOf(JellyPlayAppliedSetting("prefs", "k", 1, 1))),
        )
        collectBus()
        vm.restoreSelected()
        awaitUntil("the full restore completes") { vm.uiState.completed }

        coVerify(exactly = 1) { pluginApi.restoreSnapshot("s-gone") }
        coVerify(exactly = 1) { syncRepository.requestSync() }
        awaitUntil("the full-restore summary lands") { busMessages.isNotEmpty() }
        assertEquals(1, busMessages.size)
    }

    @Test
    fun `rejects map to short human buckets and surface in the summary`() = runTest(testDispatcher) {
        // The pure mapping first.
        assertEquals(RejectReason.STALE_WRITE, rejectReason("stale-write"))
        assertEquals(RejectReason.CLOCK_SKEW, rejectReason("clock-skew"))
        assertEquals(RejectReason.QUOTA_EXCEEDED, rejectReason("quota-exceeded"))
        assertEquals(RejectReason.NS_QUOTA_EXCEEDED, rejectReason("ns-quota-exceeded"))
        assertEquals(RejectReason.KEY_TOO_LARGE, rejectReason("key-too-large"))
        assertEquals(RejectReason.KEY_LIMIT_REACHED, rejectReason("key-limit-reached"))
        assertEquals(RejectReason.DEVICE_REVOKED, rejectReason("device-revoked"))
        assertEquals(RejectReason.OTHER, rejectReason("something-else"))

        // And the summary's rendered tail.
        gateCaptureOk()
        coEvery { pluginApi.getSnapshotContent("s1") } returns Result.success(
            JellyPlaySnapshotContent(
                id = 1L,
                profiles = listOf(
                    JellyPlaySnapshotProfile(settings = listOf(entry("search", "recents_k1", "x"))),
                ),
            ),
        )
        coEvery { pluginApi.exportSettings() } returns Result.success(exportBundle(emptyList()))
        coEvery { pluginApi.applySettings(any(), any(), any()) } returns Result.success(
            JellyPlaySettingsBatchResult(
                applied = listOf(JellyPlayAppliedSetting("search", "recents_k1", 1, 1)),
                rejected = listOf(
                    JellyPlayRejectedSetting("prefs", "a", "stale-write"),
                    JellyPlayRejectedSetting("prefs", "b", "stale-write"),
                    JellyPlayRejectedSetting("prefs", "c", "quota-exceeded"),
                    JellyPlayRejectedSetting("prefs", "d", "weird-reason"),
                ),
            ),
        )
        val vm = viewModel()
        vm.start(uri = null, snapshotId = "s1")
        awaitUntil("the snapshot diff stages") { vm.uiState.snapshot != null }
        collectBus()

        vm.toggleNamespace("search")
        vm.restoreSelected()
        awaitUntil("the apply completes") { vm.uiState.completed }

        awaitUntil("the summary lands on the bus") { busMessages.isNotEmpty() }
        val summary = busMessages.single()
        assertTrue(summary.contains(nameOf(Res.string.wizard_summary_rejected)), "the reject tail renders: $summary")
        assertTrue(summary.contains(nameOf(Res.string.wizard_reject_bucket_stale_write)) && summary.contains("×2"), "buckets aggregate: $summary")
        assertTrue(summary.contains(nameOf(Res.string.wizard_reject_bucket_quota)) && summary.contains("×1"))
        assertTrue(summary.contains(nameOf(Res.string.wizard_reject_bucket_other)) && summary.contains("×1"), "unknown reasons land in the Other bucket")
    }

    // ---------------------------------------------------------------- pure-model pins

    @Test
    fun `pure model - selection algebra keeps parent-child invariants`() {
        val declared = listOf(PreferenceResetCategory.APPEARANCE, PreferenceResetCategory.AUDIO)
        var selection = FileSelection()
        assertFalse(selection.allCategoriesSelected(declared))
        selection = selection.toggledAllCategories(declared)
        assertTrue(selection.allCategoriesSelected(declared))
        selection = selection.toggledCategory(PreferenceResetCategory.AUDIO)
        assertEquals(setOf(PreferenceResetCategory.APPEARANCE), selection.categories)
        selection = selection.toggledCategory(PreferenceResetCategory.AUDIO)
        assertTrue(selection.allCategoriesSelected(declared), "the child toggle re-completes the parent")
        selection = selection.toggledAllCategories(declared)
        assertTrue(selection.categories.isEmpty())

        // The external-slices parent tier: the same all-or-none algebra.
        val slices = listOf("integrations", "playlists", "widget")
        assertFalse(selection.allSlicesSelected(slices))
        selection = selection.toggledAllSlices(slices)
        assertTrue(selection.allSlicesSelected(slices))
        selection = selection.toggledSlice("widget")
        assertFalse(selection.allSlicesSelected(slices), "partial selection drops the parent's all face")
        selection = selection.toggledAllSlices(slices)
        assertTrue(selection.allSlicesSelected(slices), "toggling from partial selects ALL")
        selection = selection.toggledAllSlices(slices)
        assertTrue(selection.slices.isEmpty())

        val groups = listOf(
            SnapshotGroupDiff("appearance", "Appearance", "prefs", emptyList()),
            SnapshotGroupDiff(PrefsDomainCatalog.OTHER_DOMAIN, "Other", "prefs", emptyList()),
        )
        var snap = SnapshotSelection()
        assertFalse(snap.allDomainsSelected(groups))
        snap = snap.toggledPrefs(groups)
        assertTrue(snap.allDomainsSelected(groups))
        snap = snap.toggledDomain(PrefsDomainCatalog.OTHER_DOMAIN)
        assertFalse(snap.allDomainsSelected(groups))
        snap = snap.toggledNamespace("cw")
        assertTrue(snap.namespaces.contains("cw"))
    }

    @Test
    fun `pure model - tombstones scope to the selected groups only`() {
        val groups = SnapshotDiffGroups(
            namespaces = listOf(
                SnapshotGroupDiff(
                    "search", "search", "search",
                    listOf(SnapshotRowDiff("search", "k1", SnapshotRowKind.ADDED, JsonPrimitive("x"), null)),
                ),
            ),
            prefDomains = listOf(
                SnapshotGroupDiff(
                    "appearance", "Appearance", "prefs",
                    listOf(
                        SnapshotRowDiff("prefs", "theme_mode", SnapshotRowKind.CHANGED, JsonPrimitive("dark"), JsonPrimitive("light")),
                        SnapshotRowDiff("prefs", "gone_key", SnapshotRowKind.REMOVED, null, JsonPrimitive("v")),
                    ),
                ),
                SnapshotGroupDiff(
                    PrefsDomainCatalog.OTHER_DOMAIN, "Other", "prefs",
                    listOf(SnapshotRowDiff("prefs", "gone_other", SnapshotRowKind.REMOVED, null, JsonPrimitive("v"))),
                ),
            ),
        )

        // Selecting appearance alone: theme_mode write + gone_key tombstone,
        // nothing from search or Other — one batch under the base profile.
        val batches = buildSnapshotWrites(
            groups,
            SnapshotSelection(domains = setOf("appearance")),
            now = 123L,
        )
        assertEquals(1, batches.size)
        assertEquals("", batches.single().profile, "profile-less rows ride the base batch")
        val writes = batches.single().writes
        assertEquals(
            listOf(
                Triple("prefs", "theme_mode", false),
                Triple("prefs", "gone_key", true),
            ),
            writes.map { Triple(it.ns, it.key, it.deleted) },
        )
        assertTrue(writes.all { it.updatedAt == 124L }, "a batch without older rows stamps now+1")
        assertEquals(JsonNull, writes.first { it.deleted }.value)
    }

    @Test
    fun `pure model - the device profile is part of the diff identity`() {
        val snapshotRows = listOf(
            entry("prefs", "shared_k", "base-in", updatedAt = 100),
            entry("prefs", "shared_k", "tv-in", profile = "tv", updatedAt = 200),
        )
        val liveRows = listOf(
            entry("prefs", "shared_k", "base-live", updatedAt = 50),
            entry("prefs", "shared_k", "tv-live", profile = "tv", updatedAt = 300),
        )
        val groups = buildSnapshotDiffGroups(snapshotRows, liveRows) { it }

        val rows = groups.prefDomains.single().rows
        assertEquals(2, rows.size, "base and overlay rows sharing an ns/key are DISTINCT diff rows")
        val base = rows.single { it.profile.isEmpty() }
        val tv = rows.single { it.profile == "tv" }
        assertEquals(SnapshotRowKind.CHANGED, base.kind)
        assertEquals(SnapshotRowKind.CHANGED, tv.kind)
        assertEquals(100, base.snapshotUpdatedAt)
        assertEquals(50, base.liveUpdatedAt)
        assertEquals(200, tv.snapshotUpdatedAt)
        assertEquals(300, tv.liveUpdatedAt)
    }

    @Test
    fun `pure model - per-profile batches with LWW stamps clearing the newest row in scope`() {
        val groups = SnapshotDiffGroups(
            namespaces = listOf(
                SnapshotGroupDiff(
                    "search", "search", "search",
                    listOf(
                        SnapshotRowDiff(
                            "search", "future_k", SnapshotRowKind.ADDED,
                            JsonPrimitive("b"), null,
                            profile = "", snapshotUpdatedAt = 5_000,
                        ),
                        SnapshotRowDiff(
                            "search", "tv_k", SnapshotRowKind.ADDED,
                            JsonPrimitive("t"), null,
                            profile = "tv",
                        ),
                    ),
                ),
            ),
            prefDomains = emptyList(),
        )

        val batches = buildSnapshotWrites(groups, SnapshotSelection(namespaces = setOf("search")), now = 1_000)
        assertEquals(setOf("", "tv"), batches.map { it.profile }.toSet(), "one batch per origin profile")
        val base = batches.first { it.profile.isEmpty() }
        assertEquals(
            5_001,
            base.writes.single().updatedAt,
            "a future-dated snapshot row must be cleared by exactly 1 (not lost LWW to the behind wall clock)",
        )
        val tv = batches.first { it.profile == "tv" }
        assertEquals(1_001, tv.writes.single().updatedAt, "a batch without older rows stamps now+1")
    }

    @Test
    fun `captureSafetySnapshot - a pre-wave null skip is quiet and only a failed capture warns`() = runTest {
        val api = mockk<com.raulshma.jellyplay.core.network.api.JellyPlaySettingsSyncRoutes>()
        val store = mockk<JellyPlayPluginStatusStore>()
        every { store.status } returns MutableStateFlow(JellyPlayPluginStatus.AVAILABLE)
        every { store.hasFeature(JellyPlayPluginFeatures.SettingsSync) } returns true

        coEvery { api.createSnapshot() } returns Result.success(null)
        assertTrue(
            captureSafetySnapshot(api, store),
            "the pre-restore-points plugin's null 404 is a quiet skip, not a failure",
        )

        coEvery { api.createSnapshot() } returns Result.failure(RuntimeException("500"))
        assertFalse(captureSafetySnapshot(api, store), "a FAILED capture stays the warn arm")
    }
}
