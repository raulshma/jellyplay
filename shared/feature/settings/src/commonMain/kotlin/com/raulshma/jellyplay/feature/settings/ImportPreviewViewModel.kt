package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.datastore.BackupParser
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore
import com.raulshma.jellyplay.core.datastore.audio.AudioStore
import com.raulshma.jellyplay.core.datastore.audiocache.AudioCacheStore
import com.raulshma.jellyplay.core.datastore.audioeffects.AudioEffectsStore
import com.raulshma.jellyplay.core.datastore.downloads.DownloadsStore
import com.raulshma.jellyplay.core.datastore.engine.PlayerEngineStore
import com.raulshma.jellyplay.core.datastore.experimental.ExperimentalStore
import com.raulshma.jellyplay.core.datastore.home.HomeDiscoveryStore
import com.raulshma.jellyplay.core.datastore.library.LibraryStore
import com.raulshma.jellyplay.core.datastore.navigation.NavigationStore
import com.raulshma.jellyplay.core.datastore.network.NetworkOfflineStore
import com.raulshma.jellyplay.core.datastore.notification.NotificationStore
import com.raulshma.jellyplay.core.datastore.playback.PlaybackStore
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeStateStore
import com.raulshma.jellyplay.core.datastore.screensaver.ScreensaverStore
import com.raulshma.jellyplay.core.datastore.security.PinRateLimiter
import com.raulshma.jellyplay.core.datastore.security.SecurityStore
import com.raulshma.jellyplay.core.datastore.security.hasSecuritySensitive
import com.raulshma.jellyplay.core.datastore.settings.PreferenceSliceSnapshot
import com.raulshma.jellyplay.core.datastore.settings.buildPreferenceSliceSnapshotFromBackup
import com.raulshma.jellyplay.core.datastore.subtitle.SubtitleLanguageStore
import com.raulshma.jellyplay.core.datastore.syncplaycast.SyncPlayCastStore
import com.raulshma.jellyplay.core.datastore.videoplayer.VideoPlayerStore
import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.model.PreferenceResetCategory
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

/**
 * Full-screen import preview. Mirrors `FactoryResetViewModel`'s one-shot
 * snapshot approach but compares **live** vs **incoming backup** (not vs
 * factory). Supports per-category and import-all, plus the synthetic
 * `AppRuntimeState` extras card ("everything" per user request).
 *
 * The backup file is loaded from the `uri` passed via navigation. Only the v2
 * per-slice shape (and forward-compatible future versions) import; the parser
 * rejects legacy v0/v1 backups with a clear message (sunset in v0.11). The
 * `incomingPrefs` diff snapshot is decoded per-slice via
 * [buildPreferenceSliceSnapshotFromBackup] (missing/malformed slices fall back
 * to defaults) and diffed against the live slices directly — no aggregate
 * re-mapping in between.
 *
 * Security-sensitive lock fields are gated by the caller's
 * `restoreSecuritySensitive` flag (defaults false; UI exposes a checkbox).
 */
class ImportPreviewViewModel(
    private val settingsBackupIo: SettingsBackupIo,
    private val userPreferencesStore: UserPreferencesStore,
    private val playbackStore: PlaybackStore,
    private val appearanceStore: AppearanceStore,
    private val videoPlayerStore: VideoPlayerStore,
    private val downloadsStore: DownloadsStore,
    private val engineStore: PlayerEngineStore,
    private val homeDiscoveryStore: HomeDiscoveryStore,
    private val audioStore: AudioStore,
    private val audioEffectsStore: AudioEffectsStore,
    private val audioCacheStore: AudioCacheStore,
    private val libraryStore: LibraryStore,
    private val navigationStore: NavigationStore,
    private val networkOfflineStore: NetworkOfflineStore,
    private val notificationStore: NotificationStore,
    private val screensaverStore: ScreensaverStore,
    private val securityStore: SecurityStore,
    private val subtitleLanguageStore: SubtitleLanguageStore,
    private val syncPlayCastStore: SyncPlayCastStore,
    private val experimentalStore: ExperimentalStore,
    private val appRuntimeStateStore: AppRuntimeStateStore,
    private val pinRateLimiter: PinRateLimiter,
    private val diffLabelResolver: suspend (List<StringResource>) -> (StringResource) -> String = ::resolveDiffLabels,
) : JellyPlayViewModel() {

    /** Resolved label lookup shared by both snapshots; swapped in once on entry. */
    private var labels: (StringResource) -> String = { it.toString() }

    private fun diffSnapshot(slices: PreferenceSliceSnapshot): PreferenceDiffSnapshot =
        PreferenceDiffSnapshot(slices) { res -> labels(res) }

    /**
     * The live slices; non-null from construction (the screen renders the diff
     * only once the incoming snapshot is staged) and rebuilt on entry and
     * after every category import.
     */
    var currentPrefs by composeState(diffSnapshot(PreferenceSliceSnapshot.FACTORY))
        private set
    var incomingPrefs by composeState<PreferenceDiffSnapshot?>(null)
        private set

    var currentExtras by composeState(AppRuntimeState())
        private set
    var incomingExtras by composeState<AppRuntimeState?>(null)
        private set

    var rawBackup by composeState<SettingsBackup?>(null)
        private set

    var schemaVersion by composeState<Int?>(null)
        private set
    var versionMismatch by composeState(false)
        private set
    var hasSecuritySensitive by composeState(false)
        private set

    var isLoading by composeState(true)
        private set
    var error by composeState<String?>(null)
        private set

    sealed interface ImportEvent {
        data object AllImported : ImportEvent
        data object CategoryImported : ImportEvent
        data object ExtrasImported : ImportEvent
        data class Failed(val message: String) : ImportEvent
    }

    var importEvent by composeState<ImportEvent?>(null)
        private set
    // Backward compat for previous String-based API (used by tests if any)
    var importStatus: String?
        get() = when (val e = importEvent) {
            is ImportEvent.AllImported -> "All settings imported"
            is ImportEvent.CategoryImported -> "Category imported"
            is ImportEvent.ExtrasImported -> "App state imported"
            is ImportEvent.Failed -> "Import failed: ${e.message}"
            null -> null
        }
        set(_) {}

    private val loadMutex = kotlinx.coroutines.sync.Mutex()
    private var loadedUri: String? = null

    init {
        launch {
            try {
                labels = diffLabelResolver(PreferenceCategoryViews.flatMap { it.labelResources })
                currentPrefs = buildCurrentSnapshot()
                currentExtras = appRuntimeStateStore.state.first()
            } catch (e: Exception) {
                error = UserErrorMessages.resolve(e, "Failed to load current settings")
                isLoading = false
            }
            // Incoming remains loading until `loadBackup` is called from the screen.
            // Do not clear isLoading here — the screen will show the spinner.
        }
    }

    fun loadBackup(uriString: String) {
        launch {
            // Guard against concurrent loads; allow a *different* uri to reload
            // (config change or back→pick another file). The previous
            // `hasLoadedIncoming` boolean permanently blocked a second file.
            if (!loadMutex.tryLock()) return@launch
            try {
                if (loadedUri == uriString) return@launch
                loadedUri = uriString
                isLoading = true
                error = null
                // Ensure current snapshot is ready (init may still be running).
                // `buildCurrentSnapshot` is idempotent — re-reading the stores is cheap.
                labels = diffLabelResolver(PreferenceCategoryViews.flatMap { it.labelResources })
                currentPrefs = buildCurrentSnapshot()
                currentExtras = appRuntimeStateStore.state.first()
                loadIncoming(uriString)
            } catch (e: Exception) {
                // Allow retry with same uri after failure.
                loadedUri = null
                error = UserErrorMessages.resolve(e, "Failed to load backup")
            } finally {
                isLoading = false
                loadMutex.unlock()
            }
        }
    }

    private suspend fun buildCurrentSnapshot(): PreferenceDiffSnapshot =
        diffSnapshot(
            PreferenceSliceSnapshot(
                playback = playbackStore.playback.first(),
                videoPlayer = videoPlayerStore.videoPlayer.first(),
                engine = engineStore.playerEngine.first(),
                subtitle = subtitleLanguageStore.subtitle.first(),
                audio = audioStore.audio.first(),
                audioEffects = audioEffectsStore.audioEffects.first(),
                audioCache = audioCacheStore.audioCache.first(),
                appearance = appearanceStore.appearance.first(),
                homeDiscovery = homeDiscoveryStore.homeDiscovery.first(),
                library = libraryStore.library.first(),
                navigation = navigationStore.navigation.first(),
                downloads = downloadsStore.downloads.first(),
                networkOffline = networkOfflineStore.networkOffline.first(),
                notification = notificationStore.notification.first(),
                syncPlayCast = syncPlayCastStore.syncPlayCast.first(),
                screensaver = screensaverStore.screensaver.first(),
                security = securityStore.security.first(),
                experimental = experimentalStore.experimental.first(),
                runtime = appRuntimeStateStore.state.first(),
                pinLockout = pinRateLimiter.getPinLockoutState(),
            ),
        )

    private suspend fun loadIncoming(uriString: String) {
        // The payload read lives INSIDE the SettingsBackupIo actual (the
        // stream→text seam narrowing) — each actual owns its own IO hop, so
        // the former withContext(Dispatchers.IO) wrapper here is gone.
        val jsonString = settingsBackupIo.readImportPayload(uriString)
            ?: throw IllegalStateException("Cannot open backup file")
        when (val parsed = BackupParser.parse(jsonString)) {
            is BackupParser.Parsed.V2 -> stageIncoming(parsed.backup, parsed.hasSecuritySensitive, false)
            is BackupParser.Parsed.Future -> stageIncoming(parsed.backup, parsed.hasSecuritySensitive, true)
        }
    }

    private fun stageIncoming(backup: SettingsBackup, securitySensitive: Boolean, mismatch: Boolean) {
        rawBackup = backup
        schemaVersion = backup.schemaVersion
        versionMismatch = mismatch
        incomingPrefs = diffSnapshot(buildPreferenceSliceSnapshotFromBackup(backup))
        incomingExtras = backup.extras
        hasSecuritySensitive = securitySensitive
    }

    /** Import every category plus extras. */
    fun importAll(restoreSecuritySensitive: Boolean, onDone: () -> Unit) {
        launch {
            try {
                val backup = rawBackup ?: return@launch
                userPreferencesStore.restoreV2(backup, restoreSecuritySensitive)
                importEvent = ImportEvent.AllImported
                onDone()
            } catch (e: Exception) {
                importEvent = ImportEvent.Failed(UserErrorMessages.resolve(e, "Unknown error"))
            }
        }
    }

    /** Import a single preference category. */
    fun importCategory(category: PreferenceResetCategory, restoreSecuritySensitive: Boolean, onDone: () -> Unit) {
        launch {
            try {
                val backup = rawBackup ?: return@launch
                userPreferencesStore.restoreV2Categories(
                    backup = backup,
                    categories = setOf(category),
                    restoreSecuritySensitive = restoreSecuritySensitive,
                    includeExtras = false,
                )
                importEvent = ImportEvent.CategoryImported
                // Refresh current snapshot so diff updates without leaving screen.
                currentPrefs = buildCurrentSnapshot()
                currentExtras = appRuntimeStateStore.state.first()
                onDone()
            } catch (e: Exception) {
                importEvent = ImportEvent.Failed(UserErrorMessages.resolve(e, "Unknown error"))
            }
        }
    }

    /** Import only the AppRuntime extras card. */
    fun importExtras(onDone: () -> Unit) {
        launch {
            try {
                val backup = rawBackup ?: return@launch
                userPreferencesStore.restoreExtras(backup)
                importEvent = ImportEvent.ExtrasImported
                currentExtras = appRuntimeStateStore.state.first()
                onDone()
            } catch (e: Exception) {
                importEvent = ImportEvent.Failed(UserErrorMessages.resolve(e, "Unknown error"))
            }
        }
    }

    fun clearImportStatus() {
        importEvent = null
    }

    fun clearImportEvent() {
        importEvent = null
    }

}
