package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_added_to_collection
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_collection_created
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_couldnt_add_to_collection
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_no_episodes_queued

/**
 * Collection adapter over [AddToTargetActions]: fetches the collection list
 * and maps the generic add/create calls onto the collection endpoints. The
 * Jellyfin create endpoint takes only a name (+ seed ids) — no overview, no
 * media-type tagging — so both fold away here.
 */
internal class CollectionAddTarget(
    private val strings: DetailStrings,
    private val mediaRepository: MediaRepository,
) : AddTargetAdapter<CollectionSummary> {
    override suspend fun fetchTargets(): Result<List<CollectionSummary>> =
        mediaRepository.getCollections(limit = 100)

    override fun nameOf(target: CollectionSummary): String = target.name

    override fun idOf(target: CollectionSummary): String = target.id

    override suspend fun addToTarget(targetId: String, ids: List<String>): Result<Unit> =
        mediaRepository.addItemsToCollection(targetId, ids)

    override suspend fun createTarget(
        name: String,
        overview: String?,
        ids: List<String>,
        itemType: MediaType,
    ): Result<Unit> = mediaRepository.createCollection(name = name, itemIds = ids).map { }

    override suspend fun addedMessage(targetName: String): String =
        strings.get(Res.string.detail_msg_added_to_collection, targetName)

    override suspend fun createdMessage(name: String): String =
        strings.get(Res.string.detail_msg_collection_created, name)

    override suspend fun couldntAddMessage(): String =
        strings.get(Res.string.detail_msg_couldnt_add_to_collection)

    override suspend fun noEpisodesMessage(): String =
        strings.get(Res.string.detail_msg_no_episodes_queued)
}
