package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.MediaType

/**
 * One content section of the detail body, carrying the stagger slot it renders
 * into. The [delayIndex] values are the exact literals the former inline
 * [DetailContentBody] passed to [StaggeredDetailSection] — they are
 * load-bearing (the shared entrance animation subtracts
 * `delayIndex * DETAIL_STAGGER_STEP` from it, and the driver animates to
 * `1f + DETAIL_MAX_STAGGER_INDEX * DETAIL_STAGGER_STEP`), so this enum is their
 * single source of truth: the renderers take the index from here, never
 * re-state it.
 *
 * The indices are deliberately NOT position-unique — BOOK_READING_CARD and
 * MEDIA_INFO share `2` (a book renders the reading card in the media-info
 * slot), and UP_NEXT, UPCOMING_EPISODES and SEASONS share `6` (the series
 * trio: smart-play card, unaired strip, season tree) — so the body's
 * emission order is this enum's declaration order, independent of the
 * stagger index.
 *
 * The former DetailSectionAdmission fold inputs retired with the last
 * module wave: every kind's admission is a [DetailSectionModule.isAdmitted]
 * row (the fold rows moved out verbatim, wave by wave), and the registry's
 * merged emission re-sorts by this enum's declaration order.
 */
internal enum class DetailSectionKind(val delayIndex: Int) {

    /** Title block: series/episode context, logo title, metadata row, chip rows. Always admitted. */
    HEADER(0),

    /** The play/download action row. Admitted only when the caller shows it (portrait body). */
    ACTION_ROW(1),

    /** Book reading progress card — occupies the media-info slot for BOOK items. */
    BOOK_READING_CARD(2),

    /**
     * Stream quality/audio/subtitle info. Branch selection (remote MediaInfoSection
     * vs local badges vs subtitle picker, keyed on media-source presence) is a
     * render-side gate — the section still emits (and occupies a spacedBy gap)
     * when no branch renders, exactly as before.
     */
    MEDIA_INFO(2),

    /** Expandable synopsis + external-link chips. Always admitted; the
     *  nothing-to-show gate (no synopsis, no visible links) is render-side. */
    OVERVIEW(3),

    /** JellyPlay companion plugin's aggregated external-ratings chip row (ADR 0010). */
    PLUGIN_RATINGS(3),

    /** Book Contents (TOC cache) or the video chapter thumbnail row — first match wins. */
    CHAPTERS_OR_TOC(4),

    /** Album/audio track list. */
    ALBUM_TRACKS(5),

    /** Series smart-play "Up Next" card, ahead of the series trio's shared slot 6. */
    UP_NEXT(6),

    /** The series' "Airing next" strip (unaired episodes, `/Shows/Upcoming`
     *  scoped to the series) — the middle member of the shared slot-6 band,
     *  between the smart-play card and the season tree. Admitted only when
     *  the strip actually has content (the enrichment's silent-absence
     *  contract: no rows, no slot). */
    UPCOMING_EPISODES(6),

    /** Season tabs + episode lists for SERIES/EPISODE/SEASON. Admitted only when showsSeasonTree(mediaType). */
    SEASONS(6),

    /** Collection members row. Admitted only for COLLECTION items. */
    COLLECTION_ITEMS(7),

    /** Cast & crew row. Admitted for every non-BOOK item; the empty-people gate is render-side. */
    CAST(8),

    /** Seerr "Videos" (trailers/extras mined from TMDB). */
    RELATED_VIDEOS(9),

    /** The "More in {collection}" franchise rows (BoxSet ancestors +
     *  members), declared just before the similar rows and sharing their
     *  slot-10 band. Admitted only when at least one row has content (the
     *  enrichment's silent-absence contract) AND the preference is on —
     *  a whole-ROW family, unlike the always-admitted single-slot sections. */
    MORE_IN_COLLECTION(10),

    /** "More like this" — server relatedItems (remote) or localRelatedItems (local).
     *  Suppressed only on a Jellyfin 12+ pipeline host (the plugin row's
     *  pluginSimilarSuppressesStock state) while the plugin row actually
     *  renders — there the stock endpoint returns the same scored list. */
    MORE_LIKE_THIS(10),

    /** JellyPlay companion plugin's server-scored "More like this" row (ADR 0010) —
     *  renders whenever the plugin returned items; on pipeline hosts the stock
     *  row stands down (same list), on older hosts both rows render. */
    JELLYPLAY_SIMILAR(10),

    /** Seerr recommendations row. */
    SEERR_RECOMMENDATIONS(11),

    /** Seerr similar row. */
    SEERR_SIMILAR(12),

    /** Special features / extras poster row (remote discovery only). */
    SPECIAL_FEATURES(13),

    /** TMDB review cards (fetched regardless of Seerr connection). */
    TMDB_REVIEWS(14),

    /** Download info card + freshness banner footer. */
    DOWNLOAD_FOOTER(15),
}

/**
 * The admission gate of the SEASONS slot (the SEASONS module applies
 * it; the renderers check only `state.core.seasons` emptiness): which detail
 * entry types render the season tabs + episode tree. SERIES (the tree's
 * owner), EPISODE (the parent series' context tree), and — since #168 —
 * SEASON (the entry season's own tree: a season arriving from the home rows
 * must not dead-end on the bare generic page). Extracted Compose-free so the
 * gate has a direct test surface, same convention as the admission fold.
 */
internal fun showsSeasonTree(mediaType: MediaType): Boolean =
    mediaType == MediaType.SERIES || mediaType == MediaType.EPISODE || mediaType == MediaType.SEASON

/**
 * Which detail entries carry a PARENT series whose context they render — the
 * header's series link and the detail backdrop both resolve through
 * `MediaItem.seriesId`: EPISODE, and — since #168 — SEASON. SERIES is the
 * context's owner, not a consumer of it. Extracted beside
 * [showsSeasonTree] for the same direct-test-surface reason.
 */
internal fun showsParentSeriesContext(mediaType: MediaType): Boolean =
    mediaType == MediaType.EPISODE || mediaType == MediaType.SEASON
