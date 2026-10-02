package com.raulshma.jellyplay.feature.search

import com.raulshma.jellyplay.core.data.repository.SearchHistoryItem
import com.raulshma.jellyplay.core.model.MediaItem
import com.raulshma.jellyplay.core.model.OfflineMediaItem

/**
 * Pure result-presentation folds for the search screen, extracted from its
 * composables beside the [computeSearchSurface] precedent — no Compose types,
 * so both are assertable JVM-side (`SearchResultFoldsTest`).
 */

/**
 * The "did you mean?" typo-tolerance fold: with no results for a typed query,
 * Jellyfin's substring/prefix-only media search has no fuzzy variant to push
 * through the server query, so suggestions are derived from the user's own
 * recent searches — a pure client-side prefix heuristic, no extra fetches.
 *
 * A past query suggests when it shares a meaningful leading run of characters
 * with the typed query (catches single-word typos like "Interstelar") or any
 * of its whitespace tokens (length >= 3) appears inside the typed query.
 * The query itself, sub-3-character queries and empty histories yield nothing;
 * results are distinct and capped at 4 (the rendered chip row's size).
 */
internal fun didYouMeanSuggestions(query: String, history: List<SearchHistoryItem>): List<String> {
    if (query.length < 3 || history.isEmpty()) {
        return emptyList()
    }
    return history
        .asSequence()
        .map { it.query }
        .filter { it != query }
        .filter {
            // Suggest a past query that shares a meaningful
            // leading run of characters (catches single-word
            // typos) or any whitespace token with the typed query.
            it.commonPrefixWith(query, ignoreCase = true).length >= 3 ||
                it.lowercase().split(' ', '\t').any { token ->
                    token.length >= 3 && query.lowercase().contains(token)
                }
        }
        .distinct()
        .take(4)
        .toList()
}

/**
 * The on-device section's dedup fold: downloaded items that also exist in the
 * library grid below are dropped so they don't render twice (Home already
 * does this for its downloaded row). Online ids are collected from the
 * pager's loaded snapshot, skipping nulls and blank ids; an empty offline
 * list or an empty online set short-circuits to the offline list unchanged.
 */
internal fun dedupeOfflineAgainstLibrary(
    offline: List<OfflineMediaItem>,
    libraryItems: List<MediaItem?>,
): List<OfflineMediaItem> {
    if (offline.isEmpty()) return offline
    val onlineIds = buildSet {
        for (item in libraryItems) {
            item?.id?.takeUnless { it.isBlank() }?.let { add(it) }
        }
    }
    if (onlineIds.isEmpty()) return offline
    return offline.filter { it.id !in onlineIds }
}
