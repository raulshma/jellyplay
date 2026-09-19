package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.DeviceMobile
import com.composables.icons.tabler.outline.Devices
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_device_access
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_enable_all_devices

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
    UserEditSection(title = stringResource(Res.string.admin_device_access), modifier = modifier) {
        SettingToggleItem(
            icon = Tabler.Outline.Devices,
            title = stringResource(Res.string.admin_enable_all_devices),
            subtitle = if (policy.enableAllDevices) "" else "Restrict to the devices below",
            checked = policy.enableAllDevices,
            onCheckedChange = { onPolicyChange(policy.copy(enableAllDevices = it)) },
            index = 0, count = rows,
        )
        if (!policy.enableAllDevices) {
            devices.forEachIndexed { i, d ->
                val label = d.customName?.ifBlank { null } ?: d.name.ifBlank { d.appName.ifBlank { d.id } }
                val checked = d.id in policy.enabledDevices
                SettingToggleItem(
                    icon = Tabler.Outline.DeviceMobile,
                    title = label,
                    subtitle = "",
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
