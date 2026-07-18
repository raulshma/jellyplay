package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Cpu
import com.composables.icons.tabler.outline.DeviceTv
import com.composables.icons.tabler.outline.Download
import com.composables.icons.tabler.outline.Pointer
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Trash
import com.composables.icons.tabler.outline.Transform
import com.composables.icons.tabler.outline.VideoPlus
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem

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
            SettingToggleItem(
                icon = Tabler.Outline.PlayerPlay,
                title = "Allow media playback",
                subtitle = "Play movies, shows, and music",
                checked = policy.enableMediaPlayback,
                onCheckedChange = { onPolicyChange(policy.copy(enableMediaPlayback = it)) },
                index = 0, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Cpu,
                title = "Allow audio transcoding",
                subtitle = "",
                checked = policy.enableAudioPlaybackTranscoding,
                onCheckedChange = { onPolicyChange(policy.copy(enableAudioPlaybackTranscoding = it)) },
                index = 1, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Cpu,
                title = "Allow video transcoding",
                subtitle = "",
                checked = policy.enableVideoPlaybackTranscoding,
                onCheckedChange = { onPolicyChange(policy.copy(enableVideoPlaybackTranscoding = it)) },
                index = 2, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Transform,
                title = "Allow remuxing",
                subtitle = "Direct-stream playback without re-encoding",
                checked = policy.enablePlaybackRemuxing,
                onCheckedChange = { onPolicyChange(policy.copy(enablePlaybackRemuxing = it)) },
                index = 3, count = 4,
            )
        }
        UserEditSection(
            title = "Content",
            description = "Deleting and downloading media.",
        ) {
            SettingToggleItem(
                icon = Tabler.Outline.Trash,
                title = "Allow media deletion",
                subtitle = "",
                checked = policy.enableContentDeletion,
                onCheckedChange = { onPolicyChange(policy.copy(enableContentDeletion = it)) },
                index = 0, count = 2,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Download,
                title = "Allow media downloading",
                subtitle = "",
                checked = policy.enableContentDownloading,
                onCheckedChange = { onPolicyChange(policy.copy(enableContentDownloading = it)) },
                index = 1, count = 2,
            )
        }
        UserEditSection(
            title = "Features",
            description = "Live TV, remote control, and remote access.",
        ) {
            SettingToggleItem(
                icon = Tabler.Outline.DeviceTv,
                title = "Allow Live TV access",
                subtitle = "Browse the channel guide",
                checked = policy.enableLiveTvAccess,
                onCheckedChange = { onPolicyChange(policy.copy(enableLiveTvAccess = it)) },
                index = 0, count = 3,
            )
            SettingToggleItem(
                icon = Tabler.Outline.VideoPlus,
                title = "Allow Live TV management",
                subtitle = "Manage recordings and timers",
                checked = policy.enableLiveTvManagement,
                onCheckedChange = { onPolicyChange(policy.copy(enableLiveTvManagement = it)) },
                index = 1, count = 3,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Pointer,
                title = "Allow remote control of other users",
                subtitle = "",
                checked = policy.enableRemoteControlOfOtherUsers,
                onCheckedChange = { onPolicyChange(policy.copy(enableRemoteControlOfOtherUsers = it)) },
                index = 2, count = 3,
            )
        }
    }
}
