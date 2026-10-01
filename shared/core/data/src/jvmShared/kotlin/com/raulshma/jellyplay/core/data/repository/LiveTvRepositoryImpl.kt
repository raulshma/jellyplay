package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.network.api.LiveTvApiClient
import com.raulshma.jellyplay.core.network.api.MediaInfoApiClient

/**
 *  MediaRepository facade split, then the pass-through mirror retired (the
 * candidate B2 shape): the fifteen members that moved verbatim from
 * [MediaRepositoryImpl] were one-line forwards over the API family clients —
 * interface delegation now carries the fourteen [LiveTvApiClient] members
 * verbatim, and the only body left is the family's one real ROUTING decision:
 * `deleteRecording` goes through the generic item delete
 * [MediaInfoApiClient.deleteItem] (Jellyfin has no dedicated LiveTv
 * recording-delete route). The family singles compose the same impls the
 * union delegates to, so the wire behavior is byte-identical.
 */
class LiveTvRepositoryImpl(
    liveTvApiClient: LiveTvApiClient,
    private val mediaInfoApiClient: MediaInfoApiClient,
) : LiveTvRepository, LiveTvApiClient by liveTvApiClient {

    /** Permanently deletes a recorded item by its Jellyfin item id. */
    override suspend fun deleteRecording(recordingId: String): Result<Unit> =
        mediaInfoApiClient.deleteItem(recordingId)
}
