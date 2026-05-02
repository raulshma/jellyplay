package com.raulshma.jellyplay.core.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed class Route : NavKey {

    @Serializable data object ServerList : Route()
    @Serializable data object AddServer : Route()
    @Serializable data class Login(val serverAddress: String) : Route()

    @Serializable data object Home : Route()
    @Serializable data object Library : Route()
    @Serializable data object Search : Route()

    @Serializable data class MediaDetail(val itemId: String) : Route()
    @Serializable data class PersonDetail(val personId: String) : Route()

    @Serializable data class VideoPlayer(
        val itemId: String,
        val mediaSourceId: String? = null,
        val startPositionTicks: Long = 0,
    ) : Route()

    @Serializable data class AudioPlayer(val itemId: String) : Route()

    @Serializable data object Downloads : Route()

    @Serializable data object Settings : Route()

    @Serializable data object Artists : Route()
    @Serializable data object Albums : Route()
    @Serializable data object Tracks : Route()
    @Serializable data object Genres : Route()
    @Serializable data class ArtistDetail(val artistId: String) : Route()
    @Serializable data class AlbumDetail(val albumId: String) : Route()
    @Serializable data class CollectionDetail(val collectionId: String) : Route()
}

val TOP_LEVEL_ROUTES = linkedMapOf(
    Route.Home to "Home",
    Route.Library to "Library",
    Route.Search to "Search",
)
