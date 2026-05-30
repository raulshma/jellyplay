package com.raulshma.jellyplay.core.model

object KidsContentFilter {

    private val RATING_HIERARCHY: List<String> = listOf(
        "G", "TV-Y", "TV-G",
        "TV-Y7", "PG", "TV-PG",
        "PG-13", "TV-14",
        "R", "TV-MA",
        "NC-17",
    )

    val SELECTABLE_MAX_RATINGS: List<String> = listOf(
        "G", "PG", "PG-13", "TV-Y", "TV-Y7", "TV-G", "TV-PG", "TV-14",
    )

    fun isAllowed(
        item: MediaItem,
        maxRating: String,
    ): Boolean {
        val rating = item.officialRating ?: return false
        val maxIndex = RATING_HIERARCHY.indexOf(maxRating)
        if (maxIndex < 0) return false
        val itemIndex = RATING_HIERARCHY.indexOf(rating.uppercase())
        return itemIndex >= 0 && itemIndex <= maxIndex
    }

    fun <T : MediaItem> filter(items: List<T>, maxRating: String): List<T> {
        return items.filter { isAllowed(it, maxRating) }
    }
}
