package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val items: List<MediaItem>,
    val totalRecordCount: Int,
    val startIndex: Int,
)

@Serializable
data class HomeSection(
    val title: String,
    val type: HomeSectionType,
    val items: List<MediaItem>,
)

@Serializable
enum class HomeSectionType {
    CONTINUE_WATCHING,
    NEXT_UP,
    LATEST_MEDIA,
    FAVORITES,
    LIVE_TV,
}

@Serializable
data class LibraryFolder(
    val id: String,
    val name: String,
    val collectionType: String? = null,
    val type: String? = null,
)

@Serializable
data class Genre(
    val id: String,
    val name: String,
)
