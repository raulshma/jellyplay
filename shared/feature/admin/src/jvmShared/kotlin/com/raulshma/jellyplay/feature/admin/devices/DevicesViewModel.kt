package com.raulshma.jellyplay.feature.admin.devices

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.ui.viewmodel.ConfirmationHost
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.core.ui.viewmodel.loadInto

data class DevicesState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val devices: List<DeviceInfo> = emptyList(),
    val isRefreshing: Boolean = false,
    /** True while a delete request is in flight — also the dialog's confirmLoading and the delete-confirmation host's in-flight fact. */
    val isDeleting: Boolean = false,
    val showEditNameDialog: Boolean = false,
    val editDeviceId: String = "",
    val editCustomName: String = "",
)

class DevicesViewModel(
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _state = composeState(DevicesState())
    val state: DevicesState get() = _state.value

    /**
     * Delete-confirmation host (replaces the old showDeleteDialog +
     * selectedDevice state pair). Settle arm: clears on BOTH outcomes — the
     * dialog always closed on delete and the reload stayed unconditional.
     * Guard source: the host's dismiss/confirm rule; [DevicesState.isDeleting]
     * is the caller-owned in-flight fact it reads.
     */
    val deleteConfirmation = ConfirmationHost<DeviceInfo>()

    init {
        loadDevices()
    }

    fun loadDevices() {
        launch {
            loadInto(
                start = { _state.value = _state.value.copy(isLoading = true, error = null) },
                fetch = { adminRepository.getDevices() },
                onSuccess = { devices ->
                    _state.value = _state.value.copy(devices = devices, isLoading = false)
                },
                onFailure = { e ->
                    Log.e("Devices", "Failed to fetch devices", e)
                    _state.value = _state.value.copy(error = e.message, isLoading = false)
                },
            )
        }
    }

    fun refresh() {
        launch {
            _state.value = _state.value.copy(isRefreshing = true)
            val result = adminRepository.getDevices()
            result.onSuccess { devices ->
                _state.value = _state.value.copy(devices = devices, isRefreshing = false)
            }.onFailure {
                _state.value = _state.value.copy(isRefreshing = false)
            }
        }
    }

    /** Opens the delete-confirm dialog for [device]. */
    fun showDeleteDialog(device: DeviceInfo) = deleteConfirmation.show(device)

    /**
     * Refused while a delete is in flight (host rule; [DevicesState.isDeleting]
     * is the caller-owned fact) — the dialog stays open until the request settles.
     */
    fun dismissDeleteDialog() = deleteConfirmation.dismiss(inFlight = _state.value.isDeleting)

    /**
     * Deletes the pending device. Settle arm: clears on BOTH outcomes — the
     * dialog always closed on delete (the delete Result is and stays
     * unhandled) and the reload is unconditional. [DevicesState.isDeleting]
     * spans the request so a second confirm and a dismiss are refused.
     */
    fun deleteDevice() {
        val device = deleteConfirmation.confirm(inFlight = _state.value.isDeleting) ?: return
        launch {
            _state.value = _state.value.copy(isDeleting = true)
            adminRepository.deleteDevice(device.id)
            _state.value = _state.value.copy(isDeleting = false)
            deleteConfirmation.clear()
            loadDevices()
        }
    }

    fun showEditNameDialog(device: DeviceInfo) {
        _state.value = _state.value.copy(
            showEditNameDialog = true,
            editDeviceId = device.id,
            editCustomName = device.customName ?: "",
        )
    }

    fun dismissEditNameDialog() {
        _state.value = _state.value.copy(showEditNameDialog = false, editCustomName = "")
    }

    fun updateEditCustomName(name: String) {
        _state.value = _state.value.copy(editCustomName = name)
    }

    fun saveDeviceName() {
        launch {
            adminRepository.renameDevice(_state.value.editDeviceId, _state.value.editCustomName.ifBlank { null })
            _state.value = _state.value.copy(showEditNameDialog = false)
            loadDevices()
        }
    }
}
