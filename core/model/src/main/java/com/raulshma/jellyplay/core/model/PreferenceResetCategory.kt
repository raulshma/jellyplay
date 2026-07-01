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
 */
@Immutable
@Serializable
enum class PreferenceResetCategory(val key: String) {
    APPEARANCE("appearance"),
    PLAYBACK("playback"),
    AUDIO("audio"),
    SECURITY("security"),
    NOTIFICATIONS("notifications"),
}
