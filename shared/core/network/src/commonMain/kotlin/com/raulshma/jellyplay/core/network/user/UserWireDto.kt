package com.raulshma.jellyplay.core.network.user

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire DTOs for the Phase W wasm user-management client (UserApiClient on
 * wasmJs), mirroring the Jellyfin SDK schema PascalCase-for-PascalCase.
 *
 * DTO ORGANIZATION CHOICE (documented): these live in their own `user/`
 * package instead of extending `auth/AuthWireDto.kt`'s `UserDtoWire` /
 * `UserPolicyWireDto`. The auth pair is the deliberately tiny LOGIN subset
 * (non-null `Id`, 5 policy fields) that `toUserInfo` maps at sign-in; the
 * user-management flows here need the FULL Jellyfin `UserDto`/`UserPolicy`
 * schema — the rename flow POSTs the whole DTO back and the policy flow
 * overlays ~35 editable fields while preserving server bookkeeping. Growing
 * auth's DTOs to that surface would couple login decoding to
 * user-management churn for zero shared readers; separate DTOs keep the
 * auth path untouched and each package's wire surface reviewable in
 * isolation (same per-feature packaging as `auth/`, `library/`,
 * `playback/`).
 *
 * Field-by-field semantics mirror the jvmShared `JellyfinDtoMappers`
 * (`UserDto.toManagedUser`, `UserPolicy.toManagedPolicy`,
 * `UserPolicy.overlayWith`) — see [UserWireMappers.kt]. Every field is
 * optional-tolerant (defaults) because the shared wasm Json runs with
 * `ignoreUnknownKeys = true` + `isLenient = true`; the defaults equal the
 * jvmShared fallback construction in `UserApiClientImpl.updateUserPolicy`
 * (all-false policy, lockout -1, empty provider ids, SyncPlayAccess None)
 * so a missing-`Policy` user POSTs the same merged shape the JVM would.
 * Wire-encoding note: the shared wasm Json uses kotlinx's default
 * `encodeDefaults = false`, matching the SDK's serializer — default-valued
 * fields are omitted from POST bodies exactly like the JVM wire.
 */

/**
 * Full wire form of the Jellyfin `UserDto`. `Configuration` stays a raw
 * [JsonElement] placeholder: nothing in the user flows reads it, and the
 * raw tree round-trips the rename POST's GET-modify-POST body losslessly
 * (the JVM SDK decodes into a typed `UserConfiguration` and re-encodes; the
 * raw element preserves the server's own bytes instead).
 *
 * Date-typed fields (`LastLoginDate`/`LastActivityDate`) keep the raw wire
 * strings — the same documented convention as the wasm library client
 * (jvmShared maps them through the SDK `DateTime` and `toString()`).
 */
@Serializable
data class ManagedUserDtoWire(
    @SerialName("Name") val name: String? = null,
    @SerialName("ServerId") val serverId: String? = null,
    @SerialName("ServerName") val serverName: String? = null,
    @SerialName("Id") val id: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("HasPassword") val hasPassword: Boolean = false,
    @SerialName("HasConfiguredPassword") val hasConfiguredPassword: Boolean = false,
    @SerialName("HasConfiguredEasyPassword") val hasConfiguredEasyPassword: Boolean = false,
    @SerialName("EnableAutoLogin") val enableAutoLogin: Boolean? = null,
    @SerialName("LastLoginDate") val lastLoginDate: String? = null,
    @SerialName("LastActivityDate") val lastActivityDate: String? = null,
    @SerialName("Configuration") val configuration: JsonElement? = null,
    @SerialName("Policy") val policy: ManagedUserPolicyDtoWire? = null,
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
)

/**
 * Full wire form of the Jellyfin `UserPolicy` — every field of the SDK
 * class, with Kotlin defaults set to the exact values of the jvmShared
 * fallback construction in `UserApiClientImpl.updateUserPolicy` (so a
 * missing server policy and the JVM default produce the same wire shape).
 * Bookkeeping fields (`InvalidLoginAttemptCount`, the provider ids,
 * `EnablePublicSharing`, `EnableSyncTranscoding`, `EnableMediaConversion`,
 * `EnableLyricManagement`, `BlockedMediaFolders`/`BlockedChannels`) are
 * decoded and re-POSTed but never edited — see [overlayWith].
 *
 * `SyncPlayAccess`/`BlockUnratedItems`/`AccessSchedules` carry the SDK
 * enum serial names as plain strings/DTOs; [UserWireMappers] owns the
 * enum round-trip.
 */
@Serializable
data class ManagedUserPolicyDtoWire(
    // General
    @SerialName("IsAdministrator") val isAdministrator: Boolean = false,
    @SerialName("IsHidden") val isHidden: Boolean = false,
    @SerialName("IsDisabled") val isDisabled: Boolean = false,
    @SerialName("EnableUserPreferenceAccess") val enableUserPreferenceAccess: Boolean = false,
    // Profile tab (server management / playback control / syncplay)
    @SerialName("EnableCollectionManagement") val enableCollectionManagement: Boolean = false,
    @SerialName("EnableSubtitleManagement") val enableSubtitleManagement: Boolean = false,
    @SerialName("EnableLyricManagement") val enableLyricManagement: Boolean = false,
    @SerialName("EnableSharedDeviceControl") val enableSharedDeviceControl: Boolean = false,
    @SerialName("ForceRemoteSourceTranscoding") val forceRemoteSourceTranscoding: Boolean = false,
    @SerialName("RemoteClientBitrateLimit") val remoteClientBitrateLimit: Int = 0,
    @SerialName("SyncPlayAccess") val syncPlayAccess: String = SYNC_PLAY_ACCESS_NONE,
    // Permissions
    @SerialName("EnableRemoteControlOfOtherUsers") val enableRemoteControlOfOtherUsers: Boolean = false,
    @SerialName("EnableRemoteAccess") val enableRemoteAccess: Boolean = false,
    @SerialName("EnableLiveTvManagement") val enableLiveTvManagement: Boolean = false,
    @SerialName("EnableLiveTvAccess") val enableLiveTvAccess: Boolean = false,
    @SerialName("EnableMediaPlayback") val enableMediaPlayback: Boolean = false,
    @SerialName("EnableAudioPlaybackTranscoding") val enableAudioPlaybackTranscoding: Boolean = false,
    @SerialName("EnableVideoPlaybackTranscoding") val enableVideoPlaybackTranscoding: Boolean = false,
    @SerialName("EnablePlaybackRemuxing") val enablePlaybackRemuxing: Boolean = false,
    @SerialName("EnableContentDeletion") val enableContentDeletion: Boolean = false,
    @SerialName("EnableContentDownloading") val enableContentDownloading: Boolean = false,
    // Limits
    @SerialName("MaxParentalRating") val maxParentalRating: Int? = null,
    @SerialName("MaxParentalSubRating") val maxParentalSubRating: Int? = null,
    @SerialName("MaxActiveSessions") val maxActiveSessions: Int = 0,
    @SerialName("LoginAttemptsBeforeLockout") val loginAttemptsBeforeLockout: Int = -1,
    // Access (folders)
    @SerialName("EnableAllFolders") val enableAllFolders: Boolean = false,
    @SerialName("EnabledFolders") val enabledFolders: List<String>? = null,
    // Access tab (channels / devices / deletion folders)
    @SerialName("EnableAllChannels") val enableAllChannels: Boolean = false,
    @SerialName("EnabledChannels") val enabledChannels: List<String>? = null,
    @SerialName("EnableAllDevices") val enableAllDevices: Boolean = false,
    @SerialName("EnabledDevices") val enabledDevices: List<String>? = null,
    @SerialName("EnableContentDeletionFromFolders") val enableContentDeletionFromFolders: List<String>? = null,
    // Parental tab
    @SerialName("BlockUnratedItems") val blockUnratedItems: List<String>? = null,
    @SerialName("AllowedTags") val allowedTags: List<String>? = null,
    @SerialName("BlockedTags") val blockedTags: List<String>? = null,
    @SerialName("AccessSchedules") val accessSchedules: List<AccessScheduleDtoWire>? = null,
    // Bookkeeping — decoded/re-POSTed, never edited
    @SerialName("InvalidLoginAttemptCount") val invalidLoginAttemptCount: Int = 0,
    @SerialName("EnablePublicSharing") val enablePublicSharing: Boolean = false,
    @SerialName("BlockedMediaFolders") val blockedMediaFolders: List<String>? = null,
    @SerialName("BlockedChannels") val blockedChannels: List<String>? = null,
    @SerialName("EnableSyncTranscoding") val enableSyncTranscoding: Boolean = false,
    @SerialName("EnableMediaConversion") val enableMediaConversion: Boolean = false,
    @SerialName("AuthenticationProviderId") val authenticationProviderId: String = "",
    @SerialName("PasswordResetProviderId") val passwordResetProviderId: String = "",
)

/**
 * Wire form of the SDK `AccessSchedule`. [userId] is the target user's id —
 * the overlay re-stamps it on every POST (JVM:
 * `UserAccessSchedule.toSdk(userId.toUUID())`). [dayOfWeek] is the
 * `DynamicDayOfWeek` serial name ("Sunday".."Everyday"), which is exactly
 * what [com.raulshma.jellyplay.core.model.UserAccessSchedule.dayOfWeek]
 * stores, so both directions are pass-throughs.
 */
@Serializable
data class AccessScheduleDtoWire(
    @SerialName("Id") val id: Int = 0,
    @SerialName("UserId") val userId: String? = null,
    @SerialName("DayOfWeek") val dayOfWeek: String? = null,
    @SerialName("StartHour") val startHour: Double = 0.0,
    @SerialName("EndHour") val endHour: Double = 0.0,
)

/** Wire form of `POST /Users/New` (SDK `CreateUserByName`). */
@Serializable
data class CreateUserByNameRequestDtoWire(
    @SerialName("Name") val name: String,
    @SerialName("Password") val password: String? = null,
)

/**
 * Wire form of `POST /Users/Password` (SDK `UpdateUserPassword`). The JVM
 * impl always sends `currentPw = null`, `newPw = newPassword`,
 * `resetPassword = newPassword == null` — resetting when no new password is
 * given (an absent admin password clears it).
 */
@Serializable
data class UpdateUserPasswordRequestDtoWire(
    @SerialName("CurrentPw") val currentPw: String? = null,
    @SerialName("NewPw") val newPw: String? = null,
    @SerialName("ResetPassword") val resetPassword: Boolean,
)

/**
 * Wire form of the `GET /Localization/ParentalRatings` entries (SDK
 * `ParentalRating`). NOTE the schema quirk kept verbatim: `RatingScore`'s
 * own members serialize LOWERCASE-first (`score`/`subScore`), unlike every
 * other PascalCase field.
 */
@Serializable
data class ParentalRatingDtoWire(
    @SerialName("Name") val name: String = "",
    @SerialName("Value") val value: Int? = null,
    @SerialName("RatingScore") val ratingScore: ParentalRatingScoreDtoWire? = null,
)

@Serializable
data class ParentalRatingScoreDtoWire(
    @SerialName("score") val score: Int,
    @SerialName("subScore") val subScore: Int? = null,
)

/** `SyncPlayUserAccessType.NONE` serial name (see [ManagedUserPolicyDtoWire]). */
const val SYNC_PLAY_ACCESS_NONE = "None"
