package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.model.UserPreferences
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Versioned envelope for the exported settings backup.
 *
 * Export wraps the raw [UserPreferences] payload in [SettingsBackup] so that
 * import can detect format drift and reject down-level backups instead of
 * silently overwriting incompatible fields. [SCHEMA_VERSION] is bumped only
 * on a breaking change to the preference shape; minor additive changes are
 * covered by [PreferencesJson] ignoring unknown keys.
 *
 * The import path keeps backwards compatibility with the legacy un-enveloped
 * format (a bare [UserPreferences] JSON object) — those exports are tagged
 * with [LEGACY_SCHEMA_VERSION] so the UI can warn that the backup predates
 * schema versioning.
 */
@Serializable
data class SettingsBackup(
    @SerialName("schemaVersion")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("exportedAt")
    val exportedAt: Long = System.currentTimeMillis(),
    @SerialName("preferences")
    val preferences: UserPreferences,
) {
    companion object {
        /** Schema version stamped on every new export. */
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * Version reported for legacy (un-enveloped) exports so import can
         * still surface them with a clear warning rather than rejecting them.
         */
        const val LEGACY_SCHEMA_VERSION = 0
    }
}
