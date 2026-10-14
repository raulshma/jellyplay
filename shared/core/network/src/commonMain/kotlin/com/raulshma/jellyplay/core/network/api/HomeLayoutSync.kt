package com.raulshma.jellyplay.core.network.api

/**
 * The home-layout slice read back from the server's DisplayPreferences store
 * (H20 — the wrapper pair
 * [LibraryApiClient.readHomeLayoutDisplayPreferences] /
 * [LibraryApiClient.writeHomeLayoutDisplayPreferences]). Every field is
 * nullable: a user who never synced has none of the keys, and a partially
 * written store (older client) decodes to partial state — the caller's
 * lenient-decode contract treats missing as absent, never as a failure.
 */
data class HomeLayoutServerState(
    /** The stored home-layout JSON payload ([HomeLayoutPreset]-shaped), or null when never synced. */
    val json: String?,
    /** The schema version written alongside the payload, or null when the writer predated versioning. */
    val schemaVersion: Int?,
    /** The write-time epoch millis the writing client stamped, or null when absent. Client-clock sourced (the DisplayPreferences DTO carries no server timestamp) — good enough for the manual "server copy is newer" dialog, not a clock-sync guarantee. */
    val savedAtEpochMillis: Long?,
)

/**
 * The wire vocabulary of the home-layout DisplayPreferences slice (H20).
 *
 * Transport choice: the SDK's `DisplayPreferencesApi` stores a
 * [org.jellyfin.sdk.model.api.DisplayPreferencesDto] keyed by
 * (displayPreferencesId, user, client) — the [HOME_LAYOUT_CLIENT] value
 * namespaces the store to JellyPlay alone, so our custom-pref keys can never
 * collide with jellyfin-web's (which ride the "emby" client) even though
 * jellyfin-web keeps its own home-section settings under the same
 * `usersettings` display-preferences id. The payload itself travels in the
 * DTO's `CustomPrefs` string→string map under the three keys below; the
 * official DTO's key/value custom prefs ARE the SDK's extension point for
 * exactly this kind of client-private blob.
 */
internal const val HOME_LAYOUT_DISPLAY_PREFERENCES_ID = "usersettings"
internal const val HOME_LAYOUT_CLIENT = "jellyplay"
internal const val HOME_LAYOUT_JSON_KEY = "jellyplay.homeLayout"
internal const val HOME_LAYOUT_SCHEMA_VERSION_KEY = "jellyplay.homeLayout.schemaVersion"
internal const val HOME_LAYOUT_SAVED_AT_KEY = "jellyplay.homeLayout.savedAt"
