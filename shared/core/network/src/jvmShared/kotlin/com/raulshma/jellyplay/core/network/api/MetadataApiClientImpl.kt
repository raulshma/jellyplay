package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.CountryInfo
import com.raulshma.jellyplay.core.model.CultureInfo
import com.raulshma.jellyplay.core.model.EditableItemMetadata
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
import org.jellyfin.sdk.api.client.HttpMethod
import org.jellyfin.sdk.api.client.extensions.*

class MetadataApiClientImpl(
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
        itemId: String,
        metadata: EditableItemMetadata,
    ): Result<Unit> = engine.withApi { api ->
        val dto = BaseItemDto(
            id = itemIdOrRandom(itemId),
            name = metadata.name,
            type = baseItemKindOrMovie(metadata.type),
            originalTitle = metadata.originalTitle,
            forcedSortName = metadata.sortName,
            overview = metadata.overview,
            taglines = metadata.taglines.takeIf { it.isNotEmpty() },
            genres = metadata.genres.takeIf { it.isNotEmpty() },
            tags = metadata.tags.takeIf { it.isNotEmpty() },
            studios = metadata.studios.takeIf { it.isNotEmpty() }?.map { NameGuidPair(name = it, id = java.util.UUID.randomUUID()) },
            communityRating = metadata.communityRating,
            criticRating = metadata.criticRating,
            officialRating = metadata.officialRating,
            customRating = metadata.customRating,
            productionYear = metadata.productionYear,
            premiereDate = parseDateTimeOrNull(metadata.premiereDate),
            endDate = parseDateTimeOrNull(metadata.endDate),
            runTimeTicks = metadata.runtimeTicks,
            indexNumber = metadata.indexNumber,
            parentIndexNumber = metadata.parentIndexNumber,
            displayOrder = metadata.displayOrder,
            status = metadata.status,
            airDays = metadata.airDays.takeIf { it.isNotEmpty() }?.mapNotNull { dayName ->
                dayOfWeekOrNull(dayName)
            },
            airTime = metadata.airTime,
            people = metadata.people.takeIf { it.isNotEmpty() }?.map { it.toBaseItemPerson() },
            providerIds = metadata.providerIds.takeIf { it.isNotEmpty() },
            lockedFields = metadata.lockedFields.takeIf { it.isNotEmpty() }?.mapNotNull { fieldName ->
                metadataFieldOrNull(fieldName)
            },
            preferredMetadataLanguage = metadata.preferredMetadataLanguage,
            preferredMetadataCountryCode = metadata.preferredMetadataCountryCode,
            productionLocations = metadata.productionLocations.takeIf { it.isNotEmpty() },
            dateCreated = parseDateTimeOrNull(metadata.dateCreated),
            lockData = metadata.lockData,
        )
        api.itemUpdateApi.updateItem(itemId = requireItemUuid(itemId), data = dto)
    }

    override suspend fun getMetadataEditorInfo(itemId: String): Result<MetadataEditorInfo> = engine.withApi { api ->
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
    ): Result<Unit> = engine.withApi { api ->
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

    // ── Identify (remote search + apply) ────────────────────────────────
    // The itemLookupApi was unused in the SDK pin until this feature; the
    // per-type query DTOs differ only in the searchInfo shape, so the dispatch
    // is a when over the wire type with a per-type searchInfo builder.

    override suspend fun identifyRemoteSearch(query: com.raulshma.jellyplay.core.model.IdentifyQuery): Result<List<com.raulshma.jellyplay.core.model.IdentifyResult>> =
        engine.withApi { api ->
            val uuid = requireItemUuid(query.itemId)
            val providerIds = query.providerIds.takeIf { it.isNotEmpty() }?.mapValues { it.value as String? }
            val results: List<org.jellyfin.sdk.model.api.RemoteSearchResult> = when (query.itemType) {
                com.raulshma.jellyplay.core.model.IdentifyItemType.SERIES -> api.itemLookupApi.getSeriesRemoteSearchResults(
                    org.jellyfin.sdk.model.api.SeriesInfoRemoteSearchQuery(
                        searchInfo = org.jellyfin.sdk.model.api.SeriesInfo(
                            name = query.name,
                            providerIds = providerIds,
                            year = query.year,
                            isAutomated = false,
                        ),
                        itemId = uuid,
                        searchProviderName = null,
                        includeDisabledProviders = false,
                    ),
                ).content
                com.raulshma.jellyplay.core.model.IdentifyItemType.MOVIE -> api.itemLookupApi.getMovieRemoteSearchResults(
                    org.jellyfin.sdk.model.api.MovieInfoRemoteSearchQuery(
                        searchInfo = org.jellyfin.sdk.model.api.MovieInfo(
                            name = query.name,
                            providerIds = providerIds,
                            year = query.year,
                            isAutomated = false,
                        ),
                        itemId = uuid,
                        searchProviderName = null,
                        includeDisabledProviders = false,
                    ),
                ).content
            }
            results.map { dto ->
                com.raulshma.jellyplay.core.model.IdentifyResult(
                    name = dto.name ?: "",
                    year = dto.productionYear,
                    providerIds = dto.providerIds.orEmpty().mapNotNull { (k, v) -> v?.let { k.lowercase() to it } }.toMap(),
                    searchProviderName = dto.searchProviderName,
                    imageUrl = dto.imageUrl,
                    overview = dto.overview,
                    // The apply endpoint posts this DTO back verbatim (below).
                    raw = dto,
                )
            }
        }

    override suspend fun applyIdentifyResult(
        itemId: String,
        result: com.raulshma.jellyplay.core.model.IdentifyResult,
        replaceAllImages: Boolean,
    ): Result<Unit> = engine.withApi { api ->
        api.itemLookupApi.applySearchCriteria(
            itemId = requireItemUuid(itemId),
            replaceAllImages = replaceAllImages,
            // jellyfin-web parity: the applied payload is the server's ORIGINAL
            // RemoteSearchResult, untouched (provider-id key case, every field
            // the model doesn't mirror). The trimmed rebuild is only a fallback
            // for hand-constructed results that never came from a search.
            data = result.raw as? org.jellyfin.sdk.model.api.RemoteSearchResult
                ?: org.jellyfin.sdk.model.api.RemoteSearchResult(
                    name = result.name,
                    providerIds = result.providerIds.mapValues { it.value as String? },
                    productionYear = result.year,
                    imageUrl = result.imageUrl,
                    searchProviderName = result.searchProviderName,
                    overview = result.overview,
                ),
        )
    }

    // ── Version group/split (jellyfin-web parity, admin) ────────────────
    // The Jellyfin SDK has no typed API for either endpoint, so both ride the
    // raw-path escape hatch (the AdminApiClientImpl /System/Logs/Log
    // precedent). Both endpoints require elevation — the server 403s
    // non-admins; the UI gates the entries on the same isAdmin seam.

    override suspend fun mergeVersions(itemIds: List<String>): Result<Unit> {
        // Fail fast on a degenerate call: the server rejects < 2 ids with 400,
        // and a client-side guard keeps the error message local.
        if (itemIds.size < 2) {
            throw IllegalArgumentException("mergeVersions requires at least 2 item ids")
        }
        return engine.withApi { api ->
            api.request(
                method = HttpMethod.POST,
                pathTemplate = MERGE_VERSIONS_PATH,
                queryParameters = mapOf(MERGE_VERSIONS_IDS_QUERY to mergeVersionsIdsValue(itemIds)),
            )
        }
    }

    override suspend fun splitVersions(itemId: String): Result<Unit> = engine.withApi { api ->
        api.request(
            method = HttpMethod.DELETE,
            pathTemplate = splitVersionsPath(itemId),
        )
    }

    override suspend fun getItemImageInfo(itemId: String): Result<List<ImageInfo>> = engine.withApi { api ->
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

    override suspend fun setItemImage(itemId: String, imageType: String, imageBytes: ByteArray): Result<Unit> = engine.withApi { api ->
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

    override suspend fun deleteItemImage(itemId: String, imageType: String, imageIndex: Int?): Result<Unit> = engine.withApi { api ->
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
    ): Result<RemoteImageResult> = engine.withApi { api ->
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

    override suspend fun getRemoteImageProviders(itemId: String): Result<List<ImageProviderInfo>> = engine.withApi { api ->
        val uuid = requireItemUuid(itemId)
        api.remoteImageApi.getRemoteImageProviders(itemId = uuid).content.map { dto ->
            ImageProviderInfo(
                name = dto.name,
                supportedImages = dto.supportedImages.map { it.serialName },
            )
        }
    }

    override suspend fun downloadRemoteImage(itemId: String, imageType: String, imageUrl: String): Result<Unit> = engine.withApi { api ->
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
    ): Result<Unit> = engine.withApi { api ->
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

    override suspend fun deleteSubtitle(itemId: String, index: Int): Result<Unit> = engine.withApi { api ->
        val uuid = requireItemUuid(itemId)
        api.subtitleApi.deleteSubtitle(itemId = uuid, index = index)
    }

    override suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>> = engine.withApi { api ->
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

// ── Merge/Split raw-path request shapes (pure, jvmTest-covered) ─────────
// Extracted so the wire contract — path template + comma-joined ids query —
// has a direct test surface without an engine.

internal const val MERGE_VERSIONS_PATH: String = "/Videos/MergeVersions"
internal const val MERGE_VERSIONS_IDS_QUERY: String = "ids"

/** The `ids` query value: the item ids comma-joined in call order. */
internal fun mergeVersionsIdsValue(itemIds: List<String>): String = itemIds.joinToString(",")

/** The split path template: `/Videos/{itemId}/AlternateSources`. */
internal fun splitVersionsPath(itemId: String): String = "/Videos/$itemId/AlternateSources"
