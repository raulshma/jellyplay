package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.EditableItemMetadata
import com.raulshma.jellyplay.core.model.IdentifyQuery
import com.raulshma.jellyplay.core.model.IdentifyResult
import com.raulshma.jellyplay.core.model.ImageInfo
import com.raulshma.jellyplay.core.model.ImageProviderInfo
import com.raulshma.jellyplay.core.model.MetadataEditorInfo
import com.raulshma.jellyplay.core.model.RemoteImageResult
import com.raulshma.jellyplay.core.model.RemoteSubtitleInfo

interface MetadataApiClient {
    /**
     * Saves the editor's field set onto the item (`POST /Items/{itemId}`). The
     * [EditableItemMetadata] value object travels whole — the wire-level
     * explode into the SDK's [BaseItemDto] (SDK-enum resolution for
     * air-days/locked-fields/kind, date parsing, name→NameGuidPair studios)
     * happens inside the impl, so callers never thread ~30 positional
     * primitives.
     */
    suspend fun updateItem(itemId: String, metadata: EditableItemMetadata): Result<Unit>

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

    /**
     * The "Identify" provider search (jellyfin-web parity): posts the query to
     * the type-specific `/Items/RemoteSearch/{Type}` endpoint and returns the
     * provider candidates. [IdentifyQuery.itemType] dispatches the endpoint.
     */
    suspend fun identifyRemoteSearch(query: IdentifyQuery): Result<List<IdentifyResult>>

    /**
     * Applies a chosen [IdentifyResult] onto the item (metadata replacement;
     * images replaced when [replaceAllImages], the SDK/server default).
     * `POST /Items/RemoteSearch/Apply/{itemId}` with the server's original
     * result DTO when the candidate came from [identifyRemoteSearch] — the
     * server also triggers a metadata refresh of the item on apply.
     */
    suspend fun applyIdentifyResult(itemId: String, result: IdentifyResult, replaceAllImages: Boolean = true): Result<Unit>

    /**
     * Merges the version items [itemIds] into one (jellyfin-web "Merge
     * versions"): every listed item becomes a MediaSource of the first.
     * `POST /Videos/MergeVersions?ids=a,b,c` (RequiresElevation — the server
     * 403s non-admins; the caller gates the entry). Fails fast when fewer
     * than 2 ids are supplied (the server rejects those with 400).
     */
    suspend fun mergeVersions(itemIds: List<String>): Result<Unit>

    /**
     * Splits a version-merged item apart (jellyfin-web "Split versions"):
     * `DELETE /Videos/{itemId}/AlternateSources` (RequiresElevation) restores
     * each merged MediaSource to its own item. The caller must re-fetch the
     * item afterwards — the merged entry is gone server-side.
     */
    suspend fun splitVersions(itemId: String): Result<Unit>
}
