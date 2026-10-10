package com.raulshma.jellyplay.core.model

/**
 * Availability of the jellyfin-plugin-jellyplay companion server plugin on the
 * active server — the same three-state shape as [PlaybackReportingStatus]:
 * UNKNOWN (never probed this session), AVAILABLE (capabilities handshake
 * succeeded), UNAVAILABLE (probe failed or 404 — plugin absent).
 */
enum class JellyPlayPluginStatus {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE,
}

/**
 * Stable feature keys reported by the plugin's capabilities handshake.
 * Keep byte-identical with the plugin's JellyPlayContract.Features (the
 * wire keys are the cross-repo contract — see the plugin repo's
 * docs/CONTRACT.md; the contractVersion gate protects against drift).
 */
object JellyPlayPluginFeatures {
    const val SettingsSync = "settings-sync"
    const val DeviceProfiles = "device-profiles"
    const val AdminDefaults = "admin-defaults"
    const val ConfigBackup = "config-backup"
    const val Events = "events"
    const val Messages = "messages"
    const val SeerrBridge = "seerr-bridge"
    const val Newsletter = "newsletter"
    const val Ratings = "ratings"
    const val CustomRows = "custom-rows"
    const val SeasonalRows = "seasonal-rows"
    const val AnimeMarkers = "anime-markers"
    const val Recommendations = "recommendations"
    const val UserRatings = "user-ratings"
    const val Bookmarks = "bookmarks"
    const val Transcodes = "transcodes"
    const val Push = "push"
    const val Analytics = "analytics"
}

/**
 * One mdblist rating chip the detail screen's plugin ratings row renders
 * (the model projection of the plugin client's `JellyPlayRatingEntry` wire
 * shape — mapped at the core:network boundary). Scoreless entries render an
 * empty chip and are dropped before they reach the UI state.
 */
data class JellyPlayPluginRating(
    val source: String,
    val score: Double? = null,
    val votes: Int? = null,
    val url: String? = null,
)

/**
 * One episode's TMDB score the detail screen's seasons section renders (the
 * model projection of the plugin client's `JellyPlayEpisodeRatings` wire
 * shape — mapped at the core:network boundary). The season header's average
 * folds over the score-carrying episodes only.
 */
data class JellyPlayEpisodeScore(
    val tmdbScore: Double? = null,
    val tmdbVotes: Int? = null,
)

/**
 * One anime marker the detail screen's seasons section folds into episode
 * badges (the model projection of the plugin client's `JellyPlayAnimeMarker`
 * wire shape — mapped at the core:network boundary). The wire `type` strings
 * are the contract's `filler | mixed | canon | recap`; `canon` (and any
 * forward-compatible unknown type) renders NO badge.
 */
data class JellyPlayEpisodeMarker(
    val type: String,
    val episodeNumber: Int,
    val note: String? = null,
)
