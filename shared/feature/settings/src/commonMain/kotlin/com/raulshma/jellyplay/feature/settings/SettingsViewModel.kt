package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.PreferencesEditScope
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.search.SettingsRecentsStore
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.IOException

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
    private val adminRepository: AdminRepository,
    private val editor: PreferencesEditor,
    private val recentsStore: SettingsRecentsStore,
) : JellyPlayViewModel() {

    private val preferencesFlow: kotlinx.coroutines.flow.StateFlow<SettingsScreenPreferences> =
        projections.settingsScreenPreferences

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
                    if (user?.isAdmin == true) {
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
            adminRepository.getSessions()
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
                adminRepository.getSessions()
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
     * resolves to [java.time.Instant.MIN], which predates the cutoff and so
     * excludes the session — deliberate, since a session with no resolvable
     * activity timestamp should not appear as live.
     */
    private fun List<com.raulshma.jellyplay.core.model.SessionInfo>.filterActiveSessions(): List<com.raulshma.jellyplay.core.model.SessionInfo> {
        val cutoff = java.time.Instant.now().minusSeconds(5 * 60)
        return filter {
            val lastActivity = try { java.time.Instant.parse(it.lastActivityDate) } catch (_: Exception) { java.time.Instant.MIN }
            it.isActive && it.client.isNotBlank() && it.deviceName.isNotBlank() && it.client != "Jellyfin Server" &&
                (it.nowPlayingItem != null || lastActivity.isAfter(cutoff))
        }
    }

    fun sendMessageToSession(sessionId: String, header: String, text: String) {
        launch {
            adminRepository.sendMessageToSession(sessionId, header, text)
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
     * Single write command for this screen: `edit { it.screensaver.setDreamShowTitle(enabled) }`
     * (the advanced toggle is `edit { it.appearance.setShowAdvancedSettings(enabled) }`).
     * Fire-and-forget on the same application scope [PreferencesEditor.edit] uses.
     */
    fun edit(transform: suspend (PreferencesEditScope) -> Unit) = editor.edit { transform(this) }

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
                settingsBackupIo.openExportSink(uri)?.use { stream ->
                    stream.writer().use { it.write(jsonString) }
                } ?: throw IOException("Cannot open output stream")
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
