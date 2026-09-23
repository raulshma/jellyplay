package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo

/**
 * The narrow family seam for SyncPlay consumers that inject the repository
 * (`SyncPlayViewModel` for the group screen, `WatchPartyActions` for
 * watch-party creation) — kept as its own interface per the MediaRepository
 * union shrink, which made these family seams the feature-visible adapters.
 *
 * It carries exactly two kinds of members: the AWAITED queries/mutations, and
 * the transport commands a UI consumer inspects the Result of. The rest of
 * the SyncPlay wire vocabulary lives one layer down and was pruned from this
 * seam after two censuses showed no awaiting repository-typed call sites:
 * join/leave go through `SyncPlayManager`'s direct `SyncPlayApiClient` use,
 * the fire-and-forget playback/queue reports (ready, buffering, next/previous,
 * remove/move, queue) go through `SyncPlayController`, and — second census —
 * the ignored-Result transport commands (pause/unpause/seek/stop/setRepeat/
 * setShuffle/setIgnoreWait, whose only caller `SyncPlayViewModel` launched
 * them and dropped the Result) also converged on `SyncPlayController`'s
 * `safe()` via the feature-local `SyncPlaySession` seam; the
 * repository-typed pass-throughs died with them. The former repository
 * `syncPlayReady` also silently dropped `whenMs` (the impl fell back to an
 * uncorrected wall clock); that drift died with the member —
 * `SyncPlayController.reportReady` requires the TimeSyncManager-based
 * remote-now explicitly.
 *
 * The ratchet in `SyncPlayRepositorySurfaceTest` pins this count so the
 * surface can only shrink: retire members here rather than adding new ones.
 */
interface SyncPlayRepository {

    suspend fun getSyncPlayGroups(): Result<List<SyncPlayGroup>>

    suspend fun createSyncPlayGroup(groupName: String): Result<Unit>

    suspend fun getSyncPlayInfo(groupId: String? = null): Result<SyncPlayGroupInfo>

    /** Awaited: `WatchPartyActions.start` aborts the bootstrap on failure. */
    suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String? = null,
        startPositionTicks: Long = 0L,
    ): Result<Unit>
}
