package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.EditableItemMetadata
import com.raulshma.jellyplay.core.model.IdentifyQuery
import com.raulshma.jellyplay.core.model.IdentifyResult
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.ImageProviderInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.MetadataRefreshParams
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.MetadataApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient

class MetadataEditorRepositoryImpl constructor(
    private val libraryApiClient: LibraryApiClient,
    private val metadataApiClient: MetadataApiClient,
    private val playbackApiClient: PlaybackApiClient,
) : MetadataEditorRepository {

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> =
        libraryApiClient.getMediaDetail(itemId)

    override suspend fun getMetadataEditorInfo(itemId: String): Result<MetadataEditorInfo> =
        metadataApiClient.getMetadataEditorInfo(itemId)

    override suspend fun updateItem(itemId: String, metadata: EditableItemMetadata): Result<Unit> =
        metadataApiClient.updateItem(
            itemId, metadata.name, metadata.originalTitle, metadata.sortName,
            metadata.overview, metadata.tagline, metadata.genres, metadata.tags,
            metadata.studios, metadata.communityRating, metadata.criticRating,
            metadata.officialRating, metadata.customRating, metadata.productionYear,
            metadata.premiereDate, metadata.endDate, metadata.runtimeTicks,
            metadata.indexNumber, metadata.parentIndexNumber, metadata.displayOrder,
            metadata.status, metadata.airDays, metadata.airTime, metadata.people,
            metadata.providerIds, metadata.lockData, metadata.lockedFields,
            metadata.preferredMetadataLanguage, metadata.preferredMetadataCountryCode,
            metadata.taglines, metadata.productionLocations, metadata.dateCreated,
            metadata.type,
        )

    override suspend fun refreshItemMetadata(
        itemId: String,
        params: MetadataRefreshParams,
    ): Result<Unit> = metadataApiClient.refreshItemMetadata(
        itemId,
        params.metadataRefreshMode,
        params.imageRefreshMode,
        params.replaceAllMetadata,
        params.replaceAllImages,
    )

    override suspend fun getItemImageInfo(itemId: String): Result<List<ImageInfo>> =
        metadataApiClient.getItemImageInfo(itemId)

    override suspend fun setItemImage(itemId: String, imageType: String, imageBytes: ByteArray): Result<Unit> =
        metadataApiClient.setItemImage(itemId, imageType, imageBytes)

    override suspend fun deleteItemImage(itemId: String, imageType: String, imageIndex: Int?): Result<Unit> =
        metadataApiClient.deleteItemImage(itemId, imageType, imageIndex)

    override suspend fun getRemoteImages(
        itemId: String,
        imageType: String?,
        provider: String?,
        startIndex: Int?,
        limit: Int?,
    ): Result<RemoteImageResult> = metadataApiClient.getRemoteImages(itemId, imageType, provider, startIndex, limit)

    override suspend fun getRemoteImageProviders(itemId: String): Result<List<ImageProviderInfo>> =
        metadataApiClient.getRemoteImageProviders(itemId)

    override suspend fun downloadRemoteImage(itemId: String, imageType: String, imageUrl: String): Result<Unit> =
        metadataApiClient.downloadRemoteImage(itemId, imageType, imageUrl)

    override suspend fun uploadSubtitle(
        itemId: String,
        data: String,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ): Result<Unit> = metadataApiClient.uploadSubtitle(itemId, data, fileName, language, isForced, isHearingImpaired)

    override suspend fun deleteSubtitle(itemId: String, index: Int): Result<Unit> =
        metadataApiClient.deleteSubtitle(itemId, index)

    override suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>> =
        metadataApiClient.searchRemoteSubtitles(itemId, language)

    override suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit> =
        playbackApiClient.downloadRemoteSubtitle(itemId, subtitleId)

    override suspend fun identifyRemoteSearch(query: IdentifyQuery): Result<List<IdentifyResult>> =
        metadataApiClient.identifyRemoteSearch(query)

    override suspend fun applyIdentifyResult(itemId: String, result: IdentifyResult, replaceAllImages: Boolean): Result<Unit> =
        metadataApiClient.applyIdentifyResult(itemId, result, replaceAllImages)

    override fun getItemImageUrl(
        itemId: String,
        imageType: String,
        maxWidth: Int?,
        imageIndex: Int?,
        tag: String?,
    ): String = libraryApiClient.getImageUrl(itemId, imageType, maxWidth, imageIndex, tag)
}
