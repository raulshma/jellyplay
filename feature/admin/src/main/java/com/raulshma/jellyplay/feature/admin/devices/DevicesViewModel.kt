package com.raulshma.jellyplay.feature.admin.devices

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.network.JellyfinApiClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
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
    private val apiClient: JellyfinApiClient,
) : ViewModel() {

    var state by mutableStateOf(DevicesState())
        private set

    init {
        loadDevices()
    }

    fun loadDevices() {
        viewModelScope.launch {
            state = state.copy(isLoading = true, error = null)
            val result = apiClient.getDevices()
            result.onSuccess { devices ->
                state = state.copy(devices = devices, isLoading = false)
            }.onFailure { e ->
                Log.e("Devices", "Failed to fetch devices", e)
                state = state.copy(error = e.message, isLoading = false)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            state = state.copy(isRefreshing = true)
            val result = apiClient.getDevices()
            result.onSuccess { devices ->
                state = state.copy(devices = devices, isRefreshing = false)
            }.onFailure {
                state = state.copy(isRefreshing = false)
            }
        }
    }

    fun selectDevice(device: DeviceInfo?) {
        state = state.copy(selectedDevice = device)
    }

    fun showDeleteDialog(device: DeviceInfo) {
        state = state.copy(selectedDevice = device, showDeleteDialog = true)
    }

    fun dismissDeleteDialog() {
        state = state.copy(showDeleteDialog = false, selectedDevice = null)
    }

    fun deleteDevice() {
        val deviceId = state.selectedDevice?.id ?: return
        viewModelScope.launch {
            apiClient.deleteDevice(deviceId)
            state = state.copy(showDeleteDialog = false, selectedDevice = null)
            loadDevices()
        }
    }

    fun showEditNameDialog(device: DeviceInfo) {
        state = state.copy(
            selectedDevice = device,
            showEditNameDialog = true,
            editDeviceId = device.id,
            editCustomName = device.customName ?: "",
        )
    }

    fun dismissEditNameDialog() {
        state = state.copy(showEditNameDialog = false, editCustomName = "")
    }

    fun updateEditCustomName(name: String) {
        state = state.copy(editCustomName = name)
    }

    fun saveDeviceName() {
        viewModelScope.launch {
            apiClient.updateDeviceOptions(state.editDeviceId, state.editCustomName.ifBlank { null })
            state = state.copy(showEditNameDialog = false)
            loadDevices()
        }
    }
}
