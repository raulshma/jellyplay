package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SmartPlaylist(
    val id: String,
    val name: String,
    val criteria: List<PlaylistCriterion>,
    val maxItems: Int = 50,
    val sortBy: SmartPlaylistSort = SmartPlaylistSort.RANDOM,
)

@Immutable
@Serializable
data class PlaylistCriterion(
    val type: CriterionType,
    val value: String,
    val operator: CriterionOperator = CriterionOperator.EQUALS,
)

@Immutable
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

@Immutable
@Serializable
enum class CriterionOperator {
    EQUALS,
    CONTAINS,
    GREATER_THAN,
    LESS_THAN,
}

@Immutable
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
