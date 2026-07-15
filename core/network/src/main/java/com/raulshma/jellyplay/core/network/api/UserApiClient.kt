package com.raulshma.jellyplay.core.network.api

import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption

interface UserApiClient {
    suspend fun getManagedUsers(): Result<List<ManagedUser>>
    suspend fun getManagedUser(userId: String): Result<ManagedUser>
    suspend fun getCurrentUserId(): Result<String>

    /**
     * Fetches the *current* authenticated user with their full
     * [ManagedUserPolicy]. Used to re-validate admin status against the
     * server rather than trusting the value cached at login — so a
     * server-side demotion is reflected without requiring re-login.
     */
    suspend fun getCurrentUser(): Result<ManagedUser>
    suspend fun createUser(name: String, password: String?): Result<ManagedUser>
    suspend fun renameUser(userId: String, newName: String): Result<ManagedUser>
    suspend fun updateUserPolicy(userId: String, policy: ManagedUserPolicy): Result<Unit>
    suspend fun updateUserPassword(userId: String, newPassword: String?): Result<Unit>
    suspend fun deleteUser(userId: String): Result<Unit>
    suspend fun getLibraryFoldersForEditor(): Result<List<LibraryFolder>>

    /**
     * Server parental-rating options, grouped by score+subScore (web parity:
     * ratings sharing the same (score, subScore) collapse into one label,
     * e.g. "PG-13/TV-14"). The "No limit" entry is not included here — the
     * UI synthesizes it client-side from a null score.
     */
    suspend fun getParentalRatings(): Result<List<ParentalRatingOption>>
}
