package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class SearchResult(
    val items: List<MediaItem>,
    val totalRecordCount: Int,
    val startIndex: Int,
)

@Immutable
@Serializable
data class HomeSection(
    val id: String,
    val title: String,
    val type: HomeSectionType,
    val items: List<MediaItem>,
)

@Immutable
@Serializable
enum class HomeSectionType {
    CONTINUE_WATCHING,
    NEXT_UP,
    LATEST_MEDIA,
    FAVORITES,
    LIVE_TV,
    DOWNLOADED,
}

@Immutable
@Serializable
data class LibraryFolder(
    val id: String,
    val name: String,
    val collectionType: String? = null,
    val type: String? = null,
)

@Immutable
@Serializable
data class Genre(
    val id: String,
    val name: String,
)
