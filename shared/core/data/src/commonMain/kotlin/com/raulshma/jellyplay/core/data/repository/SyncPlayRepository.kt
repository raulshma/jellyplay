package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.SyncPlayGroup
import com.raulshma.jellyplay.core.model.SyncPlayGroupInfo
import com.raulshma.jellyplay.core.model.SyncPlayRepeatMode
import com.raulshma.jellyplay.core.model.SyncPlayShuffleMode

/**
 * The narrow family seam for SyncPlay consumers that inject the repository
 * (`SyncPlayViewModel` for the group screen, `WatchPartyActions` for
 * watch-party creation) — kept as its own interface per the MediaRepository
 * union shrink, which made these family seams the feature-visible adapters.
 *
 * It intentionally carries ONLY the commands those consumers issue. The rest
 * of the SyncPlay wire vocabulary lives one layer down and was pruned from
 * this seam after a census showed zero repository-typed call sites:
 * join/leave go through `SyncPlayManager`'s direct `SyncPlayApiClient` use,
 * and the fire-and-forget playback/queue reports (ready, buffering,
 * next/previous, remove/move, queue) go through `SyncPlayController`. The
 * former repository `syncPlayReady` also silently dropped `whenMs` (the impl
 * fell back to an uncorrected wall clock); that drift died with the member —
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

    suspend fun syncPlayPause(): Result<Unit>

    suspend fun syncPlayUnpause(): Result<Unit>

    suspend fun syncPlaySeek(positionTicks: Long): Result<Unit>

    suspend fun syncPlayStop(): Result<Unit>

    suspend fun syncPlaySetRepeatMode(mode: SyncPlayRepeatMode): Result<Unit>

    suspend fun syncPlaySetShuffleMode(mode: SyncPlayShuffleMode): Result<Unit>

    suspend fun syncPlaySetNewQueue(
        itemIds: List<String>,
        playingItemId: String,
        mediaSourceId: String? = null,
        startPositionTicks: Long = 0L,
    ): Result<Unit>

    suspend fun syncPlaySetIgnoreWait(ignore: Boolean): Result<Unit>
}
