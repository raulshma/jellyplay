package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.wallNowMillis
import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
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
 * Legacy v0/v1 (un-enveloped / single-aggregate) backups stopped importing in
 * v0.11 — [BackupParser.parse] rejects them with a clear message.
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
    val exportedAt: Long = wallNowMillis(),
    @SerialName("slices")
    val slices: Map<String, JsonElement> = emptyMap(),
    @SerialName("extras")
    val extras: AppRuntimeState = AppRuntimeState(),
) {
    companion object {
        /** Schema version stamped on every new export. */
        const val CURRENT_SCHEMA_VERSION = 2
    }
}

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
