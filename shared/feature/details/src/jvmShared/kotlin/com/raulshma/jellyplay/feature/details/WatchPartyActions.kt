package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.data.repository.SyncPlayRepository
import com.raulshma.jellyplay.core.data.syncplay.SyncPlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_msg_watch_party_failed
import com.raulshma.jellyplay.feature.details.generated.resources.detail_watch_party_default_name

/**
 * Owns the "Start watch party" concern for the detail screen. A plain helper
 * class constructed by the VM (via [Factory]): user-facing messages push
 * through the shared [messages] channel so the helper owns no message channel
 * of its own.
 *
 * Unlike the fire-and-forget helpers, this bootstrap is a four-step
 * client/server sequence (create group → recover its id → join → push the
 * queue) whose outcome the caller needs to know before opening the player, so
 * [start] is a suspending function returning [Result] rather than a
 * launch-internal non-suspend entry. The no-arg [startScreenItem] resolves the
 * current item into the bootstrap params (id / group title with localized
 * fallback / default media source) from the session and launches the
 * coroutine; this class does the rest.
 *
 * There is no invite link / share / deep-link: once [start] succeeds the player
 * is opened by the screen and the existing SyncPlayBridge auto-detects the
 * now-active session (`syncPlayManager.isInSyncPlaySession`).
 */
internal class WatchPartyActions(
    private val scope: CoroutineScope,
    private val session: StateFlow<DetailSession?>,
    private val messages: MutableSharedFlow<DetailMessage>,
    private val strings: DetailStrings,
    private val syncPlayRepository: SyncPlayRepository,
    private val syncPlayManager: SyncPlayManager,
) {
    /**
     * Hilt factory bundling this helper's exclusive collaborator
     * ([SyncPlayManager]) so it never appears in the [DetailViewModel]
     * constructor.
     */
    class Factory constructor(
        private val syncPlayRepository: SyncPlayRepository,
        private val syncPlayManager: SyncPlayManager,
    ) {
        fun create(
            scope: CoroutineScope,
            session: StateFlow<DetailSession?>,
            messages: MutableSharedFlow<DetailMessage>,
            strings: DetailStrings,
        ): WatchPartyActions = WatchPartyActions(
            scope = scope,
            session = session,
            messages = messages,
            strings = strings,
            syncPlayRepository = syncPlayRepository,
            syncPlayManager = syncPlayManager,
        )
    }

    /**
     * Bootstraps a watch party for the session's current item: resolves the
     * item id, the group title (the item name, falling back to a localized
     * generic label) and the default media source, then launches the
     * create→join→queue sequence via [start]. Fire-and-forget from the UI's
     * standpoint — success/failure flow back as one-shot messages.
     */
    fun startScreenItem() {
        val detail = session.value?.detail ?: return
        val item = detail.item
        val itemId = item.id
        val mediaSourceId = detail.mediaSources.firstOrNull()?.id
        // Title (incl. the localized fallback) resolves INSIDE the coroutine —
        // the suspend strings seam requires a coroutine scope (KMP move).
        scope.launch {
            val title = item.name.orEmpty().ifBlank {
                strings.get(Res.string.detail_watch_party_default_name)
            }
            start(itemId, title, mediaSourceId)
        }
    }

    /**
     * Bootstraps a SyncPlay watch party for [itemId] and pushes it into the
     * shared queue. The group is created from [title], recovered from the
     * server's group list by name (create returns no id), joined (which also
     * connects the WebSocket), and finally seeded with the item via
     * [SyncPlayRepository.syncPlaySetNewQueue] at [startPositionTicks] = 0 (a
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
     *         messages are always pushed through [messages]).
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
        val priorGroupIds = syncPlayRepository.getSyncPlayGroups().getOrNull().orEmpty()
            .map { it.groupId }
            .toSet()

        // 1. Create the group (no id is returned).
        syncPlayRepository.createSyncPlayGroup(title)
            .onFailure { return fail(it) }

        // 2. Recover the new group by name, preferring one not in the prior
        //    snapshot; fall back to the first name match (mirrors
        //    SyncPlayViewModel.createGroup when the snapshot is unavailable).
        val groupId = syncPlayRepository.getSyncPlayGroups()
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
        syncPlayRepository.syncPlaySetNewQueue(
            itemIds = listOf(itemId),
            playingItemId = itemId,
            mediaSourceId = mediaSourceId,
            startPositionTicks = 0L,
        ).onFailure { return fail(it) }

        messages.tryEmit(DetailMessage.WatchPartyStarted(itemId))
        return Result.success(Unit)
    }

    /**
     * Single failure sink: emits the localized "couldn't start watch party"
     * message and returns a [Result.failure] wrapping [cause]. Collapses the
     * per-step error handling so the four bootstrap calls stay readable.
     */
    private suspend fun fail(cause: Throwable): Result<Unit> {
        messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_msg_watch_party_failed)))
        return Result.failure(cause)
    }
}
