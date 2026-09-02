package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class ManagedUser(
    val id: String = "",
    val name: String = "",
    val primaryImageTag: String? = null,
    val hasPassword: Boolean = false,
    val hasConfiguredPassword: Boolean = false,
    val lastLoginDate: String? = null,
    val lastActivityDate: String? = null,
    val policy: ManagedUserPolicy = ManagedUserPolicy(),
)

/** Server parental-rating option. Names may be grouped ("PG-13/TV-14"). */
@Immutable
@Serializable
data class ParentalRatingOption(
    val name: String,
    val score: Int?, // null == "no limit"
    val subScore: Int?,
)

/** Admin-editable access schedule. [dayOfWeek] is a DynamicDayOfWeek serialName. */
@Immutable
@Serializable
data class UserAccessSchedule(
    val id: Int = 0,
    val dayOfWeek: String, // "Sunday".."Everyday"
    val startHour: Double, // 13.5 == 13:30
    val endHour: Double,
)

enum class SyncPlayAccessOption { CREATE_AND_JOIN, JOIN_ONLY, NONE }

enum class UnratedItemOption {
    BOOK, CHANNEL_CONTENT, LIVE_TV_CHANNEL, MOVIE, MUSIC, TRAILER, SERIES
}

@Immutable
@Serializable
data class ManagedUserPolicy(
    // General
    val isAdministrator: Boolean = false,
    val isHidden: Boolean = false,
    val isDisabled: Boolean = false,
    val enableUserPreferenceAccess: Boolean = true,
    // Access (folders)
    val enableAllFolders: Boolean = true,
    val enabledFolders: List<String> = emptyList(),
    // Permissions
    val enableMediaPlayback: Boolean = true,
    val enableAudioPlaybackTranscoding: Boolean = true,
    val enableVideoPlaybackTranscoding: Boolean = true,
    val enablePlaybackRemuxing: Boolean = true,
    val enableContentDeletion: Boolean = false,
    val enableContentDownloading: Boolean = true,
    val enableLiveTvAccess: Boolean = true,
    val enableLiveTvManagement: Boolean = false,
    val enableRemoteControlOfOtherUsers: Boolean = false,
    val enableRemoteAccess: Boolean = true,
    // Limits
    val maxParentalRating: Int? = null,
    val maxParentalSubRating: Int? = null,
    val maxActiveSessions: Int = 0,
    val loginAttemptsBeforeLockout: Int = -1,
    // Profile tab (server management / playback control / syncplay)
    val enableCollectionManagement: Boolean = false,
    val enableSubtitleManagement: Boolean = false,
    val forceRemoteSourceTranscoding: Boolean = false,
    val enableSharedDeviceControl: Boolean = false,
    val remoteClientBitrateLimit: Int = 0, // bits/sec; UI shows Mbps
    val syncPlayAccess: SyncPlayAccessOption = SyncPlayAccessOption.CREATE_AND_JOIN,
    // Access tab (channels / devices / deletion folders)
    val enableAllChannels: Boolean = true,
    val enabledChannels: List<String> = emptyList(),
    val enableAllDevices: Boolean = true,
    val enabledDevices: List<String> = emptyList(),
    val enableContentDeletionFromFolders: List<String> = emptyList(),
    // Parental tab
    val blockUnratedItems: List<UnratedItemOption> = emptyList(),
    val allowedTags: List<String> = emptyList(),
    val blockedTags: List<String> = emptyList(),
    val accessSchedules: List<UserAccessSchedule> = emptyList(),
)
