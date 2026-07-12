package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ManagedUserPolicy

@Composable
fun GeneralSection(
    name: String,
    policy: ManagedUserPolicy,
    isSelf: Boolean,
    isLastAdmin: Boolean,
    onNameChange: (String) -> Unit,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        ToggleRow(
            label = "Administrator",
            description = when {
                isSelf -> "Cannot change your own admin status"
                isLastAdmin -> "Cannot remove the last administrator"
                else -> null
            },
            checked = policy.isAdministrator,
            enabled = !isSelf && !isLastAdmin,
            onCheckedChange = { onPolicyChange(policy.copy(isAdministrator = it)) },
        )
        ToggleRow(
            label = "Active",
            description = if (isSelf) "Cannot disable yourself" else null,
            checked = !policy.isDisabled,
            enabled = !isSelf,
            onCheckedChange = { onPolicyChange(policy.copy(isDisabled = !it)) },
        )
        ToggleRow(
            label = "Hidden",
            description = "Hide this user from the login screen",
            checked = policy.isHidden,
            onCheckedChange = { onPolicyChange(policy.copy(isHidden = it)) },
        )
        ToggleRow(
            label = "Allow user preference access",
            checked = policy.enableUserPreferenceAccess,
            onCheckedChange = { onPolicyChange(policy.copy(enableUserPreferenceAccess = it)) },
        )
    }
}

@Composable
internal fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    enabled: Boolean = true,
) {
    androidx.compose.foundation.layout.Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}
