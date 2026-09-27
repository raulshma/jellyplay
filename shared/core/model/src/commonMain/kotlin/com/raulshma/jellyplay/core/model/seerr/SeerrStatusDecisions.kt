package com.raulshma.jellyplay.core.model.seerr

/**
 * Decision table for Seerr request/media status: the 4K-aware effective-status
 * resolution and the availability predicates both status-rendering features
 * fold through. Extracted from three composable sites that each re-derived
 * the same rules by hand:
 *  - feature/requests `RequestListItem` and `RequestDetailBottomSheet`
 *    carried a verbatim duplicate of the is4k pick (`if (request.is4k)
 *    media.status4k else media.status` piped into [SeerrMediaStatus.fromValue])
 *    ahead of their (also duplicated) status-presentation `when`;
 *  - feature/details `SeerrDetailScreen`'s action buttons re-derived the
 *    availability predicates off the same enum a third way
 *    (`== AVAILABLE || == PARTIALLY_AVAILABLE` etc.).
 *
 * Placement (the topology call): the enum-level decisions live HERE, beside
 * [SeerrMediaStatus], because core/model is the one status-aware module every
 * consumer already depends on (requests, details, core/ui all edge into it —
 * star topology). The label/color presentation table that rides on these
 * decisions lives in feature/requests (`RequestStatusPresentation.kt`): its
 * strings are feature-scoped compose-resources (the requests Res class),
 * invisible to core modules and to sibling features, so that half of the
 * table cannot move down here. Same fold as core:ui's SeerrRequestDefaults —
 * leaf decisions in a pure, JVM-testable home; effect shells stay in
 * composition. Declared delta vs the former inline code: none — every
 * decision is byte-identical.
 *
 * The button-precedence fold (which of the three action buttons the Seerr
 * detail screen shows) rides the same predicates below as
 * [seerrRequestButtonState] → [SeerrRequestButtonState].
 */

/**
 * The media status a request should be judged by: a 4K request reads the
 * media's `status4k` column, every other request the regular `status` — the
 * verbatim body of the two former inline `effectiveMediaStatus` locals
 * (Jellyseerr's own request list applies the same split). Unmapped ints fold
 * to [SeerrMediaStatus.UNKNOWN] via [SeerrMediaStatus.fromValue].
 */
fun SeerrRequestItem.effectiveMediaStatus(): SeerrMediaStatus =
    SeerrMediaStatus.fromValue(if (is4k) media.status4k else media.status)

/**
 * The media exists in the library, at least in part — [SeerrMediaStatus.AVAILABLE]
 * or [SeerrMediaStatus.PARTIALLY_AVAILABLE]. The details screen's "open in
 * library" affordance keys off this (a partially available series is still
 * openable), so partial counts as present.
 */
val SeerrMediaStatus.isAvailable: Boolean
    get() = this == SeerrMediaStatus.AVAILABLE || this == SeerrMediaStatus.PARTIALLY_AVAILABLE

/**
 * The media has been requested but not yet approved/grabbed
 * ([SeerrMediaStatus.PENDING]).
 */
val SeerrMediaStatus.isPending: Boolean
    get() = this == SeerrMediaStatus.PENDING

/**
 * A download/grab is in flight for the media ([SeerrMediaStatus.PROCESSING]).
 */
val SeerrMediaStatus.isProcessing: Boolean
    get() = this == SeerrMediaStatus.PROCESSING

/**
 * The single action button the Seerr detail screen shows for a media item —
 * the button-PRECEDENCE half of the decision table. The screen's
 * `SeerrActionButtons` previously folded this inline beside the (already
 * extracted) predicates above; core:ui's `seerrStatusBadgePresentation`
 * folds the identical precedence for the search card's corner badge
 * (available > processing > pending > bare requested). Both now read the
 * vocabulary below, so the precedence lives in exactly one place.
 *
 * Shape (from what the screen actually branches on): a media item is
 * [Available] (the "open in library" affordance), one of three [Requested]
 * phases (the read-only status echo), or [NotRequested] (the "Request"
 * action). The label/icon/color triple per branch is deliberately NOT here:
 * those strings/icons are feature- and surface-scoped (the details screen's
 * button labels and the requests feature's chip labels are separate
 * compose-resource classes with different vocabularies — the requests chip
 * also keys on the admin request status, which the button never sees), so
 * each renderer maps this sealed state to its own presentation. That is the
 * parameterization: the decision is shared, the presentation rides the
 * branch data. Declared delta vs the former inline `if/else if/else`: none
 * — every row of the fold is byte-identical.
 */
sealed interface SeerrRequestButtonState {
    /**
     * The media exists in the library (fully or partially) — render the
     * "Available" affordance; partial counts as present (same call as
     * [SeerrMediaStatus.isAvailable]).
     */
    data object Available : SeerrRequestButtonState

    /**
     * A request is in flight — the read-only status echo. Availability
     * outranks this arm (a partially-grabbed series with an open request
     * still shows "Available").
     */
    sealed interface Requested : SeerrRequestButtonState {
        /** A download/grab is in flight ([SeerrMediaStatus.PROCESSING]). */
        data object Processing : Requested

        /** Requested, not yet approved/grabbed ([SeerrMediaStatus.PENDING]). */
        data object Pending : Requested

        /**
         * The media carries a request entry without a pending/processing
         * media status (e.g. an UNKNOWN/DELETED status over a non-empty
         * `mediaInfo.requests`) — the screen's former bare-`else` triple
         * ("Requested" + arrow).
         */
        data object ExistingRequest : Requested
    }

    /** Nothing requested — render the "Request" action. */
    data object NotRequested : SeerrRequestButtonState
}

/**
 * Folds a media item's [SeerrMediaInfo] into the one action button to show:
 * available (partial counts) > processing > pending > a bare existing
 * request entry > requestable. Absent `mediaInfo` (never requested —
 * Overseerr omits it for untouched media) folds to
 * [SeerrRequestButtonState.NotRequested] via [SeerrMediaStatus.UNKNOWN].
 */
fun seerrRequestButtonState(mediaInfo: SeerrMediaInfo?): SeerrRequestButtonState {
    val mediaStatus = SeerrMediaStatus.fromValue(mediaInfo?.status ?: 0)
    return when {
        mediaStatus.isAvailable -> SeerrRequestButtonState.Available
        mediaStatus.isProcessing -> SeerrRequestButtonState.Requested.Processing
        mediaStatus.isPending -> SeerrRequestButtonState.Requested.Pending
        mediaInfo?.requests?.isNotEmpty() == true -> SeerrRequestButtonState.Requested.ExistingRequest
        else -> SeerrRequestButtonState.NotRequested
    }
}
