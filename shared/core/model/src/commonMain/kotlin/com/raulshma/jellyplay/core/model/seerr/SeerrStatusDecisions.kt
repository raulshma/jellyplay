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
