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

/**
 * The metadata editor's data seam: item metadata reads/writes, image CRUD,
 * remote-image search, and subtitle upload/delete. Keeps the editor feature
 * off the raw transport client while preserving the endpoint semantics the
 * editor relies on (notably the base64 subtitle upload contract).
 */
interface MetadataEditorRepository {

    suspend fun getMediaDetail(itemId: String): Result<MediaDetail>

    suspend fun getMetadataEditorInfo(itemId: String): Result<MetadataEditorInfo>

    suspend fun updateItem(itemId: String, metadata: EditableItemMetadata): Result<Unit>

    suspend fun refreshItemMetadata(
        itemId: String,
        params: MetadataRefreshParams,
    ): Result<Unit>

    suspend fun getItemImageInfo(itemId: String): Result<List<ImageInfo>>

    suspend fun setItemImage(itemId: String, imageType: String, imageBytes: ByteArray): Result<Unit>

    suspend fun deleteItemImage(itemId: String, imageType: String, imageIndex: Int? = null): Result<Unit>

    suspend fun getRemoteImages(
        itemId: String,
        imageType: String? = null,
        provider: String? = null,
        startIndex: Int? = null,
        limit: Int? = null,
    ): Result<RemoteImageResult>

    suspend fun getRemoteImageProviders(itemId: String): Result<List<ImageProviderInfo>>

    suspend fun downloadRemoteImage(itemId: String, imageType: String, imageUrl: String): Result<Unit>

    suspend fun uploadSubtitle(
        itemId: String,
        data: String,
        fileName: String,
        language: String?,
        isForced: Boolean,
        isHearingImpaired: Boolean,
    ): Result<Unit>

    suspend fun deleteSubtitle(itemId: String, index: Int): Result<Unit>

    suspend fun searchRemoteSubtitles(itemId: String, language: String): Result<List<RemoteSubtitleInfo>>

    /** The "Identify" provider search (jellyfin-web parity; Series/Movie). */
    suspend fun identifyRemoteSearch(query: IdentifyQuery): Result<List<IdentifyResult>>

    /** Applies a chosen Identify candidate onto the item (metadata + images). */
    suspend fun applyIdentifyResult(itemId: String, result: IdentifyResult, replaceAllImages: Boolean = true): Result<Unit>

    /**
     * Merges the version items [itemIds] into one (jellyfin-web "Merge
     * versions"); fails when fewer than 2 ids are supplied. Admin-only
     * server-side (RequiresElevation).
     */
    suspend fun mergeVersions(itemIds: List<String>): Result<Unit>

    /**
     * Splits a version-merged item apart (jellyfin-web "Split versions");
     * the caller re-fetches the item afterwards. Admin-only server-side
     * (RequiresElevation).
     */
    suspend fun splitVersions(itemId: String): Result<Unit>

    suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit>

    /** URL for a specific item image variant (type/index/tag), e.g. editor thumbnails. */
    fun getItemImageUrl(
        itemId: String,
        imageType: String,
        maxWidth: Int? = null,
        imageIndex: Int? = null,
        tag: String? = null,
    ): String
}
