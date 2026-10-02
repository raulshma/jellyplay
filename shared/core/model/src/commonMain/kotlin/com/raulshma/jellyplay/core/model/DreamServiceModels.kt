package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class DreamImageCategory {
    MOVIES,
    SERIES,
    MUSIC,
    PHOTOS,
}

@Immutable
@Serializable
enum class DreamTransitionStyle {
    CROSSFADE,
    SLIDE,
    NONE,
}

@Immutable
data class DreamImage(
    val itemId: String,
    /** The image to show: the backdrop for video/music, the Primary for photos. */
    val imageUrl: String,
    val title: String,
    val type: DreamImageCategory,
)
