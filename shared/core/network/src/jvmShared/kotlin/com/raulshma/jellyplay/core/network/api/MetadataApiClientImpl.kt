package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CountryInfo
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.EditorPerson
import com.raulshma.jellyplay.core.model.ExternalIdInfo
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.ImageProviderInfo
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.NameValuePair
import com.raulshma.jellyplay.core.model.ParentalRating
import com.raulshma.jellyplay.core.model.RemoteImageInfo
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemPerson
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MetadataRefreshMode
import org.jellyfin.sdk.model.api.NameGuidPair
import org.jellyfin.sdk.model.api.UploadSubtitleDto
import org.jellyfin.sdk.model.serializer.toUUID
import org.jellyfin.sdk.model.toFileInfo
import org.jellyfin.sdk.api.client.extensions.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : MetadataApiClient {

    override suspend fun updateItem(
        itemId: String, name: String, originalTitle: String?, sortName: String?,
        overview: String?, tagline: String?, genres: List<String>, tags: List<String>,
        studios: List<String>, communityRating: Float?, criticRating: Float?,
        officialRating: String?, customRating: String?, productionYear: Int?,
        premiereDate: String?, endDate: String?, runtimeTicks: Long?,
        indexNumber: Int?, parentIndexNumber: Int?, displayOrder: String?,
        status: String?, airDays: List<String>, airTime: String?,
        people: List<EditorPerson>, providerIds: Map<String, String>,
        lockData: Boolean, lockedFields: List<String>,
        preferredMetadataLanguage: String?, preferredMetadataCountryCode: String?,
        taglines: List<String>, productionLocations: List<String>, dateCreated: String?,
        type: String,
    ): Result<Unit> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val dto = BaseItemDto(
            id = runCatching { itemId.toUUID() }.getOrNull() ?: java.util.UUID.randomUUID(),
            name = name,
            type = runCatching { org.jellyfin.sdk.model.api.BaseItemKind.valueOf(toBaseItemKindName(type)) }.getOrNull() ?: org.jellyfin.sdk.model.api.BaseItemKind.MOVIE,
            originalTitle = originalTitle,
            forcedSortName = sortName,
            overview = overview,
            taglines = taglines.takeIf { it.isNotEmpty() },
            genres = genres.takeIf { it.isNotEmpty() },
            tags = tags.takeIf { it.isNotEmpty() },
            studios = studios.takeIf { it.isNotEmpty() }?.map { NameGuidPair(name = it, id = java.util.UUID.randomUUID()) },
            communityRating = communityRating,
            criticRating = criticRating,
            officialRating = officialRating,
            customRating = customRating,
            productionYear = productionYear,
            premiereDate = premiereDate?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() },
            endDate = endDate?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() },
            runTimeTicks = runtimeTicks,
            indexNumber = indexNumber,
            parentIndexNumber = parentIndexNumber,
            displayOrder = displayOrder,
            status = status,
            airDays = airDays.takeIf { it.isNotEmpty() }?.mapNotNull { dayName ->
                runCatching { org.jellyfin.sdk.model.api.DayOfWeek.valueOf(dayName.uppercase()) }.getOrNull()
            },
            airTime = airTime,
            people = people.takeIf { it.isNotEmpty() }?.map { it.toBaseItemPerson() },
            providerIds = providerIds.takeIf { it.isNotEmpty() },
            lockedFields = lockedFields.takeIf { it.isNotEmpty() }?.mapNotNull { fieldName ->
                runCatching { org.jellyfin.sdk.model.api.MetadataField.valueOf(fieldName) }.getOrNull()
            },
            preferredMetadataLanguage = preferredMetadataLanguage,
            preferredMetadataCountryCode = preferredMetadataCountryCode,
            productionLocations = productionLocations.takeIf { it.isNotEmpty() },
            dateCreated = dateCreated?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() },
            lockData = lockData,
        )
        api.itemUpdateApi.updateItem(itemId = runCatching { itemId.toUUID() }.getOrThrow(), data = dto)
    }

    override suspend fun getMetadataEditorInfo(itemId: String): Result<MetadataEditorInfo> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        val dto = api.itemUpdateApi.getMetadataEditorInfo(itemId = uuid).content
        MetadataEditorInfo(
            parentalRatingOptions = dto.parentalRatingOptions.map { it.toAppParentalRating() },
            contentTypeOptions = dto.contentTypeOptions.map { NameValuePair(name = it.name ?: "", value = it.value ?: "") },
            // Normalize key to lowercase so it matches the lowercased providerIds map
            // built in LibraryApiClientImpl.toMediaDetail — otherwise the editor can't
            // match ExternalIdInfo.key against state.providerIds and shows empty fields.
            externalIdInfos = dto.externalIdInfos.map { ExternalIdInfo(name = it.name, key = it.key.lowercase(), urlFormatString = null) },
            cultures = dto.cultures.map { CultureInfo(
                name = it.name,
                displayName = it.displayName,
                twoLetterISOLanguageName = it.twoLetterIsoLanguageName,
                threeLetterISOLanguageName = it.threeLetterIsoLanguageName,
            ) },
            countries = dto.countries.map { CountryInfo(
                name = it.name ?: "",
                displayName = it.displayName ?: "",
                twoLetterISORegionName = it.twoLetterIsoRegionName,
                threeLetterISORegionName = it.threeLetterIsoRegionName,
            ) },
        )
    }

    override suspend fun refreshItemMetadata(
        itemId: String,
        metadataRefreshMode: String,
        imageRefreshMode: String,
        replaceAllMetadata: Boolean,
        replaceAllImages: Boolean,
        regenerateTrickplay: Boolean,
    ): Result<Unit> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        api.itemRefreshApi.refreshItem(
            itemId = uuid,
            metadataRefreshMode = MetadataRefreshMode.fromNameOrNull(metadataRefreshMode) ?: MetadataRefreshMode.DEFAULT,
            imageRefreshMode = MetadataRefreshMode.fromNameOrNull(imageRefreshMode) ?: MetadataRefreshMode.DEFAULT,
            replaceAllMetadata = replaceAllMetadata,
            replaceAllImages = replaceAllImages,
            regenerateTrickplay = regenerateTrickplay,
        )
    }

    override suspend fun getItemImageInfo(itemId: String): Result<List<ImageInfo>> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        api.imageApi.getItemImageInfos(itemId = uuid).content.map { dto ->
            ImageInfo(
                imageType = dto.imageType.serialName,
                imageIndex = dto.imageIndex ?: 0,
                width = dto.width ?: 0,
                height = dto.height ?: 0,
                blurHash = dto.blurHash,
                imageTag = dto.imageTag,
            )
        }
    }

    override suspend fun setItemImage(itemId: String, imageType: String, imageBytes: ByteArray): Result<Unit> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        val type = ImageType.fromNameOrNull(imageType) ?: throw IllegalArgumentException("Unknown image type: $imageType")
        api.imageApi.setItemImage(
            itemId = uuid,
            imageType = type,
            data = imageBytes.toFileInfo(mediaType = "image/*"),
        )
    }

    override suspend fun deleteItemImage(itemId: String, imageType: String, imageIndex: Int?): Result<Unit> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        val type = ImageType.fromNameOrNull(imageType) ?: throw IllegalArgumentException("Unknown image type: $imageType")
        api.imageApi.deleteItemImage(itemId = uuid, imageType = type, imageIndex = imageIndex)
    }

    override suspend fun getRemoteImages(
        itemId: String,
        imageType: String?,
        provider: String?,
        startIndex: Int?,
        limit: Int?,
    ): Result<RemoteImageResult> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        val dto = api.remoteImageApi.getRemoteImages(
            itemId = uuid,
            type = imageType?.let { ImageType.fromNameOrNull(it) },
            startIndex = startIndex,
            limit = limit ?: 50,
            providerName = provider,
            includeAllLanguages = false,
        ).content
        RemoteImageResult(
            images = dto.images.orEmpty().map { it.toAppRemoteImageInfo() },
            totalRecordCount = dto.totalRecordCount,
            providers = dto.providers ?: emptyList(),
        )
    }

    override suspend fun getRemoteImageProviders(itemId: String): Result<List<ImageProviderInfo>> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        api.remoteImageApi.getRemoteImageProviders(itemId = uuid).content.map { dto ->
            ImageProviderInfo(
                name = dto.name,
                supportedImages = dto.supportedImages.map { it.serialName },
            )
        }
    }

    override suspend fun downloadRemoteImage(itemId: String, imageType: String, imageUrl: String): Result<Unit> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        val type = ImageType.fromNameOrNull(imageType) ?: throw IllegalArgumentException("Unknown image type: $imageType")
        api.remoteImageApi.downloadRemoteImage(itemId = uuid, type = type, imageUrl = imageUrl)
    }

    override suspend fun uploadSubtitle(
        itemId: String,
        data: String,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ): Result<Unit> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        api.subtitleApi.uploadSubtitle(
            itemId = uuid,
            data = UploadSubtitleDto(
                language = language ?: "",
                format = "srt",
                isForced = isForced,
                isHearingImpaired = isHearingImpaired,
                data = data,
            ),
        )
    }

    override suspend fun deleteSubtitle(itemId: String, index: Int): Result<Unit> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        api.subtitleApi.deleteSubtitle(itemId = uuid, index = index)
    }

    override suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = runCatching { itemId.toUUID() }.getOrThrow()
        api.subtitleApi.searchRemoteSubtitles(itemId = uuid, language = language, isPerfectMatch = null).content.map { dto ->
            RemoteSubtitleInfo(
                id = dto.id ?: "",
                threeLetterISOLanguageName = dto.threeLetterIsoLanguageName ?: "",
                // The SDK RemoteSubtitleInfo exposes no free-form `language`
                // field — only `threeLetterIsoLanguageName`. Previously this
                // was set to `dto.id`, which surfaced the subtitle's opaque ID
                // wherever the language badge is rendered (editor + player
                // search results). Use the ISO code instead.
                language = dto.threeLetterIsoLanguageName,
                name = dto.name,
                format = dto.format,
                comment = dto.comment,
                dateCreated = dto.dateCreated?.toString(),
                downloadCount = dto.downloadCount ?: 0,
                isHashMatch = dto.isHashMatch ?: false,
                isForced = dto.forced ?: false,
                isHearingImpaired = dto.hearingImpaired ?: false,
                isAiTranslated = dto.aiTranslated,
                isMachineTranslated = dto.machineTranslated,
                communityRating = dto.communityRating?.toDouble(),
                frameRate = dto.frameRate,
                author = dto.author,
                providerName = dto.providerName,
            )
        }
    }
}

private fun EditorPerson.toBaseItemPerson(): BaseItemPerson = BaseItemPerson(
    id = runCatching { id.toUUID() }.getOrNull() ?: java.util.UUID.randomUUID(),
    name = name,
    role = role,
    type = runCatching { org.jellyfin.sdk.model.api.PersonKind.valueOf(toPersonKindEnumName(type)) }.getOrNull() ?: org.jellyfin.sdk.model.api.PersonKind.UNKNOWN,
    primaryImageTag = primaryImageTag,
)

private fun toBaseItemKindName(raw: String): String = when (raw.lowercase()) {
    "movie" -> "Movie"
    "series" -> "Series"
    "episode" -> "Episode"
    "audio", "music" -> "Audio"
    "musicalbum" -> "MusicAlbum"
    "musicartist" -> "MusicArtist"
    "book" -> "Book"
    "boxset" -> "BoxSet"
    "season" -> "Season"
    "video" -> "Video"
    "photo" -> "Photo"
    "playlist" -> "Playlist"
    else -> raw
}

private fun toPersonKindEnumName(raw: String): String = when (raw.lowercase()) {
    "actor" -> "Actor"
    "director" -> "Director"
    "composer" -> "Composer"
    "writer" -> "Writer"
    "gueststar", "guest_star" -> "GuestStar"
    "producer" -> "Producer"
    "albumartist", "album_artist" -> "AlbumArtist"
    "artist" -> "Artist"
    "author" -> "Author"
    "lyricist" -> "Lyricist"
    else -> raw
}

private fun org.jellyfin.sdk.model.api.ParentalRating.toAppParentalRating(): ParentalRating = ParentalRating(
    name = name,
    value = value ?: 0,
)

private fun org.jellyfin.sdk.model.api.RemoteImageInfo.toAppRemoteImageInfo(): RemoteImageInfo = RemoteImageInfo(
    providerName = providerName ?: "",
    url = url ?: "",
    thumbnailUrl = thumbnailUrl ?: "",
    height = height ?: 0,
    width = width ?: 0,
    language = language,
    communityRating = communityRating,
    voteCount = voteCount,
    ratingType = ratingType.serialName.hashCode(),
)
