package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.IdentifyResult
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MetadataRefreshParams
import com.raulshma.jellyplay.core.network.api.LibraryApiClient
import com.raulshma.jellyplay.core.network.api.MetadataApiClient
import com.raulshma.jellyplay.core.network.api.PlaybackApiClient

/**
 * The routing impl of [MetadataEditorRepository] (the LiveTvRepositoryImpl
 * shape after the pass-through mirror retired): interface delegation carries
 * the whole [MetadataApiClient] family verbatim — the eighteen former
 * one-line forwards are gone — and the only remaining bodies are the three
 * members that route OFF the metadata client.
 */
class MetadataEditorRepositoryImpl(
    private val libraryApiClient: LibraryApiClient,
    metadataApiClient: MetadataApiClient,
    private val playbackApiClient: PlaybackApiClient,
) : MetadataEditorRepository, MetadataApiClient by metadataApiClient {

    override suspend fun getMediaDetail(itemId: String): Result<MediaDetail> =
        libraryApiClient.getMediaDetail(itemId)

    override suspend fun refreshItemMetadata(
        itemId: String,
        params: MetadataRefreshParams,
    ): Result<Unit> = refreshItemMetadata(
        itemId,
        metadataRefreshMode = params.metadataRefreshMode,
        imageRefreshMode = params.imageRefreshMode,
        replaceAllMetadata = params.replaceAllMetadata,
        replaceAllImages = params.replaceAllImages,
    )

    override fun getItemImageUrl(
        itemId: String,
        imageType: String,
        maxWidth: Int?,
        imageIndex: Int?,
        tag: String?,
    ): String = libraryApiClient.getImageUrl(itemId, imageType, maxWidth, imageIndex, tag)

    override suspend fun downloadRemoteSubtitle(itemId: String, subtitleId: String): Result<Unit> =
        playbackApiClient.downloadRemoteSubtitle(itemId, subtitleId)
}
