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
import com.raulshma.jellyplay.core.model.DeviceInfo
import com.raulshma.jellyplay.core.model.LibraryFolder
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.ManagedUserPolicy

/**
 * Access tab: library access (AccessSection) plus channel/device access and
 * per-folder media-deletion editors (added in Task 6).
 */
@Composable
fun AccessTab(
    policy: ManagedUserPolicy,
    libraries: List<LibraryFolder>,
    devices: List<DeviceInfo>,
    channels: List<LiveTvChannel>,
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
        AccessSection(
            policy = policy,
            libraries = libraries,
            onPolicyChange = onPolicyChange,
        )
        ChannelAccessSection(
            policy = policy,
            channels = channels,
            onPolicyChange = onPolicyChange,
        )
        DeviceAccessSection(
            policy = policy,
            devices = devices,
            onPolicyChange = onPolicyChange,
        )
        DeletionFolderSection(
            policy = policy,
            libraries = libraries,
            onPolicyChange = onPolicyChange,
        )
    }
}
