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
import com.raulshma.jellyplay.core.datastore.identity.PersistedIdentity
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
import com.raulshma.jellyplay.core.testfixtures.MainDispatcherRule
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
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Rule

/**
 * The unified restore wizard's model gate: source loading + degrade, the file
 * diff, the snapshot diff classification (changed/added/removed + prefs
 * sub-grouping incl. the "Other" bucket), the two-tier selection consistency,
 * apply-path branching (file vs snapshot vs old-plugin full restore), the
 * reject-reason mapping, cross-account detection, the best-effort pre-apply
 * capture (a miss warns and never blocks), and the secrets unlock/apply paths.
 *
 * Harness = stores mockk'd with REAL `MutableStateFlow` slices, a REAL
 * [PreferenceSnapshotReader] over the mock bundle, StandardTestDispatcher +
 * setMain/resetMain, `awaitUntil` pumping across the real-thread hops, crossed
 * with the [JellyPlaySyncViewModelTest] api-mockk idiom. Wizard texts resolve
 * through an injected seam so the bus-message assertions stay deterministic
 * without a resource loader.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RestoreWizardViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
    private lateinit var shortcutsStore: com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsStore
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
    private val identity = MutableStateFlow<PersistedIdentity>(PersistedIdentity.ActiveSession("srv-1", "user-1"))

    /** The real bus under test — its messages are drained by a collector. */
    private lateinit var bus: UserMessageBus
    private val busMessages = mutableListOf<String>()

    @BeforeTest
    fun setUp() {
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
        shortcutsStore = mockk(relaxed = true)
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
        every { shortcutsStore.shortcuts } returns MutableStateFlow(com.raulshma.jellyplay.core.datastore.shortcuts.ShortcutsSlice())
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

    private fun viewModel(
        assembler: SecretsBackupAssembler? = null,
        /** Null builds the file-only graph (the server arm degrades off). */
        client: JellyPlaySettingsSyncRoutes? = pluginApi,
    ): RestoreWizardViewModel {
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
            shortcuts = shortcutsStore,
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
            pluginApiClient = client,
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

    /** The staged file-diff face (the ONE-slot read the assertions share). */
    private fun RestoreWizardUiState.stagedFile(): FileRestoreState? =
        (diffStage as? DiffStage.File)?.state

    /** The staged snapshot-diff face (the ONE-slot read the assertions share). */
    private fun RestoreWizardUiState.stagedSnapshot(): SnapshotRestoreState? =
        (diffStage as? DiffStage.Snapshot)?.state

    private suspend fun TestScope.loadedWithFile(
        json: String,
        uri: String = "backup:v2",
        assembler: SecretsBackupAssembler? = null,
    ): RestoreWizardViewModel {
        stubImport(uri, json)
        val vm = viewModel(assembler).also { it.loadBackupFile(uri) }
        awaitUntil("file diff for $uri") { vm.uiState.diffStage is DiffStage.File || vm.uiState.loadFailure != null }
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
        awaitUntil("the snapshot diff stages") { vm.uiState.diffStage is DiffStage.Snapshot }
        return vm
    }

    // ---------------------------------------------------------------- source loading + degrade

    @Test
    fun `gate closed degrades the server arm quietly without touching the api`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = viewModel()
        vm.start(uri = null, snapshotId = null)
        advanceUntilIdle()

        assertEquals(
            SourcePhase.Unavailable(SourceUnavailableReason.GATE_CLOSED),
            vm.uiState.sourcePhase,
            "the closed gate is declared data — the screen keeps rendering nothing for it",
        )
        coVerify(exactly = 0) { pluginApi.getSnapshots() }
    }

    @Test
    fun `gate open loads the restore points and an old-plugin 404 degrades to unavailable`() = runTest(mainDispatcherRule.testDispatcher) {
        gateOpen()
        coEvery { pluginApi.getSnapshots() } returns Result.success(
            listOf(JellyPlaySnapshot(id = 1L, createdAt = 1_700_000_000_000, origin = "manual", keys = 4)),
        )
        val vm = viewModel()
        vm.start(uri = null, snapshotId = null)
        awaitUntil("the restore points load") { vm.uiState.sourcePhase is SourcePhase.Available }

        val available = assertIs<SourcePhase.Available>(vm.uiState.sourcePhase)
        assertEquals(1, available.snapshots.size)

        // A 404 (null payload) degrades to the same quiet face — the OLD_PLUGIN cause is data now.
        coEvery { pluginApi.getSnapshots() } returns Result.success(null)
        vm.refreshSource()
        awaitUntil("the old-plugin 404 degrades") {
            (vm.uiState.sourcePhase as? SourcePhase.Unavailable)?.reason == SourceUnavailableReason.OLD_PLUGIN
        }
    }

    @Test
    fun `source unavailable carries each cause - gate closed, no client, old plugin, read failed`() =
        runTest(mainDispatcherRule.testDispatcher) {
            // 1. gate closed (the plugin-gating contract).
            val closed = viewModel()
            closed.start(uri = null, snapshotId = null)
            advanceUntilIdle()
            assertEquals(
                SourceUnavailableReason.GATE_CLOSED,
                (closed.uiState.sourcePhase as SourcePhase.Unavailable).reason,
            )

            // 2. no client (the file-only graph — the server arm degrades off).
            val clientless = viewModel(client = null)
            clientless.start(uri = null, snapshotId = null)
            advanceUntilIdle()
            assertEquals(
                SourceUnavailableReason.NO_CLIENT,
                (clientless.uiState.sourcePhase as SourcePhase.Unavailable).reason,
            )

            // 3. old plugin (the success-null 404 payload).
            gateOpen()
            coEvery { pluginApi.getSnapshots() } returns Result.success(null)
            val oldPlugin = viewModel()
            oldPlugin.start(uri = null, snapshotId = null)
            awaitUntil("the old-plugin face lands") {
                (oldPlugin.uiState.sourcePhase as? SourcePhase.Unavailable)?.reason == SourceUnavailableReason.OLD_PLUGIN
            }

            // 4. read failed (transport / plugin 5xx) — distinguishable from the 404 now.
            coEvery { pluginApi.getSnapshots() } returns Result.failure(RuntimeException("500"))
            val failedRead = viewModel()
            failedRead.start(uri = null, snapshotId = null)
            awaitUntil("the read-failed face lands") {
                (failedRead.uiState.sourcePhase as? SourcePhase.Unavailable)?.reason ==
                    SourceUnavailableReason.READ_FAILED
            }
        }

    @Test
    fun `a nav-carried snapshot id jumps straight into the snapshot diff`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = openSnapshotVm()

        assertEquals(WizardStep.DIFF, vm.uiState.step)
        val stage = assertIs<DiffStage.Snapshot>(vm.uiState.diffStage, "the ONE slot holds the snapshot face alone")
        assertTrue(stage.state.groups.totalGroups > 0)
    }

    @Test
    fun `staging a backup file after a snapshot diff leaves exactly one face`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = openSnapshotVm()
        assertIs<DiffStage.Snapshot>(vm.uiState.diffStage)

        // The bug this slot fixed: the former parallel nullable faces let the
        // file stage ride BESIDE the stale snapshot diff (loadBackupFile never
        // cleared the others), leaving two non-null faces with
        // restoreSelected's when-chain silently preferring the file arm.
        stubImport("backup:after-snapshot", appearanceBackupJson())
        vm.loadBackupFile("backup:after-snapshot")
        awaitUntil("the file stage replaces the snapshot") { vm.uiState.diffStage is DiffStage.File }

        // Exactly one face: the slot IS the face — the snapshot arm is
        // structurally gone, not merely shadowed.
        val stage = assertIs<DiffStage.File>(vm.uiState.diffStage)
        assertEquals(WizardStep.DIFF, vm.uiState.step)
        assertEquals(2, stage.state.schemaVersion)
        assertEquals(SecretsPhase.Hidden, stage.state.secrets, "the snapshot's secrets face did not leak into the file stage")

        // And the apply follows the slot: the file arm runs, the snapshot arm
        // never does.
        gateCaptureOk()
        vm.toggle(WizardSelectionTarget.Category(PreferenceResetCategory.APPEARANCE))
        vm.restoreSelected()
        awaitUntil("the file arm applies") { vm.uiState.applyPhase is ApplyPhase.Completed }
        coVerify(exactly = 1) { userPreferencesStore.restoreV2Categories(any(), any(), any(), any()) }
        coVerify(exactly = 0) { pluginApi.applySettings(any(), any(), any()) }
    }

    // ---------------------------------------------------------------- file diff

    @Test
    fun `v2 backup loads the file diff with current-version flags`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedWithFile(appearanceBackupJson())

        assertNull(vm.uiState.loadFailure)
        val file = vm.uiState.stagedFile()!!
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
    fun `v2 security slice flags the lock-config opt-in`() = runTest(mainDispatcherRule.testDispatcher) {
        val json = v2Json(
            slices = mapOf(
                BackupSliceKey.SECURITY to sliceElement(
                    SecuritySlice.serializer(),
                    SecuritySlice(pinLockEnabled = true, pinHash = "imported-hash"),
                ),
            ),
        )
        val vm = loadedWithFile(json)

        assertTrue(vm.uiState.stagedFile()!!.hasSecuritySensitive)
    }

    @Test
    fun `future schema version flags a mismatch but still previews`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedWithFile(
            v2Json(schemaVersion = SettingsBackup.CURRENT_SCHEMA_VERSION + 1),
            uri = "backup:future",
        )

        val file = vm.uiState.stagedFile()!!
        assertTrue(file.versionMismatch)
        assertEquals(SettingsBackup.CURRENT_SCHEMA_VERSION + 1, file.schemaVersion)
    }

    @Test
    fun `unopenable backup file surfaces the inline load error and allows a retry`() = runTest(mainDispatcherRule.testDispatcher) {
        coEvery { settingsBackupIo.readImportPayload("backup:missing") } returns null
        val vm = viewModel()
        advanceUntilIdle()

        vm.loadBackupFile("backup:missing")
        awaitUntil("the load failure surfaces") { vm.uiState.loadFailure != null }

        assertNull(vm.uiState.diffStage, "nothing may be staged from an unreadable file")
        // Presence, message and arm identity land ATOMICALLY in one object —
        // the side-channel pair this replaced could not promise that.
        assertEquals(
            SourceLoadFailure.File("Cannot open backup file"),
            vm.uiState.loadFailure,
            "the file arm's failure is one co-located object",
        )

        // A retry with the SAME uri re-reads (the stage was cleared on failure).
        stubImport("backup:missing", appearanceBackupJson())
        vm.loadBackupFile("backup:missing")
        awaitUntil("the retry stages") { vm.uiState.diffStage is DiffStage.File }
        assertNull(vm.uiState.loadFailure)
    }

    @Test
    fun `legacy v0 backups are rejected by the parser into the load error`() = runTest(mainDispatcherRule.testDispatcher) {
        stubImport("backup:v0", """{"schemaVersion":0}""")
        val vm = viewModel()
        vm.loadBackupFile("backup:v0")
        awaitUntil("the rejection surfaces") { vm.uiState.loadFailure != null }

        assertNull(vm.uiState.diffStage)
        val failure = assertIs<SourceLoadFailure.File>(vm.uiState.loadFailure)
        assertTrue(failure.message.contains("schemaVersion"))
    }

    // ---------------------------------------------------------------- wave-2 slices + extras

    @Test
    fun `wave-2 slices in the backup stage external cards with human labels`() = runTest(mainDispatcherRule.testDispatcher) {
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

        val cards = vm.uiState.stagedFile()!!.externalSlices
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
        vm.toggle(WizardSelectionTarget.Slice(BackupSliceKey.INTEGRATIONS))
        var selection = vm.uiState.stagedFile()!!.selection
        assertEquals(setOf(BackupSliceKey.INTEGRATIONS), selection.slices)
        assertFalse(selection.allSlicesSelected(offered))

        vm.toggle(WizardSelectionTarget.AllSlices)
        selection = vm.uiState.stagedFile()!!.selection
        assertTrue(selection.allSlicesSelected(offered))
        assertEquals(offered.toSet(), selection.slices)

        vm.toggle(WizardSelectionTarget.AllSlices)
        assertTrue(vm.uiState.stagedFile()!!.selection.slices.isEmpty())
    }

    @Test
    fun `extras card diffs the app-runtime block`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedWithFile(
            v2Json(extras = AppRuntimeState(favoriteChannels = setOf("chan-1"))),
        )

        val extras = vm.uiState.stagedFile()!!.extras!!
        assertTrue(extras.changed.isNotEmpty(), "the incoming favorite channel must diff against the empty live block")
        assertTrue(extras.changed.any { it.factoryValue.contains("chan-1") })
    }

    // ---------------------------------------------------------------- selection consistency

    @Test
    fun `selection toggles keep the parent tier consistent`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedWithFile(appearanceBackupJson())
        val declared = PreferenceCategoryViews.map { it.category }

        // Child toggle lands in the selection…
        vm.toggle(WizardSelectionTarget.Category(PreferenceResetCategory.APPEARANCE))
        var selection = vm.uiState.stagedFile()!!.selection
        assertTrue(PreferenceResetCategory.APPEARANCE in selection.categories)
        assertFalse(selection.allCategoriesSelected(declared))

        // …the parent toggle selects ALL children from a partial state…
        vm.toggle(WizardSelectionTarget.AllCategories)
        selection = vm.uiState.stagedFile()!!.selection
        assertTrue(selection.allCategoriesSelected(declared))
        assertEquals(declared.toSet(), selection.categories)

        // …and clears them all from the fully-selected state.
        vm.toggle(WizardSelectionTarget.AllCategories)
        selection = vm.uiState.stagedFile()!!.selection
        assertTrue(selection.categories.isEmpty())

        // Extras + slices toggle independently; the confirm count follows.
        vm.toggle(WizardSelectionTarget.Extras)
        vm.toggle(WizardSelectionTarget.Slice(BackupSliceKey.WIDGET))
        vm.toggle(WizardSelectionTarget.Slice(BackupSliceKey.WIDGET))
        selection = vm.uiState.stagedFile()!!.selection
        assertTrue(selection.extras)
        assertTrue(BackupSliceKey.WIDGET !in selection.slices)
        assertEquals(1, vm.selectedGroupCount())
    }

    @Test
    fun `snapshot selection keeps the prefs parent aligned with its domains`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = openSnapshotVm()

        vm.toggle(WizardSelectionTarget.PrefDomain("appearance"))
        var selection = vm.uiState.stagedSnapshot()!!.selection
        assertTrue("appearance" in selection.domains)
        assertFalse(selection.allDomainsSelected(vm.uiState.stagedSnapshot()!!.groups.prefDomains))

        vm.toggle(WizardSelectionTarget.Prefs)
        selection = vm.uiState.stagedSnapshot()!!.selection
        assertTrue(selection.allDomainsSelected(vm.uiState.stagedSnapshot()!!.groups.prefDomains))

        vm.toggle(WizardSelectionTarget.Prefs)
        selection = vm.uiState.stagedSnapshot()!!.selection
        assertTrue(selection.domains.isEmpty())

        vm.toggle(WizardSelectionTarget.Namespace("search"))
        assertTrue("search" in vm.uiState.stagedSnapshot()!!.selection.namespaces)
    }

    // ---------------------------------------------------------------- file apply

    @Test
    fun `file apply fans to the store seams per selection and syncs after`() = runTest(mainDispatcherRule.testDispatcher) {
        gateCaptureOk()
        val vm = loadedWithFile(appearanceBackupJson())
        collectBus()

        vm.toggle(WizardSelectionTarget.Category(PreferenceResetCategory.APPEARANCE))
        vm.toggle(WizardSelectionTarget.Slice(BackupSliceKey.WIDGET))
        vm.toggle(WizardSelectionTarget.Extras)
        vm.restoreSelected()
        awaitUntil("the file apply completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

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
        assertEquals(ApplyPhase.Completed, vm.uiState.applyPhase)
    }

    @Test
    fun `completed apply phase consumes one-shot back to idle`() = runTest(mainDispatcherRule.testDispatcher) {
        gateCaptureOk()
        val vm = loadedWithFile(appearanceBackupJson())
        vm.toggle(WizardSelectionTarget.Category(PreferenceResetCategory.APPEARANCE))
        vm.restoreSelected()
        awaitUntil("the apply completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

        vm.consumeCompleted()
        assertEquals(ApplyPhase.Idle, vm.uiState.applyPhase, "the success signal is spent by the consume")

        // A second consume is inert — the one-shot signal cannot re-fire.
        vm.consumeCompleted()
        assertEquals(ApplyPhase.Idle, vm.uiState.applyPhase)
    }

    @Test
    fun `empty selection applies nothing and never completes`() = runTest(mainDispatcherRule.testDispatcher) {
        gateCaptureOk()
        val vm = loadedWithFile(appearanceBackupJson())

        vm.restoreSelected()
        advanceUntilIdle()

        coVerify(exactly = 0) { userPreferencesStore.restoreV2Categories(any(), any(), any(), any()) }
        coVerify(exactly = 1) { pluginApi.createSnapshot() }
        assertEquals(ApplyPhase.Idle, vm.uiState.applyPhase, "an empty selection settles back to idle, never completes")
    }

    @Test
    fun `pre-apply capture failure warns on the bus and never blocks the apply`() = runTest(mainDispatcherRule.testDispatcher) {
        gateOpen()
        coEvery { pluginApi.createSnapshot() } returns Result.failure(IllegalStateException("500"))
        val vm = loadedWithFile(appearanceBackupJson())
        collectBus()

        vm.toggle(WizardSelectionTarget.Category(PreferenceResetCategory.APPEARANCE))
        vm.restoreSelected()
        awaitUntil("the apply still completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

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
    fun `closed gate skips the capture entirely`() = runTest(mainDispatcherRule.testDispatcher) {
        // Defaults: probe UNAVAILABLE — the gate is closed.
        val vm = loadedWithFile(appearanceBackupJson())
        vm.toggle(WizardSelectionTarget.Category(PreferenceResetCategory.APPEARANCE))

        vm.restoreSelected()
        awaitUntil("the apply completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

        coVerify(exactly = 0) { pluginApi.createSnapshot() }
    }

    @Test
    fun `security opt-in rides the categories restore`() = runTest(mainDispatcherRule.testDispatcher) {
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

        vm.toggle(WizardSelectionTarget.Category(PreferenceResetCategory.SECURITY))
        vm.toggle(WizardSelectionTarget.SecuritySensitive)
        vm.restoreSelected()
        awaitUntil("the apply completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

        coVerify(exactly = 1) {
            userPreferencesStore.restoreV2Categories(any(), any(), eq(true), eq(false))
        }
    }

    // ---------------------------------------------------------------- cross-account

    @Test
    fun `origin ids from another account warn, own or absent stay silent`() = runTest(mainDispatcherRule.testDispatcher) {
        val foreign = loadedWithFile(
            v2Json(originUserId = "user-2", originServerId = "srv-1"),
            uri = "backup:foreign",
        )
        assertTrue(foreign.uiState.stagedFile()!!.crossAccount)

        val own = loadedWithFile(
            v2Json(originUserId = "user-1", originServerId = "srv-1"),
            uri = "backup:own",
        )
        assertFalse(own.uiState.stagedFile()!!.crossAccount)

        val unstamped = loadedWithFile(appearanceBackupJson(), uri = "backup:unstamped")
        assertFalse(unstamped.uiState.stagedFile()!!.crossAccount)
    }

    // ---------------------------------------------------------------- secrets

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
    fun `backup with secrets stages the locked card and unlock surfaces the counts`() = runTest(mainDispatcherRule.testDispatcher) {
        val assembler = mockk<SecretsBackupAssembler>(relaxed = true)
        every { assembler.summarize(any()) } returns SecretsRestoreSummary(
            servers = 2, arrServers = 1, subtitleProviders = 0, hasSeerr = false,
        )
        val vm = loadedWithFile(
            v2Json(secrets = secretsEnvelope("open-sesame-9")),
            uri = "backup:secrets",
            assembler = assembler,
        )
        val file = vm.uiState.stagedFile()!!
        assertEquals(SecretsPhase.Locked, file.secrets, "locked until the passphrase unlocks it")

        vm.unlockSecrets("open-sesame-9".toCharArray())
        awaitUntil("the unlock settles") { vm.uiState.stagedFile()?.secrets is SecretsPhase.Unlocked }

        assertEquals(2, vm.uiState.stagedFile()?.secrets?.summary?.servers)
        assertEquals(1, vm.uiState.stagedFile()?.secrets?.summary?.arrServers)
        coVerify(exactly = 1) { assembler.summarize(any()) }
    }

    @Test
    fun `unlock with the wrong passphrase flags the inline error and stays locked`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedWithFile(
            v2Json(secrets = secretsEnvelope("open-sesame-9")),
            uri = "backup:secrets",
        )

        vm.unlockSecrets("not-the-passphrase".toCharArray())
        awaitUntil("the inline error surfaces") { vm.uiState.stagedFile()?.secrets is SecretsPhase.UnlockFailed }

        assertEquals(
            SecretsPhase.UnlockFailed,
            vm.uiState.stagedFile()?.secrets,
            "a failed unlock must not stage anything — the retry face is the whole phase",
        )
    }

    @Test
    fun `applySecrets fans the unlocked payload through the assembler and a locked apply is a no-op`() = runTest(mainDispatcherRule.testDispatcher) {
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
        awaitUntil("the unlock settles") { vm.uiState.stagedFile()?.secrets is SecretsPhase.Unlocked }
        collectBus()
        vm.applySecrets()
        awaitUntil("the secrets apply posts") { busMessages.isNotEmpty() }

        coVerify(exactly = 1) { assembler.apply(any()) }

        // A successful apply SPENDS the payload: a second tap is a no-op,
        // never a second write of the decrypted copy.
        vm.applySecrets()
        advanceUntilIdle()
        coVerify(exactly = 1) { assembler.apply(any()) }
        assertEquals(ApplyPhase.Idle, vm.uiState.applyPhase, "the secrets apply never rides the restore ladder")
    }

    @Test
    fun `unlockSecrets zeroes the passphrase material`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = loadedWithFile(
            v2Json(secrets = secretsEnvelope("open-sesame-9")),
            uri = "backup:secrets",
        )

        val passphrase = "open-sesame-9".toCharArray()
        vm.unlockSecrets(passphrase)
        awaitUntil("the unlock settles") {
            val phase = vm.uiState.stagedFile()?.secrets
            phase is SecretsPhase.Unlocked || phase is SecretsPhase.UnlockFailed
        }

        assertTrue(passphrase.all { it == '\u0000' }, "the passphrase must be zeroed after the attempt")
    }

    @Test
    fun `unlockSecrets zeroes the passphrase on the early returns`() = runTest(mainDispatcherRule.testDispatcher) {
        // Early return 1: the staged file carries NO secrets envelope.
        val vm = loadedWithFile(appearanceBackupJson())
        val noEnvelope = "zero-me-now-1".toCharArray()
        vm.unlockSecrets(noEnvelope)

        assertTrue(noEnvelope.all { it == '\u0000' }, "the no-envelope return zeroes the passphrase")
        assertFalse(vm.uiState.stagedFile()!!.secrets is SecretsPhase.Unlocking)

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
            awaitUntil("the secrets file stages") { vm.uiState.stagedFile()?.secrets == SecretsPhase.Locked }

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
    fun `a rapid double confirmation queues only one KDF run`() = runTest(mainDispatcherRule.testDispatcher) {
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
            // phase is already up — not queue a second 600k KDF run.
            vm.unlockSecrets(second)

            assertTrue(second.all { it == '\u0000' }, "the refused passphrase is zeroed before returning")
            awaitUntil("the first KDF enters") { entries.get() == 1 }

            entryGate.complete(Unit)
            awaitUntil("the single unlock settles") { first.all { it == '\u0000' } }
            assertEquals(1, entries.get(), "two rapid confirmations must not queue two KDF runs")
            assertTrue(vm.uiState.stagedFile()!!.secrets is SecretsPhase.Unlocked)
        } finally {
            unmockkObject(BackupSecretsCodec)
        }
    }

    @Test
    fun `an unlock settling after a re-stage is discarded - no summary and its apply is a no-op`() =
        runTest(mainDispatcherRule.testDispatcher) {
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
                awaitUntil("file A stages") { vm.uiState.stagedFile()?.secrets == SecretsPhase.Locked }

                val passA = "open-sesame-9".toCharArray()
                vm.unlockSecrets(passA)

                // While A's decrypt is parked, re-stage a DIFFERENT source:
                // file B carries no secrets at all.
                stubImport("backup:B", appearanceBackupJson())
                vm.loadBackupFile("backup:B")
                awaitUntil("file B stages") { vm.uiState.stagedFile()?.secrets == SecretsPhase.Hidden }
                assertFalse(vm.uiState.stagedFile()!!.secrets is SecretsPhase.Unlocking)

                entryGate.complete(Unit)
                awaitUntil("the parked decrypt settles") { passA.all { it == '\u0000' } }

                val file = vm.uiState.stagedFile()!!
                assertEquals(
                    SecretsPhase.Hidden,
                    file.secrets,
                    "no secrets card for the discarded envelope — the stale unlock must not resurrect a summary for A",
                )

                vm.applySecrets()
                advanceUntilIdle()
                coVerify(exactly = 0) { assembler.apply(any()) }
            } finally {
                unmockkObject(BackupSecretsCodec)
            }
        }

    // ---------------------------------------------------------------- snapshot diff + apply

    @Test
    fun `snapshot diff classifies changed, added and removed and sub-groups prefs by domain`() = runTest(mainDispatcherRule.testDispatcher) {
        val vm = openSnapshotVm()

        val groups = vm.uiState.stagedSnapshot()!!.groups
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
    fun `snapshot apply builds one base-profile batch with fresh stamps, tombstones, and syncs after`() = runTest(mainDispatcherRule.testDispatcher) {
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

        vm.toggle(WizardSelectionTarget.Namespace("search"))
        vm.toggle(WizardSelectionTarget.PrefDomain("appearance"))
        vm.restoreSelected()
        awaitUntil("the snapshot apply completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

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
    fun `snapshot apply fans one applySettings per origin profile - base rides profile null`() = runTest(mainDispatcherRule.testDispatcher) {
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
        awaitUntil("the multi-profile diff stages") { vm.uiState.diffStage is DiffStage.Snapshot }
        collectBus()

        assertEquals(
            2,
            vm.uiState.stagedSnapshot()!!.groups.namespaces.single().rows.size,
            "base and overlay rows sharing an ns/key stay distinct diff rows",
        )
        vm.toggle(WizardSelectionTarget.Namespace("search"))
        vm.restoreSelected()
        awaitUntil("the snapshot apply completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

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
    fun `snapshot apply chunks at the page limit and aggregates the summary`() = runTest(mainDispatcherRule.testDispatcher) {
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
        awaitUntil("the big diff stages") { vm.uiState.diffStage is DiffStage.Snapshot }
        collectBus()

        vm.toggle(WizardSelectionTarget.Namespace("search"))
        vm.restoreSelected()
        awaitUntil("the chunked apply completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

        assertEquals(listOf(200, 50), chunkSizes, "250 selected writes split at the 200-write page limit")
        coVerify(exactly = 2) { pluginApi.applySettings(any(), any(), any()) }
        awaitUntil("the summary lands") { busMessages.isNotEmpty() }
        assertTrue(
            busMessages.single().contains("250"),
            "applied counts aggregate across chunks: ${busMessages.single()}",
        )
    }

    @Test
    fun `snapshot apply failure surfaces the error face and never claims success`() = runTest(mainDispatcherRule.testDispatcher) {
        gateCaptureOk()
        coEvery { pluginApi.applySettings(any(), any(), any()) } returns Result.failure(RuntimeException("socket boom"))
        val vm = openSnapshotVm()

        vm.toggle(WizardSelectionTarget.Namespace("search"))
        vm.restoreSelected()
        awaitUntil("the failure surfaces") { vm.uiState.applyPhase is ApplyPhase.Failed }

        assertEquals(
            ApplyPhase.Failed("socket boom"),
            vm.uiState.applyPhase,
            "the failure face carries the message — and it is neither the busy nor the success face",
        )
        coVerify(exactly = 0) { syncRepository.requestSync() }
    }

    @Test
    fun `a second restoreSelected while applying is refused - exactly one batch`() = runTest(mainDispatcherRule.testDispatcher) {
        gateCaptureOk()
        val applyGate = CompletableDeferred<Unit>()
        coEvery { pluginApi.applySettings(any(), any(), any()) } coAnswers {
            applyGate.await()
            Result.success(
                JellyPlaySettingsBatchResult(applied = listOf(JellyPlayAppliedSetting("search", "recents_k1", 1, 1))),
            )
        }
        val vm = openSnapshotVm()
        vm.toggle(WizardSelectionTarget.Namespace("search"))

        vm.restoreSelected()
        vm.restoreSelected() // the first apply is still parked — refused synchronously
        assertTrue(vm.uiState.applyPhase is ApplyPhase.Applying)

        applyGate.complete(Unit)
        awaitUntil("the apply completes") { vm.uiState.applyPhase is ApplyPhase.Completed }
        coVerify(exactly = 1) { pluginApi.applySettings(any(), any(), any()) }
    }

    @Test
    fun `a failed live export read is a load error - never an empty diff`() = runTest(mainDispatcherRule.testDispatcher) {
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
        awaitUntil("the load error surfaces") { vm.uiState.loadFailure != null }

        assertEquals(WizardStep.SOURCE, vm.uiState.step)
        assertNull(vm.uiState.diffStage, "no diff may stage from an unreadable live state")
        // Presence, message and arm identity land ATOMICALLY in one object —
        // the side-channel pair this replaced split them across two writes.
        assertEquals(
            SourceLoadFailure.Server("socket"),
            vm.uiState.loadFailure,
            "the server arm's own failure face, co-located in one object",
        )
    }

    @Test
    fun `a failed snapshot content read is a load error - never the degrade face`() = runTest(mainDispatcherRule.testDispatcher) {
        gateOpen()
        coEvery { pluginApi.getSnapshotContent("s-broken") } returns Result.failure(RuntimeException("500"))
        val vm = viewModel()
        vm.start(uri = null, snapshotId = "s-broken")
        awaitUntil("the load error surfaces") { vm.uiState.loadFailure != null }

        assertNull(vm.uiState.diffStage, "a FAILED read is a load error, not the old-plugin degrade")
        assertEquals(WizardStep.SOURCE, vm.uiState.step)
        assertEquals(SourceLoadFailure.Server("500"), vm.uiState.loadFailure)
    }

    @Test
    fun `partial prefs selection never tombstones the unselected domains`() = runTest(mainDispatcherRule.testDispatcher) {
        gateCaptureOk()
        val vm = openSnapshotVm()

        coEvery { pluginApi.applySettings(any(), any(), any()) } returns Result.success(
            JellyPlaySettingsBatchResult(applied = listOf(JellyPlayAppliedSetting("prefs", "theme_mode", 1, 1))),
        )
        // Only "appearance" is selected — the removed live keys live in Other.
        vm.toggle(WizardSelectionTarget.PrefDomain("appearance"))
        vm.restoreSelected()
        awaitUntil("the snapshot apply completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

        val writesSlot = slot<List<JellyPlaySettingWrite>>()
        coVerify(exactly = 1) {
            pluginApi.applySettings(profile = null, deviceId = any(), capture(writesSlot))
        }
        val writes = writesSlot.captured
        assertTrue(writes.none { it.deleted }, "no tombstones may escape the selected scope")
        assertTrue(writes.all { it.ns == "prefs" })
    }

    @Test
    fun `old-plugin null content degrades to full restore only`() = runTest(mainDispatcherRule.testDispatcher) {
        gateOpen()
        coEvery { pluginApi.getSnapshotContent("s-gone") } returns Result.success(null)
        val vm = viewModel()
        vm.start(uri = null, snapshotId = "s-gone")
        awaitUntil("the degrade face stages") { vm.uiState.diffStage is DiffStage.Full }

        assertIs<DiffStage.Full>(vm.uiState.diffStage, "the degrade face holds the slot alone")
        gateCaptureOk()
        coEvery { pluginApi.restoreSnapshot("s-gone") } returns Result.success(
            JellyPlaySettingsBatchResult(applied = listOf(JellyPlayAppliedSetting("prefs", "k", 1, 1))),
        )
        collectBus()
        vm.restoreSelected()
        awaitUntil("the full restore completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

        coVerify(exactly = 1) { pluginApi.restoreSnapshot("s-gone") }
        coVerify(exactly = 1) { syncRepository.requestSync() }
        awaitUntil("the full-restore summary lands") { busMessages.isNotEmpty() }
        assertEquals(1, busMessages.size)
    }

    @Test
    fun `rejects map to short human buckets and surface in the summary`() = runTest(mainDispatcherRule.testDispatcher) {
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
        awaitUntil("the snapshot diff stages") { vm.uiState.diffStage is DiffStage.Snapshot }
        collectBus()

        vm.toggle(WizardSelectionTarget.Namespace("search"))
        vm.restoreSelected()
        awaitUntil("the apply completes") { vm.uiState.applyPhase is ApplyPhase.Completed }

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
    fun `captureSafetySnapshot - an old-plugin null skip is quiet and only a failed capture warns`() = runTest {
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
