package com.raulshma.jellyplay.core.network.library

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Minimal Jellyfin wire DTOs for the Phase W wasm library client
 * (docs/kmp-migration-plan.md §Phase W chunk 2), following the chunk-1
 * `auth/AuthWireDto.kt` pattern: PascalCase `@SerialName`s for exactly the
 * fields the library/playback mappers read, decoded with the SDK-lenient
 * shared Json instance (`ignoreUnknownKeys + isLenient`).
 *
 * Field mapping semantics mirror the jvmShared `JellyfinDtoMappers` (THE
 * source of truth) field-for-field; see `LibraryWireMappers.kt`. Date fields
 * stay raw wire strings — the SDK's zone-shifted `DateTime` re-formatting is
 * a documented wasm delta (KDoc on the mappers).
 *
 * DTO subset grows on demand (plan: scripted generation from openapi.json
 * later).
 */
@Serializable
data class BaseItemDtoWire(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("UserData") val userData: UserItemDataDtoWire? = null,
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("Genres") val genres: List<String>? = null,
    @SerialName("Studios") val studios: List<NameItemDtoWire>? = null,
    @SerialName("Tags") val tags: List<String>? = null,
    @SerialName("ParentId") val parentId: String? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("AlbumArtist") val albumArtist: String? = null,
    @SerialName("Album") val album: String? = null,
    @SerialName("ArtistItems") val artistItems: List<NameItemDtoWire>? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String>? = null,
    @SerialName("BackdropImageTags") val backdropImageTags: List<String>? = null,
    @SerialName("ImageBlurHashes") val imageBlurHashes: Map<String, Map<String, String>>? = null,
    @SerialName("NormalizationGain") val normalizationGain: Double? = null,
    @SerialName("People") val people: List<PersonDtoWire>? = null,
    @SerialName("Chapters") val chapters: List<ChapterDtoWire>? = null,
    @SerialName("MediaSources") val mediaSources: List<MediaSourceInfoWire>? = null,
    @SerialName("ExternalUrls") val externalUrls: List<ExternalUrlDtoWire>? = null,
    // Nullable values, as in the SDK model: servers DO send null provider ids
    // (the detail mapper drops them, mirroring LibraryApiClientImpl).
    @SerialName("ProviderIds") val providerIds: Map<String, String?>? = null,
    @SerialName("ForcedSortName") val forcedSortName: String? = null,
    @SerialName("CustomRating") val customRating: String? = null,
    @SerialName("CriticRating") val criticRating: Double? = null,
    @SerialName("Taglines") val taglines: List<String>? = null,
    @SerialName("ProductionLocations") val productionLocations: List<String>? = null,
    @SerialName("LockData") val lockData: Boolean? = null,
    @SerialName("LockedFields") val lockedFields: List<String>? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("AirDays") val airDays: List<String>? = null,
    @SerialName("AirTime") val airTime: String? = null,
    @SerialName("DisplayOrder") val displayOrder: String? = null,
    @SerialName("PreferredMetadataLanguage") val preferredMetadataLanguage: String? = null,
    @SerialName("PreferredMetadataCountryCode") val preferredMetadataCountryCode: String? = null,
    @SerialName("DateCreated") val dateCreated: String? = null,
    @SerialName("CanDelete") val canDelete: Boolean? = null,
    @SerialName("PlaylistItemId") val playlistItemId: String? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("Trickplay") val trickplay: Map<String, Map<String, TrickplayInfoDtoWire>>? = null,
)

/** Wire subset of the SDK `UserItemDataDto` the item mapper reads. */
@Serializable
data class UserItemDataDtoWire(
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long? = null,
    @SerialName("Played") val played: Boolean? = null,
    @SerialName("IsFavorite") val isFavorite: Boolean? = null,
    @SerialName("PlayCount") val playCount: Int? = null,
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
    @SerialName("UnplayedItemCount") val unplayedItemCount: Int? = null,
)

/** A `{ Id, Name }` pair (studios, artist items). */
@Serializable
data class NameItemDtoWire(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
)

/** Wire subset of the SDK `BaseItemPerson`. */
@Serializable
data class PersonDtoWire(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

/** Wire subset of the SDK `ChapterInfo`. */
@Serializable
data class ChapterDtoWire(
    @SerialName("Name") val name: String? = null,
    @SerialName("StartPositionTicks") val startPositionTicks: Long? = null,
    @SerialName("ImageDateModified") val imageDateModified: String? = null,
    @SerialName("ImageTag") val imageTag: String? = null,
)

/** Wire subset of the SDK `ExternalUrl`. */
@Serializable
data class ExternalUrlDtoWire(
    @SerialName("Name") val name: String? = null,
    @SerialName("Url") val url: String? = null,
)

/** Wire subset of the SDK `MediaSourceInfo` (shared with the playback client). */
@Serializable
data class MediaSourceInfoWire(
    @SerialName("Id") val id: String? = null,
    @SerialName("Name") val name: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Bitrate") val bitrate: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("SupportsTranscoding") val supportsTranscoding: Boolean? = null,
    @SerialName("SupportsDirectStream") val supportsDirectStream: Boolean? = null,
    @SerialName("SupportsDirectPlay") val supportsDirectPlay: Boolean? = null,
    @SerialName("TranscodingUrl") val transcodingUrl: String? = null,
    @SerialName("LiveStreamId") val liveStreamId: String? = null,
    @SerialName("RequiresOpening") val requiresOpening: Boolean? = null,
    @SerialName("Path") val path: String? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDtoWire>? = null,
)

/** Wire subset of the SDK `MediaStream`. */
@Serializable
data class MediaStreamDtoWire(
    @SerialName("Index") val index: Int = 0,
    @SerialName("Type") val type: String? = null,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("IsDefault") val isDefault: Boolean? = null,
    @SerialName("IsForced") val isForced: Boolean? = null,
    @SerialName("IsHearingImpaired") val isHearingImpaired: Boolean? = null,
    @SerialName("IsExternal") val isExternal: Boolean? = null,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("BitRate") val bitRate: Int? = null,
    @SerialName("SampleRate") val sampleRate: Int? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("DeliveryUrl") val deliveryUrl: String? = null,
    @SerialName("VideoRange") val videoRange: String? = null,
    @SerialName("VideoRangeType") val videoRangeType: String? = null,
    @SerialName("RealFrameRate") val realFrameRate: Double? = null,
    @SerialName("VideoDoViTitle") val videoDoViTitle: String? = null,
)

/** Wire subset of the SDK `TrickplayInfoDto`. */
@Serializable
data class TrickplayInfoDtoWire(
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("TileWidth") val tileWidth: Int? = null,
    @SerialName("TileHeight") val tileHeight: Int? = null,
    @SerialName("ThumbnailCount") val thumbnailCount: Int? = null,
    @SerialName("Interval") val interval: Int? = null,
    @SerialName("Bandwidth") val bandwidth: Int? = null,
)

/** Wire form of `BaseItemDtoQueryResult` (Items/NextUp/Resume/Genres/…). */
@Serializable
data class BaseItemQueryResultDtoWire(
    @SerialName("Items") val items: List<BaseItemDtoWire> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
    @SerialName("StartIndex") val startIndex: Int = 0,
)

/** Wire form of the `/Items/{id}/ThemeSongs` `ThemeMediaResult` wrapper. */
@Serializable
data class ThemeMediaResultDtoWire(
    @SerialName("Items") val items: List<BaseItemDtoWire> = emptyList(),
)

/** Wire form of `CollectionCreationResult` / `PlaylistCreationResult`. */
@Serializable
data class IdResultDtoWire(
    @SerialName("Id") val id: String? = null,
)

/** Wire form of the `/Audio/{id}/Lyrics` `LyricDto`. */
@Serializable
data class LyricsDtoWire(
    @SerialName("Lyrics") val lyrics: List<LyricLineDtoWire> = emptyList(),
)

@Serializable
data class LyricLineDtoWire(
    @SerialName("Text") val text: String = "",
    @SerialName("Start") val start: Long? = null,
    @SerialName("Cues") val cues: List<LyricLineCueDtoWire>? = null,
)

@Serializable
data class LyricLineCueDtoWire(
    @SerialName("Position") val position: Int = 0,
    @SerialName("EndPosition") val endPosition: Int = 0,
    @SerialName("Start") val start: Long = 0,
    @SerialName("End") val end: Long? = null,
)

/** Wire form of the SDK `CreatePlaylistDto` posted to `POST /Playlists`. */
@Serializable
data class CreatePlaylistRequestDtoWire(
    @SerialName("Name") val name: String? = null,
    @SerialName("Ids") val ids: List<String> = emptyList(),
    @SerialName("UserId") val userId: String? = null,
    @SerialName("MediaType") val mediaType: String? = null,
    @SerialName("Users") val users: List<String> = emptyList(),
    @SerialName("IsPublic") val isPublic: Boolean = false,
)

/**
 * Wire form of the SDK `UpdatePlaylistDto` posted to `POST /Playlists/{id}`.
 * No `Overview` field: the jvmShared `updatePlaylist` never sends one either
 * (its UpdatePlaylistDto carries only name/isPublic) — mirrored verbatim.
 */
@Serializable
data class UpdatePlaylistRequestDtoWire(
    @SerialName("Name") val name: String? = null,
    @SerialName("IsPublic") val isPublic: Boolean? = null,
)
