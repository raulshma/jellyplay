package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.search.SettingsRecentsStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.HomeScreenPreferences
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The ~15 preference fields the settings root screen reads, projected
 * centrally off the owning store slices by [PreferenceProjections]. Spans
 * appearance (advanced-settings toggle + the appearance summary set), playback,
 * audio, subtitle, notification, security, screensaver, and experimental.
 */
class SettingsViewModel(
    private val settingsBackupIo: SettingsBackupIo,
    private val preferencesStore: UserPreferencesStore,
    private val projections: PreferenceProjections,
    private val authRepository: AuthRepository,
    private val seerrRepository: SeerrRepository,
    private val serverAdminActions: ServerAdminActions,
    editor: PreferencesEditor,
    private val recentsStore: SettingsRecentsStore,
    /**
     * The JellyPlay companion-plugin seams (ADR 0010). Nullable-with-default
     * keeps the direct-construction test harnesses compiling; the Koin
     * factory passes the real singles and the UI renders the sync section
     * only when both are present AND the probe reports AVAILABLE.
     */
    private val jellyPlayStatusStore: com.raulshma.jellyplay.core.data.session.JellyPlayPluginStatusStore? = null,
    private val jellyPlaySyncRepository: com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository? = null,
    /**
     * The plugin's events/messages face — backs the capability-gated
     * "Messages" entry (unread badge) on the settings root. Same
     * nullable-with-default discipline as the two seams above.
     */
    private val jellyPlayEventsRepository: com.raulshma.jellyplay.core.data.repository.JellyPlayEventsRepository? = null,
) : SettingsEditorViewModel(editor) {

    private val preferencesFlow: kotlinx.coroutines.flow.StateFlow<SettingsScreenPreferences> =
        projections.settingsScreenPreferences

    /** Home-config slice backing the root Home row's "sections visible" summary. */
    val homePreferences: kotlinx.coroutines.flow.StateFlow<HomeScreenPreferences> =
        projections.homeScreenPreferences

    var preferences by composeState(SettingsScreenPreferences())
        private set

    var currentUserName by composeState("")
        private set

    var cacheSizeMb by composeState(0L)
        private set

    var cacheError by composeState<String?>(null)
        private set

    var currentUser by composeState<UserInfo?>(null)
        private set

    var currentServerUsers by composeState<List<UserInfo>>(emptyList())
        private set

    var isLoadingUsers by composeState(false)
        private set

    val currentServerAddress = authRepository.currentServer
        .map { it?.address ?: "" }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), "")

    /**
     * The Seerr pending-request count for the Activity Insights badge.
     * Backed by the repository's shared StateFlow, but with a ONE-SHOT
     * refetch wired to subscription instead of the 60s background poll:
     * whenever the badge surface becomes active (the first lifecycle-aware
     * collector appears, or re-appears after the [SharingStarted.WhileSubscribed]
     * grace window) exactly one [SeerrRepository.getRequestCount] fires, and
     * the repository stamps its shared flow on success — same contract as
     * [SeerrRepository.currentUser].
     *
     * Settings deliberately does NOT keep the repository's poll loop alive
     * for this badge (an earlier `init { startPolling() }` woke every 60s
     * for any user who merely opened Settings, and `onCleared` then stopped
     * the singleton loop even while the Requests screen was still consuming
     * it). The loop's only owner is the Requests screen's
     * start/stop pair; see [SeerrRepository.startPolling].
     */
    val pendingRequestCount: kotlinx.coroutines.flow.StateFlow<Int> = seerrRepository.pendingRequestCount
        .onSubscription { refreshPendingRequestCount() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * The ids of the last [SettingsRecentsStore.MAX_RECENTS] settings opened from
     * the settings search, most-recent first. Backed by [recentsStore]; exposed
     * directly (it is already an app-scoped `StateFlow`) so the screen can collect
     * it lifecycle-aware without re-subscribing here.
     */
    val recentSettingIds: kotlinx.coroutines.flow.StateFlow<List<String>> = recentsStore.recents

    /** Companion-plugin availability (UNKNOWN until [refreshJellyPlayPluginStatus] probes). */
    val jellyPlayPluginStatus: kotlinx.coroutines.flow.StateFlow<com.raulshma.jellyplay.core.model.JellyPlayPluginStatus> =
        jellyPlayStatusStore?.status
            ?: kotlinx.coroutines.flow.MutableStateFlow(com.raulshma.jellyplay.core.model.JellyPlayPluginStatus.UNAVAILABLE)

    /** The sync engine's outcome (opt-in toggle, last sync, errors). */
    val jellyPlaySyncState: kotlinx.coroutines.flow.StateFlow<com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository.SyncState> =
        jellyPlaySyncRepository?.state
            ?: kotlinx.coroutines.flow.MutableStateFlow(com.raulshma.jellyplay.core.data.repository.ProfileSyncRepository.SyncState())

    /**
     * The plugin's live feature keys (the capability registry — the ONE
     * gating mechanism, ADR 0010). The "Messages" entry gates on
     * [com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures.Messages]
     * being present, reactively.
     */
    val jellyPlayPluginFeatures: kotlinx.coroutines.flow.StateFlow<Set<String>> =
        jellyPlayStatusStore?.features
            ?: kotlinx.coroutines.flow.MutableStateFlow(emptySet())

    /**
     * The plugin's inbox messages — the durable counterpart of the live
     * events stream. Feeds the "Messages" entry's unread badge; the
     * [com.raulshma.jellyplay.feature.settings.JellyPlayMessagesViewModel]
     * owns the screen-side consumption.
     */
    val jellyPlayInbox: kotlinx.coroutines.flow.StateFlow<List<com.raulshma.jellyplay.core.network.api.JellyPlayMessage>> =
        jellyPlayEventsRepository?.inbox
            ?: kotlinx.coroutines.flow.MutableStateFlow(emptyList())

    /** Unread count behind the "Messages" entry's badge. */
    val jellyPlayUnreadMessageCount: kotlinx.coroutines.flow.StateFlow<Int> =
        jellyPlayInbox
            .map { list -> list.count { !it.read } }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * One inbox refresh so the badge is fresh whenever the settings root
     * becomes visible. Called from the gated entry (AVAILABLE + Messages
     * feature), never on its own — the repository's api client 404s against
     * a stock server.
     */
    fun refreshJellyPlayInbox() {
        val repo = jellyPlayEventsRepository ?: return
        scope.launch { repo.refreshInbox() }
    }

    /** One capabilities probe; called when the sync section becomes visible. */
    fun refreshJellyPlayPluginStatus() {
        val store = jellyPlayStatusStore ?: return
        scope.launch { store.refresh() }
    }

    fun setJellyPlaySyncEnabled(enabled: Boolean) {
        val repo = jellyPlaySyncRepository ?: return
        repo.setEnabled(enabled)
        if (enabled) {
            // First enable pulls + pushes immediately so the toggle has an effect.
            scope.launch { repo.requestSync() }
        }
    }

    fun syncJellyPlayNow() {
        jellyPlaySyncRepository?.let { repo -> scope.launch { repo.requestSync() } }
    }

    var activeSessions by composeState<List<com.raulshma.jellyplay.core.model.SessionInfo>>(emptyList())
        private set

    var isLoadingSessions by composeState(false)
        private set

    var messageSentEvent by composeState<String?>(null)
        private set

    private var sessionRefreshJob: Job? = null

    init {
        launch {
            preferencesFlow.collect { prefs ->
                preferences = prefs
            }
        }
        launch {
            authRepository.currentUser
                .distinctUntilChanged { old, new ->
                    old?.id == new?.id && old?.isAdmin == new?.isAdmin && old?.name == new?.name
                }
                .collect { user ->
                    currentUser = user
                    currentUserName = user?.name ?: ""
                    if (user?.isAdmin == true && serverAdminActions.isSupported) {
                        loadSessions()
                    } else {
                        stopSessionAutoRefresh()
                        activeSessions = emptyList()
                    }
                }
        }
        launch {
            authRepository.currentServerUsers.collect { users ->
                currentServerUsers = users
                isLoadingUsers = false
            }
        }
    }

    /**
     * Recomputes cache size from disk. The internal and external cache
     * directories are walked concurrently by the [SettingsBackupIo] platform
     * actual under a single IO switch. Invoked explicitly by the settings root
     * screen on entry — not from [init] — so the walk only fires when the user
     * actually views settings.
     */
    fun refreshCacheSize() {
        launch {
            cacheSizeMb = settingsBackupIo.estimateCacheSizeBytes() / (1024 * 1024)
        }
    }

    private fun loadSessions() {
        launch {
            isLoadingSessions = true
            serverAdminActions.getSessions()
                .onSuccess { sessions -> activeSessions = sessions.filterActiveSessions() }
            isLoadingSessions = false
        }
    }

    /**
     * Starts polling `/Sessions` every 30s for admin users. Should be tied to
     * screen visibility (STARTED) by the caller via [stopSessionAutoRefresh] on
     * exit so polling does not run while settings is in the back stack.
     */
    fun startSessionAutoRefresh() {
        sessionRefreshJob?.cancel()
        sessionRefreshJob = launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                serverAdminActions.getSessions()
                    .onSuccess { sessions -> activeSessions = sessions.filterActiveSessions() }
            }
        }
    }

    /** Stops the session auto-refresh loop started by [startSessionAutoRefresh]. */
    fun stopSessionAutoRefresh() {
        sessionRefreshJob?.cancel()
        sessionRefreshJob = null
    }

    /**
     * Keeps only active, non-server Jellyfin sessions: drops the headless
     * "Jellyfin Server" entry and any session inactive for more than 5 minutes
     * (unless it is currently playing). An unparseable `lastActivityDate`
     * resolves to [Instant.DISTANT_PAST], which predates the cutoff and so
     * excludes the session — deliberate, since a session with no resolvable
     * activity timestamp should not appear as live. (: kotlin.time
     * Instant — `DISTANT_PAST` replaces the java.time `MIN` sentinel, and
     * `isAfter` folds into the comparison operators, which are all
     * kotlin.time.Instant offers.)
     */
    private fun List<com.raulshma.jellyplay.core.model.SessionInfo>.filterActiveSessions(): List<com.raulshma.jellyplay.core.model.SessionInfo> {
        val cutoff = Clock.System.now() - 5.minutes
        return filter {
            val lastActivity = try { Instant.parse(it.lastActivityDate) } catch (_: Exception) { Instant.DISTANT_PAST }
            it.isActive && it.client.isNotBlank() && it.deviceName.isNotBlank() && it.client != "Jellyfin Server" &&
                (it.nowPlayingItem != null || lastActivity > cutoff)
        }
    }

    fun sendMessageToSession(sessionId: String, header: String, text: String) {
        launch {
            serverAdminActions.sendMessageToSession(sessionId, header, text)
                .onSuccess {
                    messageSentEvent = "Message sent successfully"
                }
                .onFailure {
                    messageSentEvent = "Failed to send message"
                }
        }
    }

    fun clearMessageEvent() {
        messageSentEvent = null
    }

    override fun onCleared() {
        super.onCleared()
        sessionRefreshJob?.cancel()
    }

    /**
     * One-shot Seerr pending-count refresh behind the Activity Insights
     * badge ([pendingRequestCount]). Fire-and-forget: the repository writes
     * its shared StateFlow on success, so the badge flow above updates
     * without this VM re-plumbing the value. Triggered by subscription, not
     * [init] — same screen-entry discipline as [refreshCacheSize] — so the
     * fetch only fires when the badge is actually being observed, never on a
     * 60s loop while Settings sits in the back stack.
     */
    private fun refreshPendingRequestCount() {
        launch { seerrRepository.getRequestCount() }
    }

    /**
     * Records that the setting with [id] was opened from search. Destructive
     * *actions* (logout, sign-out-from-server) are filtered out by the caller —
     * only real settings are tracked.
     */
    fun recordSettingUsed(id: String) {
        launch { recentsStore.addRecent(id) }
    }

    /** Clears the recorded recent settings list. */
    fun clearRecentSettings() {
        launch { recentsStore.clearRecents() }
    }

    /** Clears all preferences and resets to factory defaults. */
    fun clearAllPreferences() {
        editor.clearAllPreferences()
    }

    var backupRestoreStatus by composeState<String?>(null)
        private set

    /**
     * The stage-and-navigate signal of the import flow: the picked backup uri
     * string between the file picker and the backup screen's navigation into
     * the import preview. Nothing is read or decoded here — the preview
     * screen's [ImportPreviewViewModel] re-reads the file through the pure
     * [com.raulshma.jellyplay.core.datastore.BackupParser] and owns
     * classification, security-gating and the restore itself.
     */
    var stagedImportUri by composeState<String?>(null)
        private set

    fun exportSettings(uri: String) {
        launch {
            backupRestoreStatus = null
            runCatching {
                // v2 export: snapshot every domain slice + app-runtime extras.
                // No buildUserPreferences round-trip — the per-store slices are
                // the canonical payload.
                val snapshot = preferencesStore.snapshotForBackup()
                val backup = SettingsBackup(slices = snapshot.slices, extras = snapshot.extras)
                val jsonString = com.raulshma.jellyplay.core.datastore.PreferencesJson.export
                    .encodeToString(SettingsBackup.serializer(), backup)
                if (!settingsBackupIo.writeExportPayload(uri, jsonString)) {
                    throw IllegalStateException("Cannot open output stream")
                }
                backupRestoreStatus = "Settings exported successfully"
            }.onFailure {
                backupRestoreStatus = "Export failed: ${it.message}"
            }
        }
    }

    /**
     * Stages the picked backup [uri] as the stage-and-navigate signal
     * ([stagedImportUri]); the backup screen navigates to the import preview
     * and consumes the signal. Deliberately no file read or decode here —
     * the preview ViewModel re-reads the source and reports its own load
     * failures, so the two paths cannot drift on classification.
     */
    fun importSettings(uri: String) {
        backupRestoreStatus = null
        stagedImportUri = uri
    }

    /** Discards the staged import uri after navigation (or a failed navigation). */
    fun cancelImport() {
        stagedImportUri = null
    }

    fun clearBackupRestoreStatus() {
        backupRestoreStatus = null
    }
}
