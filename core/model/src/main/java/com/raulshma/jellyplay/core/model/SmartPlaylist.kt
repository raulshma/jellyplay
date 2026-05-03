package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SmartPlaylist(
    val id: String,
    val name: String,
    val criteria: List<PlaylistCriterion>,
    val maxItems: Int = 50,
    val sortBy: SmartPlaylistSort = SmartPlaylistSort.RANDOM,
)

@Serializable
data class PlaylistCriterion(
    val type: CriterionType,
    val value: String,
    val operator: CriterionOperator = CriterionOperator.EQUALS,
)

@Serializable
enum class CriterionType {
    GENRE,
    ARTIST,
    ALBUM,
    YEAR,
    RATING,
    PLAY_COUNT,
    TAG,
}

@Serializable
enum class CriterionOperator {
    EQUALS,
    CONTAINS,
    GREATER_THAN,
    LESS_THAN,
}

@Serializable
enum class SmartPlaylistSort {
    RANDOM,
    TITLE,
    ARTIST,
    ALBUM,
    YEAR,
    RATING,
    PLAY_COUNT,
    DATE_ADDED,
}
