package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.data.util.ArtworkUrls
import com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant
import com.raulshma.jellyplay.core.designsystem.theme.detailCardBorder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_seerr_recommendations
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_seerr_similar
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_videos
import com.raulshma.jellyplay.feature.details.generated.resources.detail_trailer_badge_local
import org.jetbrains.compose.resources.stringResource

/**
 * The detail body's row/footer section modules (the wave that retired the
 * dual admission path): the season tree, the cast row, the related-videos
 * (trailer) row, and the download footer. Each module is a verbatim move of
 * its DetailSectionAdmission fold row (admission) and of its
 * [DetailSectionRenderer] `when` branch (render, routing to the existing
 * family composable — the videos row's private composables moved here with
 * their module); none carries a fetch arm (their content rides the core
 * resolution and the state arms in DetailEnrichmentModules.kt).
 */

/**
 * The SEASONS section module: the season tabs + episode tree for
 * SERIES/EPISODE/SEASON. Admission is [showsSeasonTree] — the fold row
 * verbatim; the content-presence half (empty seasons) stays render-side.
 */
internal object SeasonsSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.SEASONS

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean = showsSeasonTree(detail.item.mediaType)

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailSeasonsSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            item = scope.item,
            isLocalOrigin = scope.isLocalOrigin,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The CAST section module: the cast & crew row for every non-BOOK item — the
 * `mediaType != BOOK` fold row verbatim; the empty-people gate stays
 * render-side.
 */
internal object CastSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.CAST

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean = detail.item.mediaType != MediaType.BOOK

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailCastSection(
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
 * The RELATED_VIDEOS section module: the Seerr "Videos" (trailer/extras mined
 * from TMDB) row. Admitted unconditionally — the `admit(RELATED_VIDEOS,
 * admitted = true)` fold row verbatim; the empty-list gate is render-side.
 * The renderer is the former private `DetailRelatedVideosSection` moved
 * verbatim from MediaDetailBody.kt (its `VideosSection` body with it).
 */
internal object RelatedVideosSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.RELATED_VIDEOS

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean = true

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailRelatedVideosSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
        )
    }
}

// ── Related videos (delayIndex 9) ─────────────────────────────────────────

/**
 * The related-videos row's dispatch wrapper — moved verbatim from
 * MediaDetailBody.kt (the former private `DetailRelatedVideosSection`).
 */
@Composable
private fun DetailRelatedVideosSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
) {
    if (state.core.trailers.isEmpty()) return
    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
        VideosSection(
            trailers = state.core.trailers,
            urls = callbacks.artwork.urls,
            onVideoClick = callbacks.seerr.onVideoClick,
            onPlayLocal = { trailerId ->
                // A local (server-hosted) trailer plays through the
                // full player session — the same route the Play button
                // takes (the libmpv engine on desktop), never the
                // WebView embed.
                callbacks.playback.onPlayClick(trailerId, null, 0L)
            },
        )
    }
}

/**
 * The videos row's scaffolding — moved verbatim from MediaDetailBody.kt (the
 * former private `VideosSection`).
 */
@Composable
private fun VideosSection(
    trailers: List<DetailUiState.TrailerEntry>,
    urls: ArtworkUrls,
    onVideoClick: (SeerrRelatedVideo) -> Unit,
    onPlayLocal: (String) -> Unit,
) {
    val bodyContentPad = LocalAdaptiveInfo.current.contentPadding(isTv = LocalTvMode.current)
    // The video card border depends only on the active theme, not on the per-video
    // data, so compute it once here rather than rebuilding a BorderStroke + gradient
    // per video per recomposition. includeAurora = false keeps the historical
    // no-border look under Aurora (only the Seerr detail cards glow there).
    // (Family-local decision: the Seerr row renders the shared card borderless.)
    val themeVariant = LocalThemeVariant.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    val videoCardBorder = remember(themeVariant, primaryColor, secondaryColor, outlineColor) {
        themeVariant.detailCardBorder(primaryColor, secondaryColor, outlineColor, includeAurora = false)
    }
    // The local-source badge resolved once (the merged row mixes the two
    // card variants; the badge marks the server-hosted entries).
    val localBadge = stringResource(Res.string.detail_trailer_badge_local)
    Column {
        FadingItem {
            Text(
                text = stringResource(Res.string.detail_section_videos),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .padding(horizontal = bodyContentPad)
                    .semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(16.dp))
        TvFocusableItemRow(
            items = trailers,
            // Fall back to the video name/id when its key is null so two
            // keyless entries don't collide and collapse their composition
            // slots (the remote rule; locals key on the library item id).
            key = { entry ->
                when (entry) {
                    is DetailUiState.TrailerEntry.Local -> "local_${entry.mediaItem.id}"
                    is DetailUiState.TrailerEntry.Remote -> entry.video.key ?: entry.video.name ?: "video"
                }
            },
            contentType = { _, entry ->
                when (entry) {
                    is DetailUiState.TrailerEntry.Local -> "local_video"
                    is DetailUiState.TrailerEntry.Remote -> "video"
                }
            },
            contentPadding = PaddingValues(horizontal = bodyContentPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { _, entry, focusModifier ->
            // The card bodies are the shared [YouTubeVideoCard] /
            // [LocalTrailerVideoCard] (commonMain DetailSectionVocabulary) —
            // collapsed with the Seerr family's identical copy; only the row
            // scaffolding + this border decision stay family-local. The
            // dispatch is the [resolveTrailerEntryDispatch] policy: locals
            // play in the full player session, remotes open the YouTube
            // dialog (embed failure falling back to the external URL).
            when (val dispatch = resolveTrailerEntryDispatch(entry)) {
                is TrailerEntryDispatch.PlayerSession -> LocalTrailerVideoCard(
                    mediaItem = dispatch.mediaItem,
                    fallbackLabel = "Video",
                    // Keyed on the item (not its id) so a tag change re-keys.
                    imageUrl = remember(dispatch.mediaItem) { urls.poster(dispatch.mediaItem) },
                    badgeLabel = localBadge,
                    onClick = { onPlayLocal(dispatch.mediaItem.id) },
                    modifier = focusModifier,
                    border = videoCardBorder,
                )
                is TrailerEntryDispatch.YouTubeEmbed -> YouTubeVideoCard(
                    video = dispatch.video,
                    fallbackLabel = "Video",
                    onClick = { onVideoClick(dispatch.video) },
                    modifier = focusModifier,
                    border = videoCardBorder,
                )
            }
        }
    }
}

/**
 * The DOWNLOAD_FOOTER section module: the download info card + freshness
 * banner footer. Admission is the `hasAttachedDownload || isLocalOrigin`
 * fold row verbatim (the projections inlined: the attachment off
 * `state.core.detailContext`, the origin off `state.core.origin`).
 */
/**
 * The MORE_LIKE_THIS section module: the stock "More like this" poster row —
 * server relatedItems (remote) or localRelatedItems (local), the choice
 * render-side. Suppressed only on a Jellyfin 12+ pipeline host
 * (pluginSimilarSuppressesStock) while the plugin row actually renders —
 * there the stock endpoint returns the same scored list. The suppression
 * conjunction is the fold row verbatim (the projections inlined off
 * `state.core`).
 */
internal object MoreLikeThisSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.MORE_LIKE_THIS

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean =
        !(state.core.pluginSimilarSuppressesStock && state.core.pluginSimilarItems.isNotEmpty())

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailMoreLikeThisSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            isLocalOrigin = scope.isLocalOrigin,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The JELLYPLAY_SIMILAR section module: the companion plugin's server-scored
 * "More like this" row (ADR 0010). Admitted whenever the plugin returned
 * items — the `hasPluginSimilar` fold row verbatim (the projection inlined:
 * `state.core.pluginSimilarItems.isNotEmpty()`).
 */
internal object JellyPlaySimilarSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.JELLYPLAY_SIMILAR

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean = state.core.pluginSimilarItems.isNotEmpty()

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailJellyPlaySimilarSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The SEERR_RECOMMENDATIONS section module: the Seerr recommendations row.
 * Admission is the connection x recommendations-enabled x content
 * conjunction — the `seerrDataAvailable && hasSeerrRecommendations` fold row
 * verbatim (the projections inlined: the flags and the list off `state.core`).
 */
internal object SeerrRecommendationsSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.SEERR_RECOMMENDATIONS

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean =
        state.core.isSeerrConnected &&
            state.core.isSeerrRecommendationsEnabled &&
            state.core.seerrRecommendations.isNotEmpty()

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailSeerrRowSection(
            delayIndex = kind.delayIndex,
            titleRes = Res.string.detail_section_seerr_recommendations,
            items = scope.state.core.seerrRecommendations,
            keyPrefix = "seerr_rec",
            contentType = "seerrRecItem",
            callbacks = scope.callbacks,
        )
    }
}

/**
 * The SEERR_SIMILAR section module: the Seerr similar row — the
 * recommendations module's twin over `seerrSimilar` (the fold row verbatim).
 */
internal object SeerrSimilarSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.SEERR_SIMILAR

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean =
        state.core.isSeerrConnected &&
            state.core.isSeerrRecommendationsEnabled &&
            state.core.seerrSimilar.isNotEmpty()

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailSeerrRowSection(
            delayIndex = kind.delayIndex,
            titleRes = Res.string.detail_section_seerr_similar,
            items = scope.state.core.seerrSimilar,
            keyPrefix = "seerr_sim",
            contentType = "seerrSimItem",
            callbacks = scope.callbacks,
        )
    }
}

internal object DownloadFooterSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.DOWNLOAD_FOOTER

    override fun isAdmitted(
        state: DetailContentState,
        detail: MediaDetail,
        showActionButtons: Boolean,
        showMediaInfo: Boolean,
    ): Boolean =
        state.core.detailContext?.download != null || state.core.origin?.isLocal == true

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailDownloadFooterSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            item = scope.item,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}
