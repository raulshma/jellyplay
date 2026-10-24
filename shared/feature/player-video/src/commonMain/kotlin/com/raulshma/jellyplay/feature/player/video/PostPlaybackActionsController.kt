package com.raulshma.jellyplay.feature.player.video

import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.concurrency.runCatchingRethrowingCancellation
import com.raulshma.jellyplay.core.model.MediaType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The core:data seams #14's offer-delete-after-playback consumes, bundled for
 * ONE VM constructor slot (the [PlayerStores] pattern — a new dependency
 * widens the bundle and the DI definitions, not the VM's constructor wall).
 * Null in test graphs (and whenever the host wires no delete capability): the
 * controller then never arms an offer.
 *
 * [deleteItem] is the EXISTING server delete path
 * (`MediaInfoApiClient.deleteItem` — the live-TV recordings / admin
 * media-cleanup arm), and [invalidateAfterDelete] its cache-invalidation twin
 * (`MediaRepositoryCacheInvalidation.invalidateForUserDataChange` — the same
 * eviction set the repository runs around its user-data writes, so the
 * deleted item's detail cluster and the parent series' catalogue heal).
 */
class PostPlaybackDeleteSources(
    /** The signed-in user's delete permission (`UserInfo.canDeleteContent`). */
    val canDeleteContent: Flow<Boolean>,
    /** The server item delete (`MediaInfoApiClient.deleteItem`). */
    val deleteItem: suspend (itemId: String) -> Result<Unit>,
    /** The delete's cache invalidation (`MediaRepositoryCacheInvalidation`). */
    val invalidateAfterDelete: (itemId: String, seriesIdHint: String?) -> Unit,
)

/**
 * The pure #14 decision table: should the dismissible
 * "Delete '<title>' from server?" offer arm for the item whose playback just
 * concluded naturally?
 *
 * Every gate is conjunctive — the FIRST failing gate is the reason, but the
 * verdict is boolean (the caller only decides to raise or not):
 *  - an item must be bound (a session tail with no id offers nothing);
 *  - never twice for the same item + player session ([alreadyOffered] —
 *    the offer latches on raise, so dismissing cannot resurrect it);
 *  - incognito never offers (the whole session is keep-local by design);
 *  - offline/downloaded playback never offers (the server stream is not what
 *    played — deleting the server copy from an offline session would surprise);
 *  - the account must hold the delete permission (the metadata editor's
 *    `canDeleteContent` gate — the server enforces the same policy);
 *  - media type × pref: MOVIE under `offerDeleteAfterPlaybackMovies`,
 *    EPISODE under `offerDeleteAfterPlaybackEpisodes`, every other type
 *    never (series/season/playlist rows are not playback leaves).
 */
internal data class PostPlaybackOfferInputs(
    val itemId: String?,
    val mediaType: MediaType?,
    val offerMovies: Boolean,
    val offerEpisodes: Boolean,
    val incognito: Boolean,
    val offline: Boolean,
    val canDelete: Boolean,
    val alreadyOffered: Boolean,
)

internal fun decidePostPlaybackDeleteOffer(inputs: PostPlaybackOfferInputs): Boolean =
    inputs.itemId != null &&
        !inputs.alreadyOffered &&
        !inputs.incognito &&
        !inputs.offline &&
        inputs.canDelete &&
        when (inputs.mediaType) {
            MediaType.MOVIE -> inputs.offerMovies
            MediaType.EPISODE -> inputs.offerEpisodes
            else -> false
        }

/**
 * Owns the offer-delete-after-playback surface (#14): after playback ends
 * NATURALLY — the engine's ENDED arm or the reporter's end-of-stream safety
 * net, never a user close — for a MOVIE or EPISODE whose prefs allow it, a
 * dismissible "Delete '<title>' from server?" dialog arms with Delete / Keep.
 *
 * The [UpNextController] shape: every fact the decision reads arrives as a
 * narrow constructor lambda — NO uiState handle crosses. Constructed by the
 * VM's class body (the session is at its composition-root line ratchet); the
 * screen reaches it through the overlay session slice.
 *
 * Lifecycle contract (pinned by [PostPlaybackActionsControllerTest]):
 *  - [onPlaybackEndedNaturally] is the ONE entry point, called only from the
 *    end-of-media policy (the cinema-chain arm routes away before it — a
 *    cinema chain continuing to its next item is a continuation, not an end).
 *    It returns whether the offer flow now OWNS that end's close: a passing
 *    synchronous precheck holds the player open (the dialog is hosted in the
 *    player screen — closing first would kill it before it is ever seen),
 *    and the hold resolves through [closeAfterKeep] / [closePlayer];
 *  - the offer latches per item on raise: "Keep" (and any later natural end
 *    of the same item) can never re-arm it this session;
 *  - "Keep" dismisses the dialog and fires [closeAfterKeep] — the deferred
 *    end-of-media close, which the VM wires conditionally on the #17
 *    after-finish keep gate (a card still holding the player outlives the
 *    dismissed dialog);
 *  - "Delete" runs the server delete through [PostPlaybackDeleteSources],
 *    invalidates the affected caches, closes the player via [closePlayer]
 *    (the just-played item is gone — there is nothing left to watch in
 *    place) and NEVER re-offers; a failed delete dismisses the dialog and
 *    surfaces the failure instead of closing, so the session stays usable;
 *  - a failing delete-permission read raises nothing and releases the hold
 *    through [closeAfterKeep] (the permission set is static per session, so
 *    the natural close simply resumes).
 */
internal class PostPlaybackActionsController(
    private val scope: CoroutineScope,
    /** Null (test graphs / hosts without the delete seams) disables the whole feature. */
    private val sources: PostPlaybackDeleteSources?,
    /** The just-played item's media type (the decision table's type × pref arm). */
    private val getMediaKind: () -> MediaType?,
    /** The just-played item's id (the latch key). */
    private val getItemId: () -> String?,
    /** The just-played item's display title (the dialog's headline). */
    private val getItemTitle: () -> String,
    /** The item's series id, when it belongs to one (the invalidation's hint). */
    private val getSeriesIdHint: () -> String?,
    /** The incognito gate (never offer in a keep-local session). */
    private val isIncognito: () -> Boolean,
    /** The offline/downloaded-playback gate (server streams only). */
    private val isOfflinePlayback: () -> Boolean,
    /** The `offerDeleteAfterPlaybackMovies` pref (hydrated aggregate read). */
    private val offerForMovies: () -> Boolean,
    /** The `offerDeleteAfterPlaybackEpisodes` pref (hydrated aggregate read). */
    private val offerForEpisodes: () -> Boolean,
    /** Closes the player after a successful delete (the item is gone). */
    private val closePlayer: () -> Unit,
    /**
     * The end-of-media close, DEFERRED until the offer resolved: fired on
     * "Keep" and on a failing permission read (both release the hold the
     * armed precheck placed). The VM wires it conditionally on the #17
     * after-finish keep gate, so a card still holding the player outlives
     * the dismissed dialog.
     */
    private val closeAfterKeep: () -> Unit,
    /** Surfaces a failed delete to the user (the dialog dismisses). */
    private val notifyDeleteFailed: suspend (itemTitle: String) -> Unit,
) {

    /** The pending offer's payload: what the dialog names and what Delete removes. */
    @Immutable
    data class DeleteOffer(
        val itemId: String,
        val title: String,
    )

    private val _deleteOffer = MutableStateFlow<DeleteOffer?>(null)

    /** The dialog's state; `null` = hidden. The screen collects this at the dialog tier. */
    val deleteOffer: StateFlow<DeleteOffer?> = _deleteOffer.asStateFlow()

    /**
     * The per-session latch — the item ids this session already offered for.
     * All access rides the caller's dispatch path (the natural-end events are
     * sequential on the player's scope), so a plain set is race-free here.
     */
    private val offeredItemIds = MutableStateFlow<Set<String>>(emptySet())

    /**
     * Playback concluded naturally with no continuation — evaluate the offer.
     * Returns whether the offer flow now OWNS the end-of-media close: a
     * passing synchronous precheck holds the player open (the dialog lives
     * in the player screen — closing first would kill it unseen), and the
     * hold resolves through "Delete" ([closePlayer]), "Keep"
     * ([closeAfterKeep]) or a failing permission read ([closeAfterKeep]).
     * `false` = nothing armed, the caller proceeds with its normal close.
     *
     * The cheap gates run synchronously; the delete-permission flow read is
     * the one suspending input (the user capability, hydrated from the auth
     * stream). The latch registers BEFORE the suspend so two near-simultaneous
     * end events (engine ENDED + the reporter's safety net) can never both
     * arm the dialog; the cost is that a capability-read failure also latches
     * the item — deliberate, the permission set is static per session.
     */
    fun onPlaybackEndedNaturally(): Boolean {
        if (sources == null) return false
        val itemId = getItemId() ?: return false
        // Never twice for the same item + session.
        if (itemId in offeredItemIds.value) return false
        offeredItemIds.value = offeredItemIds.value + itemId

        val precheck = decidePostPlaybackDeleteOffer(
            PostPlaybackOfferInputs(
                itemId = itemId,
                mediaType = getMediaKind(),
                offerMovies = offerForMovies(),
                offerEpisodes = offerForEpisodes(),
                incognito = isIncognito(),
                offline = isOfflinePlayback(),
                canDelete = true,
                alreadyOffered = false,
            ),
        )
        if (!precheck) return false
        val title = getItemTitle()
        scope.launch {
            val canDelete = runCatchingRethrowingCancellation {
                sources.canDeleteContent.first()
            }.getOrDefault(false)
            if (!canDelete) {
                // Nothing will be offered — release the hold (the natural
                // close resumes; the VM's wiring re-checks the #17 card gate).
                closeAfterKeep()
                return@launch
            }
            _deleteOffer.value = DeleteOffer(itemId = itemId, title = title)
        }
        return true
    }

    /**
     * "Keep" — dismiss the dialog and release the hold via [closeAfterKeep]
     * (the deferred end-of-media close; the VM's wiring keeps the player up
     * when the #17 after-finish card still holds it). A no-op with nothing
     * pending; the latch keeps the item from re-offering.
     */
    fun keepItem() {
        val hadOffer = _deleteOffer.value != null
        _deleteOffer.value = null
        if (hadOffer) closeAfterKeep()
    }

    /**
     * "Delete" — the existing server delete path, then the repository's cache
     * invalidation (the detail cluster + the parent series' catalogue), then
     * close. On failure: dismiss + surface, stay in the player.
     */
    fun deleteOfferedItem() {
        val sessionSources = sources ?: return
        val offer = _deleteOffer.value ?: return
        _deleteOffer.value = null
        scope.launch {
            val deleted = runCatchingRethrowingCancellation {
                sessionSources.deleteItem(offer.itemId)
            }.getOrNull()?.isSuccess == true
            if (deleted) {
                runCatchingRethrowingCancellation {
                    sessionSources.invalidateAfterDelete(offer.itemId, getSeriesIdHint())
                }
                closePlayer()
            } else {
                notifyDeleteFailed(offer.title)
            }
        }
    }
}
