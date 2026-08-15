package com.raulshma.jellyplay.feature.details

import android.content.Context
import com.raulshma.jellyplay.core.data.repository.MediaRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager

/**
 * Owns the "Start watch party" concern for the detail screen. A plain helper
 * class (no `@Inject`) constructed by [DetailViewModel], structurally a mirror
 * of [PlaylistActions]: user-facing messages push through
 * [messageSink] so the helper owns no message channel of its own.
 *
 * Unlike the fire-and-forget helpers, this bootstrap is a four-step
 * client/server sequence (create group → recover its id → join → push the
 * queue) whose outcome the caller needs to know before opening the player, so
 * [start] is a suspending function returning [Result] rather than a
 * launch-internal non-suspend entry. The VM resolves the item/title/source id
 * and launches the coroutine; this class does the rest.
 *
 * There is no invite link / share / deep-link: once [start] succeeds the player
 * is opened by the screen and the existing [SyncPlayBridge] auto-detects the
 * now-active session (`syncPlayManager.isInSyncPlaySession`).
 */
internal class WatchPartyActions(
    private val mediaRepository: MediaRepository,
    private val syncPlayManager: SyncPlayManager,
    private val context: Context,
    private val messageSink: (DetailMessage) -> Unit,
) {
    /**
     * Bootstraps a SyncPlay watch party for [itemId] and pushes it into the
     * shared queue. The group is created from [title], recovered from the
     * server's group list by name (create returns no id), joined (which also
     * connects the WebSocket), and finally seeded with the item via
     * [MediaRepository.syncPlaySetNewQueue] at [startPositionTicks] = 0 (a
     * fresh group start).
     *
     * Group recovery disambiguates by id: the existing group ids are snapshotted
     * (best-effort) before creation, and the recover step prefers a name match
     * that is NOT in that snapshot — so a pre-existing same-named group can't
     * shadow the freshly-created one (which would otherwise be orphaned and
     * leave the user joined to the wrong party).
     *
     * Any step failure aborts the remainder and emits a failure message; on
     * overall success [DetailMessage.WatchPartyStarted] is emitted so the
     * screen can navigate to the player.
     *
     * @return the overall outcome so the caller may react (the success/failure
     *         messages are always pushed through [messageSink]).
     */
    suspend fun start(
        itemId: String,
        title: String,
        mediaSourceId: String?,
    ): Result<Unit> {
        // Snapshot existing group ids (best-effort) before creating, so step 2
        // can pick the freshly-created group even if a same-named group already
        // exists — create returns no id, so a name collision would otherwise
        // join a stale group and orphan the new one.
        val priorGroupIds = mediaRepository.getSyncPlayGroups().getOrNull().orEmpty()
            .map { it.groupId }
            .toSet()

        // 1. Create the group (no id is returned).
        mediaRepository.createSyncPlayGroup(title)
            .onFailure { return fail(it) }

        // 2. Recover the new group by name, preferring one not in the prior
        //    snapshot; fall back to the first name match (mirrors
        //    SyncPlayViewModel.createGroup when the snapshot is unavailable).
        val groupId = mediaRepository.getSyncPlayGroups()
            .getOrElse { return fail(it) }
            .let { groups ->
                groups.firstOrNull { it.groupName == title && it.groupId !in priorGroupIds }
                    ?: groups.firstOrNull { it.groupName == title }
            }
            ?.groupId
            ?: return fail(IllegalStateException("SyncPlay group not found after creation: $title"))

        // 3. Join the group + connect the WebSocket.
        syncPlayManager.joinGroup(groupId)
            .onFailure { return fail(it) }

        // 4. Push the item into the shared queue at position 0 (fresh start).
        mediaRepository.syncPlaySetNewQueue(
            itemIds = listOf(itemId),
            playingItemId = itemId,
            mediaSourceId = mediaSourceId,
            startPositionTicks = 0L,
        ).onFailure { return fail(it) }

        messageSink(DetailMessage.WatchPartyStarted(itemId))
        return Result.success(Unit)
    }

    /**
     * Single failure sink: emits the localized "couldn't start watch party"
     * message and returns a [Result.failure] wrapping [cause]. Collapses the
     * per-step error handling so the four bootstrap calls stay readable.
     */
    private fun fail(cause: Throwable): Result<Unit> {
        messageSink(DetailMessage.Text(context.getString(R.string.detail_msg_watch_party_failed)))
        return Result.failure(cause)
    }
}
