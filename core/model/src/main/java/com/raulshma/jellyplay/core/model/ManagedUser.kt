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
    val maxActiveSessions: Int = 0,
    val loginAttemptsBeforeLockout: Int = -1,
)
