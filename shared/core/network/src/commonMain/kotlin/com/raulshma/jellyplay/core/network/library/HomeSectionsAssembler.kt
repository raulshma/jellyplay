package com.raulshma.jellyplay.core.network.library

import com.raulshma.jellyplay.core.model.HomeSection
import com.raulshma.jellyplay.core.model.HomeSectionQuery
import com.raulshma.jellyplay.core.model.HomeSectionsResult
import com.raulshma.jellyplay.core.model.HomeSectionType
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.RecommendationResult
import com.raulshma.jellyplay.core.model.descriptor

/**
 * Pure assembly of [HomeSectionsResult] from already-fetched sub-call
 * results — the section-building/ordering half of the jvmShared
 * `LibraryApiClientImpl.getHomeSections`, extracted so the wasm client shares
 * the exact ordering logic and commonTest can pin it without a server.
 *
 * Emission order (verbatim from the JVM impl):
 * Continue Watching → Next Up → one Latest Media row per library (folder
 * order) → Recently Added (inserted right after the LAST Latest Media row) →
 * Recommendations (or the suggestions fallback when it comes back empty) →
 * user-pinned sections appended last. Section *types* that errored are
 * collected in `failedSectionTypes`; a section that legitimately returned
 * zero items is NOT a failure.
 *
 * The fetch-side pieces (concurrency, the TTL sub-call caches, the music
 * folder filter, the pinned-section item resolution) stay in the client —
 * this class only decides what the fetched data turns into.
 */
internal class HomeSectionsAssemblyInputs(
    val query: HomeSectionQuery,
    val continueWatchingResult: Result<List<MediaItem>> = Result.success(emptyList()),
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
)

internal class HomeSectionsAssemblyOutput(
    val result: HomeSectionsResult,
    /** First failure seen (input order); the caller throws it when NO section rendered. */
    val firstError: Throwable?,
)

internal fun assembleHomeSections(input: HomeSectionsAssemblyInputs): HomeSectionsAssemblyOutput {
    val query = input.query
    val enabledSections = query.enabledSections
    val sections = mutableListOf<HomeSection>()
    val failedTypes = mutableSetOf<HomeSectionType>()
    var firstError: Throwable? = null

    var continueWatchingIds = emptySet<String>()

    if (HomeSectionType.CONTINUE_WATCHING in enabledSections) {
        input.continueWatchingResult
            .onSuccess { list ->
                val filtered = list.filter { it.id !in query.hiddenCwItemIds }
                if (filtered.isNotEmpty()) {
                    continueWatchingIds = filtered.map { it.id }.toSet()
                    sections.add(HomeSectionType.CONTINUE_WATCHING.descriptor.section(filtered))
                }
            }
            .onFailure {
                if (firstError == null) firstError = it
                failedTypes.add(HomeSectionType.CONTINUE_WATCHING)
            }
    }

    if (HomeSectionType.NEXT_UP in enabledSections) {
        input.nextUpResult
            .onSuccess { list ->
                // Drop items whose series is in the user's "remove from Next Up" blocklist.
                val filtered = list.filter { it.id !in continueWatchingIds }
                    .filter { it.seriesId == null || it.seriesId !in query.nextUpExcludedSeriesIds }
                if (filtered.isNotEmpty()) {
                    // Title comes from the descriptor ("Next Up") — the
                    // pre-descriptor literal here had drifted to "NextUp".
                    sections.add(HomeSectionType.NEXT_UP.descriptor.section(filtered))
                }
            }
            .onFailure {
                if (firstError == null) firstError = it
                failedTypes.add(HomeSectionType.NEXT_UP)
            }
    }

    val allLatestItems = mutableListOf<MediaItem>()
    val wantsLatestFanOut =
        HomeSectionType.LATEST_MEDIA in enabledSections || HomeSectionType.RECENTLY_ADDED in enabledSections

    if (wantsLatestFanOut) {
        input.foldersResult
            .onSuccess {
                for ((folder, result) in input.latestPerFolder) {
                    val disabledForFolder = query.libraryHomeSectionOverrides[folder.id].orEmpty()
                    result.onSuccess { latest ->
                        // Only feed the aggregated Recently Added row from
                        // libraries the user hasn't disabled it for.
                        if (HomeSectionType.RECENTLY_ADDED !in disabledForFolder) {
                            allLatestItems.addAll(latest)
                        }
                        val latestEnabledForFolder = HomeSectionType.LATEST_MEDIA in enabledSections &&
                            HomeSectionType.LATEST_MEDIA !in disabledForFolder
                        if (latest.isNotEmpty() && latestEnabledForFolder) {
                            val descriptor = HomeSectionType.LATEST_MEDIA.descriptor
                            sections.add(
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
                        if (firstError == null) firstError = it
                        if (HomeSectionType.LATEST_MEDIA in enabledSections) {
                            failedTypes.add(HomeSectionType.LATEST_MEDIA)
                        }
                    }
                }
            }
            .onFailure {
                if (firstError == null) firstError = it
                // The shared folders fetch backs both Latest Media and
                // Recently Added rows; a failure starves both sections.
                if (HomeSectionType.LATEST_MEDIA in enabledSections) {
                    failedTypes.add(HomeSectionType.LATEST_MEDIA)
                }
                if (HomeSectionType.RECENTLY_ADDED in enabledSections) {
                    failedTypes.add(HomeSectionType.RECENTLY_ADDED)
                }
            }
    }

    if (HomeSectionType.RECENTLY_ADDED in enabledSections) {
        val recentlyAddedItems = allLatestItems
            .distinctBy { it.id }
            .filter { it.id !in continueWatchingIds }
        if (recentlyAddedItems.isNotEmpty()) {
            val recentlyAddedSection = HomeSectionType.RECENTLY_ADDED.descriptor.section(recentlyAddedItems)
            val latestMediaLastIndex = sections.indexOfLast { it.type == HomeSectionType.LATEST_MEDIA }
            val insertIndex = if (latestMediaLastIndex >= 0) latestMediaLastIndex + 1 else sections.size
            sections.add(insertIndex, recentlyAddedSection)
        }
    }

    input.recommendationsResult
        ?.onSuccess { result ->
            if (result.items.isNotEmpty()) {
                sections.add(HomeSectionType.RECOMMENDATIONS.descriptor.section(result.items, seedItem = result.seedItem))
            } else if (input.suggestions.isNotEmpty()) {
                // Fallback "For You" source when there are no similarity seeds
                // yet (new user, no watch history): surface favorited/liked
                // items so the home page still has discovery content. Mirrors
                // the search "Suggestions" data source.
                sections.add(HomeSectionType.RECOMMENDATIONS.descriptor.section(input.suggestions))
            }
        }
        ?.onFailure {
            if (firstError == null) firstError = it
            failedTypes.add(HomeSectionType.RECOMMENDATIONS)
        }

    // Append user-pinned sections (collections / playlists / favorites /
    // genres / studios) — always fetched regardless of enabledSections, and
    // placed after the standard sections so the HomeViewModel's ordering
    // logic puts them at the end of the home screen in pin order.
    input.pinnedSections.forEach { section -> sections.add(section) }

    return HomeSectionsAssemblyOutput(
        result = HomeSectionsResult(sections, failedTypes.toSet()),
        firstError = firstError,
    )
}
