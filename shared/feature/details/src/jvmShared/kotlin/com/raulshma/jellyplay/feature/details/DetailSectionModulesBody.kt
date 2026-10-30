package com.raulshma.jellyplay.feature.details

import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.isAudioType

/**
 * The detail body's structural-section modules (the wave that retired the
 * dual admission path): the sections every snapshot opens with — the title
 * block, the play/download action row, the book reading card, and the stream
 * info slot — as [DetailSectionModule]s. Each module is a verbatim move of
 * its former admission fold row (admission) and of its renderer `when`
 * branch (render, routing to the existing family composable); none carries a
 * fetch arm (their content rides the
 * core resolution and the state arms in DetailEnrichmentModules.kt).
 */

/**
 * The HEADER section module: the title block (series/episode context, logo
 * title, metadata row, chip rows). Admitted unconditionally — the former
 * `admit(HEADER, admitted = true)` fold row verbatim.
 */
internal object HeaderSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.HEADER

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean = true

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailHeaderSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            detail = scope.detail,
            item = scope.item,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The ACTION_ROW section module: the play/download action row. Admitted only
 * when the caller shows it (portrait body) — the `admit(ACTION_ROW,
 * showActionButtons)` fold row verbatim; [showActionButtons] is the one
 * module that reads the body param.
 */
internal object ActionRowSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.ACTION_ROW

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean = showActionButtons

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailActionRowSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            contentFocusRequester = scope.contentFocusRequester,
        )
    }
}

/**
 * The BOOK_READING_CARD section module: the reading-progress card that
 * occupies the media-info slot for BOOK items. Admitted for a BOOK with
 * book state present — the `mediaType == BOOK && hasBookState` fold row
 * verbatim (the hasBookState projection inlined: `state.core.book != null`).
 */
internal object BookReadingCardSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.BOOK_READING_CARD

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean = detail.item.mediaType == MediaType.BOOK && state.core.book != null

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailBookReadingCardSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            item = scope.item,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The MEDIA_INFO section module: stream quality/audio/subtitle info. Admitted
 * whenever the caller shows the media-info slot and the item is not an audio
 * type — the `showMediaInfo && !mediaType.isAudioType` fold row verbatim.
 * Branch selection (remote MediaInfoSection vs local badges vs subtitle
 * picker, keyed on media-source presence) is a render-side gate — the section
 * still emits (and occupies a spacedBy gap) when no branch renders, exactly
 * as before.
 */
internal object MediaInfoSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.MEDIA_INFO

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean = showMediaInfo && !detail.item.mediaType.isAudioType

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailMediaInfoSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            detail = scope.detail,
            item = scope.item,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The OVERVIEW section module: the expandable synopsis + external-link chips.
 * Admitted unconditionally — the `admit(OVERVIEW, admitted = true)` fold row
 * verbatim; the nothing-to-show gate (no synopsis, no visible links) is
 * render-side.
 */
internal object OverviewSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.OVERVIEW

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean = true

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailOverviewSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            detail = scope.detail,
            item = scope.item,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The CHAPTERS_OR_TOC section module: the book Contents (TOC cache) or the
 * video chapter thumbnail row — first match wins. Admission is the
 * `(BOOK && hasBookToc) || (capabilities.chapters && hasChapters)` fold row
 * verbatim (the projections inlined: the book's TOC off `state.core.book`,
 * the chapters off [detail]). The TOC branch deliberately does not read
 * `capabilities.chapters` — the local TOC cache is the authority for books.
 */
internal object ChaptersOrTocSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.CHAPTERS_OR_TOC

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean =
        (detail.item.mediaType == MediaType.BOOK && !state.core.book?.toc.isNullOrEmpty()) ||
            (state.core.capabilities.chapters && detail.chapters.isNotEmpty())

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailChaptersSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            detail = scope.detail,
            item = scope.item,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The ALBUM_TRACKS section module: the album/audio track list. Admitted for
 * an audio-type item with tracks fetched — the
 * `mediaType.isAudioType && hasAlbumTracks` fold row verbatim.
 */
internal object AlbumTracksSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.ALBUM_TRACKS

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean = detail.item.mediaType.isAudioType && state.core.albumTracks.isNotEmpty()

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailAlbumTracksSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            item = scope.item,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The UP_NEXT section module: the series smart-play "Up Next" card, ahead of
 * the series trio's shared slot 6. Admission is the SERIES × resolved-target ×
 * preference conjunction — the
 * `mediaType == SERIES && hasSmartPlayTarget && showDetailUpNext` fold row
 * verbatim (the projections inlined: the target off `state.core`, the
 * preference off `state.preferences`).
 */
internal object UpNextSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.UP_NEXT

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean =
        detail.item.mediaType == MediaType.SERIES &&
            state.core.smartPlayTarget != null &&
            state.preferences.showDetailUpNext

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailUpNextCardSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}
