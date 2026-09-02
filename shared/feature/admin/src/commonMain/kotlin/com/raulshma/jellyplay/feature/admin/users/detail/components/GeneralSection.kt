package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Adjustments
import com.composables.icons.tabler.outline.EyeOff
import com.composables.icons.tabler.outline.Star
import com.composables.icons.tabler.outline.UserCheck
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_active
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_administrator
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_preference_access
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_preference_access_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cannot_change_own_admin
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cannot_disable_self
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_cannot_remove_last_admin
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_disabled_cannot_sign_in
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_hidden
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_hidden_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_identity
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_identity_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_name

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
            label = { Text(stringResource(Res.string.admin_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        )
        UserEditSection(
            title = stringResource(Res.string.admin_identity),
            description = stringResource(Res.string.admin_identity_desc),
        ) {
            SettingToggleItem(
                icon = Tabler.Outline.Star,
                title = stringResource(Res.string.admin_administrator),
                subtitle = when {
                    isSelf -> stringResource(Res.string.admin_cannot_change_own_admin)
                    isLastAdmin -> stringResource(Res.string.admin_cannot_remove_last_admin)
                    else -> "Can manage the server and other users"
                },
                checked = policy.isAdministrator,
                enabled = !isSelf && !isLastAdmin,
                onCheckedChange = { onPolicyChange(policy.copy(isAdministrator = it)) },
                index = 0, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.UserCheck,
                title = stringResource(Res.string.admin_active),
                subtitle = if (isSelf) stringResource(Res.string.admin_cannot_disable_self) else stringResource(Res.string.admin_disabled_cannot_sign_in),
                checked = !policy.isDisabled,
                enabled = !isSelf,
                onCheckedChange = { onPolicyChange(policy.copy(isDisabled = !it)) },
                index = 1, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.EyeOff,
                title = stringResource(Res.string.admin_hidden),
                subtitle = stringResource(Res.string.admin_hidden_desc),
                checked = policy.isHidden,
                onCheckedChange = { onPolicyChange(policy.copy(isHidden = it)) },
                index = 2, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Adjustments,
                title = stringResource(Res.string.admin_allow_preference_access),
                subtitle = stringResource(Res.string.admin_allow_preference_access_desc),
                checked = policy.enableUserPreferenceAccess,
                onCheckedChange = { onPolicyChange(policy.copy(enableUserPreferenceAccess = it)) },
                index = 3, count = 4,
            )
        }
    }
}
