package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy

interface UserApiClient {
    suspend fun getManagedUsers(): Result<List<ManagedUser>>
    suspend fun getManagedUser(userId: String): Result<ManagedUser>
    suspend fun getCurrentUserId(): Result<String>
    suspend fun createUser(name: String, password: String?): Result<ManagedUser>
    suspend fun renameUser(userId: String, newName: String): Result<ManagedUser>
    suspend fun updateUserPolicy(userId: String, policy: ManagedUserPolicy): Result<Unit>
    suspend fun updateUserPassword(userId: String, newPassword: String?): Result<Unit>
    suspend fun deleteUser(userId: String): Result<Unit>
    suspend fun getLibraryFoldersForEditor(): Result<List<LibraryFolder>>
}
