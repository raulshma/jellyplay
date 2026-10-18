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
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginRating
import com.raulshma.jellyplay.core.model.MediaDetail
import com.raulshma.jellyplay.core.model.MediaItem
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
 * Wave 1 is the pilot: exactly two sections ([PluginRatingsSectionModule],
 * [TmdbReviewsSectionModule]). The remaining sections stay hand-written in
 * [DetailSectionAdmission]'s fold and [DetailSectionRenderer]'s `when` and
 * migrate in later waves.
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
 * seams plus the plugin probe/fetch roles the wave-1 arm needs; later waves
 * extend it per migrated section.
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
}

/**
 * The PLUGIN_RATINGS section module (wave 1): the companion plugin's
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
     * ("Source Score"), plus the D4 no-plugin strip's chips merged into the
     * same FlowRow (D9) while the plugin row renders. Silent absence is the
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
                        // The D4 chips, after the plugin sources (the D9 merge
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
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The TMDB_REVIEWS section module (wave 1): the TMDB review cards (D9) — a
 * preview of [REVIEWS_PREVIEW_COUNT] cards inline plus the "All reviews (N)"
 * jump to Route.Reviews. Fetched regardless of Seerr connection, so admission
 * is content + the D9 `show_reviews` preference alone.
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
 * The detail section registry: the ordered list of [DetailSectionModule]s. The
 * order is [DetailSectionKind] declaration order — the body's emission order —
 * and [admittedSections] re-sorts the merged emission by the enum, so a
 * module's position here can never reorder anything visually.
 *
 * Wave 1 (the pilot) registers the two ratings-adjacent rows; the remaining
 * sections stay hand-written ([DetailSectionAdmission]'s fold + the
 * [DetailSectionRenderer] `when`) and join in later waves.
 */
internal object DetailSectionRegistry {

    val modules: List<DetailSectionModule> = listOf(
        PluginRatingsSectionModule,
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
