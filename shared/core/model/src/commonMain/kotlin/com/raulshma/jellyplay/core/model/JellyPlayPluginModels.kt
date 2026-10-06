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
