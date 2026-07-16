package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
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
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UserEditSection(
            title = "Playback",
            description = "What this user can play and how it may be transcoded.",
        ) {
            ToggleRow("Allow media playback", policy.enableMediaPlayback, onCheckedChange = { onPolicyChange(policy.copy(enableMediaPlayback = it)) }, index = 0, count = 4, description = "Play movies, shows, and music")
            ToggleRow("Allow audio transcoding", policy.enableAudioPlaybackTranscoding, onCheckedChange = { onPolicyChange(policy.copy(enableAudioPlaybackTranscoding = it)) }, index = 1, count = 4)
            ToggleRow("Allow video transcoding", policy.enableVideoPlaybackTranscoding, onCheckedChange = { onPolicyChange(policy.copy(enableVideoPlaybackTranscoding = it)) }, index = 2, count = 4)
            ToggleRow("Allow remuxing", policy.enablePlaybackRemuxing, onCheckedChange = { onPolicyChange(policy.copy(enablePlaybackRemuxing = it)) }, index = 3, count = 4, description = "Direct-stream playback without re-encoding")
        }
        UserEditSection(
            title = "Content",
            description = "Deleting and downloading media.",
        ) {
            ToggleRow("Allow media deletion", policy.enableContentDeletion, onCheckedChange = { onPolicyChange(policy.copy(enableContentDeletion = it)) }, index = 0, count = 2)
            ToggleRow("Allow media downloading", policy.enableContentDownloading, onCheckedChange = { onPolicyChange(policy.copy(enableContentDownloading = it)) }, index = 1, count = 2)
        }
        UserEditSection(
            title = "Features",
            description = "Live TV, remote control, and remote access.",
        ) {
            ToggleRow("Allow Live TV access", policy.enableLiveTvAccess, onCheckedChange = { onPolicyChange(policy.copy(enableLiveTvAccess = it)) }, index = 0, count = 3, description = "Browse the channel guide")
            ToggleRow("Allow Live TV management", policy.enableLiveTvManagement, onCheckedChange = { onPolicyChange(policy.copy(enableLiveTvManagement = it)) }, index = 1, count = 3, description = "Manage recordings and timers")
            ToggleRow("Allow remote control of other users", policy.enableRemoteControlOfOtherUsers, onCheckedChange = { onPolicyChange(policy.copy(enableRemoteControlOfOtherUsers = it)) }, index = 2, count = 3)
        }
    }
}
