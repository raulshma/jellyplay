package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ManagedUserPolicy

@Composable
fun LimitsSection(
    policy: ManagedUserPolicy,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = policy.maxParentalRating?.toString() ?: "",
            onValueChange = { onPolicyChange(policy.copy(maxParentalRating = it.toIntOrNull())) },
            label = { Text("Max parental rating") },
            supportingText = { Text("Blank = no limit") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = if (policy.maxActiveSessions == 0) "" else policy.maxActiveSessions.toString(),
            onValueChange = { onPolicyChange(policy.copy(maxActiveSessions = it.toIntOrNull() ?: 0)) },
            label = { Text("Max active sessions") },
            supportingText = { Text("0 or blank = unlimited") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = if (policy.loginAttemptsBeforeLockout < 0) "" else policy.loginAttemptsBeforeLockout.toString(),
            onValueChange = { onPolicyChange(policy.copy(loginAttemptsBeforeLockout = it.toIntOrNull() ?: -1)) },
            label = { Text("Login attempts before lockout") },
            supportingText = { Text("Blank = no lockout") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
