package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.hasSecuritySensitive
import com.raulshma.jellyplay.core.model.legacy.UserPreferences
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pure parser for the three backup shapes (v2 per-slice, v1 aggregate,
 * v0 bare) plus future (> CURRENT) forward-compat. Keeps JSON version-sniffing
 * and `hasSecuritySensitive` detection out of ViewModels so the import preview
 * VM stays thin (deep-module principle, cf. `CONTEXT.md` HomeRefresher).
 */
object BackupParser {

    sealed interface Parsed {
        data class V2(
            val backup: SettingsBackup,
            val hasSecuritySensitive: Boolean,
        ) : Parsed

        data class V1(
            val preferences: UserPreferences,
            val hasSecuritySensitive: Boolean,
        ) : Parsed

        data class V0(
            val preferences: UserPreferences,
            val hasSecuritySensitive: Boolean,
        ) : Parsed

        data class Future(
            val backup: SettingsBackup,
            val hasSecuritySensitive: Boolean,
        ) : Parsed
    }

    fun parse(jsonString: String): Parsed {
        val json = PreferencesJson.import
        val root = json.parseToJsonElement(jsonString) as? JsonObject
        val peekVersion = root?.let { it["schemaVersion"] as? JsonPrimitive }?.content?.toIntOrNull()

        return when {
            peekVersion == SettingsBackup.CURRENT_SCHEMA_VERSION -> {
                val backup = json.decodeFromString(SettingsBackup.serializer(), jsonString)
                Parsed.V2(backup, backup.hasSecuritySensitive(json))
            }
            peekVersion == SettingsBackup.LEGACY_AGGREGATE_SCHEMA_VERSION -> {
                val legacy = json.decodeFromString(LegacySettingsBackup.serializer(), jsonString)
                Parsed.V1(legacy.preferences, legacy.preferences.hasSecuritySensitive())
            }
            peekVersion == null -> {
                val bare = json.decodeFromString(UserPreferences.serializer(), jsonString)
                Parsed.V0(bare, bare.hasSecuritySensitive())
            }
            peekVersion > SettingsBackup.CURRENT_SCHEMA_VERSION -> {
                val backup = json.decodeFromString(SettingsBackup.serializer(), jsonString)
                Parsed.Future(backup, backup.hasSecuritySensitive(json))
            }
            else -> {
                // Explicit 0 (LEGACY_UNENVELOPED) or any other unknown old version.
                // Treat 0 as V0 for compat, otherwise fail fast with a clear
                // message instead of silently trying a bare decode that will
                // throw a confusing `MissingFieldException`.
                if (peekVersion == SettingsBackup.LEGACY_UNENVELOPED_SCHEMA_VERSION) {
                    val bare = json.decodeFromString(UserPreferences.serializer(), jsonString)
                    Parsed.V0(bare, bare.hasSecuritySensitive())
                } else {
                    throw IllegalArgumentException("Unknown backup schemaVersion=$peekVersion")
                }
            }
        }
    }

    private fun SettingsBackup.hasSecuritySensitive(json: kotlinx.serialization.json.Json): Boolean =
        slices[BackupSliceKey.SECURITY]?.let { el ->
            runCatching { json.decodeFromJsonElement(SecuritySlice.serializer(), el) }.getOrNull()?.hasSecuritySensitive()
        } ?: false

    private fun AppRuntimeState.hasSecuritySensitive(): Boolean = false // extras never carry lock

    fun Parsed.toAppRuntimeState(): AppRuntimeState = when (this) {
        is Parsed.V2 -> backup.extras
        is Parsed.Future -> backup.extras
        is Parsed.V1 -> AppRuntimeState(
            favoriteChannels = preferences.favoriteChannels,
            watchLaterPlaylistId = preferences.watchLaterPlaylistId,
            onboardingCompleted = preferences.onboardingCompleted,
        )
        is Parsed.V0 -> AppRuntimeState(
            favoriteChannels = preferences.favoriteChannels,
            watchLaterPlaylistId = preferences.watchLaterPlaylistId,
            onboardingCompleted = preferences.onboardingCompleted,
        )
    }

    fun Parsed.schemaVersion(): Int = when (this) {
        is Parsed.V2 -> backup.schemaVersion
        is Parsed.Future -> backup.schemaVersion
        is Parsed.V1 -> SettingsBackup.LEGACY_AGGREGATE_SCHEMA_VERSION
        is Parsed.V0 -> SettingsBackup.LEGACY_UNENVELOPED_SCHEMA_VERSION
    }

    fun Parsed.isLegacy(): Boolean = this is Parsed.V1 || this is Parsed.V0
    fun Parsed.isFuture(): Boolean = this is Parsed.Future
}
