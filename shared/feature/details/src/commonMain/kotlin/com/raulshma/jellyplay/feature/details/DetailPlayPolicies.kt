package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.DetailOrigin
import com.raulshma.jellyplay.core.model.MediaType

/**
 * Pure play-path decision policies for the media-detail screen, extracted
 * from the hand-built [DetailContentCallbacks] adapter in `MediaDetailScreen`:
 * the play/chapter lambdas keep only dispatch, and neither re-encodes the
 * subtitle-selection or confirmation rules these folds own.
 */
internal object DetailPlayPolicies {

    /**
     * Which subtitle index a play dispatch should carry: for a local origin
     * ([DetailOrigin.isLocal]) the server stream index is meaningless, so the
     * chosen local-manifest index rides along instead — the player's offline
     * wiring (`TrackSelectionPolicy.resolveByOfflineSubtitleId`) resolves the
     * right side-loaded subtitle from it. A remote (or still-unknown) origin
     * keeps the server subtitle index. The audio index is not policy — it is
     * threaded untouched (the local audio inventory is not selectable here).
     */
    fun resolvePlayStreamSelection(
        origin: DetailOrigin?,
        localSubtitleIndex: Int?,
        remoteSubtitleIndex: Int?,
    ): Int? = if (origin?.isLocal == true) localSubtitleIndex else remoteSubtitleIndex

    /**
     * Folds a play dispatch's whole stream tail in one place so the screen's
     * play and chapter lambdas read the ViewModel-selected indexes and hand
     * the result to the player route without re-encoding the origin rule
     * (see [resolvePlayStreamSelection]). The audio index is not policy —
     * threaded untouched.
     */
    fun resolveDetailPlayDispatch(
        origin: DetailOrigin?,
        localSubtitleIndex: Int?,
        remoteSubtitleIndex: Int?,
        audioStreamIndex: Int?,
    ): DetailPlayDispatch = DetailPlayDispatch(
        subtitleStreamIndex = resolvePlayStreamSelection(origin, localSubtitleIndex, remoteSubtitleIndex),
        audioStreamIndex = audioStreamIndex,
    )

    /**
     * A series mark-played recurses into every episode and clears every resume
     * position, so it requires confirmation first; single movies/episodes flip
     * immediately (trivially reversible via the same button). Season actions
     * set [isSeasonAction] — a season mark is always series-scoped, so it
     * confirms regardless of the parent item's resolved type.
     */
    fun requiresMarkPlayedConfirmation(
        mediaType: MediaType?,
        isSeasonAction: Boolean = false,
    ): Boolean = isSeasonAction || mediaType == MediaType.SERIES

    /**
     * Dispatches exactly one of [confirm] or [action] per the
     * [requiresMarkPlayedConfirmation] table (mediaType × season action):
     * SERIES → confirm (series dialog); MOVIE / EPISODE / SEASON /
     * unresolved → direct; any season action → confirm (season dialog) —
     * which makes the season direct branch unreachable, exactly as the
     * screen's original per-callback bodies behaved. Played vs unplayed is
     * NOT a gate input: the two directions differ only in the dialog verb /
     * message, which the caller tracks alongside.
     */
    fun dispatchMarkPlayedAction(
        mediaType: MediaType?,
        isSeasonAction: Boolean,
        confirm: () -> Unit,
        action: () -> Unit,
    ) {
        if (requiresMarkPlayedConfirmation(mediaType, isSeasonAction)) confirm() else action()
    }
}

/**
 * The stream tail of a detail play dispatch: which subtitle index to carry
 * (origin-resolved) plus the pass-through audio index.
 */
internal data class DetailPlayDispatch(
    val subtitleStreamIndex: Int?,
    val audioStreamIndex: Int?,
)
