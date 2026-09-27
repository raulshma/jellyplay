package com.raulshma.jellyplay.feature.admin.users

import com.raulshma.jellyplay.core.data.error.UserErrorMessages
import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.ui.viewmodel.ConfirmationHost
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto

data class UsersState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val users: List<ManagedUser> = emptyList(),
    val isRefreshing: Boolean = false,
    val currentUserId: String? = null,
    val adminCount: Int = 0,
    val showCreateDialog: Boolean = false,
    /** True while a delete request is in flight — also the dialog's confirmLoading and the delete-confirmation host's in-flight fact. */
    val isDeleting: Boolean = false,
)

/** Decode cap for the 40 dp row avatar (128 px covers ~3.2x density). */
internal const val AVATAR_MAX_WIDTH = 128

class UsersViewModel(
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _state = composeState(UsersState())
    val state: UsersState get() = _state.value

    /**
     * Delete-confirmation host (replaces the old showDeleteDialog +
     * selectedUser state pair). Settle arm: clears on SUCCESS only — a
     * failed delete keeps the dialog open over the error. Guard source: the
     * host's dismiss/confirm rule; [UsersState.isDeleting] is the
     * caller-owned in-flight fact it reads.
     */
    val deleteConfirmation = ConfirmationHost<ManagedUser>()

    /**
     * Avatar URL for a list row: the repository's bearer-less
     * `/Users/{id}/Images/Primary` builder, or null (initials fallback) when
     * no server is active or the id is not a GUID.
     */
    fun avatarUrl(user: ManagedUser): String? =
        adminRepository.getUserImageUrl(user.id, user.primaryImageTag, maxWidth = AVATAR_MAX_WIDTH)
            .ifEmpty { null }

    init {
        loadUsers()
    }

    fun refresh() {
        launch {
            _state.value = _state.value.copy(isRefreshing = true)
            loadUsers(refreshing = true)
        }
    }

    fun loadUsers() {
        launch { loadUsers(refreshing = false) }
    }

    private suspend fun loadUsers(refreshing: Boolean) {
        // Access control is enforced by AdminRouteContainer before this screen
        // is reached; the server still 403s as a backstop if state is stale.
        // Flavour start (see loadInto): the cold load raises the pair, a
        // refresh raises nothing here (its isRefreshing was already raised by
        // the caller, and a shown error is deliberately kept).
        loadInto(
            start = {
                if (!refreshing) {
                    _state.value = _state.value.copy(isLoading = true, error = null)
                }
            },
            fetch = { adminRepository.getUsersOverview() },
            onSuccess = { overview ->
                _state.value = _state.value.copy(
                    users = overview.users,
                    currentUserId = overview.currentUserId,
                    adminCount = overview.adminCount,
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                )
            },
            onFailure = { e ->
                Log.e("Users", "Failed to fetch users", e)
                _state.value = _state.value.copy(
                    error = e.message,
                    isLoading = false,
                    isRefreshing = false,
                )
            },
        )
    }

    fun showCreateDialog() {
        _state.value = _state.value.copy(showCreateDialog = true)
    }

    fun dismissCreateDialog() {
        _state.value = _state.value.copy(showCreateDialog = false)
    }

    fun createUser(name: String, password: String?) {
        launch {
            val result = adminRepository.createUser(name, password)
            if (result.isSuccess) {
                _state.value = _state.value.copy(showCreateDialog = false, error = null)
                loadUsers()
            } else {
                Log.e("Users", "Failed to create user", result.exceptionOrNull())
                _state.value = _state.value.copy(
                    error = UserErrorMessages.resolve(result, "Failed to create user"),
                )
            }
        }
    }

    /** Opens the delete-confirm dialog for [user]. */
    fun showDeleteDialog(user: ManagedUser) = deleteConfirmation.show(user)

    /**
     * Refused while a delete is in flight (host rule; [UsersState.isDeleting]
     * is the caller-owned fact) — the dialog stays open until the request settles.
     */
    fun dismissDeleteDialog() = deleteConfirmation.dismiss(inFlight = _state.value.isDeleting)

    /**
     * Deletes the pending user. Settle arm: clears on SUCCESS only — the
     * failure arm leaves the pending user held so the dialog stays open over
     * the error. [UsersState.isDeleting] spans the request so a second
     * confirm and a dismiss are refused.
     */
    fun deleteUser() {
        val user = deleteConfirmation.confirm(inFlight = _state.value.isDeleting) ?: return
        launch {
            _state.value = _state.value.copy(isDeleting = true)
            val result = adminRepository.deleteUser(user.id)
            if (result.isSuccess) {
                _state.value = _state.value.copy(isDeleting = false, error = null)
                deleteConfirmation.clear()
                loadUsers()
            } else {
                Log.e("Users", "Failed to delete user", result.exceptionOrNull())
                _state.value = _state.value.copy(
                    isDeleting = false,
                    error = UserErrorMessages.resolve(result, "Failed to delete user"),
                )
            }
        }
    }
}
