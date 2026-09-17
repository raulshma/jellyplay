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

    /** The item-id guard every endpoint opens with: an unparseable UUID fails the call. */
    private fun requireItemUuid(itemId: String) = runCatching { itemId.toUUID() }.getOrThrow()

    // Parse guards for the updateItem DTO builder. They live in non-suspend
    // funs (BareRunCatchingRatchetTest: a bare runCatching inside a suspend
    // body is flagged; these parses cannot throw CancellationException, so
    // the extraction keeps the guard semantics without touching the seam).
    private fun itemIdOrRandom(itemId: String) =
        runCatching { itemId.toUUID() }.getOrNull() ?: java.util.UUID.randomUUID()

    private fun baseItemKindOrMovie(type: String) =
        runCatching { org.jellyfin.sdk.model.api.BaseItemKind.valueOf(toBaseItemKindName(type)) }
            .getOrNull() ?: org.jellyfin.sdk.model.api.BaseItemKind.MOVIE

    private fun parseDateTimeOrNull(raw: String?) =
        raw?.let { runCatching { java.time.LocalDateTime.parse(it) }.getOrNull() }

    private fun dayOfWeekOrNull(dayName: String) =
        runCatching { org.jellyfin.sdk.model.api.DayOfWeek.valueOf(dayName.uppercase()) }.getOrNull()

    private fun metadataFieldOrNull(fieldName: String) =
        runCatching { org.jellyfin.sdk.model.api.MetadataField.valueOf(fieldName) }.getOrNull()

    /** The image-type guard: an unknown wire name fails the call. */
    private fun requireImageType(imageType: String) = ImageType.fromNameOrNull(imageType)
        ?: throw IllegalArgumentException("Unknown image type: $imageType")

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
            id = itemIdOrRandom(itemId),
            name = name,
            type = baseItemKindOrMovie(type),
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
            premiereDate = parseDateTimeOrNull(premiereDate),
            endDate = parseDateTimeOrNull(endDate),
            runTimeTicks = runtimeTicks,
            indexNumber = indexNumber,
            parentIndexNumber = parentIndexNumber,
            displayOrder = displayOrder,
            status = status,
            airDays = airDays.takeIf { it.isNotEmpty() }?.mapNotNull { dayName ->
                dayOfWeekOrNull(dayName)
            },
            airTime = airTime,
            people = people.takeIf { it.isNotEmpty() }?.map { it.toBaseItemPerson() },
            providerIds = providerIds.takeIf { it.isNotEmpty() },
            lockedFields = lockedFields.takeIf { it.isNotEmpty() }?.mapNotNull { fieldName ->
                metadataFieldOrNull(fieldName)
            },
            preferredMetadataLanguage = preferredMetadataLanguage,
            preferredMetadataCountryCode = preferredMetadataCountryCode,
            productionLocations = productionLocations.takeIf { it.isNotEmpty() },
            dateCreated = parseDateTimeOrNull(dateCreated),
            lockData = lockData,
        )
        api.itemUpdateApi.updateItem(itemId = requireItemUuid(itemId), data = dto)
    }

    override suspend fun getMetadataEditorInfo(itemId: String): Result<MetadataEditorInfo> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = requireItemUuid(itemId)
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
        val uuid = requireItemUuid(itemId)
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
        val uuid = requireItemUuid(itemId)
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
        val uuid = requireItemUuid(itemId)
        val type = requireImageType(imageType)
        api.imageApi.setItemImage(
            itemId = uuid,
            imageType = type,
            // Jellyfin 10.11 wire contract (both halves measured — the
            // e2e flows lane + the bootstrap-jellyfin.sh fixture recipe):
            //  1. SetItemImage base64-DECODES the request body
            //     (FromBase64Transform inside ImageSaver) — a raw binary body
            //     500s ("One of the identified items was in an invalid
            //     format"). The subtitle upload's UploadSubtitleDto.data
            //     carries base64 for the same reason.
            //  2. The Content-Type must be a CONCRETE image mime — the
            //     wildcard "image/*" (and application/octet-stream) 400s.
            //     The bytes are sniffed by magic number so jpegs are not
            //     mislabeled; unknown formats fall back to png (the editor's
            //     pickers offer png/jpg/webp/gif/bmp and the server re-encodes
            //     on save).
            // Supported baseline: this is the 10.11+ contract only. The
            // fixture recipe's raw-first/base64-fallback ladder for older
            // servers is NOT attempted here — version-gating upload bodies
            // would need server-version detection that doesn't exist yet;
            // if a pre-base64 ImageSaver server must be supported, add the
            // ladder at that point (measured behavior: 10.11 raw → 500).
            data = java.util.Base64.getEncoder().encodeToString(imageBytes)
                .toByteArray()
                .toFileInfo(mediaType = sniffImageMediaType(imageBytes)),
        )
    }

    /**
     * Magic-number sniff for the image upload wire mime (see the
     * [setItemImage] contract note — a concrete type is REQUIRED).
     * Internal for the table-driven jvmTest coverage of every branch.
     */
    internal fun sniffImageMediaType(bytes: ByteArray): String = when {
        bytes.size >= 4 && bytes[0] == 0x89.toByte() && bytes[1] == 0x50.toByte() &&
            bytes[2] == 0x4E.toByte() && bytes[3] == 0x47.toByte() -> "image/png"
        bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() &&
            bytes[2] == 0xFF.toByte() -> "image/jpeg"
        bytes.size >= 12 && bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
            // RIFF container alone would also match WAV/AVI — require the
            // WEBP fourcc at bytes 8-11 before labeling it image/webp.
            bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() &&
            bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte() -> "image/webp"
        bytes.size >= 6 && bytes[0] == 0x47.toByte() && bytes[1] == 0x49.toByte() &&
            bytes[2] == 0x46.toByte() -> "image/gif"
        bytes.size >= 2 && bytes[0] == 0x42.toByte() && bytes[1] == 0x4D.toByte() -> "image/bmp"
        else -> "image/png"
    }

    override suspend fun deleteItemImage(itemId: String, imageType: String, imageIndex: Int?): Result<Unit> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = requireItemUuid(itemId)
        val type = requireImageType(imageType)
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
        val uuid = requireItemUuid(itemId)
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
        val uuid = requireItemUuid(itemId)
        api.remoteImageApi.getRemoteImageProviders(itemId = uuid).content.map { dto ->
            ImageProviderInfo(
                name = dto.name,
                supportedImages = dto.supportedImages.map { it.serialName },
            )
        }
    }

    override suspend fun downloadRemoteImage(itemId: String, imageType: String, imageUrl: String): Result<Unit> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = requireItemUuid(itemId)
        val type = requireImageType(imageType)
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
        val uuid = requireItemUuid(itemId)
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
        val uuid = requireItemUuid(itemId)
        api.subtitleApi.deleteSubtitle(itemId = uuid, index = index)
    }

    override suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        val uuid = requireItemUuid(itemId)
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
