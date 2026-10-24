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

/**
 * The hero block's presentation on home. [CAROUSEL] is the shipped
 * horizontally-paged hero; [SLIDESHOW] auto-advances on a timer (the
 * `home_hero_slideshow_interval_sec` pacing); [DETAILED] shows a single
 * expanded spotlight card; [TV_SLIDER] is the focus-driven TV row. Persistence
 * is the enum NAME.
 */
@Immutable
@Serializable
enum class HomeHeroStyle {
    CAROUSEL,
    SLIDESHOW,
    DETAILED,
    TV_SLIDER,
}
