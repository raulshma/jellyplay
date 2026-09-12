package com.raulshma.jellyplay.core.database.dao

import androidx.room3.Dao
import androidx.room3.Delete
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import androidx.room3.Update
import com.raulshma.jellyplay.core.database.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE serverId = :serverId ORDER BY lastConnected DESC")
    fun getUsersForServer(serverId: String): Flow<List<UserEntity>>

    @Query("SELECT * FROM users WHERE serverId = :serverId ORDER BY lastConnected DESC")
    suspend fun getUsersForServerOnce(serverId: String): List<UserEntity>

    @Query("SELECT * FROM users WHERE serverId = :serverId ORDER BY lastConnected DESC LIMIT 1")
    suspend fun getMostRecentUserForServer(serverId: String): UserEntity?

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: String): UserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Update
    suspend fun updateUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUserById(userId: String)

    @Query("DELETE FROM users WHERE serverId = :serverId")
    suspend fun deleteUsersForServer(serverId: String)
}
