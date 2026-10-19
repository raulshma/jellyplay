package com.raulshma.jellyplay.feature.details

import com.raulshma.jellyplay.core.model.DetailCapabilities
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isAudioType

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
     *  Suppressed only on a Jellyfin 12+ pipeline host ([DetailSectionAdmission.suppressStockSimilar])
     *  while the plugin row actually renders — there the stock endpoint returns
     *  the same scored list. */
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
 * The inputs the detail body's section admission fold reads — everything the
 * former inline `if`/`visible` gates over [DetailContentBody]'s state needed,
 * enumerated as plain values so the fold is Compose-free and directly
 * testable. Built via [from] off the loaded [DetailContentState].
 *
 * Two shapes of gate exist in the body and they are deliberately separated:
 *
 * 1. **Admission** (this fold) — whether the section's [StaggeredDetailSection]
 *    slot exists at all. Each slot contributes a child to the body Column
 *    (spacedBy 24.dp gap) even when its content renders nothing, so a section
 *    keyed to one media family owns that gate here in full: SEASONS renders
 *    only for SERIES/EPISODE/SEASON, COLLECTION_ITEMS only for COLLECTION —
 *    everywhere else there is no slot and no gap, not an admitted-but-empty
 *    one. The renderers must not re-state the media-type half of an admission
 *    rule; they check only content presence.
 * 2. **Render-side gates** — content-presence checks inside an admitted slot
 *    (overview null, seasons empty, people empty, media-source branch selection,
 *    chip-row takeIfs, ...). They stay in the section renderers; the admission
 *    rows below document them so the split is auditable. A renderer may still
 *    return before emitting its slot when it has nothing at all to show
 *    (OVERVIEW without a synopsis or links) — the empty-slot gap is then gone,
 *    which is the point.
 */
internal data class DetailSectionAdmission(
    /** Media family of the loaded item — drives the book/music/series/collection forks. */
    val mediaType: MediaType,
    /** True when the snapshot is a local (offline / remote-failure) projection. */
    val isLocalOrigin: Boolean,
    /** Source-aware capability set for the snapshot (single authority for remote/local gating). */
    val capabilities: DetailCapabilities,
    /** DetailContentBody param: portrait body shows the action row, landscape does not. */
    val showActionButtons: Boolean,
    /** DetailContentBody param: every current call site leaves it true. */
    val showMediaInfo: Boolean,
    /** Book UI state present (state.core.book != null) — reading card + TOC source. */
    val hasBookState: Boolean,
    /** The book's TOC cache carries entries (book?.toc non-empty). */
    val hasBookToc: Boolean,
    /** Server/detail chapter list carries entries (detail.chapters non-empty). */
    val hasChapters: Boolean,
    /** Album/audio track list carries entries (state.core.albumTracks non-empty). */
    val hasAlbumTracks: Boolean,
    /** Smart-play target resolved (state.core.smartPlayTarget != null). */
    val hasSmartPlayTarget: Boolean,
    /** User preference: the detail screen shows the Up Next card. */
    val showDetailUpNext: Boolean,
    /** Seerr integration is connected AND recommendations are enabled. */
    val seerrDataAvailable: Boolean,
    /** Seerr recommendation cards fetched (state.core.seerrRecommendations non-empty). */
    val hasSeerrRecommendations: Boolean,
    /** Seerr similar cards fetched (state.core.seerrSimilar non-empty). */
    val hasSeerrSimilar: Boolean,
    /** A download lifecycle is attached to the snapshot (state.core.detailContext?.download != null). */
    val hasAttachedDownload: Boolean,
    /** Plugin scored-similar items hydrated (state.core.pluginSimilarItems non-empty — ADR 0010).
     *  Admits JELLYPLAY_SIMILAR; with [suppressStockSimilar] it also stands the
     *  stock row down. */
    val hasPluginSimilar: Boolean = false,
    /** The capabilities handshake confirmed the plugin registered into the
     *  host's similar-items pipeline (Jellyfin 12+): there the stock endpoint
     *  returns the same scored list, so while the plugin row actually has
     *  content, MORE_LIKE_THIS stands down. False on pre-12 hosts / older
     *  plugins — the lists differ, both rows render. */
    val suppressStockSimilar: Boolean = false,
) {

    /**
     * Folds the inputs into the ordered list of sections the body renders. The
     * order is the former source emission order (the series trio UP_NEXT →
     * UPCOMING_EPISODES → SEASONS — all delayIndex 6), and each kind carries
     * its own delayIndex.
     *
     * Six sections left the fold in the section-module waves — PLUGIN_RATINGS
     * and TMDB_REVIEWS in the wave-1 pilot, then SPECIAL_FEATURES,
     * COLLECTION_ITEMS, UPCOMING_EPISODES and MORE_IN_COLLECTION in wave 2 —
     * admitted by their [DetailSectionModule]s instead
     * ([DetailSectionRegistry.admittedSections] merges the two halves in this
     * enum's declaration order, so the body's emission is unchanged; the
     * every-kind-exactly-one-owner pin lives in DetailSectionRegistryTest).
     */
    fun admit(): List<DetailSectionKind> = buildList {
        fun admit(kind: DetailSectionKind, admitted: Boolean) {
            if (admitted) add(kind)
        }

        admit(DetailSectionKind.HEADER, admitted = true)
        admit(DetailSectionKind.ACTION_ROW, showActionButtons)
        admit(
            DetailSectionKind.BOOK_READING_CARD,
            mediaType == MediaType.BOOK && hasBookState,
        )
        admit(
            DetailSectionKind.MEDIA_INFO,
            showMediaInfo && !mediaType.isAudioType,
        )
        admit(DetailSectionKind.OVERVIEW, admitted = true)
        admit(
            DetailSectionKind.CHAPTERS_OR_TOC,
            (mediaType == MediaType.BOOK && hasBookToc) ||
                (capabilities.chapters && hasChapters),
        )
        admit(
            DetailSectionKind.ALBUM_TRACKS,
            mediaType.isAudioType && hasAlbumTracks,
        )
        admit(
            DetailSectionKind.UP_NEXT,
            mediaType == MediaType.SERIES && hasSmartPlayTarget && showDetailUpNext,
        )
        admit(DetailSectionKind.SEASONS, showsSeasonTree(mediaType))
        admit(DetailSectionKind.CAST, mediaType != MediaType.BOOK)
        admit(DetailSectionKind.RELATED_VIDEOS, admitted = true)
        admit(
            DetailSectionKind.MORE_LIKE_THIS,
            // Stand the stock row down only where it would duplicate the plugin
            // row exactly: a pipeline host (stock == plugin list) AND the
            // plugin row actually rendering. Anywhere else — pre-12 hosts,
            // older plugins, empty/failed plugin hydration — the stock row is
            // the content that would otherwise be lost.
            !(suppressStockSimilar && hasPluginSimilar),
        )
        admit(
            DetailSectionKind.JELLYPLAY_SIMILAR,
            hasPluginSimilar,
        )
        admit(
            DetailSectionKind.SEERR_RECOMMENDATIONS,
            seerrDataAvailable && hasSeerrRecommendations,
        )
        admit(
            DetailSectionKind.SEERR_SIMILAR,
            seerrDataAvailable && hasSeerrSimilar,
        )
        admit(
            DetailSectionKind.DOWNLOAD_FOOTER,
            hasAttachedDownload || isLocalOrigin,
        )
    }

    companion object {

        /**
         * Reads the admission inputs off a loaded detail state.core. [detail] must be
         * the body's already-unwrapped `state.core.detail` (the body returns early on
         * null before admitting).
         */
        fun from(
            state: DetailContentState,
            detail: MediaDetail,
            showActionButtons: Boolean = true,
            showMediaInfo: Boolean = true,
        ): DetailSectionAdmission = DetailSectionAdmission(
            mediaType = detail.item.mediaType,
            isLocalOrigin = state.core.origin?.isLocal == true,
            capabilities = state.core.capabilities,
            showActionButtons = showActionButtons,
            showMediaInfo = showMediaInfo,
            hasBookState = state.core.book != null,
            hasBookToc = !state.core.book?.toc.isNullOrEmpty(),
            hasChapters = detail.chapters.isNotEmpty(),
            hasAlbumTracks = state.core.albumTracks.isNotEmpty(),
            hasSmartPlayTarget = state.core.smartPlayTarget != null,
            showDetailUpNext = state.preferences.showDetailUpNext,
            seerrDataAvailable = state.core.isSeerrConnected && state.core.isSeerrRecommendationsEnabled,
            hasSeerrRecommendations = state.core.seerrRecommendations.isNotEmpty(),
            hasSeerrSimilar = state.core.seerrSimilar.isNotEmpty(),
            hasAttachedDownload = state.core.detailContext?.download != null,
            hasPluginSimilar = state.core.pluginSimilarItems.isNotEmpty(),
            suppressStockSimilar = state.core.pluginSimilarSuppressesStock,
        )
    }
}

/**
 * The admission gate of the SEASONS slot ([DetailSectionAdmission] applies
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
