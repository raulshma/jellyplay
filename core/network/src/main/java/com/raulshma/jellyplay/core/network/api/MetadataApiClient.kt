package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.EditorPerson
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.ImageProviderInfo
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo

interface MetadataApiClient {
    suspend fun updateItem(
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
        type: String = "Unknown",
    ): Result<Unit>

    suspend fun getMetadataEditorInfo(itemId: String): Result<MetadataEditorInfo>
    suspend fun refreshItemMetadata(itemId: String, metadataRefreshMode: String = "Default", imageRefreshMode: String = "Default", replaceAllMetadata: Boolean = false, replaceAllImages: Boolean = false, regenerateTrickplay: Boolean = false): Result<Unit>
    suspend fun getItemImageInfo(itemId: String): Result<List<ImageInfo>>
    suspend fun setItemImage(itemId: String, imageType: String, imageBytes: ByteArray): Result<Unit>
    suspend fun deleteItemImage(itemId: String, imageType: String, imageIndex: Int? = null): Result<Unit>
    suspend fun getRemoteImages(itemId: String, imageType: String? = null, provider: String? = null, startIndex: Int? = null, limit: Int? = null): Result<RemoteImageResult>
    suspend fun getRemoteImageProviders(itemId: String): Result<List<ImageProviderInfo>>
    suspend fun downloadRemoteImage(itemId: String, imageType: String, imageUrl: String): Result<Unit>
    suspend fun uploadSubtitle(itemId: String, data: String, fileName: String, language: String?, isForced: Boolean, isHearingImpaired: Boolean): Result<Unit>
    suspend fun deleteSubtitle(itemId: String, index: Int): Result<Unit>
    suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>>
}
