package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val overview: String? = null,
    val itemCount: Int = 0,
    val imageTag: String? = null,
    val userId: String? = null,
    val isReadOnly: Boolean = false,
    val isPublic: Boolean = false,
    val canEdit: Boolean = true,
    val canDelete: Boolean = true,
    val createdAt: String? = null,
)

@Immutable
@Serializable
data class PlaylistItem(
    val id: String,
    val playlistItemId: String? = null,
    val name: String,
    val artist: String? = null,
    val album: String? = null,
    val mediaType: MediaType = MediaType.AUDIO,
    val runTimeTicks: Long? = null,
)

@Immutable
@Serializable
data class MoodPlaylistPreference(
    val playlistId: String,
    val isEnabled: Boolean = true,
    val isFavorite: Boolean = false,
    val lastPlayedAt: Long = 0L,
)
