package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Inbox
import com.raulshma.jellyplay.core.model.JellyPlayPluginFeatures
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_msgs_entry_subtitle
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_msgs_title
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_msgs_unread_count
import org.jetbrains.compose.resources.stringResource

/**
 * The settings root's "Messages" entry — the ONLY door to the plugin's inbox
 * screen — rendered ONLY where the capabilities probe reports AVAILABLE AND
 * the `messages` feature key is present (ADR 0010's graceful-absent contract:
 * a stock server shows nothing here). Probing rides the entry's visibility,
 * the same discipline as [SettingsJellyPlaySyncSection] (UNKNOWN → one
 * refresh; the store's identity reset re-arms the next visit).
 *
 * The trailing badge is the live unread count; the inbox is refreshed once
 * each time the gate passes so the badge is fresh whenever settings opens.
 */
@Composable
internal fun SettingsJellyPlayMessagesEntry(
    viewModel: SettingsViewModel,
    onOpenMessages: () -> Unit,
) {
    val pluginStatus by viewModel.jellyPlayPluginStatus.collectAsStateWithLifecycle()
    val features by viewModel.jellyPlayPluginFeatures.collectAsStateWithLifecycle()
    val unreadCount by viewModel.jellyPlayUnreadMessageCount.collectAsStateWithLifecycle()

    LaunchedEffect(pluginStatus) {
        if (pluginStatus == JellyPlayPluginStatus.UNKNOWN) {
            viewModel.refreshJellyPlayPluginStatus()
        }
    }

    val gateOpen = pluginStatus == JellyPlayPluginStatus.AVAILABLE &&
        JellyPlayPluginFeatures.Messages in features

    // One inbox pull per settings visit where the gate is open — the session
    // controller only refreshes the inbox at stream start.
    LaunchedEffect(gateOpen) {
        if (gateOpen) viewModel.refreshJellyPlayInbox()
    }

    if (!gateOpen) return

    SettingListItem(
        icon = Tabler.Outline.Inbox,
        title = stringResource(Res.string.jellyplay_msgs_title),
        subtitle = stringResource(Res.string.jellyplay_msgs_entry_subtitle),
        trailingText = if (unreadCount > 0) {
            stringResource(Res.string.jellyplay_msgs_unread_count, unreadCount)
        } else {
            null
        },
        onClick = onOpenMessages,
    )
}
