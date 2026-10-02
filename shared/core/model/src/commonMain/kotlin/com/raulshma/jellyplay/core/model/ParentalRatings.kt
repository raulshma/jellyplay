package com.raulshma.jellyplay.core.model

/**
 * The canonical rating→age table (unknown ratings map to null = "no
 * opinion"). Formerly `core.network.library.parentalRatingAge` — moved to
 * core/model because its consumers are settings UIs (the screensaver
 * rating picker, the TV dream's local rating cap) that must not see the
 * network module; the network-side parental filter
 * (`filterByParentalRating`) resolves through this same table so the wire
 * filter and the picker vocabulary can never drift.
 */
public fun parentalRatingAge(rating: String): Int? = when (rating.uppercase()) {
    "G", "TV-Y", "TV-G" -> 0
    "PG", "TV-Y7", "TV-PG" -> 7
    "PG-13", "TV-14" -> 13
    "R", "TV-MA" -> 17
    "NC-17" -> 18
    else -> null
}

/**
 * The MPAA rating ladder the local rating-cap pickers offer (the settings
 * screensaver's max-parental-rating rows), ordered strictest-last. Lives
 * beside [parentalRatingAge] so the row vocabulary and the age resolution
 * move together — a new rating lands in both or neither.
 */
public val PARENTAL_RATING_PICKER_LADDER: List<String> =
    listOf("G", "PG", "PG-13", "R", "NC-17")
