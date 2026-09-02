package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ManagedUserPolicy

/**
 * Profile tab: identity toggles (GeneralSection), playback/feature permissions
 * (PermissionsSection), and security limits (LimitsSection). New permission
 * toggles, bitrate, and SyncPlay are added in Task 5.
 */
@Composable
fun ProfileTab(
    name: String,
    policy: ManagedUserPolicy,
    isSelf: Boolean,
    isLastAdmin: Boolean,
    onNameChange: (String) -> Unit,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(top = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GeneralSection(
            name = name,
            policy = policy,
            isSelf = isSelf,
            isLastAdmin = isLastAdmin,
            onNameChange = onNameChange,
            onPolicyChange = onPolicyChange,
        )
        PermissionsSection(policy = policy, onPolicyChange = onPolicyChange)
        ProfilePermissionsSection(policy = policy, onPolicyChange = onPolicyChange)
        LimitsSection(policy = policy, onPolicyChange = onPolicyChange)
    }
}
