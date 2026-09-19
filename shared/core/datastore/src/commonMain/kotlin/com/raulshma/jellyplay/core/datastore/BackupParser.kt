package com.raulshma.jellyplay.core.datastore

import com.raulshma.jellyplay.core.datastore.runtime.AppRuntimeState
import com.raulshma.jellyplay.core.datastore.security.SecuritySlice
import com.raulshma.jellyplay.core.datastore.security.hasSecuritySensitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Pure parser for the v2 per-slice backup shape plus future (> CURRENT)
 * forward-compat. Keeps JSON version-sniffing and `hasSecuritySensitive`
 * detection out of ViewModels so the import preview VM stays thin
 * (deep-module principle, cf. `CONTEXT.md` HomeRefresher).
 *
 * Legacy v0/v1 backups (unenveloped / single-aggregate) stopped importing in
 * v0.11: [parse] rejects them with a clear message instead of fanning them
 * through the removed `UserPreferences` restore ladder.
 */
object BackupParser {

    sealed interface Parsed {
        data class V2(
            val backup: SettingsBackup,
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
            peekVersion != null && peekVersion > SettingsBackup.CURRENT_SCHEMA_VERSION -> {
                val backup = json.decodeFromString(SettingsBackup.serializer(), jsonString)
                Parsed.Future(backup, backup.hasSecuritySensitive(json))
            }
            else -> throw IllegalArgumentException(
                "Unsupported backup schemaVersion=$peekVersion — v0/v1 legacy imports were removed in v0.11",
            )
        }
    }

    private fun SettingsBackup.hasSecuritySensitive(json: Json): Boolean =
        slices[BackupSliceKey.SECURITY]?.let { el ->
            runCatching { json.decodeFromJsonElement(SecuritySlice.serializer(), el) }.getOrNull()?.hasSecuritySensitive()
        } ?: false

    fun Parsed.toAppRuntimeState(): AppRuntimeState = when (this) {
        is Parsed.V2 -> backup.extras
        is Parsed.Future -> backup.extras
    }

    fun Parsed.schemaVersion(): Int = when (this) {
        is Parsed.V2 -> backup.schemaVersion
        is Parsed.Future -> backup.schemaVersion
    }

    fun Parsed.isFuture(): Boolean = this is Parsed.Future
}
