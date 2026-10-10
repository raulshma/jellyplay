package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * The home-screen preference enums: which library shell the home hosts and
 * how a Continue Watching tap resolves.
 */

@Immutable
@Serializable
enum class HomeMode {
    VIDEO,
    MUSIC,
}

@Immutable
@Serializable
enum class ContinueWatchingClickBehavior(val displayName: String) {
    DETAILS("Open Details"),
    PLAY("Resume Playback"),
    ASK("Always Ask"),
}
