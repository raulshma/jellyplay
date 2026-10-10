package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.RecommendationResult
import com.raulshma.jellyplay.core.model.descriptor
import com.raulshma.jellyplay.core.model.home.ContinueWatchingRowRule

/**
 * Pure assembly of [HomeSectionsResult] from already-fetched sub-call
 * results — the section-building/ordering half of the jvmShared
 * `LibraryApiClientImpl.getHomeSections`, extracted so
 * commonTest can pin it without a server.
 *
 * Emission order (verbatim from the JVM impl):
 * Continue Watching → Continue Reading → Next Up → one Latest Media row per
 * library (folder order) → Recently Added (inserted right after the LAST
 * Latest Media row) → Recommendations (or the suggestions fallback when it
 * comes back empty) → user-pinned sections appended last. Section *types* that
 * errored are collected in `failedSectionTypes`; a section that legitimately
 * returned zero items is NOT a failure.
 *
 * The fetch-side pieces (concurrency, the TTL sub-call caches, the music
 * folder filter, the pinned-section item resolution) stay in the client —
 * this class only decides what the fetched data turns into.
 */
internal class HomeSectionsAssemblyInputs(
    val query: HomeSectionQuery,
    val continueWatchingResult: Result<List<MediaItem>> = Result.success(emptyList()),
    val continueReadingResult: Result<List<MediaItem>> = Result.success(emptyList()),
    val nextUpResult: Result<List<MediaItem>> = Result.success(emptyList()),
    val foldersResult: Result<List<LibraryFolder>> = Result.success(emptyList()),
    /** Latest-media sub-results in folder order (music folders already filtered out by the caller). */
    val latestPerFolder: List<Pair<LibraryFolder, Result<List<MediaItem>>>> = emptyList(),
    /** Null when the RECOMMENDATIONS section is disabled. */
    val recommendationsResult: Result<RecommendationResult>? = null,
    /**
     * Suggestions fallback for a successful-but-empty recommendations result
     * (the JVM impl calls `getSearchSuggestions(limit = 20)` there). The
     * caller pre-fetches it only when applicable, so assembly stays pure.
     */
    val suggestions: List<MediaItem> = emptyList(),
    val pinnedSections: List<HomeSection> = emptyList(),
    /**
     * User-configured JELLYFIN discover rows, already built as sections by the
     * fetcher (row order preserved). Emitted ahead of the pinned sections so
     * OrderHomeSectionsUseCase's stable sort places the DISCOVER block at the
     * user's configured type position with rows in config order.
     */
    val discoverSections: List<HomeSection> = emptyList(),
    /**
     * Plugin-sourced rows (PLUGIN_ROW — the seasonal row today), already
     * built as sections by the fetcher (empty = gated off / absent — the
     * capability registry is the gate, never the layout config). Emitted
     * after the discover rows and before the user's pinned sections, so the
     * order's stable sort (both types sort last) keeps the curated plugin
     * rows ahead of the pins while inheriting the standard sections' order.
     */
    val pluginRowSections: List<HomeSection> = emptyList(),
)

internal class HomeSectionsAssemblyOutput(
    val result: HomeSectionsResult,
    /** First failure seen (input order); the caller throws it when NO section rendered. */
    val firstError: Throwable?,
)

/**
 * The shared state one assembly pass carries between arms: the emitted
 * sections (in emission order), the failed types, the first failure, the
 * Continue Watching id set the CW arm captures (the NEXT_UP and
 * RECENTLY_ADDED arms filter against it) and the latest-media aggregate the
 * fan-out arm accumulates (the RECENTLY_ADDED arm folds it into its row).
 */
internal class HomeRowAssembly(
    val query: HomeSectionQuery,
    val input: HomeSectionsAssemblyInputs,
) {
    val sections = mutableListOf<HomeSection>()
    val failedTypes = mutableSetOf<HomeSectionType>()
    var firstError: Throwable? = null
    var continueWatchingIds: Set<String> = emptySet()
    val latestAggregate = mutableListOf<MediaItem>()

    /** Records the first failure (input order decides which error surfaces). */
    fun recordFailure(error: Throwable) {
        if (firstError == null) firstError = error
    }
}

/** One per-type assembly arm: reads its sub-results from the pass's [HomeRowAssembly]. */
internal fun interface HomeRowAssembleArm {
    fun assemble(ctx: HomeRowAssembly)
}

/**
 * The per-type assembly arms — the transport-half registry's assembly table
 * (the model [com.raulshma.jellyplay.core.model.home.HomeRowModule] carries
 * the type's identity/gates; these arms carry what the fetched sub-results
 * BECOME). Registered for every type; the rows the network never constructs
 * (FAVORITES, LIVE_TV, DOWNLOADED) carry the inert arm, and the walk order
 * ([HomeRowAssembleArms.emissionOrder]) IS the emission order — the
 * verbatim order the former hand-written pass emitted in.
 */
internal object HomeRowAssembleArms {

    /**
     * The emission order: Continue Watching → Continue Reading → Next Up →
     * the latest fan-out (one row per library, folder order) → Recently
     * Added (inserted right after the LAST Latest Media row) →
     * Recommendations (or the suggestions fallback) → the discover block →
     * the plugin rows → the user's pinned sections.
     */
    internal val emissionOrder: List<HomeSectionType> = listOf(
        HomeSectionType.CONTINUE_WATCHING,
        HomeSectionType.CONTINUE_READING,
        HomeSectionType.NEXT_UP,
        HomeSectionType.LATEST_MEDIA,
        HomeSectionType.RECENTLY_ADDED,
        HomeSectionType.RECOMMENDATIONS,
        HomeSectionType.DISCOVER,
        HomeSectionType.PLUGIN_ROW,
        HomeSectionType.PINNED,
        HomeSectionType.FAVORITES,
        HomeSectionType.LIVE_TV,
        HomeSectionType.DOWNLOADED,
    )

    private val inert = HomeRowAssembleArm { }

    private val arms: Map<HomeSectionType, HomeRowAssembleArm> = mapOf(

        HomeSectionType.CONTINUE_WATCHING to HomeRowAssembleArm { ctx ->
            if (HomeSectionType.CONTINUE_WATCHING in ctx.query.enabledSections) {
                ctx.input.continueWatchingResult
                    .onSuccess { list ->
                        val filtered = list.excludingHiddenItemIds(ctx.query.hiddenCwItemIds)
                        if (filtered.isNotEmpty()) {
                            ctx.continueWatchingIds = continueWatchingFilterIds(list, ctx.query.hiddenCwItemIds)
                            ctx.sections.add(HomeSectionType.CONTINUE_WATCHING.descriptor.section(filtered))
                        }
                    }
                    .onFailure {
                        ctx.recordFailure(it)
                        ctx.failedTypes.add(HomeSectionType.CONTINUE_WATCHING)
                    }
            }
        },

        HomeSectionType.CONTINUE_READING to HomeRowAssembleArm { ctx ->
            if (HomeSectionType.CONTINUE_READING in ctx.query.enabledSections) {
                ctx.input.continueReadingResult
                    .onSuccess { list ->
                        // Books ride the same per-item "hide from resume rows"
                        // affordance as Continue Watching — one hiddenCwItemIds
                        // set covers both rows.
                        val filtered = list.excludingHiddenItemIds(ctx.query.hiddenCwItemIds)
                        if (filtered.isNotEmpty()) {
                            ctx.sections.add(HomeSectionType.CONTINUE_READING.descriptor.section(filtered))
                        }
                    }
                    .onFailure {
                        ctx.recordFailure(it)
                        ctx.failedTypes.add(HomeSectionType.CONTINUE_READING)
                    }
            }
        },

        HomeSectionType.NEXT_UP to HomeRowAssembleArm { ctx ->
            if (HomeSectionType.NEXT_UP in ctx.query.enabledSections) {
                ctx.input.nextUpResult
                    .onSuccess { list ->
                        // Drop items whose series is in the user's "remove from Next Up" blocklist.
                        val filtered = list.filterNextUpEligible(ctx.continueWatchingIds, ctx.query.nextUpExcludedSeriesIds)
                        if (filtered.isNotEmpty()) {
                            // Title comes from the descriptor ("Next Up") — the
                            // pre-descriptor literal here had drifted to "NextUp".
                            ctx.sections.add(HomeSectionType.NEXT_UP.descriptor.section(filtered))
                        }
                    }
                    .onFailure {
                        ctx.recordFailure(it)
                        ctx.failedTypes.add(HomeSectionType.NEXT_UP)
                    }
            }
        },

        // The latest fan-out backs TWO rows (the per-library Latest Media
        // sections and the Recently Added aggregate), so its arm owns the
        // shared folders handling and accumulates [HomeRowAssembly.latestAggregate]
        // for the RECENTLY_ADDED arm downstream.
        HomeSectionType.LATEST_MEDIA to HomeRowAssembleArm { ctx ->
            val enabledSections = ctx.query.enabledSections
            val wantsLatestFanOut =
                HomeSectionType.LATEST_MEDIA in enabledSections || HomeSectionType.RECENTLY_ADDED in enabledSections

            if (wantsLatestFanOut) {
                ctx.input.foldersResult
                    .onSuccess {
                        for ((folder, result) in ctx.input.latestPerFolder) {
                            val disabledForFolder = ctx.query.libraryHomeSectionOverrides[folder.id].orEmpty()
                            result.onSuccess { latest ->
                                // Only feed the aggregated Recently Added row from
                                // libraries the user hasn't disabled it for.
                                if (HomeSectionType.RECENTLY_ADDED !in disabledForFolder) {
                                    ctx.latestAggregate.addAll(latest)
                                }
                                val latestEnabledForFolder = HomeSectionType.LATEST_MEDIA in enabledSections &&
                                    HomeSectionType.LATEST_MEDIA !in disabledForFolder
                                if (latest.isNotEmpty() && latestEnabledForFolder) {
                                    val descriptor = HomeSectionType.LATEST_MEDIA.descriptor
                                    ctx.sections.add(
                                        HomeSection(
                                            id = descriptor.idFor(folder.id),
                                            title = descriptor.titleFor(folder.name),
                                            type = HomeSectionType.LATEST_MEDIA,
                                            items = latest,
                                            libraryId = folder.id,
                                            collectionType = folder.collectionType,
                                        ),
                                    )
                                }
                            }.onFailure {
                                // A per-folder Latest Media 403 (e.g. a stale cached
                                // folder list racing with a permission change) should
                                // surface as a partial-load banner, not vanish silently.
                                ctx.recordFailure(it)
                                if (HomeSectionType.LATEST_MEDIA in enabledSections) {
                                    ctx.failedTypes.add(HomeSectionType.LATEST_MEDIA)
                                }
                            }
                        }
                    }
                    .onFailure {
                        ctx.recordFailure(it)
                        // The shared folders fetch backs both Latest Media and
                        // Recently Added rows; a failure starves both sections.
                        if (HomeSectionType.LATEST_MEDIA in enabledSections) {
                            ctx.failedTypes.add(HomeSectionType.LATEST_MEDIA)
                        }
                        if (HomeSectionType.RECENTLY_ADDED in enabledSections) {
                            ctx.failedTypes.add(HomeSectionType.RECENTLY_ADDED)
                        }
                    }
            }
        },

        HomeSectionType.RECENTLY_ADDED to HomeRowAssembleArm { ctx ->
            if (HomeSectionType.RECENTLY_ADDED in ctx.query.enabledSections) {
                val recentlyAddedItems = ctx.latestAggregate.distinctByIdExcluding(ctx.continueWatchingIds)
                if (recentlyAddedItems.isNotEmpty()) {
                    val recentlyAddedSection = HomeSectionType.RECENTLY_ADDED.descriptor.section(recentlyAddedItems)
                    val latestMediaLastIndex = ctx.sections.indexOfLast { it.type == HomeSectionType.LATEST_MEDIA }
                    val insertIndex = if (latestMediaLastIndex >= 0) latestMediaLastIndex + 1 else ctx.sections.size
                    ctx.sections.add(insertIndex, recentlyAddedSection)
                }
            }
        },

        HomeSectionType.RECOMMENDATIONS to HomeRowAssembleArm { ctx ->
            ctx.input.recommendationsResult
                ?.onSuccess { result ->
                    if (result.items.isNotEmpty()) {
                        ctx.sections.add(
                            HomeSectionType.RECOMMENDATIONS.descriptor.section(result.items, seedItem = result.seedItem),
                        )
                    } else if (ctx.input.suggestions.isNotEmpty()) {
                        // Fallback "For You" source when there are no similarity seeds
                        // yet (new user, no watch history): surface favorited/liked
                        // items so the home page still has discovery content. Mirrors
                        // the search "Suggestions" data source.
                        ctx.sections.add(HomeSectionType.RECOMMENDATIONS.descriptor.section(ctx.input.suggestions))
                    }
                }
                ?.onFailure {
                    ctx.recordFailure(it)
                    ctx.failedTypes.add(HomeSectionType.RECOMMENDATIONS)
                }
        },

        // User-configured discover rows (Jellyfin sources; Seerr rows are
        // spliced in by the feature layer after ordering). Config order
        // within the block — the fetcher emits them already ordered.
        HomeSectionType.DISCOVER to HomeRowAssembleArm { ctx ->
            ctx.input.discoverSections.forEach { section -> ctx.sections.add(section) }
        },

        // Plugin-sourced rows (the companion plugin's seasonal row today) —
        // capability-gated, not layout-driven; ahead of the user's pins.
        HomeSectionType.PLUGIN_ROW to HomeRowAssembleArm { ctx ->
            ctx.input.pluginRowSections.forEach { section -> ctx.sections.add(section) }
        },

        // Append user-pinned sections (collections / playlists / favorites /
        // genres / studios) — always fetched regardless of enabledSections, and
        // placed after the standard sections so the HomeViewModel's ordering
        // logic puts them at the end of the home screen in pin order.
        HomeSectionType.PINNED to HomeRowAssembleArm { ctx ->
            ctx.input.pinnedSections.forEach { section -> ctx.sections.add(section) }
        },
    )

    /** The arm for [type]; the rows the network never constructs get the inert arm. */
    internal fun forType(type: HomeSectionType): HomeRowAssembleArm = arms[type] ?: inert
}

internal fun assembleHomeSections(input: HomeSectionsAssemblyInputs): HomeSectionsAssemblyOutput {
    val ctx = HomeRowAssembly(query = input.query, input = input)
    for (type in HomeRowAssembleArms.emissionOrder) {
        HomeRowAssembleArms.forType(type).assemble(ctx)
    }
    return HomeSectionsAssemblyOutput(
        result = HomeSectionsResult(ctx.sections.toList(), ctx.failedTypes.toSet()),
        firstError = ctx.firstError,
    )
}

/**
 * The assembler's Next Up eligibility filters — CW-overlap drop (keyed on the
 * HIDDEN-FILTERED Continue Watching id set) plus the "remove from Next Up"
 * series blocklist — shared verbatim with [HomeSectionsFetcher.refreshSection]'s
 * single-row refetch so the two cannot drift. Routes through
 * [com.raulshma.jellyplay.core.model.home.ContinueWatchingRowRule.nextUpEligible],
 * the rule's single owner (the offline Next Up row consumes the same member).
 */
internal fun List<MediaItem>.filterNextUpEligible(
    continueWatchingIds: Set<String>,
    excludedSeriesIds: Set<String>,
): List<MediaItem> =
    ContinueWatchingRowRule.nextUpEligible(this, continueWatchingIds, excludedSeriesIds)

