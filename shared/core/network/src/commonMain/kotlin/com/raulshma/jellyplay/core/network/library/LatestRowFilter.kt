package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType

/**
 * The latest-media rows' client-side kind narrowing — the #168 classic-rows
 * belt-and-braces applied by `LibraryApiClientImpl.getLatestMedia` when the
 * call carries an `includeKinds` narrowing.
 *
 * `/Items/Latest` on a Jellyfin 12.x server returns a mix of Series, Season
 * and Episode rows for TV folders (upstream regression jellyfin#17887 /
 * jellyfin#17978). The server-side `includeItemTypes` narrowing is the
 * primary fix; this fold guarantees the semantics regardless of server
 * generation — old servers may ignore the query param entirely, the same
 * rationale the books resume fold records. Wire kind names resolve to
 * [MediaType]s through [wireItemKindToMediaType] (one canonical table pair),
 * unknown names drop, and null allowed-kinds = unconstrained.
 *
 * Like every post-limit fold (the parental filter, [resumableOnly]), this can
 * under-fill the row relative to `limit` — accepted, pre-existing tradeoff.
 */
internal fun List<MediaItem>.toFilteredLatestRows(
    maxParentalRating: Int?,
    allowedKinds: Set<MediaType>?,
): List<MediaItem> {
    val filtered = filterByParentalRating(maxParentalRating)
    return filtered.filterAllowedKinds(allowedKinds)
}
