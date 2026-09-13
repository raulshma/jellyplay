package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_blank_no_lockout
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_login_attempts
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_max_active_sessions
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_security_limits
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_security_limits_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_zero_blank_unlimited

/**
 * Security limits: active-session cap and login lockout. The max parental
 * rating lives on the Parental Control tab (with a proper rating dropdown),
 * so it is intentionally not surfaced here.
 */
@Composable
fun LimitsSection(
    policy: ManagedUserPolicy,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    UserEditSection(
        title = stringResource(Res.string.admin_security_limits),
        description = stringResource(Res.string.admin_security_limits_desc),
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = if (policy.maxActiveSessions == 0) "" else policy.maxActiveSessions.toString(),
            onValueChange = { onPolicyChange(policy.copy(maxActiveSessions = it.toIntOrNull() ?: 0)) },
            label = { Text(stringResource(Res.string.admin_max_active_sessions)) },
            supportingText = { Text(stringResource(Res.string.admin_zero_blank_unlimited)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = if (policy.loginAttemptsBeforeLockout < 0) "" else policy.loginAttemptsBeforeLockout.toString(),
            onValueChange = { onPolicyChange(policy.copy(loginAttemptsBeforeLockout = it.toIntOrNull() ?: -1)) },
            label = { Text(stringResource(Res.string.admin_login_attempts)) },
            supportingText = { Text(stringResource(Res.string.admin_blank_no_lockout)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}
