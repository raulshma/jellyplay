package com.raulshma.jellyplay.core.model

/**
 * Pure grouping logic for the library grid, lifted out of `GroupedLibraryContent`
 * so it can be unit-tested. This was the site of two #113 crashes (duplicate
 * lazy keys from scattered group labels, and a throw on [GroupBy.NONE] while a
 * grouped view was transiently mounted) — both pinned by `LibraryGrouperTest`.
 */
object LibraryGrouper {

    /**
     * A group key derived from a [MediaItem] for the active [GroupBy] dimension.
     *
     * Returns an empty string for [GroupBy.NONE] instead of throwing: although the
     * caller only mounts grouped rendering when grouping is active, the persisted
     * [GroupBy] value flows in asynchronously and can transiently resolve to NONE
     * while a grouped view is still mounted. Throwing here crashed the app on
     * every library open once a non-NONE value had been persisted (issue #113).
     */
    fun groupKey(item: MediaItem, groupBy: GroupBy): String = when (groupBy) {
        GroupBy.NONE -> ""
        GroupBy.NAME -> (item.name.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#').toString()
        GroupBy.TYPE -> item.mediaType.name
        GroupBy.GENRE -> item.genres.firstOrNull() ?: "Unknown"
        GroupBy.YEAR -> item.year?.toString() ?: "Unknown"
    }
}
