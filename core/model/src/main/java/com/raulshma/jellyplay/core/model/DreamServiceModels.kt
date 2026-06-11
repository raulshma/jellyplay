package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
enum class DreamImageCategory {
    MOVIES,
    SERIES,
    MUSIC,
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
    val backdropUrl: String,
    val title: String,
    val type: DreamImageCategory,
)
