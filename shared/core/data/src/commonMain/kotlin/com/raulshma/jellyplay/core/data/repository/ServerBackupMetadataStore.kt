package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.database.JellyPlayDatabase
import com.raulshma.jellyplay.core.database.dao.ServerDao
import com.raulshma.jellyplay.core.database.dao.UserDao
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import com.raulshma.jellyplay.core.model.wallNowMillis
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

/**
 * One saved Jellyfin server row, shaped for the settings-backup secrets block:
 * connection metadata plus the per-server user NAMES — and deliberately
 * NOTHING else. [ServerEntity.accessToken]/`userId` and
 * `UserEntity.accessToken` are never read here: a settings backup must never
 * carry session material, so this type has no field a token could hide in.
 */
data class ServerBackupMetadata(
    val id: String,
    val name: String,
    val address: String,
    val alternateAddresses: List<String> = emptyList(),
    val userNames: List<String> = emptyList(),
)

/**
 * The settings-backup server-list seam — the "narrow collaborator" the
 * [AuthRepositorySurfaceTest] ratchet asks for instead of growing
 * [AuthRepository]: exactly the two operations the LOCAL FILE backup's
 * secrets block needs over the `servers`/`users` Room tables, with no path to
 * a token.
 *
 *  - [gatherServers] reads every server row plus its user names (both tables'
 *   token columns are never touched — gathering cannot leak a token even by
 *   accident).
 *  - [restoreServerMetadata] upserts server rows WITHOUT session state:
 *   inserted rows carry `userId = null`/`accessToken = null` (the user signs
 *   in again; no `UserEntity` row is created — a user without a token is
 *   unrepresentable), and an existing row keeps its stored session while its
 *   name/address/alternates adopt the backup's values.
 *
 * Bound as a Koin single next to [AuthRepositoryImpl] (dataRepositoriesModule).
 */
class ServerBackupMetadataStore(
    private val database: JellyPlayDatabase,
    private val serverDao: ServerDao,
    private val userDao: UserDao,
    private val json: Json,
) {

    /**
     * Every saved server with its per-server user names, most-recently-used
     * first (the DAO's `lastConnected DESC` order). Empty when the device has
     * never connected.
     */
    suspend fun gatherServers(): List<ServerBackupMetadata> =
        serverDao.getAllServers().first().map { server ->
            ServerBackupMetadata(
                id = server.id,
                name = server.name,
                address = server.address,
                alternateAddresses = decodeAlternateAddresses(server.alternateAddresses),
                userNames = userDao.getUsersForServerOnce(server.id).map { it.name },
            )
        }

    /**
     * Merges backup [servers] into the local table without session material.
     *
     * Per entry:
     *  - blank id or address → skipped (a row without either is unrenderable).
     *  - another server row already owns [ServerBackupMetadata.address] →
     *    skipped: the `servers` table's unique address index would turn the
     *    insert into a REPLACE that silently deletes that other row.
     *  - new id → inserted with `accessToken = null` (counted in the result).
     *  - existing id → updated in place; name/address adopt the backup (blank
     *    incoming values keep the local ones), alternates adopt when the
     *    backup carries any, `userId`/`accessToken`/`lastConnected` are
     *    preserved so a signed-in server stays signed in.
     *
     * @return how many rows were INSERTED (the "restored N servers" count the
     *   import preview reports — updates are reconciliations, not additions).
     */
    suspend fun restoreServerMetadata(servers: List<ServerBackupMetadata>): Int {
        var inserted = 0
        database.withTransaction {
            for (server in servers) {
                if (server.id.isBlank() || server.address.isBlank()) continue
                val addressOwner = serverDao.getServerByAddress(server.address)
                if (addressOwner != null && addressOwner.id != server.id) continue
                val alternates = server.alternateAddresses.filter { it.isNotBlank() }.distinct()
                val existing = serverDao.getServerById(server.id)
                when {
                    existing == null -> {
                        serverDao.insertServer(
                            ServerEntity(
                                id = server.id,
                                name = server.name,
                                address = server.address,
                                // No session material: the restore path never
                                // carries tokens, so the row signs in fresh.
                                userId = null,
                                accessToken = null,
                                alternateAddresses = encodeAlternateAddresses(alternates),
                                lastConnected = wallNowMillis(),
                            ),
                        )
                        inserted++
                    }

                    else -> serverDao.updateServer(
                        existing.copy(
                            name = server.name.ifBlank { existing.name },
                            address = server.address,
                            alternateAddresses = if (alternates.isEmpty()) {
                                existing.alternateAddresses
                            } else {
                                encodeAlternateAddresses(alternates)
                            },
                        ),
                    )
                }
            }
        }
        return inserted
    }

    /**
     * The `alternate_addresses` JSON column's decode — same column encoding
     * `AuthRepositoryImpl` writes (`JSON array`, null/absent → empty). Local
     * copy rather than a shared mapper: the column contract stays one line and
     * this seam must not grow an AuthRepositoryImpl dependency.
     */
    private fun decodeAlternateAddresses(raw: String?): List<String> = raw?.let { column ->
        try {
            json.decodeFromString<List<String>>(column)
        } catch (_: Exception) {
            emptyList()
        }
    } ?: emptyList()

    /** Matches the DAO-family encoding: empty list → NULL column (see `removeServerAddress`). */
    private fun encodeAlternateAddresses(alternates: List<String>): String? =
        if (alternates.isEmpty()) null else json.encodeToString(alternates)
}
