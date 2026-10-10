package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Message2
import com.raulshma.jellyplay.core.network.api.JellyPlayMessage
import com.raulshma.jellyplay.core.ui.components.JellyPlayScreenScaffold
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.rememberScreenBackgroundColorState
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_msgs_empty
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_msgs_title
import com.raulshma.jellyplay.feature.settings.generated.resources.jellyplay_msgs_unread_badge
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The JellyPlay companion-plugin's inbox messages screen (ADR 0010) — the
 * durable counterpart of the live events stream: the plugin's admin-published
 * messages with read state. Deliberately minimal: the existing
 * [SettingListItem] rows carry title / body / read state, tapping an unread
 * row marks it read, and the scaffold is the shared [JellyPlayScreenScaffold].
 *
 * Reachability IS the gate — this screen is only navigated to from the
 * settings root's capability-gated "Messages" entry (plugin AVAILABLE +
 * `messages` feature key); the ViewModel still re-checks the feature key
 * before each api call.
 */
@Composable
fun JellyPlayMessagesScreen(
    onBack: () -> Unit,
    viewModel: JellyPlayMessagesViewModel = koinViewModel(),
) {
    val backgroundColorState = rememberScreenBackgroundColorState()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    // Freshness on open: the session controller only refreshed the inbox at
    // stream start, so the screen re-pulls once per visit.
    LaunchedEffect(Unit) { viewModel.refresh() }

    JellyPlayScreenScaffold(
        title = stringResource(Res.string.jellyplay_msgs_title),
        onBack = onBack,
        backgroundColorState = backgroundColorState,
    ) { contentPadding ->
        if (messages.isEmpty()) {
            Text(
                text = stringResource(Res.string.jellyplay_msgs_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = contentPadding.calculateTopPadding() + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 16.dp,
                    start = 16.dp,
                    end = 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(messages, key = { _, message -> message.id }) { _, message ->
                    MessageRow(
                        message = message,
                        onClick = { if (!message.read) viewModel.markRead(message.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageRow(
    message: JellyPlayMessage,
    onClick: () -> Unit,
) {
    SettingListItem(
        icon = Tabler.Outline.Message2,
        title = message.title,
        subtitle = message.body.ifBlank { null },
        trailingText = if (!message.read) stringResource(Res.string.jellyplay_msgs_unread_badge) else null,
        onClick = onClick,
    )
}
