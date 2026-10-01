package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.IdentifyResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MetadataRefreshParams
import com.raulshma.jellyplay.core.network.api.MetadataApiClient

/**
 * The metadata editor's data seam: item metadata reads/writes, image CRUD,
 * remote-image search, and subtitle upload/delete. Keeps the editor feature
 * off the raw transport client while preserving the endpoint semantics the
 * editor relies on (notably the base64 subtitle upload contract).
 *
 * Retired pass-through mirror (the LiveTvRepository shape): every member this
 * interface used to re-declare was a one-line forward over [MetadataApiClient],
 * so the data-facing interface now EXTENDS the client interface and adds only
 * the three members that route to a DIFFERENT client — the detail read and the
 * image-URL builder ([com.raulshma.jellyplay.core.network.api.LibraryApiClient])
 * and the subtitle download ([com.raulshma.jellyplay.core.network.api.PlaybackApiClient]).
 * [MetadataEditorRepositoryImpl] stays as the routing impl (interface
 * delegation carries the metadata-client family; the three overrides route).
 */
interface MetadataEditorRepository : MetadataApiClient {

    suspend fun getMediaDetail(itemId: String): Result<MediaDetail>

    /**
     * The editor's refresh vocabulary: [MetadataRefreshParams] travels whole
     * and the impl explodes it onto the inherited
     * [MetadataApiClient.refreshItemMetadata] primitives (the same
     * bundle-travels-whole shape as updateItem's EditableItemMetadata — the
     * params object is the editor's contract, not a mirror of the client's
     * argument list).
     */
    suspend fun refreshItemMetadata(
        itemId: String,
        params: MetadataRefreshParams,
    ): Result<Unit>

    /** URL for a specific item image variant (type/index/tag), e.g. editor thumbnails. */
    fun getItemImageUrl(
        itemId: String,
        imageType: String,
        maxWidth: Int? = null,
        imageIndex: Int? = null,
        tag: String? = null,
    ): String

    /** Downloads a remote subtitle onto the item (the playback family's route). */
    suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit>
}
