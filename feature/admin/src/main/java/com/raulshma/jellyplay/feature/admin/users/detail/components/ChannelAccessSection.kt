package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.LiveTvChannel
import com.raulshma.jellyplay.core.model.ManagedUserPolicy

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
    UserEditSection(title = "Channel access", modifier = modifier) {
        ToggleRow(
            label = "Enable access to all channels",
            description = if (policy.enableAllChannels) null else "Restrict to the channels below",
            checked = policy.enableAllChannels,
            onCheckedChange = { onPolicyChange(policy.copy(enableAllChannels = it)) },
            index = 0, count = rows,
        )
        if (!policy.enableAllChannels) {
            channels.forEachIndexed { i, ch ->
                val checked = ch.id in policy.enabledChannels
                ToggleRow(
                    label = ch.name.ifBlank { ch.number ?: ch.id },
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
