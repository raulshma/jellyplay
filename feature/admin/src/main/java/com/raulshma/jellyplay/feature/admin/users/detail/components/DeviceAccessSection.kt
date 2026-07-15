package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
    val rows = if (policy.enableAllDevices) 1 else 1 + devices.size
    UserEditSection(title = "Device access", modifier = modifier) {
        ToggleRow(
            label = "Enable access from all devices",
            description = if (policy.enableAllDevices) null else "Restrict to the devices below",
            checked = policy.enableAllDevices,
            onCheckedChange = { onPolicyChange(policy.copy(enableAllDevices = it)) },
            index = 0, count = rows,
        )
        if (!policy.enableAllDevices) {
            devices.forEachIndexed { i, d ->
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
                    index = i + 1, count = rows,
                )
            }
        }
    }
}
