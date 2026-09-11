package com.raulshma.jellyplay.feature.admin.users

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.PendingConfirmation
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.admin.AdminLoad

data class UsersState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val users: List<ManagedUser> = emptyList(),
    val isRefreshing: Boolean = false,
    val currentUserId: String? = null,
    val adminCount: Int = 0,
    val showCreateDialog: Boolean = false,
    /**
     * Delete-confirmation machine (replaces the old showDeleteDialog +
     * selectedUser pair). Settle arm: clears on SUCCESS only — a failed
     * delete keeps the dialog open over the error. Guard source: the
     * machine's dismiss/confirm rule; [isDeleting] is the caller-owned
     * in-flight fact it reads.
     */
    val pendingDelete: PendingConfirmation<ManagedUser> = PendingConfirmation(),
    /** True while a delete request is in flight — also the dialog's confirmLoading. */
    val isDeleting: Boolean = false,
) {
    /** The delete dialog's open flag, derived from the pending machine. */
    val showDeleteDialog: Boolean get() = pendingDelete.isPending

    /**
     * The user awaiting delete confirmation — the dialog's payload.
     * Compatibility alias for the pre-fold `selectedUser` field name (the
     * screen and suite read it unchanged); the value is the machine's
     * pending delete target.
     */
    val selectedUser: ManagedUser? get() = pendingDelete.item
}

/** Decode cap for the 40 dp row avatar (128 px covers ~3.2x density). */
internal const val AVATAR_MAX_WIDTH = 128

class UsersViewModel(
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _state = composeState(UsersState())
    val state: UsersState get() = _state.value

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
            loadInto(refreshing = true)
        }
    }

    fun loadUsers() {
        launch { loadInto(refreshing = false) }
    }

    private suspend fun loadInto(refreshing: Boolean) {
        // Access control is enforced by AdminRouteContainer before this screen
        // is reached; the server still 403s as a backstop if state is stale.
        // Flavour start (see AdminLoad): the cold load raises the pair, a
        // refresh raises nothing here (its isRefreshing was already raised by
        // the caller, and a shown error is deliberately kept).
        AdminLoad.load(
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
                    error = result.exceptionOrNull()?.message ?: "Failed to create user",
                )
            }
        }
    }

    /** Opens the delete-confirm dialog for [user]. */
    fun showDeleteDialog(user: ManagedUser) {
        _state.value = _state.value.copy(pendingDelete = _state.value.pendingDelete.hold(user))
    }

    /**
     * Refused while a delete is in flight (machine rule; [UsersState.isDeleting]
     * is the caller-owned fact) — the dialog stays open until the request settles.
     */
    fun dismissDeleteDialog() {
        _state.value = _state.value.copy(pendingDelete = _state.value.pendingDelete.dismiss(_state.value.isDeleting))
    }

    /**
     * Deletes the pending user. Settle arm: clears on SUCCESS only — the
     * failure arm leaves the pending user held so the dialog stays open over
     * the error. [UsersState.isDeleting] spans the request so a second
     * confirm and a dismiss are refused.
     */
    fun deleteUser() {
        val user = _state.value.pendingDelete.confirm(inFlight = _state.value.isDeleting) ?: return
        launch {
            _state.value = _state.value.copy(isDeleting = true)
            val result = adminRepository.deleteUser(user.id)
            if (result.isSuccess) {
                _state.value = _state.value.copy(
                    isDeleting = false,
                    pendingDelete = _state.value.pendingDelete.clear(),
                    error = null,
                )
                loadUsers()
            } else {
                Log.e("Users", "Failed to delete user", result.exceptionOrNull())
                _state.value = _state.value.copy(
                    isDeleting = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to delete user",
                )
            }
        }
    }
}
