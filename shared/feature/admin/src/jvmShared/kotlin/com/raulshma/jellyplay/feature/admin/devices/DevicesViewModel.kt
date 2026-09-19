package com.raulshma.jellyplay.feature.admin.devices

import com.raulshma.jellyplay.core.data.log.Log
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.PendingConfirmation
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import com.raulshma.jellyplay.feature.admin.AdminLoad

data class DevicesState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val devices: List<DeviceInfo> = emptyList(),
    val isRefreshing: Boolean = false,
    /**
     * Delete-confirmation machine (replaces the old showDeleteDialog +
     * selectedDevice pair). Settle arm: clears on BOTH outcomes — the dialog
     * always closed on delete and the reload stayed unconditional. Guard
     * source: the machine's dismiss/confirm rule; [isDeleting] is the
     * caller-owned in-flight fact it reads.
     */
    val pendingDelete: PendingConfirmation<DeviceInfo> = PendingConfirmation(),
    /** True while a delete request is in flight — also the dialog's confirmLoading. */
    val isDeleting: Boolean = false,
    val showEditNameDialog: Boolean = false,
    val editDeviceId: String = "",
    val editCustomName: String = "",
) {
    /** The delete dialog's open flag, derived from the pending machine. */
    val showDeleteDialog: Boolean get() = pendingDelete.isPending

    /**
     * The device awaiting delete confirmation — the dialog's payload.
     * Compatibility alias for the pre-fold `selectedDevice` field name (the
     * screen and suite read it unchanged); the value is the machine's
     * pending delete target.
     */
    val selectedDevice: DeviceInfo? get() = pendingDelete.item
}

class DevicesViewModel(
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _state = composeState(DevicesState())
    val state: DevicesState get() = _state.value

    init {
        loadDevices()
    }

    fun loadDevices() {
        launch {
            AdminLoad.load(
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
    fun showDeleteDialog(device: DeviceInfo) {
        _state.value = _state.value.copy(pendingDelete = _state.value.pendingDelete.hold(device))
    }

    /**
     * Refused while a delete is in flight (machine rule; [DevicesState.isDeleting]
     * is the caller-owned fact) — the dialog stays open until the request settles.
     */
    fun dismissDeleteDialog() {
        _state.value = _state.value.copy(pendingDelete = _state.value.pendingDelete.dismiss(_state.value.isDeleting))
    }

    /**
     * Deletes the pending device. Settle arm: clears on BOTH outcomes — the
     * dialog always closed on delete (the delete Result is and stays
     * unhandled) and the reload is unconditional. [DevicesState.isDeleting]
     * spans the request so a second confirm and a dismiss are refused.
     */
    fun deleteDevice() {
        val device = _state.value.pendingDelete.confirm(inFlight = _state.value.isDeleting) ?: return
        launch {
            _state.value = _state.value.copy(isDeleting = true)
            adminRepository.deleteDevice(device.id)
            _state.value = _state.value.copy(
                isDeleting = false,
                pendingDelete = _state.value.pendingDelete.clear(),
            )
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
