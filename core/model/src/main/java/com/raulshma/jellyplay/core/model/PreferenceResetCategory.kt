package com.raulshma.jellyplay.core.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

/**
 * Identifies a logical category of user preferences that can be reset as a group via
 * `UserPreferencesStore.resetCategory`. Replaces the previous magic-string contract
 * (`resetCategory("appearance")`, `resetCategory("notifications")`, …) which was brittle to
 * typos and led to an incomplete notifications-reset list slipping through.
 *
 * The enum is `@Serializable` so it round-trips through DataStore backup/restore payloads.
 *
 * The union of every category's key list in `UserPreferencesStore.resetCategory` must cover
 * every user-tunable preference key (see the `uncoveredResetKeys` coverage guard there).
 * Runtime / per-item state (PIN rate-limit counters, recall slots, migration flags) is excluded.
 */
@Immutable
@Serializable
enum class PreferenceResetCategory(val key: String) {
    APPEARANCE("appearance"),
    PLAYBACK("playback"),
    AUDIO("audio"),
    SUBTITLES_LANGUAGE("subtitles_language"),
    DOWNLOADS_NETWORK("downloads_network"),
    HOME_DISCOVERY("home_discovery"),
    AUDIO_CACHE("audio_cache"),
    SECURITY("security"),
    NOTIFICATIONS("notifications"),
    SCREENSAVER("screensaver"),
    NEWSLETTER("newsletter"),
    SYNCPLAY_CASTING("syncplay_casting"),
    PLAYER_ENGINES("player_engines"),
    EXPERIMENTAL("experimental"),
    MISC_APP("misc_app"),
}
