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
 * Unlike the fire-and-forget helpers, this bootstrap is a two-step
 * client/server sequence (create + join the group — one
 * [SyncPlayManager.createGroup] call — then push the queue) whose outcome the
 * caller needs to know before opening the player, so
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
     * shared queue. The group is created AND joined by
     * [SyncPlayManager.createGroup] — the one owner of the create→join-MY-group
     * choreography (the wire `New` command returns no id; the manager recovers
     * the fresh group with a bounded, snapshot-disambiguated poll, so a
     * pre-existing same-named group can't shadow it and the bootstrap no
     * longer carries its own snapshot/recover/join steps) — and finally the
     * item is seeded via [SyncPlayRepository.syncPlaySetNewQueue] at
     * [startPositionTicks] = 0 (a fresh group start).
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
        // 1. Create the group and join it (also connects the WebSocket).
        syncPlayManager.createGroup(title)
            .onFailure { return fail(it) }

        // 2. Push the item into the shared queue at position 0 (fresh start).
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
     * per-step error handling so the two bootstrap calls stay readable.
     */
    private suspend fun fail(cause: Throwable): Result<Unit> {
        messages.tryEmit(DetailMessage.Text(strings.get(Res.string.detail_msg_watch_party_failed)))
        return Result.failure(cause)
    }
}
