package com.raulshma.jellyplay.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.home.generated.resources.Res
import com.raulshma.jellyplay.feature.home.generated.resources.home_because_you_watched
import com.raulshma.jellyplay.feature.home.generated.resources.home_coming_soon
import com.raulshma.jellyplay.feature.home.generated.resources.home_details
import com.raulshma.jellyplay.feature.home.generated.resources.home_discover
import com.raulshma.jellyplay.feature.home.generated.resources.home_discover_reroll
import com.raulshma.jellyplay.feature.home.generated.resources.home_no_content_available
import com.raulshma.jellyplay.feature.home.generated.resources.home_no_content_description
import com.raulshma.jellyplay.feature.home.generated.resources.home_refresh
import com.raulshma.jellyplay.feature.home.generated.resources.home_resume
import com.raulshma.jellyplay.feature.home.generated.resources.home_resume_or_details
import com.raulshma.jellyplay.feature.home.generated.resources.home_retry
import com.raulshma.jellyplay.feature.home.generated.resources.home_sections_load_failed
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertCircle
import com.composables.icons.tabler.outline.Movie
import com.composables.icons.tabler.outline.PlayerPlay
import com.raulshma.jellyplay.core.designsystem.theme.detailEntrance
import com.raulshma.jellyplay.core.designsystem.theme.rememberDetailEntrance
import com.raulshma.jellyplay.core.model.ContinueWatchingClickBehavior
import com.raulshma.jellyplay.core.model.Genre
import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.MediaType
import com.raulshma.jellyplay.core.model.OfflineMediaItem
import com.raulshma.jellyplay.core.model.bookProgressFraction
import com.raulshma.jellyplay.core.model.descriptor
import com.raulshma.jellyplay.core.model.home.HomeRowModules
import com.raulshma.jellyplay.core.model.toMediaItem
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.animation.lazyItemPlacementSpec
import com.raulshma.jellyplay.core.ui.adaptive.bottomPadding
import com.raulshma.jellyplay.core.ui.adaptive.itemSpacing
import com.raulshma.jellyplay.core.ui.components.DelayedLoadingScreen
import com.raulshma.jellyplay.core.ui.components.ScreenEmptyState
import com.raulshma.jellyplay.core.ui.components.seerr.SeerrCardLoadingState
import com.raulshma.jellyplay.core.ui.components.mouseScroll
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.rememberInt
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * WHAT the content list renders, decided once at the construction site from
 * [HomeRenderSource]: the server feed ([Online]) or the offline-derived one
 * ([Offline]). The former pairing — a nullable `offlineContent` plus
 * `sections`/`isLoading`/banners that each had to be masked with
 * `!renderingOffline &&` at every construction — allowed the two halves to
 * disagree (silently empty rows); here each branch's constructor IS the mask,
 * and the banner fields simply do not exist on the offline feed.
 */
internal sealed interface HomeRenderFeed {

    /** The rendered section list — server sections online, derived offline. */
    val sections: List<HomeSection>

    /** True while this feed's content is still arriving (spinner). */
    val isLoading: Boolean

    /**
     * The server feed. [partialLoadError] and [newsletterBannerVisible] are
     * online-only surfaces — offline rendering short-circuits them by
     * construction, not by boolean masks at the read sites.
     */
    @Immutable
    data class Online(
        override val sections: List<HomeSection>,
        override val isLoading: Boolean,
        val partialLoadError: Boolean,
        val newsletterBannerVisible: Boolean,
    ) : HomeRenderFeed

    /**
     * The offline feed: the whole [OfflineHomeContent] render model.
     * [isLoading] covers the FallbackPending window after the gate opens but
     * before the first library emission (downloads may yet exist).
     */
    @Immutable
    data class Offline(
        val content: OfflineHomeContent,
        override val isLoading: Boolean = false,
    ) : HomeRenderFeed {
        override val sections: List<HomeSection> get() = content.sections
    }
}

/**
 * Bundles the home content list's flat parameters into a
 * single `@Immutable` value so the composable is skippable without relying on
 * every caller `remember`-ing dozens of unstable lambdas. Lambdas that must
 * remain stable across recompositions (image/url builders, click handlers) are
 * grouped into [HomeContentCallbacks].
 *
 * ## Shape: core + screen-resolved extras
 *
 * [core] is the tightest sub-bag of [HomeUiState] the content list actually
 * reads, embedded as one value (the DetailContentState precedent): readers
 * qualify through `state.core.X` — there are NO passthrough properties
 * restating core fields. The sub-bag is deliberately NOT the whole
 * [HomeUiState]: the screen orchestrator already recomposes on every uiState
 * emission, and carrying all ~45 fields would re-validate this list (and
 * every row) on churn the list never reads — spinner flips, sheet states,
 * search-active. The extras beside [core] are exactly the values the
 * ViewModel does NOT own — the screen's own resolutions:
 *
 *  - [feed]: the [homeSurface] fold's verdict carried whole — the online or
 *    offline feed with the offline-only masks (spinner, banners) and the
 *    FallbackPending loading window already applied,
 *  - [featuredItem]: the hero controller's featured pick,
 *  - [curatedHeroOverlay]: the curated billboard copy keyed by item id,
 *    merged screen-side ahead of the local hero candidates,
 *  - [backgroundColor] / [contentPad] / [headerHeight]: the screen's layout
 *    resolutions,
 *  - [bookProgressFractions]: whichever CR-fractions map belongs to the
 *    carried feed (the surface fold's pick — the refresher's decodes online,
 *    the offline gate's offline, FallbackPending included),
 *  - [discoverRows] / [allDiscoverItems] / [randomDiscoverRowIds]: the
 *    discover-grid derivations (rows, flattened items, the RANDOM-sorted ids),
 *  - [statusBanner]: the implicit-offline notice the screen folds.
 */
@Immutable
internal data class HomeContentState(
    /** The [HomeUiState] fields the content list reads — see [Core]. */
    val core: Core,
    val feed: HomeRenderFeed,
    val featuredItem: MediaItem?,
    /**
     * The plugin-curated billboard copy keyed by item id — the hero looks
     * its featured item up here; a hit's headline/subhead override the item's
     * own title/kicker slots, a miss renders item metadata as today.
     */
    val curatedHeroOverlay: Map<String, com.raulshma.jellyplay.core.model.CuratedHeroEntry> = emptyMap(),
    /**
     * The hero presentation fold ([homeHeroStyleDecision]) — the screen's
     * resolution of the style pref against the form factor and the candidate
     * pool. The hero slot branches on its render flags; the default instance
     * IS the CAROUSEL output, so a default renders exactly the shipped hero.
     */
    val heroStyleDecision: HomeHeroStyleDecision = HomeHeroStyleDecision(),
    /**
     * The full rotation candidate pool (the screen's [selectHomeHeroCandidates]
     * output) — the TV_SLIDER pager's pages; unused by every other style.
     */
    val heroCandidates: List<MediaItem> = emptyList(),
    val backgroundColor: Color,
    val contentPad: Dp,
    val headerHeight: Dp,
    /**
     * Continue Reading reading-progress bars keyed by item id —
     * [com.raulshma.jellyplay.core.model.bookProgressFraction] decodes with
     * TOC-cache page counts (the refresher's map online, the offline gate's
     * offline). Read by the online and offline CR rows.
     */
    val bookProgressFractions: Map<String, Float> = emptyMap(),
    val discoverRows: List<List<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem>>,
    val allDiscoverItems: List<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem>,
    /**
     * Row ids of the RANDOM-sorted custom discover rows — the only rows that
     * render the dice affordance in their header (a non-random sort re-fetch
     * would return the same order; pointless).
     */
    val randomDiscoverRowIds: Set<String> = emptySet(),
    /**
     * Non-blocking informational banner (e.g. the implicit-offline
     * "couldn't reach the server — showing your downloads" notice). Null hides it.
     */
    val statusBanner: String? = null,
) {
    /**
     * The [HomeUiState] slice the content list reads — one embedded value so
     * the fold at the construction site stays a one-line copy per field and
     * every reader qualifies through [HomeContentState.core]. The former shape
     * interleaved these eleven fields among the screen-derived extras; the
     * core/extras split is what keeps "which surface facts come from the VM"
     * answerable by looking at one type.
     */
    @Immutable
    data class Core(
        val homeHeroEnabled: Boolean,
        val homeBackdropEnabled: Boolean,
        val discoverEnabled: Boolean,
        val experimentalCardClippingEnabled: Boolean,
        val continueWatchingClickBehavior: ContinueWatchingClickBehavior,
        /** Direct *arr "Recently Grabbed / Coming Soon" calendar row. */
        val recentlyGrabbed: List<com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem> =
            emptyList(),
        /**
         * Visit memory: the PREVIOUS home visit's stamp (epoch ms; 0 =
         * never). Read once per home session before the dwell timer stamps the
         * new one, so this session's own stamp can never clear the badges it
         * just showed. Feeds the arrival rows' NEW-badge predicate
         * ([HomeMediaRow.showNewBadges]); 0 (the default) badges nothing.
         */
        val homeLastVisitEpochMs: Long = 0L,
        /** Personal stats card data — null hides the card. */
        val statsCard: HomeStatsCardData? = null,
        /** The `home_stats_card` pref mirror — the card's render gate. */
        val statsCardEnabled: Boolean = false,
        /** Genre quick-access rail chips (server-ordered, top ~24). */
        val genreChips: List<Genre> = emptyList(),
        /** The `home_genre_rail` pref mirror — the rail's render gate. */
        val genreRailEnabled: Boolean = false,
    )

    /** The online feed, null while the offline branch renders. */
    val online: HomeRenderFeed.Online? get() = feed as? HomeRenderFeed.Online

    /** The offline render model, null while the online branch renders. */
    val offlineContent: OfflineHomeContent? get() = (feed as? HomeRenderFeed.Offline)?.content
}

/**
 * The content list's per-surface event sinks + the seams only it consumes
 * ([getImageUrl] for the series-art resolvers, the per-item photo-folder
 * URL slice). The shared render wiring (URL builders, click funnels,
 * prefetch/loading, hero focus) lives in [HomeRenderInputs] — passed beside
 * these callbacks so the two bundles never re-thread the same lambdas.
 */
@Immutable
internal data class HomeContentCallbacks(
    val onRetrySectionLoad: () -> Unit,
    val onDismissNewsletterBanner: () -> Unit,
    val onNewsletterClick: () -> Unit,
    val onOfflineLibraryClick: () -> Unit,
    val onItemClick: (String) -> Unit,
    val onFocusChange: (Boolean) -> Unit,
    /** Image URL for an item ID — the series-poster resolver's source. */
    val getImageUrl: (String) -> String,
    /** Per-item slice of the photo-folder child-URL cache — collected at the
     * photo-folder card, not orchestrator scope, so a prefetch merge only
     * recomposes the card whose urls changed. */
    val photoFolderChildUrlsFor: (String) -> Flow<List<String>>,
    val onSeerrItemClick: (Int, String) -> Unit,
    val onSeerrRequest: (com.raulshma.jellyplay.core.model.seerr.SeerrSearchItem) -> Unit,
    /** Open the inline section-config sheet for a given section. Carries the
     * optional [libraryId] for per-library sections (LATEST_MEDIA) so the sheet
     * can apply a per-library override instead of a global toggle. */
    val onConfigureSection: (HomeSectionType, String?) -> Unit,
    /** Deep-link into Settings → Home Screen Layout (global ordering / presets). */
    val onConfigureHomeLayout: () -> Unit,
    /** Deep-link into Settings → Configure Libraries (per-library overrides). */
    val onConfigureLibraries: () -> Unit,
    /** Open the full library screen for a home-section "See All" action. */
    val onSeeAllClick: (sectionType: HomeSectionType, libraryId: String?, collectionType: String?, title: String) -> Unit = { _, _, _, _ -> },
    /** TV-only: reports the D-pad-focused item so the Menu key can open its
     * quick actions */
    val onFocusedMediaItem: (MediaItem) -> Unit = {},
    /** Dice affordance: re-roll one RANDOM-sorted custom discover row (row id). */
    val onRollDiscoverRow: (String) -> Unit = {},
    /** Edge-pull refresh of ONE section row (section id) — see [HomeRefresher.refreshSectionRow]. */
    val onRefreshSection: (String) -> Unit = {},
    val onPresenceChipClick: (HomePresenceChip) -> Unit = {},
    val onPresenceJoinClick: (HomePresenceChip) -> Unit = {},
    val onPresenceDismiss: (HomePresenceChip) -> Unit = {},
    val onStatsCardClick: (() -> Unit)? = null,
    val onGenreClick: (String) -> Unit = {},
)

/**
 * Re-resolves an offline-derived section's [HomeSection.items] back to their
 * [OfflineMediaItem] originals by id (the shared [OfflineHomeContent.itemsById]
 * lookup) so the row cards render local artwork and clicks route offline.
 */
private fun offlineSectionItems(
    section: HomeSection,
    byId: Map<String, OfflineMediaItem>,
): List<OfflineMediaItem> = section.items.mapNotNull { byId[it.id] }

/**
 * A cheap, order-sensitive Int fingerprint over a section list's item
 * ids (sizes folded in) — the only thing the downloaded-row dedupe consumes.
 * Remembered off the coarse list, so the hashing runs once per new list
 * instance while the remember key comparing it is a single Int per body
 * recomposition.
 */
private fun sectionItemIdFingerprint(sections: List<HomeSection>): Int {
    var hash = sections.size
    for (section in sections) {
        hash = hash * 31 + section.items.size
        for (item in section.items) {
            hash = hash * 31 + item.id.hashCode()
        }
    }
    return hash
}

/**
 * The home LazyColumn: hero, partial-load / newsletter banners, media rows,
 * Seerr discover grid, *arr "Recently Grabbed" row, downloaded row, and the
 * continue-watching "Resume vs Details" dialog.
 *
 * Behaviour is identical to the former `HomeContentList`; extracted here and
 * refactored to (a) take a [HomeContentState] + [HomeContentCallbacks] bundle,
 * (b) render the discover + recently-grabbed rows via the shared
 * [SeerrDiscoverRow], and (c) route focus restoration through
 * [RestoreHomeRowFocus].
 */
@Composable
internal fun HomeContentList(
    state: HomeContentState,
    callbacks: HomeContentCallbacks,
    renderInputs: HomeRenderInputs,
    /**
     * The hero controller — the TV_SLIDER hero's pager syncs the settled page
     * into it ([HeroController.snapToSliderPage]) so the ambient backdrop and
     * the candidate state stay in step with what the slider shows.
     */
    heroController: HeroController,
    listState: LazyListState,
    density: Density,
    /**
     * Watch-party presence chips — the VM's dedicated flow (NOT a
     * [HomeContentState] field, the search-query precedent): the poll loop
     * writes it on the home refresh cadence, so collecting it HERE (the row
     * is this composable's only reader) keeps the per-tick write off the
     * whole-[HomeUiState] object.
     */
    presenceChips: StateFlow<List<HomePresenceChip>>,
) {
    val isTv = LocalTvMode.current
    val adaptiveInfo = LocalAdaptiveInfo.current
    val sections = state.feed.sections
    val presenceChipList by presenceChips.collectAsStateWithLifecycle()

    // Discover-row dimensions computed once at the composable scope (not inside
    // the LazyColumn's LazyListScope, where the window-info read isn't typed).
    // KMP replacement for the Android-only LocalConfiguration.screenWidthDp:
    // the window container width in dp (settings AudioSettingsScreen precedent).
    val discoverScreenWidth = with(LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp()
    }
    val discoverRowWidth = discoverScreenWidth - state.contentPad * 2
    val discoverSpacing = adaptiveInfo.itemSpacing(isTv)
    val discoverPattern = discoverPatternFor(adaptiveInfo.windowSizeClass)
    // The nine SeerrDiscoverRow arguments the discover and *arr rows share,
    // bundled once so both call sites pass only what differs (DiscoverRowSlot).
    val discoverSlotArgs = DiscoverRowSlotArgs(
        rowHorizontalPadding = state.contentPad,
        spacing = discoverSpacing,
        backgroundColor = state.backgroundColor,
        homeBackdropEnabled = state.core.homeBackdropEnabled,
        clippingEnabled = state.core.experimentalCardClippingEnabled,
        seerrCardLoadingState = renderInputs.seerrCardLoadingState,
        seerrPrefetch = renderInputs.seerrPrefetch,
        onSeerrItemClick = callbacks.onSeerrItemClick,
        onSeerrRequest = callbacks.onSeerrRequest,
    )

    var askContinueItem by remember { mutableStateOf<MediaItem?>(null) }

    // The resume-row sinks every CW / Continue Reading / Next Up row routes
    // through — built ONCE at composable scope (all rows hand the same
    // funnels; HomeContentCallbacks is a data class, so whole-callbacks keys
    // are stable) and handed to resumeRowClick / posterRowClick below, so no
    // render branch can rebuild a divergent triple.
    val resumeSinks = remember(callbacks, renderInputs) {
        ResumeRowSinks(
            onDetails = renderInputs.mediaOnItemClick,
            onPlay = renderInputs.mediaOnPlayClick,
            onAsk = { askContinueItem = it },
        )
    }

    // Per-row focus requesters so D-pad navigation can target each content row.
    var homeFocusRow by rememberInt(-1)
    val savedRowIsValid = homeFocusRow in 0..sections.lastIndex
    // Key on the sections list itself (part of the @Immutable HomeContentState,
    // so structural equality is cheap) rather than on sections.size: a refresh
    // that drops one section and adds another can produce a same-size list and
    // reallocate these requesters, losing any attached D-pad focus.
    val rowFocusRequesters = remember(sections) { List(sections.size) { FocusRequester() } }
    RestoreHomeRowFocus(
        listState = listState,
        savedRow = homeFocusRow,
        sectionCount = sections.size,
        newsletterBannerVisible = state.online?.newsletterBannerVisible == true,
        rowFocusRequesters = { rowFocusRequesters },
    )

    if (sections.isEmpty()) {
        if (state.feed.isLoading) {
            // Initial online fetch with nothing to show yet — a real loading state
            // instead of a blank screen. Delayed so fast loads don't flicker.
            DelayedLoadingScreen(modifier = Modifier.padding(horizontal = state.contentPad))
        } else {
            ScreenEmptyState(
                icon = Tabler.Outline.Movie,
                title = stringResource(Res.string.home_no_content_available),
                description = stringResource(Res.string.home_no_content_description),
                actionLabel = stringResource(Res.string.home_refresh),
                onAction = callbacks.onRetrySectionLoad,
                modifier = Modifier.padding(horizontal = state.contentPad),
            )
        }
    } else {
        // De-duplicate the downloaded row against the online sections so a title
        // that already appears in Continue Watching / Latest / Recently Added
        // isn't shown twice. Reads the aggregate's (already mode-filtered)
        // library slice.
        val offlineContent = state.offlineContent
        // The dedupe consumes only the online sections' item ids, and the
        // sections list identity churns per refresher emission — so the
        // remember keys on a cheap ids fingerprint instead of the list, and
        // the fold below re-runs only when the online id set actually changes
        // (same idiom as the screen layer's offline fingerprints).
        val onlineSectionIdsFingerprint = remember(sections) { sectionItemIdFingerprint(sections) }
        val dedupedOfflineLibrary = remember(offlineContent, onlineSectionIdsFingerprint) {
            val library = offlineContent?.library.orEmpty()
            if (library.isEmpty()) library
            else {
                val onlineIds = buildSet {
                    for (section in sections) for (item in section.items) add(item.id)
                }
                if (onlineIds.isEmpty()) library else library.filter { it.id !in onlineIds }
            }
        }

        // Id→offline-item lookup for the offline hero's click routing and the
        // DOWNLOADED section rows — prebuilt inside [OfflineHomeContent] (one
        // build per offline emission, shared with the screen's hero backdrop
        // resolver). Read via [currentOfflineById] so the hero's click lambda
        // keeps its identity across download-progress emissions.
        val currentOfflineById by rememberUpdatedState(offlineContent?.itemsById ?: emptyMap())

        // Hoisted out of the items() lambda so the gradient brush is built once
        // per (backgroundColor, density) change rather than re-instantiated for
        // every section item in the list, even though only the first section
        // actually applies it.
        val heroTransitionBrush = remember(state.backgroundColor, density) {
            Brush.verticalGradient(
                colors = listOf(Color.Transparent, state.backgroundColor),
                startY = 0f,
                endY = with(density) { 10.dp.toPx() },
            )
        }

        // Standalone headers (discover / *arr) keep their flat fill when the
        // ambient backdrop is off — the behavior of the former private
        // SectionHeader.
        val headerModifier = if (state.core.homeBackdropEnabled) Modifier else Modifier.background(state.backgroundColor)

        // One shared-scalar reveal for the whole feed on first data arrival
        // (this branch only composes once sections exist). Cells composed
        // later during scroll just read the settled 1f and render instantly.
        val feedEntrance = rememberDetailEntrance()

        CompositionLocalProvider(
            com.raulshma.jellyplay.core.ui.components.LocalScrollIdle provides
                remember(listState) { { !listState.isScrollInProgress } }
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .detailEntrance(progress = { feedEntrance.value })
                // Desktop: mouse drag scrolls the page (the wheel already does
                // via the built-in scrollable; drag is touch-only there).
                .mouseScroll(listState, Orientation.Vertical),
            contentPadding = PaddingValues(bottom = adaptiveInfo.bottomPadding(isTv)),
        ) {
            if (state.featuredItem != null && state.core.homeHeroEnabled) {
                item(key = "hero") {
                    val featured = state.featuredItem
                    // The curated billboard copy for whichever candidate is
                    // featured — absent/null fields render the item's own
                    // metadata exactly as the local hero always has.
                    val curated = state.curatedHeroOverlay[featured.id]
                    // The presentation fold carried whole from the screen
                    // ([homeHeroStyleDecision]); its default instance is the
                    // CAROUSEL output, so the shipped hero is untouched.
                    val decision = state.heroStyleDecision
                    // The DETAILED spotlight renders taller than the adaptive base.
                    val heroHeight = if (decision.chromeVisibility == HomeHeroChromeVisibility.RICH) {
                        state.headerHeight + DetailedHeroHeightExtra
                    } else {
                        state.headerHeight
                    }
                    // Online and offline heroes alike open the unified detail
                    // tree — it renders remote and downloaded items alike, so
                    // the id is all the routing needs.
                    if (decision.isSlider) {
                        HeroSliderHeader(
                            candidates = state.heroCandidates,
                            controller = heroController,
                            getBackdropUrl = remember(renderInputs.heroBackdropUrlBuilder) {
                                { id: String -> renderInputs.heroBackdropUrlBuilder(id) }
                            },
                            height = heroHeight,
                            backgroundColor = state.backgroundColor,
                            contentPadding = state.contentPad,
                            homeBackdropEnabled = state.core.homeBackdropEnabled,
                            listState = listState,
                            curatedHeroOverlay = state.curatedHeroOverlay,
                            onItemClick = callbacks.onItemClick,
                            onDetailsClick = callbacks.onItemClick,
                            requestInitialFocus = !savedRowIsValid,
                            onFocusChange = callbacks.onFocusChange,
                            focusRequester = renderInputs.heroFocusRequester,
                        )
                    } else {
                        AnimatedHeroHeader(
                            featuredItem = featured,
                            headline = curated?.headline,
                            subhead = curated?.subhead,
                            getBackdropUrl = remember(renderInputs.heroBackdropUrlBuilder) {
                                { id: String -> renderInputs.heroBackdropUrlBuilder(id) }
                            },
                            height = heroHeight,
                            backgroundColor = state.backgroundColor,
                            contentPadding = state.contentPad,
                            homeBackdropEnabled = state.core.homeBackdropEnabled,
                            listState = listState,
                            onItemClick = callbacks.onItemClick,
                            onDetailsClick = callbacks.onItemClick,
                            requestInitialFocus = !savedRowIsValid,
                            onFocusChange = callbacks.onFocusChange,
                            focusRequester = renderInputs.heroFocusRequester,
                            chromeVisibility = decision.chromeVisibility,
                            fullBleed = decision.fullBleed,
                        )
                    }
                }
            } else {
                item(key = "hero_spacer") { Spacer(Modifier.height(100.dp)) }
            }

            // ── presence chips ("Alex is watching Dune — join") ──────
            val showPresenceChips = state.online != null && presenceChipList.isNotEmpty()
            if (showPresenceChips) {
                item(key = "presence_chips") {
                    HomePresenceChipRow(
                        chips = presenceChipList,
                        onChipClick = callbacks.onPresenceChipClick,
                        onJoinClick = callbacks.onPresenceJoinClick,
                        onDismiss = callbacks.onPresenceDismiss,
                        contentPad = state.contentPad,
                        isPhone = isPhoneLayout(isTv, adaptiveInfo.windowSizeClass),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                }
            }

            // ── stats card ("Your library week") ─────────────────────
            val showStatsCard = state.online != null && state.core.statsCardEnabled && state.core.statsCard != null
            if (showStatsCard) {
                item(key = "stats_card") {
                    HomeStatsCard(
                        data = state.core.statsCard,
                        onClick = callbacks.onStatsCardClick,
                    )
                }
            }

            // ── genre rail ──────────────────────────────────────────
            val showGenreRail = state.online != null && state.core.genreRailEnabled && state.core.genreChips.isNotEmpty()
            if (showGenreRail) {
                item(key = "genre_rail") {
                    HomeGenreRail(
                        genres = state.core.genreChips,
                        onGenreClick = callbacks.onGenreClick,
                        contentPad = state.contentPad,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
            }

            // Non-blocking informational banner (implicit-offline fallback notice).
            val showStatusBanner = state.statusBanner != null
            if (showStatusBanner) {
                item(key = "status_banner") {
                    HomeStatusBannerRow(text = state.statusBanner, contentPad = state.contentPad)
                }
            }

            // Non-blocking notice when some home sections failed to load.
            val showPartialLoadBanner = state.online?.partialLoadError == true
            if (showPartialLoadBanner) {
                item(key = "partial_load_banner") {
                    HomeStatusBannerRow(
                        text = stringResource(Res.string.home_sections_load_failed),
                        onRetry = callbacks.onRetrySectionLoad,
                        contentPad = state.contentPad,
                    )
                }
            }

            val showNewsletterBanner = state.online?.newsletterBannerVisible == true
            if (showNewsletterBanner) {
                item(key = "newsletter_banner") {
                    NewsletterBanner(
                        onClick = callbacks.onNewsletterClick,
                        onDismiss = callbacks.onDismissNewsletterBanner,
                    )
                }
            }

            // The hero-merge treatment (background gradient + zero top padding)
            // applies to the first section only when it directly follows the
            // hero — i.e. nothing above renders between the two. Derived from
            // the same admission booleans so the two can't drift apart.
            val firstSectionFollowsHero = state.featuredItem != null && state.core.homeHeroEnabled &&
                !showPresenceChips && !showStatsCard && !showGenreRail &&
                !showStatusBanner && !showPartialLoadBanner && !showNewsletterBanner

            items(count = sections.size, key = { sections[it].id }, contentType = { "homeSection_${sections[it].type}" }) { index ->
                val section = sections[index]
                val isFirstAfterHero = index == 0 && firstSectionFollowsHero
                val isCurrentlyVisible by remember(section.id) {
                    derivedStateOf {
                        listState.layoutInfo.visibleItemsInfo.any { it.key == section.id }
                    }
                }

                var hasBeenVisible by rememberSaveable { mutableStateOf(false) }
                // Fold the one-shot visibility latch into a LaunchedEffect so the
                // state write happens as a side effect, not during composition —
                // writing to MutableState during composition triggers a redundant
                // recomposition of this item on the next frame.
                LaunchedEffect(isCurrentlyVisible) {
                    if (isCurrentlyVisible && !hasBeenVisible) hasBeenVisible = true
                }

                val sectionAnimation by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (hasBeenVisible) 1f else 0f,
                    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                    label = "sectionAnimation",
                )

                val placementSpec = lazyItemPlacementSpec()
                val sectionModifier = Modifier
                    .animateItem(placementSpec = placementSpec)
                    .fillMaxWidth()
                    .then(
                        when {
                            // With the ambient backdrop on, sections stay transparent so the
                            // backdrop shows through between cards; the hero itself fades to
                            // transparent so no merge gradient is needed here.
                            state.core.homeBackdropEnabled -> Modifier
                            isFirstAfterHero -> Modifier.background(heroTransitionBrush)
                            else -> Modifier.background(state.backgroundColor)
                        }
                    )
                    .padding(top = if (isFirstAfterHero) 0.dp else 16.dp)
                    .graphicsLayer {
                        alpha = sectionAnimation
                        translationY = (1f - sectionAnimation) * 16.dp.toPx()
                    }

                val seedItem = section.seedItem
                val sectionTitle = if (HomeRowModules[section.type].titleCarriesSeed && seedItem != null) {
                    stringResource(Res.string.home_because_you_watched, seedItem.name)
                } else {
                    section.title
                }

                // Long-press affordance only for user-configurable section
                // types; non-configurable rows (FAVORITES, PINNED, …) are
                // managed from their own surfaces and pass null.
                val sectionLongClick = remember(section.type, section.libraryId, callbacks) {
                    if (section.type.isConfigurable) {
                        { callbacks.onConfigureSection(section.type, section.libraryId) }
                    } else null
                }

                // "See All" pill for the poster rows: both the offline-mirrored
                // and the online branch render it, so the gate + click
                // lambda is built once above the chassis dispatch and handed to
                // whichever poster row runs.
                val seeAllClick = remember(callbacks, section.type, section.libraryId, section.collectionType, sectionTitle) {
                    if (sectionHasSeeAll(section.type)) {
                        { callbacks.onSeeAllClick(section.type, section.libraryId, section.collectionType, sectionTitle) }
                    } else null
                }

                // The per-row spinner mirrors, leaf-collected at row scope
                // (the `pendingSyncEntries` pattern): a single-row refresh's
                // spin-up/data/spin-down emissions invalidate only THIS
                // item's recompose scope, never the orchestrator above.
                // Derivation is set membership on the small mirror sets
                // (see [HomeRefreshSpinnerState]).
                val spinners by renderInputs.refreshSpinners.collectAsStateWithLifecycle()

                // Edge-pull refresh wiring: online rows of the refreshable
                // types only ([isEdgeRefreshableSection] — the same gate the
                // refresher re-checks, so the gesture and the fetch cannot
                // disagree). Offline-derived and Seerr rows pass null and
                // render without the gesture.
                val edgeRefreshCtx = if (offlineContent == null && isEdgeRefreshableSection(section)) {
                    EdgeRefreshContext(
                        sectionId = section.id,
                        inProgress = section.id in spinners.refreshingSectionIds,
                        onRefresh = { callbacks.onRefreshSection(section.id) },
                    )
                } else {
                    null
                }

                // Which row renders is the chassis dispatch's decision (see
                // homeRowChassis): the offline-mirror rule and the
                // wide-row source split live in one pure, pinned place, so
                // this `when` is exhaustive and decides nothing. Each branch
                // renders one row shape.
                //
                // Seerr-sourced custom discover rows bypass the chassis
                // entirely — they carry TMDB cards with request actions, not
                // server MediaItems. The plugin-sourced rows (PLUGIN_ROW)
                // bypass it the same way — a mixed list of resolved media
                // cards and fallback tiles neither poster arm can render.
                val armContext = HomeRowArmContext(
                    section = section,
                    title = sectionTitle,
                    modifier = sectionModifier,
                    onSectionLongClick = sectionLongClick,
                    onSeeAllClick = seeAllClick,
                    resumeSinks = resumeSinks,
                    focusRequester = rowFocusRequesters[index],
                    onRowFocused = { homeFocusRow = index },
                    currentOfflineById = currentOfflineById,
                    discoverSpacing = discoverSpacing,
                    seerrCardLoadingState = renderInputs.seerrCardLoadingState,
                    edgeRefresh = edgeRefreshCtx,
                )
                if (section.seerrItems.isNotEmpty()) {
                    HomeSectionSeerrRow(ctx = armContext, state = state, callbacks = callbacks, renderInputs = renderInputs)
                } else if (section.jellyPlayRowEntries.isNotEmpty()) {
                    HomeSectionJellyPlayRow(ctx = armContext, state = state, callbacks = callbacks, renderInputs = renderInputs)
                } else when (homeRowChassis(section, offlineContent != null)) {
                    is HomeRowChassis.OfflinePoster -> HomeSectionOfflinePosterRow(ctx = armContext, state = state, callbacks = callbacks, renderInputs = renderInputs)
                    is HomeRowChassis.OfflineWide -> HomeSectionOfflineWideRow(ctx = armContext, state = state, callbacks = callbacks, renderInputs = renderInputs)
                    is HomeRowChassis.OnlineWide -> HomeSectionOnlineWideRow(ctx = armContext, state = state, callbacks = callbacks, renderInputs = renderInputs)
                    is HomeRowChassis.OnlinePoster -> HomeSectionOnlinePosterRow(ctx = armContext, state = state, callbacks = callbacks, renderInputs = renderInputs)
                    is HomeRowChassis.OnlineTiles -> HomeSectionOnlineTilesRow(ctx = armContext, state = state, callbacks = callbacks, renderInputs = renderInputs)
                }
            }

            if (state.core.discoverEnabled && state.allDiscoverItems.isNotEmpty()) {
                item(key = "seerr_discover_header") {
                    HomeRowTitle(
                        title = stringResource(Res.string.home_discover),
                        contentPad = state.contentPad,
                        modifier = headerModifier,
                        standalone = true,
                    )
                }

                items(
                    count = state.discoverRows.size,
                    // Row content is positional (a reroll reorders items within
                    // the rows), so the key is the stable slot index — keying
                    // on the row's first item id re-keyed EVERY row the moment
                    // a roll reordered its items, forcing a full re-layout
                    // mid-repaint.
                    key = { rowIndex -> "seerr_row_$rowIndex" },
                    contentType = { "seerrRow" },
                ) { rowIndex ->
                    DiscoverRowSlot(
                        items = state.discoverRows[rowIndex],
                        targetSize = discoverPattern[rowIndex % discoverPattern.size],
                        rowWidth = discoverRowWidth,
                        args = discoverSlotArgs,
                    )
                }
            }

            // ── Direct *arr "Recently Grabbed / Coming Soon" calendar row ──
            // Reuses SeerrMediaCard (TMDB-keyed) so no new card UI is needed.
            // Empty (and thus hidden) when the DIRECT_ARR_INTEGRATION flag is
            // off, no *arr is configured, or the calendar window is empty.
            if (state.core.recentlyGrabbed.isNotEmpty()) {
                item(key = "arr_recently_grabbed_header") {
                    HomeRowTitle(
                        title = stringResource(Res.string.home_coming_soon),
                        contentPad = state.contentPad,
                        modifier = headerModifier,
                        standalone = true,
                    )
                }
                item(key = "arr_recently_grabbed_row") {
                    DiscoverRowSlot(
                        items = state.core.recentlyGrabbed,
                        targetSize = discoverPattern[0],
                        rowWidth = discoverRowWidth,
                        args = discoverSlotArgs,
                    )
                }
            }

            // While offline (offlineContent != null — every rendered section is
            // offline-derived then), the offline library IS the section list
            // above, so the inline Downloaded row would duplicate every title.
            if (dedupedOfflineLibrary.isNotEmpty() && offlineContent == null) {
                item(key = "downloaded_row") {
                    // DownloadedSection renders its own "Downloaded" header, so we
                    // intentionally don't emit a separate header item here.
                    DownloadedSection(
                        offlineLibrary = dedupedOfflineLibrary,
                        onOfflineLibraryClick = callbacks.onOfflineLibraryClick,
                        contentPad = state.contentPad,
                        backgroundColor = state.backgroundColor,
                    )
                }
            }
        }
        } // end CompositionLocalProvider(LocalScrollIdle)
    }

    val askItem = askContinueItem
    if (askItem != null) {
        HomeAskContinueDialog(
            item = askItem,
            onDismissRequest = { askContinueItem = null },
            onPlay = {
                askContinueItem = null
                renderInputs.mediaOnPlayClick(askItem)
            },
            onDetails = {
                askContinueItem = null
                renderInputs.mediaOnItemClick(askItem)
            },
        )
    }
}

/** Per-item locals every chassis arm renders with — see the dispatch in [HomeContentList]. */
private data class HomeRowArmContext(
    val section: HomeSection,
    val title: String,
    val modifier: Modifier,
    val onSectionLongClick: (() -> Unit)?,
    val onSeeAllClick: (() -> Unit)?,
    val resumeSinks: ResumeRowSinks,
    val focusRequester: FocusRequester,
    val onRowFocused: () -> Unit,
    val currentOfflineById: Map<String, OfflineMediaItem>,
    val discoverSpacing: Dp,
    val seerrCardLoadingState: SeerrCardLoadingState,
    /** Edge-pull refresh wiring; null on every row the gesture is off for. */
    val edgeRefresh: EdgeRefreshContext?,
)

/**
 * The edge-pull refresh wiring for one online row: the section id the
 * callback targets, whether that row's refetch is in flight (the edge
 * spinner), and the release callback. Built once per item in
 * [HomeContentList] — null on every row the gesture is disabled for
 * ([isEdgeRefreshableSection] excludes the type; the offline feed and Seerr
 * rows exclude themselves upstream).
 */
internal data class EdgeRefreshContext(
    val sectionId: String,
    val inProgress: Boolean,
    val onRefresh: () -> Unit,
)

/** Seerr-sourced custom discover row (TMDB cards with request actions) — bypasses the row chassis entirely. */
@Composable
private fun HomeSectionSeerrRow(
    ctx: HomeRowArmContext,
    state: HomeContentState,
    callbacks: HomeContentCallbacks,
    renderInputs: HomeRenderInputs,
) {
    Column(modifier = ctx.modifier) {
        HomeRowTitle(
            title = ctx.title,
            contentPad = state.contentPad,
            onLongClick = ctx.onSectionLongClick,
        )
        SeerrDiscoverRow(
            items = ctx.section.seerrItems,
            itemWidth = 120.dp,
            rowHorizontalPadding = state.contentPad,
            spacing = ctx.discoverSpacing,
            backgroundColor = state.backgroundColor,
            homeBackdropEnabled = state.core.homeBackdropEnabled,
            clippingEnabled = state.core.experimentalCardClippingEnabled,
            seerrCardLoadingState = ctx.seerrCardLoadingState,
            seerrPrefetch = renderInputs.seerrPrefetch,
            onSeerrItemClick = callbacks.onSeerrItemClick,
            onSeerrRequest = callbacks.onSeerrRequest,
        )
    }
}

/**
 * Plugin-sourced row (PLUGIN_ROW — the companion plugin's seasonal row):
 * resolved entries as native poster cards, unmatched entries as compact
 * fallback tiles, dispatched here — ahead of the row chassis — for the same
 * reason the Seerr arm is: no chassis arm can render the mixed list. Clicks
 * ride the standard poster-row policy (plugin rows are not resume sections —
 * [posterRowClick] falls through to the plain click) and the row is
 * edge-refreshable like the other single-row types.
 */
@Composable
private fun HomeSectionJellyPlayRow(
    ctx: HomeRowArmContext,
    state: HomeContentState,
    callbacks: HomeContentCallbacks,
    renderInputs: HomeRenderInputs,
) {
    val rowItemClick: (MediaItem) -> Unit = remember(
        ctx.section.type, state.core.continueWatchingClickBehavior, renderInputs.mediaOnItemClick,
    ) {
        posterRowClick(
            sectionType = ctx.section.type,
            behavior = state.core.continueWatchingClickBehavior,
            toMediaItem = { it },
            sinks = ctx.resumeSinks,
            onPlainClick = renderInputs.mediaOnItemClick,
        )
    }
    JellyPlayHomeRow(
        title = ctx.title,
        entries = ctx.section.jellyPlayRowEntries,
        imageUrlBuilder = renderInputs.mediaImageUrlBuilder,
        fallbackImageUrlBuilder = renderInputs.fallbackImageUrlBuilder,
        onItemClick = rowItemClick,
        onPlayClick = renderInputs.mediaOnPlayClick,
        modifier = ctx.modifier,
        focusRequester = ctx.focusRequester,
        onRowFocused = ctx.onRowFocused,
        clippingEnabled = state.core.experimentalCardClippingEnabled,
        onSectionLongClick = ctx.onSectionLongClick,
        onFocusedItemChange = callbacks.onFocusedMediaItem,
        edgeRefresh = ctx.edgeRefresh,
    )
}

/**
 * The My Media tiles row over the server feed — the chassis's
 * [HomeRowChassis.OnlineTiles] arm. The section's items are the assembler's
 * FOLDER-typed library tiles; a tile tap routes into the library screen
 * through the existing "See All" carrier ([HomeContentCallbacks.onSeeAllClick],
 * the one wired `Route.LibrarySection` construction — id + name, the dock
 * drawer's identity fields), and the tile long-press opens the inline
 * section-config sheet scoped to that library ([onConfigureSection], the
 * rows' existing long-press pattern — the same affordance the section title
 * carries, per-library here). No offline arm: the offline mirror drops the
 * row (library art is a server artifact).
 */
@Composable
private fun HomeSectionOnlineTilesRow(
    ctx: HomeRowArmContext,
    state: HomeContentState,
    callbacks: HomeContentCallbacks,
    renderInputs: HomeRenderInputs,
) {
    val section = ctx.section
    val tileClick: (MediaItem) -> Unit = remember(callbacks, section.type) {
        { tile -> callbacks.onSeeAllClick(section.type, tile.id, null, tile.name) }
    }
    val tileLongClick: (MediaItem) -> Unit = remember(callbacks, section.type) {
        { tile -> callbacks.onConfigureSection(section.type, tile.id) }
    }
    HomeLibraryTilesRow(
        title = ctx.title,
        items = section.items,
        imageUrlFor = renderInputs.mediaImageUrlBuilder,
        onTileClick = tileClick,
        modifier = ctx.modifier,
        focusRequester = ctx.focusRequester,
        onRowFocused = ctx.onRowFocused,
        clippingEnabled = state.core.experimentalCardClippingEnabled,
        onTileLongClick = tileLongClick,
        onSectionLongClick = ctx.onSectionLongClick,
        onFocusedItemChange = callbacks.onFocusedMediaItem,
    )
}

/** Offline-derived poster row: offline originals re-resolved by id, resume-row policy for Continue Reading. */
@Composable
private fun HomeSectionOfflinePosterRow(
    ctx: HomeRowArmContext,
    state: HomeContentState,
    callbacks: HomeContentCallbacks,
    renderInputs: HomeRenderInputs,
) {
    val section = ctx.section
    val sectionTitle = ctx.title
    val sectionModifier = ctx.modifier
    val sectionLongClick = ctx.onSectionLongClick
    val seeAllClick = ctx.onSeeAllClick
    // Offline-derived section (see buildOfflineHomeSections):
    // re-resolve the offline originals by id (shared lookup
    // above) so the cards render local artwork; clicks go to
    // the unified detail tree like any online item. The
    // mirrored Continue Reading row instead rides the
    // resume-row click policy (Details / Play / Ask) like
    // its online counterpart.
    val byId = ctx.currentOfflineById
    val offlineItems = remember(section, byId) { offlineSectionItems(section, byId) }
    // Percent fallback for books the TOC decode missed,
    // pre-lifted once per items change so the per-card
    // lookup below stays a map read — the toMediaItem
    // lift runs ≤20 times per items change, not per card
    // per recomposition.
    val bookFallbackFractions = if (section.type == HomeSectionType.CONTINUE_READING) {
        remember(offlineItems) {
            offlineItems.associate { it.id to it.toMediaItem().bookProgressFraction() }
        }
    } else {
        emptyMap()
    }
    // Continue Reading rides the resume-row click policy
    // (Details / Play / Ask); every other offline poster
    // row opens details by id — the selection lives in
    // posterRowClick so the online poster row cannot
    // drift from it.
    val rowItemClick: (OfflineMediaItem) -> Unit = remember(
        section.type, state.core.continueWatchingClickBehavior, callbacks,
    ) {
        posterRowClick(
            sectionType = section.type,
            behavior = state.core.continueWatchingClickBehavior,
            toMediaItem = { it.toMediaItem() },
            sinks = ctx.resumeSinks,
            onPlainClick = { item -> callbacks.onItemClick(item.id) },
        )
    }
    OfflineHomeMediaRow(
        title = sectionTitle,
        items = offlineItems,
        onItemClick = rowItemClick,
        modifier = sectionModifier,
        focusRequester = ctx.focusRequester,
        onRowFocused = ctx.onRowFocused,
        clippingEnabled = state.core.experimentalCardClippingEnabled,
        // Mirrored rows keep their online counterparts'
        // long-press configure affordance (configurable types
        // only — same gate as the online branch).
        onSectionLongClick = sectionLongClick,
        // Mirrored RECENTLY_ADDED / LATEST_MEDIA rows keep the
        // online "See All" pill and play overlay.
        onSeeAllClick = seeAllClick,
        onPlayClick = remember(renderInputs.mediaOnPlayClick) {
            { item -> renderInputs.mediaOnPlayClick(item.toMediaItem()) }
        },
        onFocusedItemChange = callbacks.onFocusedMediaItem,
        // Book reading bars: the TOC-cache decodes picked
        // by render source at the HomeContentState build
        // (online: refresher; offline: the offline gate),
        // percent fallback for items the cache misses.
        progressFractionFor = if (section.type == HomeSectionType.CONTINUE_READING) {
            remember(state.bookProgressFractions, bookFallbackFractions) {
                bookProgressFractionFor(
                    fractions = state.bookProgressFractions,
                    idOf = { it.id },
                    fallback = { bookFallbackFractions[it.id] },
                )
            }
        } else {
            null
        },
    )
}

/** Offline-derived Continue Watching / Next Up wide row, re-resolved to offline originals by id. */
@Composable
private fun HomeSectionOfflineWideRow(
    ctx: HomeRowArmContext,
    state: HomeContentState,
    callbacks: HomeContentCallbacks,
    renderInputs: HomeRenderInputs,
) {
    val section = ctx.section
    val sectionTitle = ctx.title
    val sectionModifier = ctx.modifier
    val sectionLongClick = ctx.onSectionLongClick
    // Offline-derived Continue Watching / Next Up (see
    // buildOfflineHomeSections): same wide-card row and the
    // same Resume-vs-Details click behavior as the online
    // home, but re-resolved to the offline originals by id
    // (shared lookup above) so cards render local artwork.
    // Every sink — Details, PLAY, the ASK dialog — rides
    // the same unified funnels as online: MediaDetail and
    // the player resolve downloaded items themselves, so no
    // offline-specific routing exists.
    val byId = ctx.currentOfflineById
    val offlineItems = remember(section, byId) { offlineSectionItems(section, byId) }
    val rowItemClick: (OfflineMediaItem) -> Unit = remember(
        section.type, state.core.continueWatchingClickBehavior, callbacks,
    ) {
        resumeRowClick(
            sectionType = section.type,
            behavior = state.core.continueWatchingClickBehavior,
            toMediaItem = { it.toMediaItem() },
            sinks = ctx.resumeSinks,
        )
    }
    ContinueWatchingRow(
        title = sectionTitle,
        items = offlineItems,
        toMediaItem = { it.toMediaItem() },
        imageUrl = { it.posterPath.orEmpty() },
        backdropUrl = { it.backdropPath.orEmpty() },
        key = { it.id },
        onItemClick = rowItemClick,
        onPlayClick = remember(renderInputs.mediaOnPlayClick) {
            { item -> renderInputs.mediaOnPlayClick(item.toMediaItem()) }
        },
        modifier = sectionModifier,
        focusRequester = ctx.focusRequester,
        onRowFocused = ctx.onRowFocused,
        clippingEnabled = state.core.experimentalCardClippingEnabled,
        onSectionLongClick = sectionLongClick,
        onFocusedItemChange = callbacks.onFocusedMediaItem,
    )
}

/** Online Continue Watching / Next Up wide row over the server feed. */
@Composable
private fun HomeSectionOnlineWideRow(
    ctx: HomeRowArmContext,
    state: HomeContentState,
    callbacks: HomeContentCallbacks,
    renderInputs: HomeRenderInputs,
) {
    val section = ctx.section
    val sectionTitle = ctx.title
    val sectionModifier = ctx.modifier
    val sectionLongClick = ctx.onSectionLongClick
    val rowItemClick: (MediaItem) -> Unit = remember(
        section.type, state.core.continueWatchingClickBehavior, renderInputs.mediaOnItemClick, renderInputs.mediaOnPlayClick,
    ) {
        resumeRowClick(
            sectionType = section.type,
            behavior = state.core.continueWatchingClickBehavior,
            toMediaItem = { it },
            sinks = ctx.resumeSinks,
        )
    }
    ContinueWatchingRow(
        title = sectionTitle,
        items = section.items,
        toMediaItem = { it },
        imageUrl = renderInputs.mediaImageUrlBuilder,
        backdropUrl = renderInputs.mediaBackdropUrlBuilder,
        onItemClick = rowItemClick,
        onPlayClick = renderInputs.mediaOnPlayClick,
        modifier = sectionModifier,
        focusRequester = ctx.focusRequester,
        onRowFocused = ctx.onRowFocused,
        clippingEnabled = state.core.experimentalCardClippingEnabled,
        onSectionLongClick = sectionLongClick,
        onFocusedItemChange = callbacks.onFocusedMediaItem,
        edgeRefresh = ctx.edgeRefresh,
    )
}

/** Online poster row (Latest / Continue Reading / custom discover) over the server feed. */
@Composable
private fun HomeSectionOnlinePosterRow(
    ctx: HomeRowArmContext,
    state: HomeContentState,
    callbacks: HomeContentCallbacks,
    renderInputs: HomeRenderInputs,
) {
    val section = ctx.section
    val sectionTitle = ctx.title
    val sectionModifier = ctx.modifier
    val sectionLongClick = ctx.onSectionLongClick
    val seeAllClick = ctx.onSeeAllClick
    // Continue Reading rides the resume-row click policy
    // (Details / Play / Ask — the same pref as Continue
    // Watching; PLAY funnels to the reader through the
    // play fork's BOOK branch). Every other poster row
    // opens details — the selection lives in posterRowClick
    // so the offline poster row cannot drift from it.
    // (Same remember-key shape as the offline poster
    // branch: whole-bundle keys are stable.)
    val rowItemClick: (MediaItem) -> Unit = remember(
        section.type, state.core.continueWatchingClickBehavior, renderInputs.mediaOnItemClick,
    ) {
        posterRowClick(
            sectionType = section.type,
            behavior = state.core.continueWatchingClickBehavior,
            toMediaItem = { it },
            sinks = ctx.resumeSinks,
            onPlainClick = renderInputs.mediaOnItemClick,
        )
    }
    // The dice-roll in-flight mirror, leaf-collected at row scope (the
    // `pendingSyncEntries` pattern — see the content list's edge-pull
    // note): only the rolling row's arm recomposes when a roll spins up
    // or down, never the content list above it.
    val spinners by renderInputs.refreshSpinners.collectAsStateWithLifecycle()
    // Dice affordance: RANDOM-sorted custom discover rows only
    // (the descriptor resolves the row id back out of the
    // `discover_<rowId>` section id). Resolved once here so
    // the click, the tumble flag and the label key the SAME id.
    val diceRowId: String? = if (section.type == HomeSectionType.DISCOVER) {
        remember(section.id, state.randomDiscoverRowIds) {
            HomeSectionType.DISCOVER.descriptor.instanceIdFor(section.id)
                ?.takeIf { it in state.randomDiscoverRowIds }
        }
    } else null
    val rollDiscoverRow = remember(callbacks) { callbacks.onRollDiscoverRow }
    val diceRowAccessibilityLabel = if (diceRowId != null) {
        stringResource(Res.string.home_discover_reroll)
    } else null
    HomeMediaRow(
        title = sectionTitle,
        items = section.items,
        imageUrlBuilder = renderInputs.mediaImageUrlBuilder,
        fallbackImageUrlBuilder = renderInputs.fallbackImageUrlBuilder,
        onItemClick = rowItemClick,
        onPlayClick = renderInputs.mediaOnPlayClick,
        modifier = sectionModifier,
        photoFolderChildUrlsFor = callbacks.photoFolderChildUrlsFor,
        focusRequester = ctx.focusRequester,
        onRowFocused = ctx.onRowFocused,
        clippingEnabled = state.core.experimentalCardClippingEnabled,
        showEpisodeSeriesBadge = section.type == HomeSectionType.LATEST_MEDIA,
        // NEW badges: the arrival rows only (the Upcoming row's items
        // are also library arrivals — episodes added for airing shows).
        showNewBadges = section.type == HomeSectionType.LATEST_MEDIA ||
            section.type == HomeSectionType.RECENTLY_ADDED ||
            section.type == HomeSectionType.UPCOMING,
        homeLastVisitEpochMs = state.core.homeLastVisitEpochMs,
        // The air-date chip rides the Upcoming row's cards — its items
        // are unaired episodes, the premiere date being the point of the row.
        showAirDateChips = section.type == HomeSectionType.UPCOMING,
        onSectionLongClick = sectionLongClick,
        onSeeAllClick = seeAllClick,
        onRollClick = if (diceRowId != null) {
            { rollDiscoverRow(diceRowId) }
        } else null,
        rollInProgress = diceRowId != null && diceRowId in spinners.rollingDiscoverRowIds,
        rollAccessibilityLabel = diceRowAccessibilityLabel,
        onFocusedItemChange = callbacks.onFocusedMediaItem,
        // Book progress bars: exact page fractions from the
        // refresher's TOC-cache decodes, percent fallback for
        // items the cache doesn't know (see bookProgressFraction).
        progressFractionFor = if (section.type == HomeSectionType.CONTINUE_READING) {
            remember(state.bookProgressFractions) {
                bookProgressFractionFor(
                    fractions = state.bookProgressFractions,
                    idOf = { it.id },
                    fallback = { it.bookProgressFraction() },
                )
            }
        } else {
            null
        },
        seriesPosterResolver = remember(callbacks.getImageUrl) { { id: String -> callbacks.getImageUrl(id) } },
        seriesBackdropResolver = remember(renderInputs.onlineBackdropResolver) { { id: String -> renderInputs.onlineBackdropResolver(id) } },
        edgeRefresh = ctx.edgeRefresh,
    )
}

/** The continue-watching "Resume vs Details" ask dialog for a partially-watched item. */
@Composable
private fun HomeAskContinueDialog(
    item: MediaItem,
    onDismissRequest: () -> Unit,
    onPlay: () -> Unit,
    onDetails: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { Icon(Tabler.Outline.PlayerPlay, contentDescription = null) },
        title = { Text(item.name) },
        text = { Text(stringResource(Res.string.home_resume_or_details)) },
        confirmButton = {
            TextButton(onClick = onPlay) { Text(stringResource(Res.string.home_resume)) }
        },
        dismissButton = {
            TextButton(onClick = onDetails) { Text(stringResource(Res.string.home_details)) }
        },
    )
}

/**
 * The content list's ONE non-blocking notice row, shared by the
 * implicit-offline banner and the partial-load banner.
 * [onRetry] optional: the partial-load variant carries a Retry button.
 */
@Composable
private fun HomeStatusBannerRow(
    text: String,
    contentPad: Dp,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = contentPad, vertical = 4.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Tabler.Outline.AlertCircle,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = if (onRetry != null) Modifier.weight(1f) else Modifier,
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
                Text(stringResource(Res.string.home_retry))
            }
        }
    }
}
