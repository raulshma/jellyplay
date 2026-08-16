package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.EditableItemMetadata
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.ImageProviderInfo
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetadataEditorRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : MetadataEditorRepository {

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> =
        apiClient.getMediaDetail(itemId)

    override suspend fun getMetadataEditorInfo(itemId: String): Result<MetadataEditorInfo> =
        apiClient.getMetadataEditorInfo(itemId)

    override suspend fun updateItem(itemId: String, metadata: EditableItemMetadata): Result<Unit> =
        apiClient.updateItem(
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
        metadataRefreshMode: String,
        imageRefreshMode: String,
        replaceAllMetadata: Boolean,
        replaceAllImages: Boolean,
    ): Result<Unit> = apiClient.refreshItemMetadata(itemId, metadataRefreshMode, imageRefreshMode, replaceAllMetadata, replaceAllImages)

    override suspend fun getItemImageInfo(itemId: String): Result<List<ImageInfo>> =
        apiClient.getItemImageInfo(itemId)

    override suspend fun setItemImage(itemId: String, imageType: String, imageBytes: ByteArray): Result<Unit> =
        apiClient.setItemImage(itemId, imageType, imageBytes)

    override suspend fun deleteItemImage(itemId: String, imageType: String, imageIndex: Int?): Result<Unit> =
        apiClient.deleteItemImage(itemId, imageType, imageIndex)

    override suspend fun getRemoteImages(
        itemId: String,
        imageType: String?,
        provider: String?,
        startIndex: Int?,
        limit: Int?,
    ): Result<RemoteImageResult> = apiClient.getRemoteImages(itemId, imageType, provider, startIndex, limit)

    override suspend fun getRemoteImageProviders(itemId: String): Result<List<ImageProviderInfo>> =
        apiClient.getRemoteImageProviders(itemId)

    override suspend fun downloadRemoteImage(itemId: String, imageType: String, imageUrl: String): Result<Unit> =
        apiClient.downloadRemoteImage(itemId, imageType, imageUrl)

    override suspend fun uploadSubtitle(
        itemId: String,
        data: String,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ): Result<Unit> = apiClient.uploadSubtitle(itemId, data, fileName, language, isForced, isHearingImpaired)

    override suspend fun deleteSubtitle(itemId: String, index: Int): Result<Unit> =
        apiClient.deleteSubtitle(itemId, index)

    override suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>> =
        apiClient.searchRemoteSubtitles(itemId, language)

    override suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit> =
        apiClient.downloadRemoteSubtitle(itemId, subtitleId)

    override fun getItemImageUrl(
        itemId: String,
        imageType: String,
        maxWidth: Int?,
        imageIndex: Int?,
        tag: String?,
    ): String = apiClient.getImageUrl(itemId, imageType, maxWidth, imageIndex, tag)
}
