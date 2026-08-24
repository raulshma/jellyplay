package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
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
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_audio_transcoding
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_live_tv
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_live_tv_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_live_tv_mgmt
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_live_tv_mgmt_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_media_deletion
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_media_downloading
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_media_playback
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_media_playback_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_remote_control
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_remuxing
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_remuxing_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_allow_video_transcoding
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_content
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_content_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_features
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_features_desc
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_playback
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_playback_desc

@Composable
fun PermissionsSection(
    policy: ManagedUserPolicy,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        UserEditSection(
            title = stringResource(Res.string.admin_playback),
            description = stringResource(Res.string.admin_playback_desc),
        ) {
            SettingToggleItem(
                icon = Tabler.Outline.PlayerPlay,
                title = stringResource(Res.string.admin_allow_media_playback),
                subtitle = stringResource(Res.string.admin_allow_media_playback_desc),
                checked = policy.enableMediaPlayback,
                onCheckedChange = { onPolicyChange(policy.copy(enableMediaPlayback = it)) },
                index = 0, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Cpu,
                title = stringResource(Res.string.admin_allow_audio_transcoding),
                subtitle = "",
                checked = policy.enableAudioPlaybackTranscoding,
                onCheckedChange = { onPolicyChange(policy.copy(enableAudioPlaybackTranscoding = it)) },
                index = 1, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Cpu,
                title = stringResource(Res.string.admin_allow_video_transcoding),
                subtitle = "",
                checked = policy.enableVideoPlaybackTranscoding,
                onCheckedChange = { onPolicyChange(policy.copy(enableVideoPlaybackTranscoding = it)) },
                index = 2, count = 4,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Transform,
                title = stringResource(Res.string.admin_allow_remuxing),
                subtitle = stringResource(Res.string.admin_allow_remuxing_desc),
                checked = policy.enablePlaybackRemuxing,
                onCheckedChange = { onPolicyChange(policy.copy(enablePlaybackRemuxing = it)) },
                index = 3, count = 4,
            )
        }
        UserEditSection(
            title = stringResource(Res.string.admin_content),
            description = stringResource(Res.string.admin_content_desc),
        ) {
            SettingToggleItem(
                icon = Tabler.Outline.Trash,
                title = stringResource(Res.string.admin_allow_media_deletion),
                subtitle = "",
                checked = policy.enableContentDeletion,
                onCheckedChange = { onPolicyChange(policy.copy(enableContentDeletion = it)) },
                index = 0, count = 2,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Download,
                title = stringResource(Res.string.admin_allow_media_downloading),
                subtitle = "",
                checked = policy.enableContentDownloading,
                onCheckedChange = { onPolicyChange(policy.copy(enableContentDownloading = it)) },
                index = 1, count = 2,
            )
        }
        UserEditSection(
            title = stringResource(Res.string.admin_features),
            description = stringResource(Res.string.admin_features_desc),
        ) {
            SettingToggleItem(
                icon = Tabler.Outline.DeviceTv,
                title = stringResource(Res.string.admin_allow_live_tv),
                subtitle = stringResource(Res.string.admin_allow_live_tv_desc),
                checked = policy.enableLiveTvAccess,
                onCheckedChange = { onPolicyChange(policy.copy(enableLiveTvAccess = it)) },
                index = 0, count = 3,
            )
            SettingToggleItem(
                icon = Tabler.Outline.VideoPlus,
                title = stringResource(Res.string.admin_allow_live_tv_mgmt),
                subtitle = stringResource(Res.string.admin_allow_live_tv_mgmt_desc),
                checked = policy.enableLiveTvManagement,
                onCheckedChange = { onPolicyChange(policy.copy(enableLiveTvManagement = it)) },
                index = 1, count = 3,
            )
            SettingToggleItem(
                icon = Tabler.Outline.Pointer,
                title = stringResource(Res.string.admin_allow_remote_control),
                subtitle = "",
                checked = policy.enableRemoteControlOfOtherUsers,
                onCheckedChange = { onPolicyChange(policy.copy(enableRemoteControlOfOtherUsers = it)) },
                index = 2, count = 3,
            )
        }
    }
}
