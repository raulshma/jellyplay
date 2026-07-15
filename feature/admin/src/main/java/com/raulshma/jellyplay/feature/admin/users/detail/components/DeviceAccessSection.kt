package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.ManagedUserPolicy

/**
 * Device access: "all devices" toggle + per-device toggles. Hidden entirely
 * when the user is an administrator (web parity — admins bypass device limits)
 * or when no devices have connected.
 */
@Composable
fun DeviceAccessSection(
    policy: ManagedUserPolicy,
    devices: List<DeviceInfo>,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (policy.isAdministrator || devices.isEmpty()) return
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Device access", style = MaterialTheme.typography.titleSmall)
        ToggleRow(
            label = "Enable access from all devices",
            checked = policy.enableAllDevices,
            onCheckedChange = { onPolicyChange(policy.copy(enableAllDevices = it)) },
        )
        if (!policy.enableAllDevices) {
            devices.forEach { d ->
                val label = d.customName?.ifBlank { null } ?: d.name.ifBlank { d.appName.ifBlank { d.id } }
                val checked = d.id in policy.enabledDevices
                ToggleRow(
                    label = label,
                    checked = checked,
                    onCheckedChange = { enable ->
                        val next = if (enable) policy.enabledDevices + d.id
                        else policy.enabledDevices - d.id
                        onPolicyChange(policy.copy(enabledDevices = next))
                    },
                )
            }
        }
    }
}
