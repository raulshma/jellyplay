package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.BackupComponentOptions
import com.raulshma.jellyplay.core.model.ServerBackup

/**
 * One backups-list read: the server's archives plus whether it exposes the
 * backup service at all. The service only exists on Jellyfin 10.11+ — an
 * older server answers `GET /Backup` with 404, which the impl folds into
 * `supportsBackups = false` instead of a failure so the screen can hide the
 * feature rather than render an error.
 */
data class AdminBackupsSnapshot(
    val backups: List<ServerBackup> = emptyList(),
    val supportsBackups: Boolean = true,
)

/**
 * AdminRepository facade split (the [PluginAdminRepository] precedent — the
 * [AdminRepositorySurfaceTest] ratchet pins the admin union's member count,
 * so a new admin capability lands as a narrow collaborator): the backups
 * screen is a single-family consumer and injects this seam instead. The
 * members are stateless forwards over [com.raulshma.jellyplay.core.network.api.AdminApiClient]'s
 * raw-path backup calls — this impl owns NO cache or realtime state (the
 * backup service reports nothing live; create returns its manifest
 * synchronously and restore restarts the server).
 */
interface AdminBackupRepository {

    /** The archives list plus the server-version gate, in one read. */
    suspend fun getBackupsSnapshot(): Result<AdminBackupsSnapshot>

    /** Runs a backup with [options]; resolves to the finished archive's manifest. */
    suspend fun createBackup(options: BackupComponentOptions): Result<ServerBackup>

    /**
     * Restores [archiveFileName]. Fire-and-forget: the server answers 204 and
     * restarts immediately — callers drive the poll-and-reconnect ladder.
     */
    suspend fun restoreBackup(archiveFileName: String): Result<Unit>
}
