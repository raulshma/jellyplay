package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.model.SyncPlayAccessOption

/**
 * Profile-tab permission toggles and options that have no home in the existing
 * PermissionsSection: server/collection management, subtitle management,
 * forced remote transcoding, shared-device control, remote bitrate limit, and
 * SyncPlay access.
 */
@Composable
fun ProfilePermissionsSection(
    policy: ManagedUserPolicy,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToggleRow(
            label = "Allow collection management",
            checked = policy.enableCollectionManagement,
            onCheckedChange = { onPolicyChange(policy.copy(enableCollectionManagement = it)) },
        )
        ToggleRow(
            label = "Allow subtitle management",
            checked = policy.enableSubtitleManagement,
            onCheckedChange = { onPolicyChange(policy.copy(enableSubtitleManagement = it)) },
        )
        ToggleRow(
            label = "Force transcoding of remote media sources",
            checked = policy.forceRemoteSourceTranscoding,
            onCheckedChange = { onPolicyChange(policy.copy(forceRemoteSourceTranscoding = it)) },
        )
        ToggleRow(
            label = "Allow remote control of shared devices",
            checked = policy.enableSharedDeviceControl,
            onCheckedChange = { onPolicyChange(policy.copy(enableSharedDeviceControl = it)) },
        )

        // SyncPlay access — single-select chips.
        Text(
            "SyncPlay access",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            label = { Text("Remote client bitrate limit") },
            supportingText = { Text("Mbps. Blank = unlimited") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

private fun SyncPlayAccessOption.syncPlayLabel() = when (this) {
    SyncPlayAccessOption.CREATE_AND_JOIN -> "Create & join"
    SyncPlayAccessOption.JOIN_ONLY -> "Join only"
    SyncPlayAccessOption.NONE -> "None"
}
