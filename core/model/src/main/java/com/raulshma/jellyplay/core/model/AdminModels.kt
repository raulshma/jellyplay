package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SystemInfo(
    val serverName: String = "",
    val version: String = "",
    val productName: String = "",
    val id: String = "",
    val localAddress: String = "",
    val wanAddress: String = "",
    val operatingSystem: String = "",
    val operatingSystemDisplayName: String = "",
    val hasPendingRestart: Boolean = false,
    val isShuttingDown: Boolean = false,
    val startupWizardCompleted: Boolean = true,
    val webSocketPortNumber: Int = 0,
    val packageName: String = "",
    val canSelfRestart: Boolean = false,
    val canLaunchWebBrowser: Boolean = false,
    val transcodingTempPath: String = "",
    val cachePath: String = "",
    val logPath: String = "",
    val internalMetadataPath: String = "",
)

@Immutable
@Serializable
data class ItemCounts(
    val movieCount: Long = 0,
    val seriesCount: Long = 0,
    val episodeCount: Long = 0,
    val albumCount: Long = 0,
    val songCount: Long = 0,
    val musicVideoCount: Long = 0,
    val bookCount: Long = 0,
    val totalCount: Long = 0,
)

@Immutable
@Serializable
data class ScheduledTaskInfo(
    val id: String = "",
    val key: String = "",
    val name: String = "",
    val state: TaskState = TaskState.IDLE,
    val isHidden: Boolean = false,
    val isEnabled: Boolean = true,
    val triggers: List<TaskTriggerInfo> = emptyList(),
    val lastExecutionResult: TaskExecutionInfo? = null,
    val currentProgressPercentage: Double? = null,
    val description: String? = null,
    val category: String? = null,
)

@Immutable
@Serializable
data class TaskTriggerInfo(
    val type: String = "",
    val timeOfDayTicks: Long? = null,
    val intervalTicks: Long? = null,
    val dayOfWeek: String? = null,
    val maxRuntimeTicks: Long? = null,
)

@Immutable
@Serializable
data class TaskExecutionInfo(
    val name: String = "",
    val key: String = "",
    val startTimeUtc: String? = null,
    val endTimeUtc: String? = null,
    val status: String = "",
    val errorMessage: String? = null,
)

@Immutable
@Serializable
data class DeviceInfo(
    val id: String = "",
    val name: String = "",
    val customName: String? = null,
    val appName: String = "",
    val appVersion: String = "",
    val lastUserName: String = "",
    val lastUserId: String = "",
    val dateLastActivity: String = "",
    val iconUrl: String? = null,
    val accessToken: String? = null,
    val capabilities: DeviceCapabilities? = null,
)

@Immutable
@Serializable
data class DeviceCapabilities(
    val playableMediaTypes: List<String> = emptyList(),
    val supportedCommands: List<String> = emptyList(),
    val supportsMediaControl: Boolean = false,
    val supportsContentUploading: Boolean = false,
    val deviceProfile: String? = null,
)

@Immutable
@Serializable
data class LogFile(
    val name: String = "",
    val dateModified: String = "",
    val size: Long = 0,
    val contentType: String = "text/plain",
)

@Immutable
@Serializable
data class ActivityLogEntry(
    val id: Long = 0,
    val name: String = "",
    val type: String = "",
    val userId: String? = null,
    val overview: String? = null,
    val shortOverview: String? = null,
    val itemId: String? = null,
    val date: String = "",
    val severity: ActivityLogSeverity = ActivityLogSeverity.INFORMATION,
)

@Immutable
@Serializable
enum class ActivityLogSeverity {
    TRACE, DEBUG, INFORMATION, WARNING, ERROR, FATAL
}

@Immutable
@Serializable
enum class TaskState {
    IDLE, RUNNING, CANCELLING
}

@Immutable
@Serializable
data class SessionInfo(
    val id: String = "",
    val deviceId: String = "",
    val userId: String = "",
    val userName: String = "",
    val client: String = "",
    val lastActivityDate: String = "",
    val lastPlaybackCheckIn: String? = null,
    val deviceName: String = "",
    val deviceType: String = "",
    val nowPlayingItem: SessionNowPlayingItem? = null,
    val playState: SessionPlayState? = null,
    val isActive: Boolean = false,
    val supportsRemoteControl: Boolean = false,
)

@Immutable
@Serializable
data class SessionNowPlayingItem(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    val mediaType: String? = null,
    val runTimeTicks: Long? = null,
    val primaryImageTag: String? = null,
    val seriesName: String? = null,
    val backdropImageTag: String? = null,
)

@Immutable
@Serializable
data class SessionPlayState(
    val positionTicks: Long? = null,
    val isPaused: Boolean = false,
    val isMuted: Boolean = false,
    val volumeLevel: Int? = null,
    val repeatMode: String = "RepeatNone",
    val playMethod: String? = null,
)

/**
 * Users-screen summary: the managed-user list joined with the current user id
 * and the count of active administrators (disabled admins excluded).
 */
@Immutable
data class UsersOverview(
    val users: List<ManagedUser> = emptyList(),
    val currentUserId: String? = null,
    val adminCount: Int = 0,
)

/**
 * User-editor context: everything the user-detail screen needs on open.
 * Auxiliary tab data (devices, channels, ratings, tags) loads lazily and is
 * fetched through the repository's individual operations.
 */
@Immutable
data class UserEditorContext(
    val user: ManagedUser,
    val libraries: List<LibraryFolder> = emptyList(),
    val currentUserId: String? = null,
    val adminCount: Int = 0,
)
