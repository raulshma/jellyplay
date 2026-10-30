package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.flow.Flow

/**
 * The synthetic-push half of the user-data change feed: announces locally
 * confirmed writes (played/favorite flips, delivered outbox rows, session-end
 * position saves) as if the server had pushed them — [UserDataChange]s on the
 * same flow server WS pushes use — so the home refresher and open detail
 * sessions heal exactly like they do for a server push, even when the socket
 * is down. Writers whose mutation reached the server through a path the
 * WebSocket echo cannot cover announce here; the no-op cases (empty ids, no
 * signed-in identity) are the impl's contract, shared by every caller.
 *
 * A SEPARATE leaf single rather than a view of the repository: the write
 * paths that announce ([PlayedStateSyncImpl], [PlaybackRepositoryImpl]) sit
 * upstream of the repository's construction graph, and a repository-typed
 * dependency there is exactly the cycle the former `lazy { get<MediaRepositoryImpl>()
 * }` deferrals existed to break. Owning the fan-out (the synthetic flow + the
 * [RecentLocalUserDataWrites] record) in one leaf lets every one of those
 * lazies die. The repository funnels its own union member through the same
 * seam, adding only its repo-internal staleness arming.
 */
interface UserDataAnnouncer {

    /**
     * Emits [itemIds] as a synthetic [UserDataChange] for the current identity.
     * No-op with no ids or before login. The announcement records its ids in
     * [RecentLocalUserDataWrites] BEFORE emitting, so the home refresher can
     * recognize the resulting push (and the socket echo of the underlying
     * server write) as this device's own and demote it to a non-forced
     * (TTL-semantics) refresh.
     */
    fun notifyUserDataChanged(itemIds: List<String>)

    /**
     * The synthetic half of the user-data change feed — the repository
     * merges this beside the realtime channel's pushes so a local announce
     * and a server push reach every consumer through one flow.
     */
    val changes: Flow<UserDataChange>
}
