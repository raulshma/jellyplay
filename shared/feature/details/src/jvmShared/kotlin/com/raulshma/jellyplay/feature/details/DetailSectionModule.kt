package com.raulshma.jellyplay.feature.details

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.data.repository.MediaCatalogueEnrichments
import com.raulshma.jellyplay.core.data.repository.CollectionOperations
import com.raulshma.jellyplay.core.data.repository.DetailFeed
import com.raulshma.jellyplay.core.model.arr.ArrServiceSummary
import com.raulshma.jellyplay.core.model.CollectionSummary
import com.raulshma.jellyplay.core.model.DetailPreferences
import com.raulshma.jellyplay.core.model.JellyPlayEpisodeMarker
import com.raulshma.jellyplay.core.model.JellyPlayEpisodeScore
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginOmdbAwards
import com.raulshma.jellyplay.core.model.JellyPlayPluginRating
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaSegment
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.SearchResult
import com.raulshma.jellyplay.core.model.seerr.SeerrRatings
import com.raulshma.jellyplay.core.model.seerr.TmdbDetails
import com.raulshma.jellyplay.core.model.seerr.TmdbReview
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.raulshma.jellyplay.feature.details.generated.resources.Res
import com.raulshma.jellyplay.feature.details.generated.resources.detail_reviews_all
import com.raulshma.jellyplay.feature.details.generated.resources.detail_section_reviews
import com.raulshma.jellyplay.feature.details.generated.resources.jellyplay_det_ratings_title
import org.jetbrains.compose.resources.stringResource

/**
 * The detail section module (the glossary term): everything one detail section
 * knows about itself — admission (does this media get this section), the
 * optional fetch/enrichment arm, and the renderer — behind one small interface
 * registered in [DetailSectionRegistry]. The Home row module's pattern applied
 * to the section whose inputs restated themselves in four places (state field,
 * content-bag field, admission fold row, hand-wired renderer + enrichment).
 *
 * [DetailSectionKind.delayIndex] stays on the enum (the render order's single
 * source of truth); a module renders into its kind's stagger slot, never
 * restating the index. The event projection a full module would also carry
 * still lives in [DetailContentCallbacks] — a later wave.
 *
 * Wave 1 was the pilot: the two ratings-adjacent sections
 * ([PluginRatingsSectionModule], [TmdbReviewsSectionModule]). Wave 2 brings
 * the four clean 1:1 collection-family/strip sections
 * ([SpecialFeaturesSectionModule], [CollectionItemsSectionModule],
 * [UpcomingEpisodesSectionModule], [MoreInCollectionSectionModule]) — each a
 * verbatim move of its admission fold row and its hand-written enrichment
 * entry, with the renderer slot routing to the existing family composable.
 * The remaining sections stay hand-written in [DetailSectionAdmission]'s fold
 * and [DetailSectionRenderer]'s `when` and migrate in later waves.
 */
internal interface DetailSectionModule {

    /** The section this module owns; its [DetailSectionKind.delayIndex] is the render slot. */
    val kind: DetailSectionKind

    /**
     * Admission: does this media get this section. Reads the same state
     * surface [DetailSectionAdmission.from] folds, so migrating a section is a
     * verbatim move of its fold row. Media-family gates read [detail]; the
     * render-side content gates (list emptiness inside an admitted slot)
     * stay in [Content].
     */
    fun isAdmitted(state: DetailContentState, detail: MediaDetail): Boolean

    /**
     * The renderer slot. [scope] bundles the region params the body's dispatch
     * already holds; a module reads what its region needs. The delayIndex
     * comes from [kind], never restated.
     */
    @Composable
    fun Content(scope: DetailSectionRenderScope)

    /**
     * The optional enrichment arm: the [DetailEnrichment] declarations this
     * section contributes to the ViewModel's ordered fan-out, verbatim from
     * the former hand-written entry. [host] supplies the VM-side seams the
     * fetch bodies read. Empty for a section whose fetch rides another
     * declaration or has none — the arm is optional.
     */
    fun enrichments(host: DetailSectionEnrichmentHost): List<DetailEnrichment> = emptyList()
}

/**
 * The params a section's region renders from — the exact surface
 * [DetailSectionRenderer] receives, bundled so a module's
 * [DetailSectionModule.Content] slot takes one parameter and reads what its
 * region needs. [DetailSectionKind.delayIndex] is deliberately absent: it
 * stays on the enum.
 */
@Immutable
internal data class DetailSectionRenderScope(
    val state: DetailContentState,
    val callbacks: DetailContentCallbacks,
    val detail: MediaDetail,
    val item: MediaItem,
    val isLocalOrigin: Boolean,
    val bodyContentPad: Dp,
    val contentFocusRequester: FocusRequester?,
)

/**
 * The VM-side seams a section module's enrichment arm reads — implemented by
 * [DetailViewModel] (thin delegations, no new state). The generic write/guard
 * seams, the plugin probe/fetch roles the wave-1 arm needs, and the
 * collection-family/strip roles the wave-2 arms need; later waves extend it
 * per migrated section.
 */
internal interface DetailSectionEnrichmentHost {

    /** The staleness guard's identity check at suspension-point writes ([DetailLoadGuard.isCurrent]). */
    fun isCurrent(itemId: String): Boolean

    /** The single content-state write seam (the `_uiState.update` funnel). */
    fun updateState(reduce: (DetailUiState) -> DetailUiState)

    /**
     * The ADR 0010 plugin feature gate (one latched probe per item navigation
     * AND the user's per-feature toggle) — the DetailViewModel's one gate seam.
     */
    suspend fun pluginFeature(feature: String, itemId: String): Boolean

    /**
     * The mdblist ratings role ([com.raulshma.jellyplay.core.network.api.JellyPlayRatingsRoutes.getMdbListRatings]).
     * Null when the plugin seam is unwired (direct-construction tests) — the
     * arm degrades to silent absence.
     */
    suspend fun mdbListRatings(imdbId: String): Result<List<JellyPlayPluginRating>?>?

    // The collection-family / strip roles (specialFeatures,
    //    collectionItems, franchise, upcoming).

    /** The item-attached extras read ([LibraryApiClient.getSpecialFeatures]) — the SPECIAL_FEATURES arm. */
    suspend fun specialFeatures(itemId: String): Result<List<MediaItem>>

    /**
     * The collection-members page read ([CollectionOperations.getCollectionItems]) —
     * the COLLECTION_ITEMS arm, and the franchise rows' per-BoxSet members.
     */
    suspend fun collectionItems(collectionId: String, startIndex: Int = 0, limit: Int): Result<SearchResult>

    /** The BoxSet ancestors read ([MediaCatalogueEnrichments.getBoxSetsContaining]) — the franchise arm's enumerate leg. */
    suspend fun boxSetsContaining(itemId: String): Result<List<CollectionSummary>>

    /** The series "Airing next" strip read ([LibraryApiClient.getUpcoming]) — the UPCOMING_EPISODES arm. */
    suspend fun upcomingEpisodes(parentId: String, limit: Int): Result<List<MediaItem>>

    /**
     * The projected detail-preference snapshot the arms' BODY-pref halves read
     * (the externalRatings convention: the gate lambda stays a pure snapshot
     * read, so a user preference is checked in the body instead).
     */
    fun detailPreferences(): DetailPreferences

    /**
     * The shared bounded-parallel fan-out (the VM's hydration semaphore,
     * [com.raulshma.jellyplay.core.concurrency.mapConcurrentCatching] semantics:
     * order-preserving, per-item failures drop) — the franchise members
     * hydration rides it.
     */
    suspend fun <T, R> mapConcurrent(items: List<T>, transform: suspend (T) -> R): List<R>

    // ── Wave 3: the VM-state enrichment arms (DetailEnrichmentModules.kt) —
    //    the reads their former hand-written bodies in DetailViewModel made
    //    directly on the VM's collaborators. Unlike the wave-1/2 roles these
    //    carry DEFAULT bodies with the unwired-seam contract every plugin arm
    //    already documents: an unoverridden role degrades to silent absence
    //    (null / empty / false / no-op), so the registry's test fakes override
    //    only the roles their arms exercise.

    /** The published detail state read (suspension-point reads: isSeerrConnected, seasonRatings). */
    fun currentState(): DetailUiState = DetailUiState()

    /** The theme-music player's start ([DetailThemeMusic.playThemeFor]) — the themeMusic arm. Defaults to no-op. */
    fun playThemeMusic(itemId: String) {}

    /** The ARR servers resolve ([ArrRepository.resolveServers]) — the arrServerResolve arm. */
    suspend fun resolveArrServers(): Result<ArrServiceSummary> = Result.success(ArrServiceSummary())

    /** The stock similar read ([DetailFeed.getSimilarItems]) — the similarItems arm. */
    suspend fun similarItems(itemId: String, limit: Int): Result<List<MediaItem>> = Result.success(emptyList())

    /** The media-segments pre-warm ([PlaybackRepository.getMediaSegments]) — the mediaSegments arm. */
    suspend fun mediaSegments(itemId: String): Result<List<MediaSegment>> = Result.success(emptyList())

    /** The Seerr companion trigger (the VM's loadSeerrDataIfNeeded) — the seerrDiscovery arm. No-op by default. */
    suspend fun triggerSeerrDiscovery(detail: MediaDetail) {}

    /** The on-device related read ([OfflineRepository.getLocalRelated]) — the localRelatedItems arm. */
    suspend fun localRelatedItems(
        currentId: String,
        genres: List<String>,
        studios: List<String>,
        limit: Int,
    ): List<MediaItem> = emptyList()

    /** The Seerr ratings read ([SeerrRepository.getRatings]) — the externalRatings arm's first leg. */
    suspend fun seerrRatings(tmdbId: Int, mediaType: String): Result<SeerrRatings> =
        Result.failure(UnsupportedOperationException("unwired"))

    /** The direct-TMDB details read — the externalRatings fallback leg. Null when the seam is unwired. */
    suspend fun tmdbDetails(tmdbId: Int, mediaType: MediaType): TmdbDetails? = null

    /** The OMDb awards read. Null when the plugin seam is unwired — silent absence. */
    suspend fun omdbAwards(imdbId: String): JellyPlayPluginOmdbAwards? = null

    /** The appearance toggle for award badges — the externalRatings awards leg. */
    fun awardsBadgesEnabled(): Boolean = false

    /** The plugin-scored similar ids ([JellyPlayPluginApiRoles.getJellyPlaySimilarItems]). Empty when unwired. */
    suspend fun pluginSimilarIds(itemId: String, limit: Int): List<String> = emptyList()

    /** The per-item detail hydration ([DetailFeed.getMediaDetail]) — the pluginSimilarItems arm. Null on failure. */
    suspend fun mediaDetailItem(itemId: String): MediaItem? = null

    /** The capabilities' server-similar-pipeline flag ([JellyPlayCapabilities.serverSimilarPipeline]). */
    fun serverSimilarPipeline(): Boolean = false

    /** The series-scoped anime markers. Null when the plugin seam is unwired — silent absence. */
    suspend fun animeMarkers(seriesId: String, providerSeriesId: String): List<JellyPlayEpisodeMarker>? = null

    /** The per-season TMDB scores. Null when the plugin seam is unwired — silent absence. */
    suspend fun tmdbSeasonRatings(tmdbId: String, seasonNumber: Int): Map<Int, JellyPlayEpisodeScore>? = null

    /** The server-hosted local trailers ([LibraryApiClient.getLocalTrailers]) — the trailers arm. */
    suspend fun localTrailers(itemId: String): Result<List<MediaItem>> = Result.success(emptyList())

    /** The server ThemeVideo clips ([LibraryApiClient.getThemeVideos]) — the themeVideo arm. */
    suspend fun themeVideoClips(itemId: String): List<MediaItem> = emptyList()

    /** The shaped stream URL ([PlaybackRepository.getStreamUrl]) — the themeVideo arm. Blank by default. */
    fun streamUrl(itemId: String, mediaSourceId: String): String = ""
}

/**
 * The PLUGIN_RATINGS section module: the companion plugin's
 * aggregated external-ratings row (ADR 0010). Admission is the silent-absence
 * contract (no plugin data → no slot at all); the renderer and the mdblist
 * fetch arm moved verbatim from MediaDetailBodySections.kt and
 * DetailViewModel's declaration list respectively.
 */
internal object PluginRatingsSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.PLUGIN_RATINGS

    override fun isAdmitted(state: DetailContentState, detail: MediaDetail): Boolean =
        state.core.pluginRatings.isNotEmpty()

    override fun enrichments(host: DetailSectionEnrichmentHost): List<DetailEnrichment> = listOf(
        DetailEnrichment(
            name = "pluginRatings",
            // The feature-key gate is a suspend probe, so it lives in the BODY
            // (the gate stays a pure snapshot read); failure/null → the field
            // stays empty → the section is silently absent.
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            if (!host.pluginFeature(JellyPlayPluginFeatures.Ratings, inputs.itemId)) return@DetailEnrichment
            val imdbId = resolveImdbId(inputs.detail) ?: return@DetailEnrichment
            val ratings = host.mdbListRatings(imdbId)?.getOrNull() ?: return@DetailEnrichment
            if (!host.isCurrent(inputs.itemId)) return@DetailEnrichment
            host.updateState {
                // Scoreless entries would render an empty chip — dropped here so
                // the section's emptiness check matches what it can render.
                it.copy(pluginRatings = ratings.filter { r -> r.score != null })
            }
        },
    )

    /**
     * One non-clickable chip per source the server sent, rendered verbatim
     * ("Source Score"), plus the no-plugin strip's chips merged into the
     * same FlowRow while the plugin row renders. Silent absence is the
     * plugin contract — there is no error or loading state by design.
     */
    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        val ratings = scope.state.core.pluginRatings
        StaggeredDetailSection(visible = ratings.isNotEmpty(), delayIndex = kind.delayIndex) {
            Column(modifier = Modifier.padding(horizontal = scope.bodyContentPad)) {
                FadingItem {
                    Text(
                        text = stringResource(Res.string.jellyplay_det_ratings_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.semantics { heading() },
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(10.dp))
                FadingItem {
                    // Chips wrap (the header metadata row's FlowRow shape) — the
                    // source set is small (≤6) and reads as one line on every
                    // width that matters.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ratings.forEach { entry ->
                            val score = entry.score ?: return@forEach
                            TagChip(
                                label = "${entry.source} ${formatPluginScore(score)}",
                                containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.95f),
                                enabled = false,
                            )
                        }
                        // The external-ratings chips, after the plugin sources (the merge
                        // order). Suppressed while the probe says the plugin owns
                        // ratings AND this row is rendering — never duplicated.
                        if (
                            externalRatingsMergedIntoPluginRow(
                                hasPluginRatings = ratings.isNotEmpty(),
                                external = scope.state.core.externalRatings,
                                externalSuppressedByPlugin = scope.state.core.externalRatingsSuppressedByPlugin,
                            )
                        ) {
                            RatingsRow(
                                critics = scope.state.core.externalRatings.critics,
                                audience = scope.state.core.externalRatings.audience,
                                imdb = scope.state.core.externalRatings.imdb,
                                tmdb = scope.state.core.externalRatings.tmdb,
                                rtUrl = scope.state.core.externalRatings.rtUrl,
                                imdbUrl = scope.state.core.externalRatings.imdbUrl,
                                tmdbUrl = scope.state.core.externalRatings.tmdbUrl,
                                awards = scope.state.core.externalRatings.awards,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The TMDB_REVIEWS section module: the TMDB review cards — a
 * preview of [REVIEWS_PREVIEW_COUNT] cards inline plus the "All reviews (N)"
 * jump to Route.Reviews. Fetched regardless of Seerr connection, so admission
 * is content + the the `show_reviews` preference alone.
 */
internal object TmdbReviewsSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.TMDB_REVIEWS

    override fun isAdmitted(state: DetailContentState, detail: MediaDetail): Boolean =
        state.preferences.showReviews && state.core.tmdbReviews.isNotEmpty()

    // The enrichment arm is deliberately empty: the reviews fetch rides the
    // seerrDiscovery declaration's TmdbCompanion leg — the shared 350ms
    // frame-priority delay, the one-per-navigation idempotency latch, and the
    // shared landings fold — and a reviews-only declaration would fork all
    // three. The fetch arm rejoins this module when that leg decomposes.

    /**
     * The preview slot: [REVIEWS_PREVIEW_COUNT] cards inline (bodies capped at
     * the former 6-line preview), the "All reviews (N)" button when the
     * fetched list hides reviews behind the preview ([tmdbReviewsPreviewSplit]).
     * The card vocabulary is the shared [TmdbReviewCard] — the same renderer
     * the Route.Reviews screen shows uncapped.
     */
    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        StaggeredDetailSection(
            visible = scope.state.core.tmdbReviews.isNotEmpty(),
            delayIndex = kind.delayIndex,
        ) {
            ReviewsSection(
                reviews = scope.state.core.tmdbReviews,
                onAllReviews = {
                    scope.callbacks.navigation.onNavigate(
                        com.raulshma.jellyplay.core.ui.navigation.Route.Reviews(scope.state.itemId),
                    )
                },
            )
        }
    }
}

/**
 * The reviews section's body: the preview cards + the "All reviews" jump.
 * Moved verbatim from MediaDetailBody.kt (the former private `ReviewsSection`).
 */
@Composable
private fun ReviewsSection(
    reviews: List<TmdbReview>,
    onAllReviews: () -> Unit,
) {
    val bodyContentPad = LocalAdaptiveInfo.current.contentPadding(isTv = LocalTvMode.current)
    val split = remember(reviews) { tmdbReviewsPreviewSplit(reviews) }
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
            split.preview.forEach { review ->
                FadingItem {
                    TmdbReviewCard(review = review, contentMaxLines = 6)
                }
            }
            split.allCount?.let { allCount ->
                FadingItem {
                    val allReviewsFocusState = rememberTvFocusState(focusedScale = 1.05f)
                    Text(
                        text = stringResource(Res.string.detail_reviews_all, allCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(ShapeCache.smooth16)
                            .then(allReviewsFocusState.focusModifier)
                            .then(Modifier.tvFocusIndicator(allReviewsFocusState, ShapeCache.smooth16))
                            .clickable(onClick = onAllReviews)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * The SPECIAL_FEATURES section module (wave 2): the special features / extras
 * poster row (remote discovery only). Admission and the fetch arm moved
 * verbatim from DetailSectionAdmission's fold row and DetailViewModel's
 * hand-written entry; the renderer slot routes to the existing
 * [DetailSpecialFeaturesSection] family composable.
 */
internal object SpecialFeaturesSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.SPECIAL_FEATURES

    override fun isAdmitted(state: DetailContentState, detail: MediaDetail): Boolean =
        state.core.capabilities.remoteDiscovery && state.core.specialFeatures.isNotEmpty()

    override fun enrichments(host: DetailSectionEnrichmentHost): List<DetailEnrichment> = listOf(
        DetailEnrichment(
            name = "specialFeatures",
            gate = { it.remoteDiscoveryAllowed },
        ) { inputs ->
            // Fetch special features / extras (featurettes, deleted scenes, etc.)
            // concurrently so the core detail renders immediately; the result lands
            // in specialFeatures and renders as its own horizontal row.
            host.specialFeatures(inputs.itemId)
                .onSuccess { extras ->
                    if (!host.isCurrent(inputs.itemId)) return@onSuccess
                    host.updateState { it.copy(specialFeatures = extras) }
                }
        },
    )

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailSpecialFeaturesSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The COLLECTION_ITEMS section module (wave 2): the collection members row.
 * Admission is the media family alone (the empty-members gate is render-side,
 * exactly as the fold row had it); the fetch arm moved verbatim from
 * DetailViewModel's hand-written entry, and the renderer slot routes to the
 * existing [DetailCollectionItemsSection] family composable.
 */
internal object CollectionItemsSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.COLLECTION_ITEMS

    override fun isAdmitted(state: DetailContentState, detail: MediaDetail): Boolean =
        detail.item.mediaType == MediaType.COLLECTION

    override fun enrichments(host: DetailSectionEnrichmentHost): List<DetailEnrichment> = listOf(
        DetailEnrichment(
            name = "collectionItems",
            // Collections are remote-only companion content (not part of the
            // snapshot); gated on remoteDiscovery so a local origin never
            // starts it.
            gate = {
                it.remoteDiscoveryAllowed && it.mediaType == MediaType.COLLECTION
            },
        ) { inputs ->
            host.collectionItems(collectionId = inputs.itemId, limit = 100)
                .onSuccess { result ->
                    if (!host.isCurrent(inputs.itemId)) return@onSuccess
                    host.updateState { it.copy(collectionItems = result.items) }
                }
        },
    )

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailCollectionItemsSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The UPCOMING_EPISODES section module (wave 2): the "Airing next" strip,
 * SERIES detail only. Admission is content + the `show_upcoming_strip`
 * preference (the fold row's terms — content-driven, NOT media-type-driven;
 * the SERIES-only rule lives on the enrichment's gate). The fetch arm moved
 * verbatim from DetailViewModel's hand-written entry — including the keying
 * rule: the fetch keys on `parentId` = the loaded item id, which inside the
 * SERIES-only gate IS the series id (an EPISODE/SEASON detail's series id
 * differs, and those types are excluded rather than re-keyed). The renderer
 * slot routes to the existing [DetailUpcomingEpisodesSection] family
 * composable.
 */
internal object UpcomingEpisodesSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.UPCOMING_EPISODES

    /** The upcoming strip's row bound (an "airing next" rail; small by design) — moved with the arm. */
    private const val UPCOMING_STRIP_LIMIT = 12

    override fun isAdmitted(state: DetailContentState, detail: MediaDetail): Boolean =
        state.preferences.showUpcomingStrip && state.core.upcomingEpisodes.isNotEmpty()

    override fun enrichments(host: DetailSectionEnrichmentHost): List<DetailEnrichment> = listOf(
        DetailEnrichment(
            name = "upcoming",
            gate = { it.remoteDiscoveryAllowed && it.mediaType == MediaType.SERIES },
        ) { inputs ->
            // The user-pref half of the gate rides the body (the franchise
            // convention).
            if (!host.detailPreferences().showUpcomingStrip) return@DetailEnrichment
            host.upcomingEpisodes(parentId = inputs.itemId, limit = UPCOMING_STRIP_LIMIT)
                .onSuccess { upcoming ->
                    if (!host.isCurrent(inputs.itemId)) return@onSuccess
                    host.updateState { it.copy(upcomingEpisodes = upcoming) }
                }
        },
    )

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailUpcomingEpisodesSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The MORE_IN_COLLECTION section module (wave 2): the "More in {collection}"
 * franchise rows (BoxSet ancestors + members), declared just before the
 * similar rows and sharing their slot-10 band. Admission counts only VISIBLE
 * rows (a row the user eye-hid is excluded — not just render-side — so a
 * fully hidden row family doesn't leave an empty admitted slot's gap; the
 * fold's `from` projection moved verbatim). The fetch arm moved verbatim from
 * DetailViewModel's hand-written entry — the enumerate-then-hydrate fan-out
 * over [FRANCHISE_MAX_COLLECTIONS] BoxSets, whose constants live beside the
 * row's presentation ([FRANCHISE_MEMBER_LIMIT] included). The renderer slot
 * routes to the existing [DetailMoreInCollectionSection] family composable.
 */
internal object MoreInCollectionSectionModule : DetailSectionModule {

    override val kind: DetailSectionKind = DetailSectionKind.MORE_IN_COLLECTION

    override fun isAdmitted(state: DetailContentState, detail: MediaDetail): Boolean =
        state.preferences.showMoreInCollection &&
            // The franchise admission counts only VISIBLE rows: a row the user
            // eye-hid is excluded here (not just render-side) so a fully
            // hidden row family doesn't leave an empty admitted slot's gap in
            // the body column.
            state.core.franchiseCollections.any { collection ->
                collection.id !in state.core.hiddenFranchiseCollections[state.itemId].orEmpty()
            }

    override fun enrichments(host: DetailSectionEnrichmentHost): List<DetailEnrichment> = listOf(
        DetailEnrichment(
            name = "franchise",
            // The "More in this collection" franchise rows. MOVIE/SERIES
            // only — BoxSet ancestry is a film-franchise concept, and the
            // members read is a per-collection page the other families have
            // no use for. Ancestors → up to [FRANCHISE_MAX_COLLECTIONS]
            // BoxSets → each hydrated with its members in the server's
            // default order; rows with no (non-self) members drop out and a
            // wholly empty fold leaves the field empty — silent absence.
            gate = {
                it.remoteDiscoveryAllowed &&
                    (it.mediaType == MediaType.MOVIE || it.mediaType == MediaType.SERIES)
            },
        ) { inputs ->
            // The user-pref half of the gate rides the body: the gate lambda
            // stays a pure snapshot read (the DetailEnrichmentInputs
            // contract), so `showMoreInCollection` is checked here instead —
            // a preference read, not snapshot state (the externalRatings
            // convention).
            if (!host.detailPreferences().showMoreInCollection) return@DetailEnrichment
            // The franchise read rides the repository's cache (the detail
            // cluster's boxsets-containing member): the enumerate-then-probe
            // fan-out below is the costliest enrichment, so a re-entry inside
            // its TTL window re-serves the row instead of re-probing.
            val boxSets = host.boxSetsContaining(inputs.itemId)
                .getOrNull()
                ?.take(FRANCHISE_MAX_COLLECTIONS)
                .orEmpty()
            if (boxSets.isEmpty()) return@DetailEnrichment
            // The shared bounded-parallel members hydration (the
            // pluginSimilarItems idiom): per-BoxSet failures and empty member
            // lists drop out; the source order is preserved
            // (mapConcurrentCatching is order-preserving), so the rows keep
            // the server's default order.
            val rows = host.mapConcurrent(boxSets) { boxSet ->
                val members = host.collectionItems(collectionId = boxSet.id, limit = FRANCHISE_MEMBER_LIMIT)
                    .getOrNull()
                    ?.items
                    // The loaded item never renders inside its own franchise
                    // row (the similarItems enrichment's self-filter).
                    ?.filter { it.id != inputs.itemId }
                    .orEmpty()
                if (members.isEmpty()) {
                    null
                } else {
                    DetailUiState.FranchiseCollection(id = boxSet.id, name = boxSet.name, members = members)
                }
            }.filterNotNull()
            if (rows.isEmpty()) return@DetailEnrichment
            if (!host.isCurrent(inputs.itemId)) return@DetailEnrichment
            host.updateState { it.copy(franchiseCollections = rows) }
        },
    )

    @Composable
    override fun Content(scope: DetailSectionRenderScope) {
        DetailMoreInCollectionSection(
            delayIndex = kind.delayIndex,
            state = scope.state,
            callbacks = scope.callbacks,
            bodyContentPad = scope.bodyContentPad,
        )
    }
}

/**
 * The detail section registry: the ordered list of [DetailSectionModule]s. The
 * order is [DetailSectionKind] declaration order — the body's emission order —
 * and [admittedSections] re-sorts the merged emission by the enum, so a
 * module's position here can never reorder anything visually.
 *
 * Wave 1 (the pilot) registered the two ratings-adjacent rows; wave 2 brings
 * the four collection-family/strip rows. The remaining sections stay
 * hand-written ([DetailSectionAdmission]'s fold + the [DetailSectionRenderer]
 * `when`) and join in later waves.
 */
internal object DetailSectionRegistry {

    val modules: List<DetailSectionModule> = listOf(
        PluginRatingsSectionModule,
        UpcomingEpisodesSectionModule,
        CollectionItemsSectionModule,
        MoreInCollectionSectionModule,
        SpecialFeaturesSectionModule,
        TmdbReviewsSectionModule,
    )

    /** The module owning [kind], or null for a hand-written section. */
    fun moduleFor(kind: DetailSectionKind): DetailSectionModule? =
        modules.firstOrNull { it.kind == kind }

    /**
     * The module-owned admissions for this snapshot, in registry (= enum
     * declaration) order. The fold and the registry own disjoint kinds.
     */
    fun admittedKinds(state: DetailContentState, detail: MediaDetail): List<DetailSectionKind> =
        modules.filter { it.isAdmitted(state, detail) }.map { it.kind }

    /**
     * The body's full admission: the hand-written fold (the unmigrated
     * sections) merged with the registry's module admissions, re-sorted into
     * [DetailSectionKind] declaration order. The sort is the structural pin on
     * "registry modules never reorder the emission".
     */
    fun admittedSections(
        handFold: List<DetailSectionKind>,
        state: DetailContentState,
        detail: MediaDetail,
    ): List<DetailSectionKind> =
        (handFold + admittedKinds(state, detail)).sortedBy { it.ordinal }

    /**
     * The modules' enrichment registrations for the ViewModel's fan-out, in
     * registry order. The VM splices this list at the exact position the
     * migrated hand-written entries occupied (see DetailViewModel.enrichments).
     */
    fun enrichments(host: DetailSectionEnrichmentHost): List<DetailEnrichment> =
        modules.flatMap { it.enrichments(host) }
}
