package com.raulshma.jellyplay.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val overview: String? = null,
    val itemCount: Int = 0,
    val imageTag: String? = null,
)

@Serializable
data class PlaylistItem(
    val id: String,
    val name: String,
    val artist: String? = null,
    val album: String? = null,
    val mediaType: MediaType = MediaType.AUDIO,
    val runTimeTicks: Long? = null,
)
