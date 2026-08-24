package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Broadcast
import com.composables.icons.tabler.outline.DeviceTv
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.ManagedUserPolicy
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.feature.admin.generated.resources.Res
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_channel_access
import com.raulshma.jellyplay.feature.admin.generated.resources.admin_enable_all_channels

/**
 * Channel access: "all channels" toggle + per-channel toggles. Hidden when the
 * server has no Live TV channels (web parity).
 */
@Composable
fun ChannelAccessSection(
    policy: ManagedUserPolicy,
    channels: List<LiveTvChannel>,
    onPolicyChange: (ManagedUserPolicy) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (channels.isEmpty()) return
    val rows = if (policy.enableAllChannels) 1 else 1 + channels.size
    UserEditSection(title = stringResource(Res.string.admin_channel_access), modifier = modifier) {
        SettingToggleItem(
            icon = Tabler.Outline.DeviceTv,
            title = stringResource(Res.string.admin_enable_all_channels),
            subtitle = if (policy.enableAllChannels) "" else "Restrict to the channels below",
            checked = policy.enableAllChannels,
            onCheckedChange = { onPolicyChange(policy.copy(enableAllChannels = it)) },
            index = 0, count = rows,
        )
        if (!policy.enableAllChannels) {
            channels.forEachIndexed { i, ch ->
                val checked = ch.id in policy.enabledChannels
                SettingToggleItem(
                    icon = Tabler.Outline.Broadcast,
                    title = ch.name.ifBlank { ch.number ?: ch.id },
                    subtitle = "",
                    checked = checked,
                    onCheckedChange = { enable ->
                        val next = if (enable) policy.enabledChannels + ch.id
                        else policy.enabledChannels - ch.id
                        onPolicyChange(policy.copy(enabledChannels = next))
                    },
                    index = i + 1, count = rows,
                )
            }
        }
    }
}
