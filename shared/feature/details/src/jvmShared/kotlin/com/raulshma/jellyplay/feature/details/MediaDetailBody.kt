package com.raulshma.jellyplay.feature.details

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Heart
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Star
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import com.raulshma.jellyplay.core.designsystem.theme.LocalThemeVariant
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.detailCardBorder
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.formatBytes
import com.raulshma.jellyplay.core.model.formatFixed
import com.raulshma.jellyplay.core.model.isAudioType
import com.raulshma.jellyplay.core.model.progressFraction
import com.raulshma.jellyplay.core.model.seerr.SeerrRelatedVideo
import com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem
import com.raulshma.jellyplay.core.model.seerr.TmdbImageUrls
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.adaptive.WindowSizeClass
import com.raulshma.jellyplay.core.ui.adaptive.detailBodyMaxWidth
import com.raulshma.jellyplay.core.ui.components.EpisodeWatchedTag
import com.raulshma.jellyplay.core.ui.components.ExpandableText
import com.raulshma.jellyplay.core.ui.components.PosterCard
import com.raulshma.jellyplay.core.ui.components.seerr.SeerrMediaCard
import com.raulshma.jellyplay.core.ui.components.formatRuntimeLabelFromTicks
import com.raulshma.jellyplay.core.ui.components.seerr.seerrCardClickHandler
import com.raulshma.jellyplay.core.ui.image.MediaImage
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.TvFocusableItemRow
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_episode_play
import com.raulshma.jellyplay.feature.details.generated.resources.detail_cd_hide_up_next
import com.raulshma.jellyplay.feature.details.generated.resources.detail_more_like_this_on_device
import com.raulshma.jellyplay.feature.details.generated.resources.detail_review_rating
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_cast_crew
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_chapters
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_items
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_more_like_this
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_reviews
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_seerr_recommendations
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_seerr_similar
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_special_features
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_tracks
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_videos
import com.raulshma.jellyplay.feature.details.generated.resources.detail_see_all
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_format_comic
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_finished_badge
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_page_progress
import com.raulshma.jellyplay.feature.details.generated.resources.detail_book_percent_progress
import com.raulshma.jellyplay.feature.details.generated.resources.detail_time_left_format
import com.raulshma.jellyplay.feature.details.generated.resources.detail_up_next
import com.raulshma.jellyplay.feature.details.generated.resources.detail_watched_badge
import com.raulshma.jellyplay.feature.details.generated.resources.detail_episodes_count
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.pluralStringResource

/**
 * Shared entrance-reveal progress (0f → 1f) for the whole detail body.
 *
 * Previously each [FadingItem] and [StaggeredDetailSection] ran its own
 * `LaunchedEffect(Unit) + animateFloatAsState` pair — ~33 independent
 * coroutines and ~66 snapshot-state subscriptions fired on screen entry and
 * never fully released. This local is provided once (by [DetailContentBody])
 * from a single [Animatable], so every entrance-animated element reads the
 * same one snapshot/state and applies its stagger as pure arithmetic.
 *
 * `1f` is the default so elements outside a provider scope (e.g. tests) render
 * immediately instead of stuck at alpha 0.
 */
internal val LocalDetailEntrance = compositionLocalOf<Float> { 1f }

/**
 * Drives the single shared entrance animation. Returns the current progress as a
 * snapshot-read [Float] so consumers re-render only while the animation runs.
 *
 * The target is `1f + maxStaggerSpan` (not `1f`) because [StaggeredDetailSection]
 * subtracts a per-index offset from this value: animating only to `1f` would
 * leave every section with delayIndex > 0 permanently stuck below full alpha
 * (e.g. the lowest section at alpha ~0.46), making the lower half of the detail
 * body look dim once the animation finishes. Driving past `1f` lets the stagger
 * only delay each section's *start* — all of them clamp to alpha 1.0 at rest.
 */
@Composable
private fun rememberDetailEntranceProgress(): Float {
    val animatable = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val target = 1f + DETAIL_MAX_STAGGER_INDEX * DETAIL_STAGGER_STEP
    LaunchedEffect(spec) {
        animatable.animateTo(target, spec)
    }
    return animatable.asState().value
}

@Composable
internal fun FadingItem(
    modifier: Modifier = Modifier,
    delayIndex: Int = 0,
    content: @Composable () -> Unit,
) {
    val entrance = LocalDetailEntrance.current
    // Per-element stagger is a pure offset against the shared progress — no
    // coroutine, no extra snapshot subscription.
    val alpha = (entrance - delayIndex * 0.03f).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .graphicsLayer { this.alpha = alpha }
    ) {
        content()
    }
}

/**
 * The pure decision behind the detail title block: the logo renders only when
 * the user opted into "prefer logos" AND the server carries a clear-logo
 * (non-null tag) AND a URL resolved for it. Every miss — and specifically a
 * missing logo tag — returns false, so the plain text-title path is byte-for-
 * byte the one that existed before the feature. Pure → directly unit-testable
 * (the [MissingEpisodeBadge] pattern).
 */
internal fun preferLogoTitleEnabled(preferLogos: Boolean, logoTag: String?, logoUrl: String): Boolean =
    preferLogos && logoTag != null && logoUrl.isNotBlank()

/**
 * The detail body's trunk. The former ~1,060-line inline column decomposed
 * into two halves:
 *
 * 1. [DetailSectionAdmission] — a Compose-free fold deciding WHICH sections
 *    exist and in what order (each [DetailSectionKind] carries its own
 *    delayIndex, pinned by DetailSectionAdmissionTest). Sections whose slot
 *    used to compose empty (overview without a synopsis, seasons on a movie,
 *    ...) stay admitted: every slot contributes a spacedBy gap to the body
 *    Column, so admission reproduces the former `visible` predicates exactly
 *    and the content gates remain render-side.
 * 2. The family renderers in MediaDetailBodySections.kt — one
 *    [StaggeredDetailSection] slot each, bodies kept verbatim from the inline
 *    flow, delayIndex threaded from the admission descriptor. The already
 *    extracted sections (VideosSection / ReviewsSection / UpNextSection /
 *    the book + music files) join the trunk unchanged.
 *
 * The single shared entrance animation is unchanged: one
 * [rememberDetailEntranceProgress] driver provided through
 * [LocalDetailEntrance], every FadingItem / StaggeredDetailSection reading it
 * and applying its stagger as pure arithmetic.
 */
@Composable
internal fun DetailContentBody(
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.TopCenter,
    showActionButtons: Boolean = true,
    showMediaInfo: Boolean = true,
    contentFocusRequester: FocusRequester? = null,
) {
    val detail = state.detail ?: return
    val item = detail.item

    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    // Single source of truth for the body's horizontal inset so text, chips, and the
    // poster/action row all share the same left edge (previously the body hard-coded 24.dp
    // while the poster row used adaptiveInfo.contentPadding, causing misalignment on phones).
    val bodyContentPad = adaptiveInfo.contentPadding(isTv)

    // Single shared entrance animation drives every FadingItem / StaggeredDetailSection
    // in the body — replaces ~33 per-element LaunchedEffect + animateFloatAsState pairs.
    val entranceProgress = rememberDetailEntranceProgress()

    val admittedSections = DetailSectionAdmission
        .from(state, detail, showActionButtons, showMediaInfo)
        .admit()

    CompositionLocalProvider(LocalDetailEntrance provides entranceProgress) {
        Box(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = contentAlignment,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                admittedSections.forEach { section ->
                    // Key each slot by its kind so a membership change on item
                    // switch keeps per-section state anchored to its identity
                    // (equivalent to the former distinct inline call sites).
                    key(section) {
                        DetailSectionRenderer(
                            section = section,
                            state = state,
                            callbacks = callbacks,
                            detail = detail,
                            item = item,
                            isLocalOrigin = state.origin?.isLocal == true,
                            bodyContentPad = bodyContentPad,
                            contentFocusRequester = contentFocusRequester,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Dispatches one admitted section to its family renderer. Pure wiring — the
 * `when` is exhaustive over [DetailSectionKind] so a new admission row cannot
 * silently render nothing.
 */
@Composable
internal fun DetailSectionRenderer(
    section: DetailSectionKind,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
    detail: MediaDetail,
    item: MediaItem,
    isLocalOrigin: Boolean,
    bodyContentPad: Dp,
    contentFocusRequester: FocusRequester?,
) {
    when (section) {
        DetailSectionKind.HEADER ->
            DetailHeaderSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                detail = detail,
                item = item,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.ACTION_ROW ->
            DetailActionRowSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                contentFocusRequester = contentFocusRequester,
            )

        DetailSectionKind.BOOK_READING_CARD ->
            DetailBookReadingCardSection(
                delayIndex = section.delayIndex,
                state = state,
                item = item,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.MEDIA_INFO ->
            DetailMediaInfoSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                detail = detail,
                item = item,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.OVERVIEW ->
            DetailOverviewSection(
                delayIndex = section.delayIndex,
                state = state,
                item = item,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.PLUGIN_RATINGS ->
            DetailPluginRatingsSection(
                delayIndex = section.delayIndex,
                state = state,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.CHAPTERS_OR_TOC ->
            DetailChaptersSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                detail = detail,
                item = item,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.ALBUM_TRACKS ->
            DetailAlbumTracksSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                item = item,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.UP_NEXT ->
            DetailUpNextCardSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.SEASONS ->
            DetailSeasonsSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                item = item,
                isLocalOrigin = isLocalOrigin,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.COLLECTION_ITEMS ->
            DetailCollectionItemsSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                item = item,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.CAST ->
            DetailCastSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                detail = detail,
                item = item,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.RELATED_VIDEOS ->
            DetailRelatedVideosSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
            )

        DetailSectionKind.MORE_LIKE_THIS ->
            DetailMoreLikeThisSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                isLocalOrigin = isLocalOrigin,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.JELLYPLAY_SIMILAR ->
            DetailJellyPlaySimilarSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.SEERR_RECOMMENDATIONS ->
            DetailSeerrRowSection(
                delayIndex = section.delayIndex,
                titleRes = Res.string.detail_section_seerr_recommendations,
                items = state.seerrRecommendations,
                keyPrefix = "seerr_rec",
                contentType = "seerrRecItem",
                callbacks = callbacks,
            )

        DetailSectionKind.SEERR_SIMILAR ->
            DetailSeerrRowSection(
                delayIndex = section.delayIndex,
                titleRes = Res.string.detail_section_seerr_similar,
                items = state.seerrSimilar,
                keyPrefix = "seerr_sim",
                contentType = "seerrSimItem",
                callbacks = callbacks,
            )

        DetailSectionKind.SPECIAL_FEATURES ->
            DetailSpecialFeaturesSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                bodyContentPad = bodyContentPad,
            )

        DetailSectionKind.TMDB_REVIEWS ->
            DetailReviewsSlotSection(
                delayIndex = section.delayIndex,
                state = state,
            )

        DetailSectionKind.DOWNLOAD_FOOTER ->
            DetailDownloadFooterSection(
                delayIndex = section.delayIndex,
                state = state,
                callbacks = callbacks,
                item = item,
                bodyContentPad = bodyContentPad,
            )
    }
}

// ── Related videos (delayIndex 9) ─────────────────────────────────────────

@Composable
private fun DetailRelatedVideosSection(
    delayIndex: Int,
    state: DetailContentState,
    callbacks: DetailContentCallbacks,
) {
    StaggeredDetailSection(visible = true, delayIndex = delayIndex) {
        if (state.relatedVideos.isNotEmpty()) {
            VideosSection(videos = state.relatedVideos, onVideoClick = callbacks.seerr.onVideoClick)
        }
    }
}

// ── TMDB reviews (delayIndex 14) ──────────────────────────────────────────

@Composable
private fun DetailReviewsSlotSection(
    delayIndex: Int,
    state: DetailContentState,
) {
    // ── TMDB Reviews ──
    // Fetched straight from TMDB (see DetailViewModel.loadSeerrData), so the
    // section renders regardless of Seerr connection state. Rendered as a
    // vertical card list — review bodies are paragraphs, not glanceable tiles,
    // so they join the screen scroll instead of a horizontal row.
    StaggeredDetailSection(
        visible = state.tmdbReviews.isNotEmpty(),
        delayIndex = delayIndex,
    ) {
        ReviewsSection(reviews = state.tmdbReviews)
    }
}

@Composable
internal fun SeerrItemsRow(
    title: String,
    keyPrefix: String,
    contentType: String,
    items: List<SeerrSearchItem>,
    onSeerrRequest: (SeerrSearchItem) -> Unit,
    onNavigate: (com.raulshma.jellyplay.core.ui.navigation.Route) -> Unit,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val loadingState = com.raulshma.jellyplay.core.ui.components.seerr.LocalSeerrCardLoadingState.current
    val prefetch = com.raulshma.jellyplay.core.ui.components.seerr.LocalSeerrPrefetch.current
    val cardWidth = if (adaptiveInfo.windowSizeClass != WindowSizeClass.Compact) 200.dp else 160.dp
    val bodyContentPad = adaptiveInfo.contentPadding(isTv = LocalTvMode.current)

    Column {
        FadingItem {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .padding(horizontal = bodyContentPad)
                    .semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(16.dp))
        TvFocusableItemRow(
            items = items,
            key = { "${keyPrefix}_${it.id}" },
            contentPadding = PaddingValues(horizontal = bodyContentPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { _, seerrItem, focusModifier ->
                SeerrMediaCard(
                    item = seerrItem,
                    imageUrl = seerrItem.posterUrl,
                    isLoading = loadingState?.isLoading(seerrItem.id) == true,
                    onClick = seerrCardClickHandler(
                        loadingState = loadingState,
                        prefetch = prefetch,
                        id = seerrItem.id,
                        mediaType = seerrItem.mediaType,
                    ) {
                        onNavigate(com.raulshma.jellyplay.core.ui.navigation.Route.SeerrDetail(seerrItem.id, seerrItem.mediaType))
                    },
                    onRequestClick = { onSeerrRequest(seerrItem) },
                    modifier = focusModifier.width(cardWidth),
                )
        }
    }
}


@Composable
private fun VideosSection(
    videos: List<SeerrRelatedVideo>,
    onVideoClick: (SeerrRelatedVideo) -> Unit,
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
            items = videos,
            // Fall back to the video name when its key is null so two null-key
            // videos don't collide and collapse their composition slots.
            key = { video -> video.key ?: video.name ?: "video" },
            contentType = { _, _ -> "video" },
            contentPadding = PaddingValues(horizontal = bodyContentPad),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) { _, video, focusModifier ->
            // The card body is the shared [YouTubeVideoCard] (commonMain
            // DetailSectionVocabulary) — collapsed with the Seerr family's
            // identical copy; only the row scaffolding + this border decision
            // stay family-local.
            YouTubeVideoCard(
                video = video,
                fallbackLabel = "Video",
                onClick = { onVideoClick(video) },
                modifier = focusModifier,
                border = videoCardBorder,
            )
        }
    }
}

/** TMDB avatar URL for the 40dp review circle (w90 covers it at 2x density). */
private fun tmdbAvatarUrl(avatarPath: String?): String? =
    avatarPath?.takeIf { it.isNotBlank() }?.let { path ->
        // Gravatar-linked accounts ship "/https://…" — the path is already an
        // absolute URL, so it must not get the image.tmdb.org prefix.
        if (path.startsWith("/http")) path.drop(1) else "${TmdbImageUrls.BASE}/w90$path"
    }

/**
 * Vertical list of TMDB review cards. Part of the screen scroll (not a lazy
 * row) — the VM caps the list at 5 entries, and review bodies are paragraphs
 * that want the full content width. Cards are informational only: no click.
 */
@Composable
private fun ReviewsSection(
    reviews: List<TmdbReview>,
) {
    val bodyContentPad = LocalAdaptiveInfo.current.contentPadding(isTv = LocalTvMode.current)
    Column(modifier = Modifier.padding(horizontal = bodyContentPad)) {
        FadingItem {
            Text(
                text = stringResource(Res.string.detail_section_reviews),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            reviews.forEach { review ->
                FadingItem {
                    // Display name falls back username-first: TMDB often ships an
                    // empty author_details.name for casual reviewers.
                    val authorName = review.authorDetails.name.takeIf { it.isNotBlank() }
                        ?: review.authorDetails.username.takeIf { it.isNotBlank() }
                        ?: review.author
                    val avatarUrl = remember(review.id) { tmdbAvatarUrl(review.authorDetails.avatarPath) }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(ShapeCache.smooth16)
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (avatarUrl != null) {
                                    MediaImage(
                                        url = avatarUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                    )
                                } else {
                                    Text(
                                        text = authorName.take(1).uppercase().ifBlank { "?" },
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = authorName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                review.authorDetails.rating?.let { rating ->
                                    Text(
                                        text = stringResource(Res.string.detail_review_rating, formatFixed(rating, 1)),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(
                            text = review.content,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun UpNextSection(
    target: DetailUiState.SmartPlayTarget,
    onPlayClick: () -> Unit,
    onHideClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.98f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
        label = "upNextCardScale",
    )
    val playInteractionSource = remember { MutableInteractionSource() }
    val isPlayPressed by playInteractionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (isPlayPressed) 0.85f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "upNextPlayScale",
    )

    // Border depends only on the active theme, not on this card's state — wrap
    // in remember so the Modifier + gradient aren't rebuilt per recomposition.
    // includeAurora = false keeps the historical no-border look under Aurora
    // (only the Seerr detail cards glow there).
    val themeVariant = LocalThemeVariant.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val outlineColor = MaterialTheme.colorScheme.outline
    val borderModifier = remember(themeVariant, primaryColor, secondaryColor, outlineColor) {
        themeVariant.detailCardBorder(primaryColor, secondaryColor, outlineColor, includeAurora = false)
            ?.let { Modifier.border(it, ShapeCache.smooth16) }
            ?: Modifier
    }

    val cardFocusState = rememberTvFocusState(focusedScale = 1.02f)
    val hideFocusState = rememberTvFocusState(focusedScale = 1.1f)
    val confirmHaptic = com.raulshma.jellyplay.core.ui.feedback.rememberConfirmHaptic()

    Column(modifier = modifier.fillMaxWidth()) {
        FadingItem {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.detail_up_next),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() },
                )
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .clip(ShapeCache.smooth16)
                        .then(hideFocusState.focusModifier)
                        .then(Modifier.tvFocusIndicator(hideFocusState, ShapeCache.smooth16))
                        .clickable {
                            confirmHaptic()
                            onHideClick()
                        },
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = ShapeCache.smooth16,
                ) {
                    Icon(
                        imageVector = Tabler.Outline.EyeOff,
                        contentDescription = stringResource(Res.string.detail_cd_hide_up_next),
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
            }
        }

        FadingItem {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(borderModifier)
                    .clip(ShapeCache.smooth16)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
                    .graphicsLayer { scaleX = cardScale; scaleY = cardScale }
                    .then(cardFocusState.focusModifier)
                    .then(Modifier.tvFocusIndicator(cardFocusState, ShapeCache.smooth16))
                    .clickable(
                        interactionSource = cardInteractionSource,
                        indication = null,
                        onClick = {
                            confirmHaptic()
                            onPlayClick()
                        },
                    ),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 160.dp, height = 90.dp)
                        .clip(ShapeCache.smooth16),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaImage(
                        url = target.primaryImageUrl ?: "",
                        contentDescription = target.episode.name,
                        blurHash = target.episode.blurHashes.primary,
                        size = coil3.size.Size(480, 270),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.3f)))
                    val epPlayFocusState = rememberTvFocusState(focusedScale = 1.15f)
                    Icon(
                        Tabler.Outline.PlayerPlay,
                        contentDescription = stringResource(Res.string.detail_cd_episode_play),
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .size(40.dp)
                            .graphicsLayer { scaleX = playScale; scaleY = playScale }
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), CircleShape)
                            .then(epPlayFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(epPlayFocusState, CircleShape))
                            .clickable(
                                interactionSource = playInteractionSource,
                                indication = null,
                                onClick = {
                                    confirmHaptic()
                                    onPlayClick()
                                },
                            )
                            .padding(8.dp),
                    )
                    // Smart-play resume math over the resolver's start position;
                    // the > 0f check keeps the "no resume position" case barless.
                    val progress = target.episode.progressFraction(target.startPositionTicks)
                    if (progress != null && progress > 0f) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(ShapeCache.smooth8)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = target.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = target.episode.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val runtimeTicks = target.episode.runTimeTicks
                    val positionTicks = target.startPositionTicks
                    val hasWatchProgress = positionTicks > 0
                    val remainingTime = if (hasWatchProgress && runtimeTicks != null && runtimeTicks > 0) {
                        com.raulshma.jellyplay.core.ui.components.formatRemainingTimeFromTicks(runtimeTicks, positionTicks)
                    } else null
                    val totalTime = if (runtimeTicks != null) {
                        com.raulshma.jellyplay.core.ui.components.formatDurationFromTicks(runtimeTicks)
                    } else null

                    if (remainingTime != null && totalTime != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            Text(
                                text = stringResource(Res.string.detail_time_left_format, remainingTime),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Text(
                                text = "•",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.outlineVariant,
                            )
                            Text(
                                text = totalTime,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else if (totalTime != null) {
                        Text(
                            text = totalTime,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }

                    target.episode.overview?.takeIf { it.isNotBlank() }?.let { overview ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = overview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = androidx.compose.ui.unit.TextUnit(16f, androidx.compose.ui.unit.TextUnitType.Sp),
                        )
                    }
                }
            }
        }
    }
}
