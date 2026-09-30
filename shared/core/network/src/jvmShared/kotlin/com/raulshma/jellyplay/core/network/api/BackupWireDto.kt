package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.BackupComponentOptions
import com.raulshma.jellyplay.core.model.ServerBackup
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/**
 * Wire DTOs for the Jellyfin backup service (`/Backup` routes — the SDK has
 * no backup API, so the calls ride [ApiClient.request]'s raw-path escape
 * hatch and these classes are the local transcriptions of the OpenAPI
 * schemas). Fields default so a partial manifest still decodes; decoding
 * runs through [JellyfinApiEngine.sharedJson] (ignoreUnknownKeys).
 */

/** `GET /Backup` / `POST /Backup/Create` response item. */
@Serializable
internal data class BackupManifestDto(
    val backupEngineVersion: String? = null,
    val dateCreated: String? = null,
    val options: BackupOptionsDto? = null,
    val path: String? = null,
    val serverVersion: String? = null,
)

/** The four backup components (the create request body, and the manifest's options block). */
@Serializable
internal data class BackupOptionsDto(
    val metadata: Boolean = false,
    val trickplay: Boolean = false,
    val subtitles: Boolean = false,
    val database: Boolean = false,
)

/** `POST /Backup/Restore` request body: the archive to restore. */
@Serializable
internal data class BackupRestoreRequestDto(
    val archiveFileName: String,
)

/**
 * Decodes a `GET /Backup` page or a `POST /Backup/Create` manifest body.
 *
 * The manifest's JSON keys have already drifted across server generations —
 * 10.11 wrote `backupEngineVersion`/`dateCreated`/`options`/`path`/
 * `serverVersion`, 12.x renamed the lot — and a strict decode into the 10.11
 * shape silently null-defaults every field on a renamed server (the "blank
 * backup card" regression: the page decodes, one empty-looking entry renders).
 * Each manifest object is therefore read key-agnostically through the alias
 * lists below, so both generations — and the next rename — still render;
 * when none of an entry's fields resolve it is dropped rather than shown.
 */
internal fun decodeBackupManifests(body: String): List<BackupManifestDto> {
    val root = runCatching { JellyfinApiEngine.sharedJson.parseToJsonElement(body) }.getOrNull()
        ?: return emptyList()
    val entries = when (root) {
        is JsonArray -> root
        // Tolerate a future paged envelope (`{ items: [...] }`).
        is JsonObject -> root["items"] as? JsonArray
        else -> null
    } ?: return emptyList()
    return entries.mapNotNull { entry -> (entry as? JsonObject)?.let(::manifestFromAliases) }
        // An entry where NO alias resolved isn't a manifest we understand —
        // dropping it beats rendering a blank card (the exact regression).
        .filter { manifest ->
            manifest.backupEngineVersion != null || manifest.dateCreated != null ||
                manifest.path != null || manifest.serverVersion != null || manifest.options != null
        }
}

private fun manifestFromAliases(obj: JsonObject): BackupManifestDto {
    val manifest = BackupManifestDto(
        backupEngineVersion = obj.firstText("backupEngineVersion", "engineVersion", "pluginVersion"),
        dateCreated = obj.firstText("dateCreated", "createdUtc", "createdAt", "creationTime", "created", "timestamp"),
        options = obj.firstOptions(),
        path = obj.firstText("path", "archiveFileName", "fileName", "file", "archivePath", "filePath", "name"),
        serverVersion = obj.firstText("serverVersion", "jellyfinVersion", "server_version"),
    )
    return manifest
}

/**
 * Case-insensitive key lookup: the 12.x server answers with PascalCase
 * (`DateCreated`/`Path`/`Options`) where 10.11 wrote camelCase — the alias
 * lists above are matched against a lower-cased key map so both render.
 */
private fun JsonObject.field(vararg names: String): JsonElement? {
    val byLowerCase = entries.associate { (key, value) -> key.lowercase() to value }
    return names.firstNotNullOfOrNull { name -> byLowerCase[name.lowercase()] }
}

private fun JsonObject.firstOptions(): BackupOptionsDto? {
    val block = field("options", "backupOptions", "components") ?: return null
    return when (block) {
        is JsonObject -> BackupOptionsDto(
            metadata = block.firstBool("metadata", "includeMetadata") ?: false,
            trickplay = block.firstBool("trickplay", "includeTrickplay") ?: false,
            subtitles = block.firstBool("subtitles", "includeSubtitles") ?: false,
            database = block.firstBool("database", "includeDatabase") ?: false,
        )
        // A 12.x-style component list (`["Metadata","Database"]`) maps by name.
        is JsonArray -> {
            val names = block.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.lowercase() }.toSet()
            BackupOptionsDto(
                metadata = "metadata" in names,
                trickplay = "trickplay" in names,
                subtitles = "subtitles" in names,
                database = "database" in names,
            )
        }
        else -> null
    }
}

private fun JsonObject.firstText(vararg keys: String): String? =
    keys.firstNotNullOfOrNull { key ->
        (field(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.contentOrNull?.takeIf(String::isNotBlank)
    }

private fun JsonObject.firstBool(vararg keys: String): Boolean? =
    keys.firstNotNullOfOrNull { key ->
        (field(key) as? JsonPrimitive)?.takeIf { it !is JsonNull }?.booleanOrNull
    }

/** Maps the wire manifest onto the model the repository seam exposes. */
internal fun BackupManifestDto.toServerBackup(): ServerBackup = ServerBackup(
    backupEngineVersion = backupEngineVersion ?: "",
    dateCreated = dateCreated ?: "",
    options = options?.toComponentOptions() ?: BackupComponentOptions(),
    path = path ?: "",
    serverVersion = serverVersion ?: "",
)

private fun BackupOptionsDto.toComponentOptions(): BackupComponentOptions = BackupComponentOptions(
    metadata = metadata,
    trickplay = trickplay,
    subtitles = subtitles,
    database = database,
)
