package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.network.api.LiveTvApiClient

/**
 * The Live TV data seam — RETIRED pass-through mirror: the fourteen members
 * this interface used to re-declare were one-line forwards over
 * [LiveTvApiClient], so the data-facing interface now EXTENDS the client
 * interface (core/data already sits on the core/network API edge) and adds
 * only the one member that routes to a DIFFERENT client — the recording
 * delete, which goes through the generic item delete
 * [com.raulshma.jellyplay.core.network.api.MediaInfoApiClient.deleteItem]
 * (Jellyfin has no dedicated LiveTv recording-delete route).
 * [LiveTvRepositoryImpl] stays as the routing impl (interface delegation
 * carries the client family; the one override routes).
 */
interface LiveTvRepository : LiveTvApiClient {

    /** Permanently deletes a recorded item by its Jellyfin item id. */
    suspend fun deleteRecording(recordingId: String): Result<Unit>
}
