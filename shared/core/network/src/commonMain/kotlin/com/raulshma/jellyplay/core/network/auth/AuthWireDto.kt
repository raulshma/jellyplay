package com.raulshma.jellyplay.core.network.auth

import com.raulshma.jellyplay.core.model.ServerInfo
import com.raulshma.jellyplay.core.model.UserInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Minimal Jellyfin wire DTOs for the Phase W wasm auth client
 * (docs/kmp-migration-plan.md §Phase W chunk 1).
 *
 * The domain models in `shared/core/model` are @Serializable in their own
 * (camelCase, app-shaped) format — the WIRE format is Jellyfin's PascalCase
 * API schema, which the JVM side gets mapped by the Jellyfin SDK. These DTOs
 * carry the PascalCase `@SerialName`s for exactly the fields the auth client
 * reads, with mapping semantics mirroring the jvmShared
 * `AuthApiClientImpl.toUserInfo` / `probeServerInfo` and
 * `JellyfinApiEngine.cachedCapabilities` constructions.
 *
 * DTO subset grows on demand (plan: scripted generation from openapi.json
 * later); every field is optional-tolerant because the shared Json instance
 * runs with `ignoreUnknownKeys = true` + `isLenient = true` to mirror the
 * SDK's leniency.
 */
@Serializable
data class PublicSystemInfoDto(
    @SerialName("Id") val id: String? = null,
    @SerialName("ServerName") val serverName: String? = null,
)

@Serializable
data class AuthenticateByNameRequestDto(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val pw: String,
)

@Serializable
data class QuickConnectAuthRequestDto(
    @SerialName("Secret") val secret: String,
)

/** Wire subset of the SDK's `UserPolicy` read by the auth login mapping. */
@Serializable
data class UserPolicyWireDto(
    @SerialName("IsAdministrator") val isAdministrator: Boolean? = null,
    @SerialName("EnableContentDeletion") val enableContentDeletion: Boolean? = null,
    @SerialName("MaxParentalRating") val maxParentalRating: Int? = null,
    @SerialName("EnableAllFolders") val enableAllFolders: Boolean? = null,
    @SerialName("EnabledFolders") val enabledFolders: List<String>? = null,
)

/** Wire subset of the SDK's `UserDto` read by the auth login mapping. */
@Serializable
data class UserDtoWire(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("Policy") val policy: UserPolicyWireDto? = null,
)

/** Wire form of the `/Users/AuthenticateByName` + Quick Connect responses. */
@Serializable
data class AuthenticationResultDto(
    @SerialName("User") val user: UserDtoWire? = null,
    @SerialName("AccessToken") val accessToken: String? = null,
)

/** Wire form of the Quick Connect initiate/state responses. */
@Serializable
data class QuickConnectResultDto(
    @SerialName("Authenticated") val authenticated: Boolean = false,
    @SerialName("Secret") val secret: String = "",
    @SerialName("Code") val code: String? = null,
)

/**
 * Wire form of `ClientCapabilitiesDto` for `POST /Sessions/Capabilities/Full`.
 * `deviceProfile` stays a raw [JsonElement] placeholder: the JVM engine sends
 * the full codec-negotiating DeviceProfile built by `DeviceProfileProvider`,
 * which has no wasm equivalent yet (web playback lands with HtmlVideoEngine,
 * Phase W chunk 3+) — the wasm client omits it and documents the cut.
 */
@Serializable
data class ClientCapabilitiesWireDto(
    @SerialName("PlayableMediaTypes") val playableMediaTypes: List<String> = emptyList(),
    @SerialName("SupportedCommands") val supportedCommands: List<String> = emptyList(),
    @SerialName("SupportsMediaControl") val supportsMediaControl: Boolean = false,
    @SerialName("SupportsPersistentIdentifier") val supportsPersistentIdentifier: Boolean = false,
    @SerialName("DeviceProfile") val deviceProfile: JsonElement? = null,
)

/**
 * Serial names of `GeneralCommandType` entries in
 * `JellyfinApiEngine.SUPPORTED_REMOTE_COMMANDS`, in the SAME order — the
 * capabilities payload the wasm client posts must match the JVM engine's
 * command-for-command (server-side remote-control UIs key off this list).
 */
val SUPPORTED_REMOTE_COMMANDS: List<String> = listOf(
    "SetVolume",
    "VolumeUp",
    "VolumeDown",
    "Mute",
    "Unmute",
    "ToggleMute",
    "SetAudioStreamIndex",
    "SetSubtitleStreamIndex",
    "SetRepeatMode",
    "SetShuffleQueue",
    "SetPlaybackOrder",
    "SetMaxStreamingBitrate",
    "ToggleFullscreen",
    "DisplayMessage",
    "Play",
)

/**
 * Capabilities payload mirroring `JellyfinApiEngine.cachedCapabilities`
 * (playable Video+Audio, the remote-command list above, media control on,
 * persistent identifier on) minus the DeviceProfile — see
 * [ClientCapabilitiesWireDto.deviceProfile].
 */
fun defaultClientCapabilities(): ClientCapabilitiesWireDto = ClientCapabilitiesWireDto(
    playableMediaTypes = listOf("Video", "Audio"),
    supportedCommands = SUPPORTED_REMOTE_COMMANDS,
    supportsMediaControl = true,
    supportsPersistentIdentifier = true,
)

/**
 * Maps an authentication response's user DTO to the model [UserInfo] —
 * semantics verbatim from `AuthApiClientImpl.toUserInfo` (jvmShared) so
 * policy fields can't drift between the JVM and wasm login paths.
 */
fun UserDtoWire.toUserInfo(
    serverAddress: String,
    accessToken: String,
    fallbackName: String,
): UserInfo {
    val policy = policy
    return UserInfo(
        id = id,
        name = name ?: fallbackName,
        serverAddress = serverAddress,
        accessToken = accessToken,
        isAdmin = policy?.isAdministrator ?: false,
        canDeleteContent = policy?.enableContentDeletion ?: false,
        maxParentalAgeRating = policy?.maxParentalRating,
        primaryImageTag = primaryImageTag,
        enabledFolderIds = if (policy?.enableAllFolders == false) {
            policy.enabledFolders ?: emptyList()
        } else emptyList(),
    )
}

/**
 * Maps a `GET /System/Info/Public` probe payload to [ServerInfo] — semantics
 * mirroring `AuthApiClientImpl.probeServerInfo`: fall back to a caller-supplied
 * random id / the generic server name when the payload omits them.
 */
fun PublicSystemInfoDto.toServerInfo(
    address: String,
    fallbackServerId: String,
): ServerInfo = ServerInfo(
    id = id ?: fallbackServerId,
    name = serverName ?: "Jellyfin Server",
    address = address,
)
