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

/** The home poster rows' card width scale. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class HomeCardScale {
    SMALL,
    DEFAULT,
    LARGE,
}

/** How a continue-watching card renders its watch progress. Persistence is the enum NAME. */
@Immutable
@Serializable
enum class HomeCwProgressStyle {
    DEFAULT,
    THICK,
    PERCENT,
}

/**
 * The home feed's automatic refresh cadence — how often the periodic
 * background re-fetch runs while home is foregrounded (see
 * [HomeFreshness.intervalMsFor]). [DEFAULT] is the shipped 60 s cadence;
 * [EAGER] / [RELAXED] pace the same loop faster/slower; [MANUAL] disables
 * every automatic refresh (the periodic loop and the stale-on-start fetch) —
 * pull-to-refresh and section edge-pulls stay available. Persistence is the
 * enum NAME.
 */
@Immutable
@Serializable
enum class HomeRefreshCadence {
    DEFAULT,
    EAGER,
    RELAXED,
    MANUAL,
}

/**
 * The custom Discover rows' partition pattern — how many cards each
 * discover row shows per chunk. [DEFAULT] is the shipped 3-2-3 (compact) /
 * 5-4-6-5 (expanded) rhythm; [UNIFORM] gives every row the same equal size;
 * [COMPACT] leads with a smaller first row so more rows peek above the fold.
 * Persistence is the enum NAME.
 */
@Immutable
@Serializable
enum class HomeDiscoverPattern {
    DEFAULT,
    UNIFORM,
    COMPACT,
}

/**
 * The home newsletter banner's weekday window — stacking on the
 * `home_newsletter_banner_enabled` on/off toggle. [DEFAULT] keeps the
 * plugin's weekday-window logic; [ALWAYS] shows the banner every day the
 * issue is due; [WEEKENDS] restricts it to Saturday/Sunday. Persistence is
 * the enum NAME.
 */
@Immutable
@Serializable
enum class NewsletterBannerWindow {
    DEFAULT,
    ALWAYS,
    WEEKENDS,
}
