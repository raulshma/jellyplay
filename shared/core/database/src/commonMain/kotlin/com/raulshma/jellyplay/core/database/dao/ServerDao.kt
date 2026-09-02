package com.raulshma.jellyplay.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.raulshma.jellyplay.core.database.entity.ServerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerDao {

    @Query("SELECT * FROM servers ORDER BY lastConnected DESC")
    fun getAllServers(): Flow<List<ServerEntity>>

    @Query("SELECT * FROM servers WHERE id = :id")
    suspend fun getServerById(id: String): ServerEntity?

    @Query("SELECT * FROM servers WHERE address = :address LIMIT 1")
    suspend fun getServerByAddress(address: String): ServerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertServer(server: ServerEntity)

    @Update
    suspend fun updateServer(server: ServerEntity)

    @Delete
    suspend fun deleteServer(server: ServerEntity)

    @Query("DELETE FROM servers WHERE id = :id")
    suspend fun deleteServerById(id: String)

    /**
     * Clears the user binding (userId + accessToken) from every server row
     * currently bound to [userId]. Single bulk UPDATE — replaces the per-row
     * read-modify-write loop in `AuthRepositoryImpl.removeUser` (N+1 write).
     * No schema change, no migration.
     */
    @Query("UPDATE servers SET userId = NULL, accessToken = NULL WHERE userId = :userId")
    suspend fun clearUserFromServers(userId: String)
}
