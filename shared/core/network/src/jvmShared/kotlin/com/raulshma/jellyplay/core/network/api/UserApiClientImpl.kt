package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption
import org.jellyfin.sdk.model.api.CreateUserByName
import org.jellyfin.sdk.model.api.UpdateUserPassword
import org.jellyfin.sdk.api.client.extensions.*
import org.jellyfin.sdk.model.serializer.toUUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserApiClientImpl @Inject constructor(
    private val engine: JellyfinApiEngine,
) : UserApiClient {

    override suspend fun getManagedUsers(): Result<List<ManagedUser>> = engine.apiResultWithRetry {
        val users = engine.requireApi().userApi.getUsers().content ?: emptyList()
        users.map { it.toManagedUser() }
    }

    override suspend fun getManagedUser(userId: String): Result<ManagedUser> = engine.apiResultWithRetry {
        engine.requireApi().userApi.getUserById(userId.toUUID()).content.toManagedUser()
    }

    override suspend fun getCurrentUserId(): Result<String> = engine.apiResultWithRetry {
        engine.requireApi().userApi.getCurrentUser().content.id.toString()
    }

    override suspend fun getCurrentUser(): Result<ManagedUser> = engine.apiResultWithRetry {
        engine.requireApi().userApi.getCurrentUser().content.toManagedUser()
    }

    override suspend fun createUser(name: String, password: String?): Result<ManagedUser> = engine.apiResultWithRetry {
        engine.requireApi().userApi.createUserByName(
            CreateUserByName(name = name, password = password),
        ).content.toManagedUser()
    }

    override suspend fun renameUser(userId: String, newName: String): Result<ManagedUser> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        // Fetch the full DTO, copy the name, POST it back. Never construct a
        // partial UserDto — id is non-null with no default and dropping
        // policy/configuration would clear them server-side.
        val current = api.userApi.getUserById(userId.toUUID()).content
        val renamed = current.copy(name = newName)
        api.userApi.updateUser(userId = userId.toUUID(), data = renamed)
        api.userApi.getUserById(userId.toUUID()).content.toManagedUser()
    }

    override suspend fun updateUserPolicy(
        userId: String,
        policy: ManagedUserPolicy,
    ): Result<Unit> = engine.apiResultWithRetry {
        val api = engine.requireApi()
        // Rehydrate the full server policy, overlay the edited fields,
        // POST the merged object. Preserves all bookkeeping fields.
        val current = api.userApi.getUserById(userId.toUUID()).content
        val serverPolicy = current.policy ?: org.jellyfin.sdk.model.api.UserPolicy(
            isAdministrator = false,
            isHidden = false,
            isDisabled = false,
            enableUserPreferenceAccess = false,
            enableRemoteControlOfOtherUsers = false,
            enableSharedDeviceControl = false,
            enableRemoteAccess = false,
            enableLiveTvManagement = false,
            enableLiveTvAccess = false,
            enableMediaPlayback = false,
            enableAudioPlaybackTranscoding = false,
            enableVideoPlaybackTranscoding = false,
            enablePlaybackRemuxing = false,
            forceRemoteSourceTranscoding = false,
            enableContentDeletion = false,
            enableContentDownloading = false,
            enableSyncTranscoding = false,
            enableMediaConversion = false,
            enableAllDevices = false,
            enableAllChannels = false,
            enableAllFolders = false,
            invalidLoginAttemptCount = 0,
            loginAttemptsBeforeLockout = -1,
            maxActiveSessions = 0,
            enablePublicSharing = false,
            remoteClientBitrateLimit = 0,
            authenticationProviderId = "",
            passwordResetProviderId = "",
            syncPlayAccess = org.jellyfin.sdk.model.api.SyncPlayUserAccessType.NONE,
        )
        val merged = serverPolicy.overlayWith(policy, userId)
        api.userApi.updateUserPolicy(userId.toUUID(), merged)
    }

    override suspend fun updateUserPassword(userId: String, newPassword: String?): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().userApi.updateUserPassword(
            userId = userId.toUUID(),
            data = UpdateUserPassword(
                currentPw = null,
                newPw = newPassword,
                resetPassword = newPassword == null,
            ),
        )
    }

    override suspend fun deleteUser(userId: String): Result<Unit> = engine.apiResultWithRetry {
        engine.requireApi().userApi.deleteUser(userId.toUUID())
    }

    override suspend fun getLibraryFoldersForEditor(): Result<List<LibraryFolder>> = engine.apiResultWithRetry {
        val response = engine.requireApi().libraryApi.getMediaFolders().content
            ?: throw IllegalStateException("Server returned empty response")
        (response.items ?: emptyList()).map { item ->
            LibraryFolder(
                id = item.id.toString(),
                name = item.name ?: "",
                collectionType = item.collectionType?.serialName,
                type = item.type?.serialName,
            )
        }
        // NOTE: deliberately NOT filtered by engine.currentUser.enabledFolderIds —
        // the editor needs the full server folder list.
    }

    override suspend fun getParentalRatings(): Result<List<ParentalRatingOption>> = engine.apiResultWithRetry {
        val ratings = engine.requireApi().localizationApi.getParentalRatings().content
        // Group by (score, subScore), concatenating names on collision so ratings
        // sharing a score/subScore collapse into one label (web parity, e.g. "PG-13/TV-14").
        ratings
            .filter { it.ratingScore != null }
            .groupBy { it.ratingScore!!.score to it.ratingScore!!.subScore }
            .map { (key, group) ->
                ParentalRatingOption(
                    name = group.joinToString("/") { it.name },
                    score = key.first,
                    subScore = key.second,
                )
            }
            .sortedBy { it.score }
    }
}
