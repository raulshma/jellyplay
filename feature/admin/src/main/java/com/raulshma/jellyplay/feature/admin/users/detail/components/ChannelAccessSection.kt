package com.raulshma.jellyplay.feature.admin.users.detail.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Channel access", style = MaterialTheme.typography.titleSmall)
        ToggleRow(
            label = "Enable access to all channels",
            checked = policy.enableAllChannels,
            onCheckedChange = { onPolicyChange(policy.copy(enableAllChannels = it)) },
        )
        if (!policy.enableAllChannels) {
            channels.forEach { ch ->
                val checked = ch.id in policy.enabledChannels
                ToggleRow(
                    label = ch.name.ifBlank { ch.number ?: ch.id },
                    checked = checked,
                    onCheckedChange = { enable ->
                        val next = if (enable) policy.enabledChannels + ch.id
                        else policy.enabledChannels - ch.id
                        onPolicyChange(policy.copy(enabledChannels = next))
                    },
                )
            }
        }
    }
}
