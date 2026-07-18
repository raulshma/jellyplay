package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Adjustments
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.UserCheck
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem

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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        UserEditSection(
            title = "Identity",
            description = "Control who this user is and how they appear.",
        ) {
            SettingToggleItem(
                icon = Tabler.Outline.Star,
                title = "Administrator",
                subtitle = when {
                    isSelf -> "Cannot change your own admin status"
                    isLastAdmin -> "Cannot remove the last administrator"
                    else -> "Can manage the server and other users"
                },
                checked = policy.isAdministrator,
                enabled = !isSelf && !isLastAdmin,
                onCheckedChange = { onPolicyChange(policy.copy(isAdministrator = it)) },
                index = 0, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.UserCheck,
                title = "Active",
                subtitle = if (isSelf) "Cannot disable yourself" else "Disabled users cannot sign in",
                checked = !policy.isDisabled,
                enabled = !isSelf,
                onCheckedChange = { onPolicyChange(policy.copy(isDisabled = !it)) },
                index = 1, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.EyeOff,
                title = "Hidden",
                subtitle = "Hide this user from the login screen",
                checked = policy.isHidden,
                onCheckedChange = { onPolicyChange(policy.copy(isHidden = it)) },
                index = 2, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Adjustments,
                title = "Allow user preference access",
                subtitle = "Let the user change their own display preferences",
                checked = policy.enableUserPreferenceAccess,
                onCheckedChange = { onPolicyChange(policy.copy(enableUserPreferenceAccess = it)) },
                index = 3, count = 4,
            )
        }
    }
}
