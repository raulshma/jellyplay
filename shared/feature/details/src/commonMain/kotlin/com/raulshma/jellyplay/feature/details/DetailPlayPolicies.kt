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
}
