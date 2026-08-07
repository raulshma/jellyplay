package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Versioned envelope for the exported settings backup.
 *
 * **v2 shape** (current): the aggregate `UserPreferences` payload is split into
 * one serializable blob per preference domain ([slices], keyed by
 * [BackupSliceKey]) plus an [extras] block for app-runtime state that no
 * preference domain owns (favorite channels, last live-TV channel, watch-later
 * playlist, onboarding flag, recent DLNA devices). Each slice is the canonical
 * `@Serializable XSlice` owned by its domain store, so export/import no longer
 * round-trips through the decommissioned `UserPreferences` aggregate and a
 * single domain can evolve its slice without touching the others.
 *
 * **Legacy v0/v1** (un-enveloped / single-aggregate) backups are decoded by
 * [LegacySettingsBackup] on import and fanned back to the per-store
 * `restorePreferences(UserPreferences)` path; they cannot be produced anymore.
 *
 * [CURRENT_SCHEMA_VERSION] is bumped only on a breaking change to the backup
 * shape; minor additive slice changes are covered by [PreferencesJson] ignoring
 * unknown keys.
 */
@Serializable
data class SettingsBackup(
    @SerialName("schemaVersion")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("exportedAt")
    val exportedAt: Long = System.currentTimeMillis(),
    @SerialName("slices")
    val slices: Map<String, JsonElement> = emptyMap(),
    @SerialName("extras")
    val extras: AppRuntimeState = AppRuntimeState(),
) {
    companion object {
        /** Schema version stamped on every new export. */
        const val CURRENT_SCHEMA_VERSION = 2

        /**
         * Version stamped on v1 exports — a single enveloped [UserPreferences]
         * aggregate. Decoded by [LegacySettingsBackup] on import.
         */
        const val LEGACY_AGGREGATE_SCHEMA_VERSION = 1

        /**
         * Version reported for the pre-versioning legacy (un-enveloped) format —
         * a bare [UserPreferences] JSON object — so import can still surface it
         * with a clear warning rather than rejecting it.
         */
        const val LEGACY_UNENVELOPED_SCHEMA_VERSION = 0
    }
}

/**
 * Decode-only shape for v0/v1 backups. v1 wraps a single [UserPreferences]
 * aggregate; v0 is a bare [UserPreferences] object (no envelope) and is
 * detected by import before this type is consulted. Retained solely so import
 * can fan legacy fields back to the per-store `restorePreferences` path; do not
 * extend — new preferences belong on a domain slice.
 */
@Serializable
data class LegacySettingsBackup(
    @SerialName("schemaVersion")
    val schemaVersion: Int = SettingsBackup.LEGACY_AGGREGATE_SCHEMA_VERSION,
    @SerialName("exportedAt")
    val exportedAt: Long = 0L,
    @SerialName("preferences")
    val preferences: UserPreferences,
)

/**
 * Stable string keys for the [SettingsBackup.slices] map. Each key names one
 * preference domain and is a wire contract: renaming one breaks v2 backup
 * compatibility. The value is the domain store's canonical `@Serializable`
 * `XSlice`, encoded by export and decoded (and fanned to `store.restore`) by
 * import.
 */
object BackupSliceKey {
    const val PLAYBACK = "playback"
    const val APPEARANCE = "appearance"
    const val VIDEO_PLAYER = "videoPlayer"
    const val DOWNLOADS = "downloads"
    const val PLAYER_ENGINE = "playerEngine"
    const val HOME_DISCOVERY = "homeDiscovery"
    const val AUDIO = "audio"
    const val AUDIO_EFFECTS = "audioEffects"
    const val AUDIO_CACHE = "audioCache"
    const val LIBRARY = "library"
    const val NAVIGATION = "navigation"
    const val NETWORK_OFFLINE = "networkOffline"
    const val NOTIFICATION = "notification"
    const val SCREENSAVER = "screensaver"
    const val SECURITY = "security"
    const val SUBTITLE = "subtitle"
    const val SYNC_PLAY_CAST = "syncPlayCast"
    const val EXPERIMENTAL = "experimental"
}
