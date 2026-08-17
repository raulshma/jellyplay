package com.raulshma.jellyplay.feature.admin.users

import android.util.Log
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class UsersState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val users: List<ManagedUser> = emptyList(),
    val isRefreshing: Boolean = false,
    val currentUserId: String? = null,
    val adminCount: Int = 0,
    val showCreateDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val selectedUser: ManagedUser? = null,
)

@HiltViewModel
class UsersViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _state = composeState(UsersState())
    val state: UsersState get() = _state.value

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
        if (!refreshing) {
            _state.value = _state.value.copy(isLoading = true, error = null)
        }
        adminRepository.getUsersOverview()
            .onSuccess { overview ->
                _state.value = _state.value.copy(
                    users = overview.users,
                    currentUserId = overview.currentUserId,
                    adminCount = overview.adminCount,
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                )
            }.onFailure { e ->
                Log.e("Users", "Failed to fetch users", e)
                _state.value = _state.value.copy(
                    error = e.message,
                    isLoading = false,
                    isRefreshing = false,
                )
            }
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

    fun showDeleteDialog(user: ManagedUser) {
        _state.value = _state.value.copy(selectedUser = user, showDeleteDialog = true)
    }

    fun dismissDeleteDialog() {
        _state.value = _state.value.copy(showDeleteDialog = false, selectedUser = null)
    }

    fun deleteUser() {
        val userId = _state.value.selectedUser?.id ?: return
        launch {
            val result = adminRepository.deleteUser(userId)
            if (result.isSuccess) {
                _state.value = _state.value.copy(showDeleteDialog = false, selectedUser = null, error = null)
                loadUsers()
            } else {
                Log.e("Users", "Failed to delete user", result.exceptionOrNull())
                _state.value = _state.value.copy(
                    error = result.exceptionOrNull()?.message ?: "Failed to delete user",
                )
            }
        }
    }
}
