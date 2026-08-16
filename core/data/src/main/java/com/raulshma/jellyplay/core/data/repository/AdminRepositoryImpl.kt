package com.raulshma.jellyplay.core.data.repository

import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption
import com.raulshma.jellyplay.core.model.SystemInfo
import com.raulshma.jellyplay.core.model.UserEditorContext
import com.raulshma.jellyplay.core.model.UsersOverview
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : AdminRepository {

    override suspend fun getSystemInfo(): Result<SystemInfo> = apiClient.getSystemInfo()

    override suspend fun getUsersOverview(): Result<UsersOverview> {
        val usersResult = apiClient.getManagedUsers()
        val meResult = apiClient.getCurrentUserId()
        return usersResult.map { users ->
            UsersOverview(
                users = users,
                currentUserId = meResult.getOrNull(),
                adminCount = users.activeAdminCount(),
            )
        }
    }

    override suspend fun createUser(name: String, password: String?): Result<ManagedUser> =
        apiClient.createUser(name, password)

    override suspend fun deleteUser(userId: String): Result<Unit> =
        apiClient.deleteUser(userId)

    override suspend fun getUserEditorContext(userId: String): Result<UserEditorContext> {
        val userResult = apiClient.getManagedUser(userId)
        val libsResult = apiClient.getLibraryFoldersForEditor()
        val meResult = apiClient.getCurrentUserId()
        val allUsersResult = apiClient.getManagedUsers()
        return userResult.map { user ->
            UserEditorContext(
                user = user,
                libraries = libsResult.getOrNull().orEmpty(),
                currentUserId = meResult.getOrNull(),
                adminCount = allUsersResult.getOrNull().orEmpty().activeAdminCount(),
            )
        }
    }

    override suspend fun getManagedUser(userId: String): Result<ManagedUser> =
        apiClient.getManagedUser(userId)

    override suspend fun renameUser(userId: String, newName: String): Result<ManagedUser> =
        apiClient.renameUser(userId, newName)

    override suspend fun updateUserPolicy(userId: String, policy: ManagedUserPolicy): Result<Unit> =
        apiClient.updateUserPolicy(userId, policy)

    override suspend fun updateUserPassword(userId: String, newPassword: String?): Result<Unit> =
        apiClient.updateUserPassword(userId, newPassword)

    override suspend fun getDevices(): Result<List<DeviceInfo>> =
        apiClient.getDevices()

    override suspend fun getLiveTvChannels(limit: Int): Result<List<LiveTvChannel>> =
        apiClient.getLiveTvChannels(limit = limit)

    override suspend fun getParentalRatings(): Result<List<ParentalRatingOption>> =
        apiClient.getParentalRatings()

    override suspend fun getTags(limit: Int): Result<List<String>> =
        apiClient.getTags(limit = limit)

    private fun List<ManagedUser>.activeAdminCount(): Int =
        count { it.policy.isAdministrator && !it.policy.isDisabled }
}
