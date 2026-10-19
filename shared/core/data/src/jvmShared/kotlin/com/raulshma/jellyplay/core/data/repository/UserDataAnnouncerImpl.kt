package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.data.session.HomeSession
import com.raulshma.jellyplay.core.model.UserDataChange
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * The ONE owner of the synthetic user-data push fan-out (see
 * [UserDataAnnouncer]): the shared flow the repository's
 * `userDataChanges` merges beside the realtime channel, and the
 * [RecentLocalUserDataWrites] record its echo demotion consults — extracted
 * from MediaRepositoryImpl so the write paths upstream of the repository's
 * construction graph ([PlayedStateSyncImpl], [PlaybackRepositoryImpl]) can
 * announce through an eager, cycle-free leaf dependency.
 *
 * Call contract: no ids or no signed-in identity are quiet no-ops (an emit
 * without an identity would only risk refreshing another account's screens on
 * a stale collector).
 */
class UserDataAnnouncerImpl(
    private val homeSession: HomeSession,
    private val recentLocalWrites: RecentLocalUserDataWrites,
    /**
     * The confirmed-write side effect the repository used to own when every
     * announce rode its union funnel: arming the stale-read groups (#157 —
     * the next read of each announced-stale group refetches even if no
     * consumer was collecting when the write landed). It lives HERE because
     * the write paths that announce (PlayedStateSyncImpl,
     * PlaybackRepositoryImpl) bypass the union funnel — the arming must ride
     * every confirmed write, not just the funnel's. The DI wiring resolves
     * the repository lazily at announce time (no construction cycle: the
     * announcer's graph never touches the repository's).
     */
    private val onConfirmedWrite: () -> Unit = {},
) : UserDataAnnouncer {

    private val syntheticUserDataChanges = MutableSharedFlow<UserDataChange>(
        extraBufferCapacity = SYNTHETIC_CHANGES_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /**
     * The synthetic half of the user-data change feed — the repository merges
     * this beside the WS channel's pushes, so a local announce and a server
     * push reach every consumer through one flow.
     */
    override val changes: Flow<UserDataChange> get() = syntheticUserDataChanges

    override fun notifyUserDataChanged(itemIds: List<String>) {
        if (itemIds.isEmpty()) return
        // No identity → nothing is keyed to a user yet; emitting would only
        // risk refreshing another account's screens on a stale collector.
        val userId = homeSession.currentIdentitySnapshot()?.userId ?: return
        // The confirmed-write side effects ride the announce itself — the
        // #157 stale-read arming (see [onConfirmedWrite]).
        onConfirmedWrite()
        val distinctIds = itemIds.distinct()
        // Record the confirmed write BEFORE the announcement so the refresher
        // can recognize the resulting push (and the socket echo of the
        // underlying server write) as this device's own and demote it to a
        // non-forced refresh.
        recentLocalWrites.record(distinctIds)
        syntheticUserDataChanges.tryEmit(UserDataChange(userId, distinctIds))
    }

    companion object {
        /**
         * Buffer for [syntheticUserDataChanges]: large enough that a drain of
         * dozens of flips never suspends or drops wholesale, small enough to
         * be pointless to tune. DROP_OLDEST keeps the tryEmit non-suspending.
         */
        private const val SYNTHETIC_CHANGES_BUFFER = 64
    }
}
