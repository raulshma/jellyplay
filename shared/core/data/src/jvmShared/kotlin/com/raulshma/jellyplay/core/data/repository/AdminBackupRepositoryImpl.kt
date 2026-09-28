package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.BackupComponentOptions
import com.raulshma.jellyplay.core.model.ServerBackup
import com.raulshma.jellyplay.core.network.api.AdminApiClient
import com.raulshma.jellyplay.core.network.api.ApiException

class AdminBackupRepositoryImpl(
    private val adminApiClient: AdminApiClient,
) : AdminBackupRepository {

    override suspend fun getBackupsSnapshot(): Result<AdminBackupsSnapshot> =
        adminApiClient.listBackups().fold(
            onSuccess = { backups -> Result.success(AdminBackupsSnapshot(backups = backups)) },
            onFailure = { e ->
                // The server-version gate: no backup service (Jellyfin < 10.11)
                // is "feature hidden", never an error surface.
                if (e is ApiException && e.httpCode == BACKUP_SERVICE_MISSING_CODE) {
                    Result.success(AdminBackupsSnapshot(backups = emptyList(), supportsBackups = false))
                } else {
                    Result.failure(e)
                }
            },
        )

    override suspend fun createBackup(options: BackupComponentOptions): Result<ServerBackup> =
        adminApiClient.createBackup(options)

    override suspend fun restoreBackup(archiveFileName: String): Result<Unit> =
        adminApiClient.restoreBackup(archiveFileName)

    private companion object {
        /** `GET /Backup` on servers without the backup service. */
        const val BACKUP_SERVICE_MISSING_CODE = 404
    }
}
