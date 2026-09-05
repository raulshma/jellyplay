package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.data.repository.AuthRepository
import com.raulshma.jellyplay.core.data.repository.SeerrRepository
import com.raulshma.jellyplay.core.datastore.BackupSliceKey
import com.raulshma.jellyplay.core.datastore.LegacySettingsBackup
import com.raulshma.jellyplay.core.datastore.PreferencesEditor
import com.raulshma.jellyplay.core.datastore.SettingsBackup
import com.raulshma.jellyplay.core.datastore.UserPreferencesStore
import com.raulshma.jellyplay.core.datastore.search.SettingsRecentsStore
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.settings.PreferenceProjections
import com.raulshma.jellyplay.core.model.DreamImageCategory
import com.raulshma.jellyplay.core.model.DreamTransitionStyle
import com.raulshma.jellyplay.core.model.SettingsScreenPreferences
import com.raulshma.jellyplay.core.model.UserInfo
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
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
    appearanceStore: com.raulshma.jellyplay.core.datastore.appearance.AppearanceStore,
    private val editor: PreferencesEditor,
    private val recentsStore: SettingsRecentsStore,
) : JellyPlayViewModel() {

    private companion object {
        /** JSON field name carrying the backup schema version in the envelope. */
        const val SCHEMA_VERSION_FIELD = "schemaVersion"
    }

    private val advancedSettings = AdvancedSettingsGate(appearanceStore, editor)

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

    val pendingRequestCount: kotlinx.coroutines.flow.StateFlow<Int> = seerrRepository.pendingRequestCount
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
        seerrRepository.startPolling()
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
        seerrRepository.stopPolling()
    }

    fun setShowAdvancedSettings(enabled: Boolean) = advancedSettings.setShowAdvancedSettings(enabled)

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

    fun setDreamImageCategories(categories: Set<DreamImageCategory>) {
        editor.edit { screensaver.setDreamImageCategories(categories) }
    }

    fun setDreamSlideshowIntervalMs(ms: Long) {
        editor.edit { screensaver.setDreamSlideshowIntervalMs(ms) }
    }

    fun setDreamKenBurnsEnabled(enabled: Boolean) {
        editor.edit { screensaver.setDreamKenBurnsEnabled(enabled) }
    }

    fun setDreamTransitionStyle(style: DreamTransitionStyle) {
        editor.edit { screensaver.setDreamTransitionStyle(style) }
    }

    fun setDreamShowTitle(enabled: Boolean) {
        editor.edit { screensaver.setDreamShowTitle(enabled) }
    }

    /** Clears all preferences and resets to factory defaults. */
    fun clearAllPreferences() {
        editor.clearAllPreferences()
    }

    var backupRestoreStatus by composeState<String?>(null)
        private set

    /**
     * Details surfaced to the UI when an import needs user confirmation before
     * overwriting preferences. [isLegacy] is true for the pre-versioning format
     * and [versionMismatch] is true when the backup's schema version differs
     * from the app's current one. [hasSecuritySensitive] is true when the
     * backup would overwrite the PIN/biometric lock — the user can opt in via
     * [confirmImport].
     */
    @Immutable
    data class PendingImport(
        val uri: String,
        val schemaVersion: Int,
        val isLegacy: Boolean,
        val versionMismatch: Boolean,
        val hasSecuritySensitive: Boolean,
    )

    var pendingImport by composeState<PendingImport?>(null)
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
     * Reads the selected backup, detects its schema version (v2 per-slice,
     * v1 single-aggregate envelope, or the pre-versioning bare
     * [UserPreferences] object), and stages a [PendingImport] for the UI to
     * confirm. Nothing is written until [confirmImport] is called.
     *
     * Security-sensitive detection works for both formats: v2 reads it off the
     * decoded [SecuritySlice]; v0/v1 off the [UserPreferences] aggregate.
     */
    fun importSettings(uri: String) {
        launch {
            backupRestoreStatus = null
            runCatching {
                val jsonString = settingsBackupIo.openImportSource(uri)?.use { stream ->
                    stream.reader().use { it.readText() }
                } ?: throw IOException("Cannot open input stream")
                pendingImport = parsePendingImport(uri, jsonString)
            }.onFailure {
                backupRestoreStatus = "Import failed: ${it.message}"
            }
        }
    }

    /**
     * Classifies a backup JSON string into v2 / v1 / v0 and builds the matching
     * [PendingImport]. Extracted so the detection logic is testable without a
     * ContentResolver.
     *
     * - **v2** (`schemaVersion == 2`): per-slice envelope. Security-sensitive
     *   detection decodes the [SecuritySlice] element.
     * - **v1** (`schemaVersion == 1`): single-aggregate envelope
     *   ([LegacySettingsBackup]).
     * - **v0** (no envelope, bare [UserPreferences] object): pre-versioning.
     */
    private suspend fun parsePendingImport(uri: String, jsonString: String): PendingImport {
        val json = com.raulshma.jellyplay.core.datastore.PreferencesJson.import
        val current = SettingsBackup.CURRENT_SCHEMA_VERSION

        // Peek the schemaVersion field to classify without committing to one
        // shape. A bare UserPreferences object (v0) has no envelope, so the
        // field is absent and falls through to the legacy path.
        val root = json.parseToJsonElement(jsonString) as? kotlinx.serialization.json.JsonObject
        val schemaVersion = root
            ?.let { it[SCHEMA_VERSION_FIELD] as? kotlinx.serialization.json.JsonPrimitive }
            ?.content?.toIntOrNull()

        val hasSecuritySensitive: Boolean = when (schemaVersion) {
            SettingsBackup.CURRENT_SCHEMA_VERSION -> {
                val backup = json.decodeFromString(SettingsBackup.serializer(), jsonString)
                backup.slices[BackupSliceKey.SECURITY]?.let { secElement ->
                    runCatching {
                        json.decodeFromJsonElement(SecuritySlice.serializer(), secElement)
                    }.getOrNull()?.hasSecuritySensitive()
                } ?: false
            }
            SettingsBackup.LEGACY_AGGREGATE_SCHEMA_VERSION -> {
                json.decodeFromString(LegacySettingsBackup.serializer(), jsonString)
                    .preferences.hasSecuritySensitive()
            }
            else -> {
                // v0 (bare aggregate) or unknown — decode as bare UserPreferences.
                json.decodeFromString(UserPreferences.serializer(), jsonString).hasSecuritySensitive()
            }
        }

        val isLegacy = schemaVersion == null ||
            schemaVersion <= SettingsBackup.LEGACY_AGGREGATE_SCHEMA_VERSION
        val resolvedVersion = schemaVersion
            ?: SettingsBackup.LEGACY_UNENVELOPED_SCHEMA_VERSION

        return PendingImport(
            uri = uri,
            schemaVersion = resolvedVersion,
            isLegacy = isLegacy,
            versionMismatch = resolvedVersion != current,
            hasSecuritySensitive = hasSecuritySensitive,
        )
    }

    private fun SecuritySlice.hasSecuritySensitive(): Boolean =
        pinLockEnabled || biometricLockEnabled || pinHash != null || usePinForPlayerLock

    private fun UserPreferences.hasSecuritySensitive(): Boolean =
        pinLockEnabled || biometricLockEnabled || pinHash != null || usePinForPlayerLock

    /**
     * Applies a staged import after the user confirms. Routes by the staged
     * schema version: v2 fans each slice to its store; v1 decodes the legacy
     * aggregate and fans via the per-store `restorePreferences(UserPreferences)`
     * path; v0 (bare aggregate) is decoded and handled the same as v1.
     *
     * Security-sensitive lock fields are only restored when
     * [restoreSecuritySensitive] is true (the UI defaults this to false unless
     * the user explicitly opts in).
     */
    fun confirmImport(restoreSecuritySensitive: Boolean) {
        val pending = pendingImport ?: return
        launch {
            backupRestoreStatus = null
            runCatching {
                val jsonString = settingsBackupIo.openImportSource(pending.uri)?.use { stream ->
                    stream.reader().use { it.readText() }
                } ?: throw IOException("Cannot open input stream")
                val json = com.raulshma.jellyplay.core.datastore.PreferencesJson.import
                when (pending.schemaVersion) {
                    SettingsBackup.CURRENT_SCHEMA_VERSION -> {
                        val backup = json.decodeFromString(SettingsBackup.serializer(), jsonString)
                        preferencesStore.restoreV2(backup, restoreSecuritySensitive)
                    }
                    SettingsBackup.LEGACY_AGGREGATE_SCHEMA_VERSION -> {
                        val legacy = json.decodeFromString(LegacySettingsBackup.serializer(), jsonString)
                        preferencesStore.restorePreferences(legacy.preferences, restoreSecuritySensitive)
                    }
                    else -> {
                        // v0: bare, un-enveloped UserPreferences object.
                        val bare = json.decodeFromString(UserPreferences.serializer(), jsonString)
                        preferencesStore.restorePreferences(bare, restoreSecuritySensitive)
                    }
                }
                pendingImport = null
                backupRestoreStatus = "Settings imported successfully"
            }.onFailure {
                pendingImport = null
                backupRestoreStatus = "Import failed: ${it.message}"
            }
        }
    }

    fun cancelImport() {
        pendingImport = null
    }

    fun clearBackupRestoreStatus() {
        backupRestoreStatus = null
    }
}
