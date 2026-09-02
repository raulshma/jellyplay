package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Cpu
import com.composables.icons.tabler.outline.Pointer
import com.composables.icons.tabler.outline.Stack
import com.composables.icons.tabler.outline.Subtitles
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.SyncPlayAccessOption
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_collection_mgmt
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_remote_shared
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_subtitle_mgmt
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_force_transcoding_remote
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_force_transcoding_remote_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_management_control
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_management_control_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_remote_bitrate_limit
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_remote_bitrate_limit_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_streaming
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_streaming_desc

/**
 * Profile-tab permission toggles and options that have no home in the existing
 * PermissionsSection: server/collection management, subtitle management,
 * forced remote transcoding, shared-device control, remote bitrate limit, and
 * SyncPlay access.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun ProfilePermissionsSection(
    policy: ManagedUserPolicy,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UserEditSection(
            title = stringResource(Res.string.admin_management_control),
            description = stringResource(Res.string.admin_management_control_desc),
        ) {
            SettingToggleItem(
                icon = Tabler.Outline.Stack,
                title = stringResource(Res.string.admin_allow_collection_mgmt),
                subtitle = "",
                checked = policy.enableCollectionManagement,
                onCheckedChange = { onPolicyChange(policy.copy(enableCollectionManagement = it)) },
                index = 0, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Subtitles,
                title = stringResource(Res.string.admin_allow_subtitle_mgmt),
                subtitle = "",
                checked = policy.enableSubtitleManagement,
                onCheckedChange = { onPolicyChange(policy.copy(enableSubtitleManagement = it)) },
                index = 1, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Pointer,
                title = stringResource(Res.string.admin_allow_remote_shared),
                subtitle = "",
                checked = policy.enableSharedDeviceControl,
                onCheckedChange = { onPolicyChange(policy.copy(enableSharedDeviceControl = it)) },
                index = 2, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Cpu,
                title = stringResource(Res.string.admin_force_transcoding_remote),
                subtitle = stringResource(Res.string.admin_force_transcoding_remote_desc),
                checked = policy.forceRemoteSourceTranscoding,
                onCheckedChange = { onPolicyChange(policy.copy(forceRemoteSourceTranscoding = it)) },
                index = 3, count = 4,
            )
        }

        UserEditSection(
            title = stringResource(Res.string.admin_streaming),
            description = stringResource(Res.string.admin_streaming_desc),
        ) {
            // SyncPlay access — single-select chips.
            Text(
                "SyncPlay access",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncPlayAccessOption.entries.forEach { option ->
                    FilterChip(
                        selected = policy.syncPlayAccess == option,
                        onClick = { onPolicyChange(policy.copy(syncPlayAccess = option)) },
                        label = { Text(option.syncPlayLabel()) },
                    )
                }
            }

            // Remote client bitrate limit — UI Mbps, storage bits/sec.
            val bitrateMbps = remember(policy.remoteClientBitrateLimit) {
                if (policy.remoteClientBitrateLimit == 0) {
                    ""
                } else {
                    "%.2f".format(policy.remoteClientBitrateLimit / 1_000_000.0)
                }
            }
            OutlinedTextField(
                value = bitrateMbps,
                onValueChange = { input ->
                    val mb = input.toDoubleOrNull() ?: 0.0
                    onPolicyChange(policy.copy(remoteClientBitrateLimit = (mb * 1_000_000).toInt()))
                },
                label = { Text(stringResource(Res.string.admin_remote_bitrate_limit)) },
                supportingText = { Text(stringResource(Res.string.admin_remote_bitrate_limit_desc)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

private fun SyncPlayAccessOption.syncPlayLabel() = when (this) {
    SyncPlayAccessOption.CREATE_AND_JOIN -> "Create & join"
    SyncPlayAccessOption.JOIN_ONLY -> "Join only"
    SyncPlayAccessOption.NONE -> "None"
}
