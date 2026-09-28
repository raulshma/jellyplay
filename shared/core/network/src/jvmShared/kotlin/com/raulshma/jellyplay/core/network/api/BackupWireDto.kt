package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.BackupComponentOptions
import com.raulshma.jellyplay.core.model.ServerBackup
import kotlinx.serialization.Serializable

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
