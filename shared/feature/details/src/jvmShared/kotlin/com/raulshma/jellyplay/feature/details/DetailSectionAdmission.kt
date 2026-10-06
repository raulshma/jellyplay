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
 * slot), and UP_NEXT and SEASONS share `6` — so the body's emission order is
 * this enum's declaration order, independent of the stagger index.
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

    /** Expandable synopsis. Always admitted; the overview-null gate is render-side. */
    OVERVIEW(3),

    /** JellyPlay companion plugin's aggregated external-ratings chip row (ADR 0010). */
    PLUGIN_RATINGS(3),

    /** Book Contents (TOC cache) or the video chapter thumbnail row — first match wins. */
    CHAPTERS_OR_TOC(4),

    /** Album/audio track list. */
    ALBUM_TRACKS(5),

    /** Series smart-play "Up Next" card, ahead of SEASONS in emission order (both delayIndex 6). */
    UP_NEXT(6),

    /** Season tabs + episode lists for SERIES/EPISODE/SEASON. Always admitted; empty gate is render-side. */
    SEASONS(6),

    /** Collection members row. Always admitted; empty gate is render-side. */
    COLLECTION_ITEMS(7),

    /** Cast & crew row. Admitted for every non-BOOK item; the empty-people gate is render-side. */
    CAST(8),

    /** Seerr "Videos" (trailers/extras mined from TMDB). */
    RELATED_VIDEOS(9),

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
 *    (spacedBy 24.dp gap) even when its content renders nothing, so admission
 *    must match the former `visible` / outer-`if` predicates EXACTLY — a
 *    section that used to emit an empty staggered box (SEASONS on a movie,
 *    OVERVIEW without a synopsis, CAST with no people, ...) must stay admitted
 *    or the vertical rhythm of every detail screen changes.
 * 2. **Render-side gates** — content-presence checks inside an admitted slot
 *    (overview null, seasons empty, people empty, media-source branch selection,
 *    chip-row takeIfs, ...). They stay in the section renderers; the admission
 *    rows below document them so the split is auditable.
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
    /** Book UI state present (state.book != null) — reading card + TOC source. */
    val hasBookState: Boolean,
    /** The book's TOC cache carries entries (book?.toc non-empty). */
    val hasBookToc: Boolean,
    /** Server/detail chapter list carries entries (detail.chapters non-empty). */
    val hasChapters: Boolean,
    /** Album/audio track list carries entries (state.albumTracks non-empty). */
    val hasAlbumTracks: Boolean,
    /** Smart-play target resolved (state.smartPlayTarget != null). */
    val hasSmartPlayTarget: Boolean,
    /** User preference: the detail screen shows the Up Next card. */
    val showDetailUpNext: Boolean,
    /** Seerr integration is connected AND recommendations are enabled. */
    val seerrDataAvailable: Boolean,
    /** Seerr recommendation cards fetched (state.seerrRecommendations non-empty). */
    val hasSeerrRecommendations: Boolean,
    /** Seerr similar cards fetched (state.seerrSimilar non-empty). */
    val hasSeerrSimilar: Boolean,
    /** Special features fetched (state.specialFeatures non-empty). */
    val hasSpecialFeatures: Boolean,
    /** TMDB reviews fetched (state.tmdbReviews non-empty). */
    val hasTmdbReviews: Boolean,
    /** A download lifecycle is attached to the snapshot (state.detailContext?.download != null). */
    val hasAttachedDownload: Boolean,
    /** Plugin ratings chips fetched (state.pluginRatings non-empty — ADR 0010). */
    val hasPluginRatings: Boolean = false,
    /** Plugin scored-similar items hydrated (state.pluginSimilarItems non-empty — ADR 0010).
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
     * order is the former source emission order (UP_NEXT before SEASONS — both
     * delayIndex 6), and each kind carries its own delayIndex.
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
            DetailSectionKind.PLUGIN_RATINGS,
            hasPluginRatings,
        )
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
        admit(DetailSectionKind.SEASONS, admitted = true)
        admit(DetailSectionKind.COLLECTION_ITEMS, admitted = true)
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
            DetailSectionKind.SPECIAL_FEATURES,
            capabilities.remoteDiscovery && hasSpecialFeatures,
        )
        admit(DetailSectionKind.TMDB_REVIEWS, hasTmdbReviews)
        admit(
            DetailSectionKind.DOWNLOAD_FOOTER,
            hasAttachedDownload || isLocalOrigin,
        )
    }

    companion object {

        /**
         * Reads the admission inputs off a loaded detail state. [detail] must be
         * the body's already-unwrapped `state.detail` (the body returns early on
         * null before admitting).
         */
        fun from(
            state: DetailContentState,
            detail: MediaDetail,
            showActionButtons: Boolean = true,
            showMediaInfo: Boolean = true,
        ): DetailSectionAdmission = DetailSectionAdmission(
            mediaType = detail.item.mediaType,
            isLocalOrigin = state.origin?.isLocal == true,
            capabilities = state.capabilities,
            showActionButtons = showActionButtons,
            showMediaInfo = showMediaInfo,
            hasBookState = state.book != null,
            hasBookToc = !state.book?.toc.isNullOrEmpty(),
            hasChapters = detail.chapters.isNotEmpty(),
            hasAlbumTracks = state.albumTracks.isNotEmpty(),
            hasSmartPlayTarget = state.smartPlayTarget != null,
            showDetailUpNext = state.preferences.showDetailUpNext,
            seerrDataAvailable = state.isSeerrConnected && state.isSeerrRecommendationsEnabled,
            hasSeerrRecommendations = state.seerrRecommendations.isNotEmpty(),
            hasSeerrSimilar = state.seerrSimilar.isNotEmpty(),
            hasSpecialFeatures = state.specialFeatures.isNotEmpty(),
            hasTmdbReviews = state.tmdbReviews.isNotEmpty(),
            hasAttachedDownload = state.detailContext?.download != null,
            hasPluginRatings = state.pluginRatings.isNotEmpty(),
            hasPluginSimilar = state.pluginSimilarItems.isNotEmpty(),
            suppressStockSimilar = state.pluginSimilarSuppressesStock,
        )
    }
}

/**
 * The render-side gate of the SEASONS slot ([DetailSectionAdmission] admits
 * the slot for every item; this predicate is the render half): which detail
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
