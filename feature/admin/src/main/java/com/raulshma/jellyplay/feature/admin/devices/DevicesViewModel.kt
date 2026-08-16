package com.raulshma.jellyplay.feature.admin.devices

import android.util.Log
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.data.repository.AdminRepository
import com.raulshma.jellyplay.core.ui.viewmodel.JellyPlayViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

data class DevicesState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val devices: List<DeviceInfo> = emptyList(),
    val isRefreshing: Boolean = false,
    val selectedDevice: DeviceInfo? = null,
    val showDeleteDialog: Boolean = false,
    val showEditNameDialog: Boolean = false,
    val editDeviceId: String = "",
    val editCustomName: String = "",
)

@HiltViewModel
class DevicesViewModel @Inject constructor(
    private val adminRepository: AdminRepository,
) : JellyPlayViewModel() {

    private val _state = composeState(DevicesState())
    val state: DevicesState get() = _state.value

    init {
        loadDevices()
    }

    fun loadDevices() {
        launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            val result = adminRepository.getDevices()
            result.onSuccess { devices ->
                _state.value = _state.value.copy(devices = devices, isLoading = false)
            }.onFailure { e ->
                Log.e("Devices", "Failed to fetch devices", e)
                _state.value = _state.value.copy(error = e.message, isLoading = false)
            }
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

    fun selectDevice(device: DeviceInfo?) {
        _state.value = _state.value.copy(selectedDevice = device)
    }

    fun showDeleteDialog(device: DeviceInfo) {
        _state.value = _state.value.copy(selectedDevice = device, showDeleteDialog = true)
    }

    fun dismissDeleteDialog() {
        _state.value = _state.value.copy(showDeleteDialog = false, selectedDevice = null)
    }

    fun deleteDevice() {
        val deviceId = _state.value.selectedDevice?.id ?: return
        launch {
            adminRepository.deleteDevice(deviceId)
            _state.value = _state.value.copy(showDeleteDialog = false, selectedDevice = null)
            loadDevices()
        }
    }

    fun showEditNameDialog(device: DeviceInfo) {
        _state.value = _state.value.copy(
            selectedDevice = device,
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
