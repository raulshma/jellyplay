package com.raulshma.jellyplay.feature.admin.users.detail

import android.util.Log
import androidx.compose.runtime.Immutable
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.ManagedUser
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.ParentalRatingOption
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@Immutable
data class UserDetailState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val user: ManagedUser? = null,
    val libraries: List<LibraryFolder> = emptyList(),
    val editedPolicy: ManagedUserPolicy? = null,
    val editedName: String? = null,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val message: String? = null,
    val showDeleteDialog: Boolean = false,
    val showPasswordDialog: Boolean = false,
    val isSelf: Boolean = false,
    val isLastAdmin: Boolean = false,
    // Auxiliary data for the new editors, lazily loaded per tab.
    val parentalRatings: List<ParentalRatingOption> = emptyList(),
    val devices: List<DeviceInfo> = emptyList(),
    val channels: List<LiveTvChannel> = emptyList(),
    val tags: List<String> = emptyList(),
    val auxLoadedTabs: Set<UserEditTab> = emptySet(),
    val auxError: String? = null,
) {
    val isDirty: Boolean get() = editedPolicy != null || editedName != null
}

@HiltViewModel
class UserDetailViewModel @Inject constructor(
    private val apiClient: JellyfinApiClient,
) : JellyPlayViewModel() {

    private val _uiState = stateFlow(UserDetailState())
    val uiState = _uiState.flow

    private var userId: String = ""

    fun loadUser(userId: String) {
        if (this.userId == userId && _uiState.value.user != null) return
        this.userId = userId
        _uiState.set(UserDetailState())
        launch {
            // Access control is enforced by AdminRouteContainer before this
            // screen is reached; the server still 403s as a backstop.
            val userResult = apiClient.getManagedUser(userId)
            val libsResult = apiClient.getLibraryFoldersForEditor()
            val meResult = apiClient.getCurrentUserId()
            val allUsersResult = apiClient.getManagedUsers()
            userResult.onSuccess { user ->
                val me = meResult.getOrNull()
                val allUsers = allUsersResult.getOrNull().orEmpty()
                val adminCount = allUsers.count { it.policy.isAdministrator && !it.policy.isDisabled }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        user = user,
                        libraries = libsResult.getOrNull().orEmpty(),
                        isSelf = me != null && me == user.id,
                        isLastAdmin = adminCount == 1 && user.policy.isAdministrator && !user.policy.isDisabled,
                    )
                }
            }.onFailure { e ->
                Log.e("UserDetail", "Failed to load user", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    /** Called by section components with a full [ManagedUserPolicy] value. */
    fun onPolicyChange(policy: ManagedUserPolicy) {
        _uiState.update { it.copy(editedPolicy = policy, saveError = null) }
    }

    fun editName(name: String) {
        _uiState.update { it.copy(editedName = name, saveError = null) }
    }

    fun discard() {
        _uiState.update { it.copy(editedPolicy = null, editedName = null, saveError = null) }
    }

    /**
     * Lazily loads the auxiliary lists a tab needs (devices/channels for Access,
     * ratings/tags for Parental). Idempotent: a tab fetches at most once per
     * loaded user. Failures are non-fatal — [UserDetailState.auxError] is set
     * and the affected list renders empty, but editing/saving other fields
     * remains possible.
     */
    fun loadAuxFor(tab: UserEditTab) {
        if (tab == UserEditTab.PROFILE || tab == UserEditTab.ACCOUNT) return
        if (tab in _uiState.value.auxLoadedTabs) return
        _uiState.update { it.copy(auxLoadedTabs = it.auxLoadedTabs + tab) }
        launch {
            runCatching {
                when (tab) {
                    UserEditTab.ACCESS -> {
                        val devs = apiClient.getDevices().getOrThrow()
                        val chans = apiClient.getLiveTvChannels(limit = 500).getOrThrow()
                        _uiState.update { it.copy(devices = devs, channels = chans) }
                    }
                    UserEditTab.PARENTAL -> {
                        val ratings = apiClient.getParentalRatings().getOrThrow()
                        val tagList = apiClient.getTags(limit = 500).getOrThrow()
                        _uiState.update { it.copy(parentalRatings = ratings, tags = tagList) }
                    }
                    else -> Unit
                }
            }.onFailure { e ->
                Log.e("UserDetail", "aux load failed for $tab", e)
                _uiState.update { it.copy(auxError = e.message) }
            }
        }
    }

    /** Unsaved-change counts per tab (value-based diff). Account never dirty. */
    fun profileDirtyCount(): Int {
        val st = _uiState.value
        var n = PolicyDiff.changedCount(st.editedPolicy, st.user?.policy, PolicyDiff.PROFILE_FIELDS)
        if (st.editedName != null && st.editedName != st.user?.name) n++
        return n
    }

    fun accessDirtyCount(): Int =
        PolicyDiff.changedCount(_uiState.value.editedPolicy, _uiState.value.user?.policy, PolicyDiff.ACCESS_FIELDS)

    fun parentalDirtyCount(): Int =
        PolicyDiff.changedCount(_uiState.value.editedPolicy, _uiState.value.user?.policy, PolicyDiff.PARENTAL_FIELDS)

    fun save() {
        val id = userId
        val editedName = _uiState.value.editedName
        val editedPolicy = _uiState.value.editedPolicy
        if (editedName == null && editedPolicy == null) return
        _uiState.update { it.copy(isSaving = true, saveError = null) }
        launch {
            // 1. rename first (if pending)
            if (editedName != null) {
                val r = apiClient.renameUser(id, editedName)
                if (r.isFailure) {
                    val e = r.exceptionOrNull()
                    Log.e("UserDetail", "rename failed", e)
                    _uiState.update { it.copy(isSaving = false, saveError = e?.message) }
                    return@launch
                }
                _uiState.update { it.copy(editedName = null) }
            }
            // 2. policy second (if pending)
            if (editedPolicy != null) {
                val r = apiClient.updateUserPolicy(id, editedPolicy)
                if (r.isFailure) {
                    val e = r.exceptionOrNull()
                    Log.e("UserDetail", "policy update failed", e)
                    // keep editedPolicy so user can retry; editedName already cleared
                    _uiState.update { it.copy(isSaving = false, saveError = e?.message) }
                    return@launch
                }
                _uiState.update { it.copy(editedPolicy = null) }
            }
            // 3. reload
            apiClient.getManagedUser(id).onSuccess { fresh ->
                _uiState.update { it.copy(isSaving = false, user = fresh, message = "Changes saved") }
            }.onFailure {
                _uiState.update { it.copy(isSaving = false, message = "Saved — could not reload; tap refresh to verify", saveError = "Could not reload updated user") }
            }
        }
    }

    fun updatePassword(newPassword: String?) {
        val id = userId
        launch {
            val r = apiClient.updateUserPassword(id, newPassword)
            _uiState.update {
                it.copy(
                    showPasswordDialog = false,
                    message = if (r.isSuccess) (if (newPassword == null) "Password reset" else "Password updated") else null,
                    saveError = if (r.isFailure) r.exceptionOrNull()?.message else null,
                )
            }
        }
    }

    fun showPasswordDialog() = _uiState.update { it.copy(showPasswordDialog = true) }
    fun dismissPasswordDialog() = _uiState.update { it.copy(showPasswordDialog = false) }

    fun showDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = true) }
    fun dismissDeleteDialog() = _uiState.update { it.copy(showDeleteDialog = false) }

    fun deleteUser(onDone: () -> Unit) {
        if (_uiState.value.isSelf) return // self-guard
        val id = userId
        launch {
            apiClient.deleteUser(id)
            _uiState.update { it.copy(showDeleteDialog = false) }
            onDone()
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }
    fun consumeSaveError() = _uiState.update { it.copy(saveError = null) }
}
