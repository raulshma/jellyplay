package com.raulshma.jellyplay.core.model

/**
 * Client-side projection of the offline library onto the library screen's
 * [LibraryFilters] — the data path behind the "Downloaded" filter, where the
 * browse grid is served from the local store (see
 * `OfflineRepository.getOfflineLibraryInFolder`) instead of the server. The
 * filter dimensions and sort the server would normally apply in its query are
 * re-implemented here over the fields an offline row carries.
 *
 * Dimensions with no offline column are ignored: [LibraryFilters.tags] has no
 * offline storage (the chip row hides Tags while the filter is active).
 *
 * Sort mapping uses download-time fields as the closest offline analogue:
 * DATE_ADDED / DATE_LAST_CONTENT_ADDED sort by the row's download date, and
 * PREMIERE_DATE falls back to production year (premiere timestamps aren't
 * stored).
 */
fun List<OfflineMediaItem>.toFilteredLibraryItems(filters: LibraryFilters): List<MediaItem> {
    val wantedTypes = filters.mediaTypes.toSet()
    val wantedGenres = filters.genres.toSet()
    val wantedYears = filters.years.toSet()

    // Map to MediaItem up front so played-status filtering uses the same
    // normalized watch state (>=95% resume counts as played) the cards render.
    val mapped = asSequence()
        .map { it to it.toMediaItem() }
        .filter { (_, item) ->
            (wantedTypes.isEmpty() || item.mediaType in wantedTypes) &&
                (wantedGenres.isEmpty() || item.genres.any { it in wantedGenres }) &&
                (wantedYears.isEmpty() || (item.year ?: Int.MIN_VALUE) in wantedYears) &&
                (filters.minRating <= 0f || (item.communityRating ?: 0f) >= filters.minRating) &&
                when (filters.playedStatus) {
                    PlayedStatus.ALL -> true
                    PlayedStatus.PLAYED -> item.isPlayed
                    PlayedStatus.UNPLAYED -> !item.isPlayed
                } &&
                (filters.isResumable != true || item.playbackPositionTicks != null)
        }
        .toList()

    val sorted = when (filters.sortBy) {
        SortOption.SORT_NAME, SortOption.ALBUM, SortOption.ALBUM_ARTIST ->
            mapped.sortedByCachedKey { (_, item) -> item.name.lowercase() }
        SortOption.YEAR_DESC, SortOption.PREMIERE_DATE ->
            mapped.sortedByDescending { (_, item) -> item.year ?: Int.MIN_VALUE }
        SortOption.YEAR_ASC ->
            mapped.sortedBy { (_, item) -> item.year ?: Int.MAX_VALUE }
        SortOption.RATING -> mapped.sortedByDescending { (_, item) -> item.communityRating ?: -1f }
        SortOption.DATE_ADDED, SortOption.DATE_LAST_CONTENT_ADDED ->
            mapped.sortedByDescending { (raw, _) -> raw.createdAt }
        SortOption.DATE_PLAYED, SortOption.IN_PROGRESS -> mapped.sortedWith(
            compareBy<Pair<OfflineMediaItem, MediaItem>> { (raw, item) ->
                // Items with playback progress (or a play history) first,
                // most recently played leading.
                when {
                    item.playbackPositionTicks != null -> 0
                    raw.lastPlayedDate != null -> 1
                    else -> 2
                }
            }.thenComparator { (aRaw, _), (bRaw, _) ->
                compareValues(bRaw.lastPlayedDate, aRaw.lastPlayedDate)
            },
        )
        SortOption.RANDOM -> mapped.shuffled()
    }

    return sorted.map { (_, item) -> item }
}
