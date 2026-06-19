package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.SessionInfo
import com.raulshma.jellyplay.core.model.UserInfo

/**
 * Server / session slice of [SettingsViewModel] UI state.
 *
 * Grouped so admin / session UI collects only this flow instead of six
 * unrelated `composeState` properties. Source of truth remains the individual
 * backing fields on [SettingsViewModel]; the aggregator is derived from a
 * Compose snapshot via `snapshotFlow { ... }` so any mutation that fires in
 * the same snapshot batch emits one consolidated [ServerState] update.
 */
@Immutable
data class ServerState(
    val currentUser: UserInfo? = null,
    val currentUserName: String = "",
    val currentServerUsers: List<UserInfo> = emptyList(),
    val isLoadingUsers: Boolean = false,
    val activeSessions: List<SessionInfo> = emptyList(),
    val isLoadingSessions: Boolean = false,
    val messageSentEvent: String? = null,
)

/** Library folders + load flag for notification / library-config UI. */
@Immutable
data class LibraryState(
    val libraryFolders: List<LibraryFolder> = emptyList(),
    val isLoadingLibraries: Boolean = false,
)

/** State for the "Add pinned section" picker dialog. */
@Immutable
data class PinnedBrowseState(
    val options: List<SettingsViewModel.PinnableOption> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

/** Combined status of preset import + backup/restore operations. */
@Immutable
data class BackupRestoreState(
    val presetImportError: String? = null,
    val backupRestoreStatus: String? = null,
)
