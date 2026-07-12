package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.ManagedUserPolicy

@Composable
fun PermissionsSection(
    policy: ManagedUserPolicy,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ToggleRow("Allow media playback", policy.enableMediaPlayback, onCheckedChange = { onPolicyChange(policy.copy(enableMediaPlayback = it)) })
        ToggleRow("Allow audio transcoding", policy.enableAudioPlaybackTranscoding, onCheckedChange = { onPolicyChange(policy.copy(enableAudioPlaybackTranscoding = it)) })
        ToggleRow("Allow video transcoding", policy.enableVideoPlaybackTranscoding, onCheckedChange = { onPolicyChange(policy.copy(enableVideoPlaybackTranscoding = it)) })
        ToggleRow("Allow remuxing", policy.enablePlaybackRemuxing, onCheckedChange = { onPolicyChange(policy.copy(enablePlaybackRemuxing = it)) })
        ToggleRow("Allow media deletion", policy.enableContentDeletion, onCheckedChange = { onPolicyChange(policy.copy(enableContentDeletion = it)) })
        ToggleRow("Allow media downloading", policy.enableContentDownloading, onCheckedChange = { onPolicyChange(policy.copy(enableContentDownloading = it)) })
        ToggleRow("Allow Live TV access", policy.enableLiveTvAccess, onCheckedChange = { onPolicyChange(policy.copy(enableLiveTvAccess = it)) })
        ToggleRow("Allow Live TV management", policy.enableLiveTvManagement, onCheckedChange = { onPolicyChange(policy.copy(enableLiveTvManagement = it)) })
        ToggleRow("Allow remote control of other users", policy.enableRemoteControlOfOtherUsers, onCheckedChange = { onPolicyChange(policy.copy(enableRemoteControlOfOtherUsers = it)) })
        ToggleRow("Allow remote access", policy.enableRemoteAccess, onCheckedChange = { onPolicyChange(policy.copy(enableRemoteAccess = it)) })
    }
}
