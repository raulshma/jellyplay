package com.raulshma.jellyplay.core.network.user

import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption
import com.raulshma.jellyplay.core.model.SyncPlayAccessOption
import com.raulshma.jellyplay.core.model.UnratedItemOption
import com.raulshma.jellyplay.core.model.UserAccessSchedule

/**
 * DTO → core.model mappers for the wasm user-management client — commonMain
 * pure functions mirroring the jvmShared `JellyfinDtoMappers` field-for-field
 * (`UserDto.toManagedUser`, `UserPolicy.toManagedPolicy`,
 * `UserPolicy.overlayWith`, the parental-rating grouping in
 * `UserApiClientImpl.getParentalRatings`), so commonTest can pin the parity
 * the wasm client substitutes the SDK with.
 */

/** Mirrors `UserDto.toManagedUser` (jvmShared) field-for-field. */
internal fun ManagedUserDtoWire.toManagedUser() = ManagedUser(
    id = id ?: "",
    name = name ?: "",
    primaryImageTag = primaryImageTag,
    hasPassword = hasPassword,
    hasConfiguredPassword = hasConfiguredPassword,
    lastLoginDate = lastLoginDate,
    lastActivityDate = lastActivityDate,
    policy = policy?.toManagedPolicy() ?: ManagedUserPolicy(),
)

/** Mirrors `UserPolicy.toManagedPolicy` (jvmShared) field-for-field. */
internal fun ManagedUserPolicyDtoWire.toManagedPolicy() = ManagedUserPolicy(
    isAdministrator = isAdministrator,
    isHidden = isHidden,
    isDisabled = isDisabled,
    enableUserPreferenceAccess = enableUserPreferenceAccess,
    enableAllFolders = enableAllFolders,
    enabledFolders = enabledFolders ?: emptyList(),
    enableMediaPlayback = enableMediaPlayback,
    enableAudioPlaybackTranscoding = enableAudioPlaybackTranscoding,
    enableVideoPlaybackTranscoding = enableVideoPlaybackTranscoding,
    enablePlaybackRemuxing = enablePlaybackRemuxing,
    enableContentDeletion = enableContentDeletion,
    enableContentDownloading = enableContentDownloading,
    enableLiveTvAccess = enableLiveTvAccess,
    enableLiveTvManagement = enableLiveTvManagement,
    enableRemoteControlOfOtherUsers = enableRemoteControlOfOtherUsers,
    enableRemoteAccess = enableRemoteAccess,
    maxParentalRating = maxParentalRating,
    maxParentalSubRating = maxParentalSubRating,
    maxActiveSessions = maxActiveSessions,
    loginAttemptsBeforeLockout = loginAttemptsBeforeLockout,
    enableCollectionManagement = enableCollectionManagement,
    enableSubtitleManagement = enableSubtitleManagement,
    forceRemoteSourceTranscoding = forceRemoteSourceTranscoding,
    enableSharedDeviceControl = enableSharedDeviceControl,
    remoteClientBitrateLimit = remoteClientBitrateLimit,
    syncPlayAccess = syncPlayAccess.toSyncPlayOption(),
    enableAllChannels = enableAllChannels,
    enabledChannels = enabledChannels ?: emptyList(),
    enableAllDevices = enableAllDevices,
    enabledDevices = enabledDevices ?: emptyList(),
    enableContentDeletionFromFolders = enableContentDeletionFromFolders ?: emptyList(),
    blockUnratedItems = (blockUnratedItems ?: emptyList()).map { it.toUnratedOption() },
    allowedTags = allowedTags ?: emptyList(),
    blockedTags = blockedTags ?: emptyList(),
    accessSchedules = (accessSchedules ?: emptyList()).map { it.toAppSchedule() },
)

/**
 * Copies every editable [ManagedUserPolicy] field onto a full wire policy,
 * preserving bookkeeping fields (auth provider ids, invalid-login count,
 * public sharing, sync-transcoding/conversion/lyric flags, blocked
 * media-folders/channels). [userId] is the target user's id, required to
 * re-stamp [AccessScheduleDtoWire.userId] — the wire stand-in for the JVM
 * `AccessSchedule.userId` UUID. Used by the wasm
 * `KtorWasmUserApiClient.updateUserPolicy` so non-edited server state is
 * never reset; port of `UserPolicy.overlayWith` (jvmShared).
 */
internal fun ManagedUserPolicyDtoWire.overlayWith(
    edited: ManagedUserPolicy,
    userId: String,
): ManagedUserPolicyDtoWire = copy(
    isAdministrator = edited.isAdministrator,
    isHidden = edited.isHidden,
    isDisabled = edited.isDisabled,
    enableUserPreferenceAccess = edited.enableUserPreferenceAccess,
    enableAllFolders = edited.enableAllFolders,
    enabledFolders = edited.enabledFolders,
    enableMediaPlayback = edited.enableMediaPlayback,
    enableAudioPlaybackTranscoding = edited.enableAudioPlaybackTranscoding,
    enableVideoPlaybackTranscoding = edited.enableVideoPlaybackTranscoding,
    enablePlaybackRemuxing = edited.enablePlaybackRemuxing,
    enableContentDeletion = edited.enableContentDeletion,
    enableContentDownloading = edited.enableContentDownloading,
    enableLiveTvAccess = edited.enableLiveTvAccess,
    enableLiveTvManagement = edited.enableLiveTvManagement,
    enableRemoteControlOfOtherUsers = edited.enableRemoteControlOfOtherUsers,
    enableRemoteAccess = edited.enableRemoteAccess,
    maxParentalRating = edited.maxParentalRating,
    maxParentalSubRating = edited.maxParentalSubRating,
    maxActiveSessions = edited.maxActiveSessions,
    loginAttemptsBeforeLockout = edited.loginAttemptsBeforeLockout,
    enableCollectionManagement = edited.enableCollectionManagement,
    enableSubtitleManagement = edited.enableSubtitleManagement,
    forceRemoteSourceTranscoding = edited.forceRemoteSourceTranscoding,
    enableSharedDeviceControl = edited.enableSharedDeviceControl,
    remoteClientBitrateLimit = edited.remoteClientBitrateLimit,
    syncPlayAccess = edited.syncPlayAccess.toWireName(),
    enableAllChannels = edited.enableAllChannels,
    enabledChannels = edited.enabledChannels,
    enableAllDevices = edited.enableAllDevices,
    enabledDevices = edited.enabledDevices,
    enableContentDeletionFromFolders = edited.enableContentDeletionFromFolders,
    blockUnratedItems = edited.blockUnratedItems.map { it.toWireName() },
    allowedTags = edited.allowedTags,
    blockedTags = edited.blockedTags,
    accessSchedules = edited.accessSchedules.map { it.toWire(userId) },
)

/**
 * Group by (score, subScore), concatenating names on collision so ratings
 * sharing a score/subScore collapse into one label (web parity, e.g.
 * "PG-13/TV-14"); entries without a score are dropped ("No limit" is
 * synthesized client-side). Sorted by score — port of the grouping in
 * `UserApiClientImpl.getParentalRatings` (jvmShared).
 */
internal fun List<ParentalRatingDtoWire>.toParentalRatingOptions(): List<ParentalRatingOption> =
    asSequence()
        .filter { it.ratingScore != null }
        .groupBy { it.ratingScore!!.score to it.ratingScore!!.subScore }
        .map { (key, group) ->
            ParentalRatingOption(
                name = group.joinToString("/") { it.name },
                score = key.first,
                subScore = key.second,
            )
        }
        .sortedBy { it.score }
        .toList()

// --- SDK ↔ app enum mappers (wire names, mirroring JellyfinDtoMappers) ---

/** `SyncPlayUserAccessType.toAppOption` over the wire serial names. */
private fun String.toSyncPlayOption(): SyncPlayAccessOption = when (this) {
    "CreateAndJoinGroups" -> SyncPlayAccessOption.CREATE_AND_JOIN
    "JoinGroups" -> SyncPlayAccessOption.JOIN_ONLY
    else -> SyncPlayAccessOption.NONE
}

/** `SyncPlayAccessOption.toSdk` → the wire serial name. */
private fun SyncPlayAccessOption.toWireName(): String = when (this) {
    SyncPlayAccessOption.CREATE_AND_JOIN -> "CreateAndJoinGroups"
    SyncPlayAccessOption.JOIN_ONLY -> "JoinGroups"
    SyncPlayAccessOption.NONE -> "None"
}

/**
 * `UnratedItem.toAppOption` over the wire serial names — LIVE_TV_PROGRAM /
 * OTHER (and anything unknown) map to MOVIE, the app UI's safe default,
 * exactly like the jvmShared else-branch.
 */
private fun String.toUnratedOption(): UnratedItemOption = when (this) {
    "Book" -> UnratedItemOption.BOOK
    "ChannelContent" -> UnratedItemOption.CHANNEL_CONTENT
    "LiveTvChannel" -> UnratedItemOption.LIVE_TV_CHANNEL
    "Music" -> UnratedItemOption.MUSIC
    "Trailer" -> UnratedItemOption.TRAILER
    "Series" -> UnratedItemOption.SERIES
    else -> UnratedItemOption.MOVIE
}

/** `UnratedItemOption.toSdk` → the wire serial name (exhaustive 7 cases). */
private fun UnratedItemOption.toWireName(): String = when (this) {
    UnratedItemOption.BOOK -> "Book"
    UnratedItemOption.CHANNEL_CONTENT -> "ChannelContent"
    UnratedItemOption.LIVE_TV_CHANNEL -> "LiveTvChannel"
    UnratedItemOption.MOVIE -> "Movie"
    UnratedItemOption.MUSIC -> "Music"
    UnratedItemOption.TRAILER -> "Trailer"
    UnratedItemOption.SERIES -> "Series"
}

/** `AccessSchedule.toAppSchedule` — dayOfWeek is the serial name both ways. */
private fun AccessScheduleDtoWire.toAppSchedule() = UserAccessSchedule(
    id = id,
    dayOfWeek = dayOfWeek ?: "",
    startHour = startHour,
    endHour = endHour,
)

/** `UserAccessSchedule.toSdk` — re-stamps the target user's id. */
private fun UserAccessSchedule.toWire(userId: String) = AccessScheduleDtoWire(
    id = id,
    userId = userId,
    dayOfWeek = dayOfWeek,
    startHour = startHour,
    endHour = endHour,
)
