package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Devices
import com.raulshma.jellyplay.core.model.JellyPlayPluginStatus
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_jellyplay_sync_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_sync_never
import org.jetbrains.compose.resources.stringResource

/**
 * The `group_jellyplay_sync` section: the JellyPlay companion-plugin's
 * settings-sync group (ADR 0010) — opt-in toggle + sync-now, emitted ONLY
 * where the capabilities probe reports AVAILABLE (the caller gates; a
 * stock server renders nothing here, exactly the graceful-absent contract).
 */
@Composable
internal fun SettingsJellyPlaySyncSection(
    viewModel: SettingsViewModel,
    lastClickedSettingId: String?,
    syncIcon: ImageVector = Tabler.Outline.Devices,
) {
    val pluginStatus by viewModel.jellyPlayPluginStatus.collectAsStateWithLifecycle()
    val syncState by viewModel.jellyPlaySyncState.collectAsStateWithLifecycle()

    // The probe rides the section's visibility: opening settings is the only
    // moment a user can act on the toggle, so it is the only moment worth
    // probing. UNKNOWN → one refresh; UNAVAILABLE/AVAILABLE never re-probe
    // (the identity reset in the store re-arms the next visit).
    LaunchedEffect(pluginStatus) {
        if (pluginStatus == JellyPlayPluginStatus.UNKNOWN) {
            viewModel.refreshJellyPlayPluginStatus()
        }
    }

    if (pluginStatus != JellyPlayPluginStatus.AVAILABLE) {
        return
    }

    val syncNowCount = rowTotalFor(JellyPlaySyncGroup, RowAdmissionFlags())

    SettingsGroup(
        icon = syncIcon,
        title = stringResource(Res.string.settings_jellyplay_sync),
        summary = {
            if (syncState.enabled) {
                stringResource(Res.string.settings_jellyplay_sync_on)
            } else {
                stringResource(Res.string.settings_jellyplay_sync_off)
            }
        },
        initiallyExpanded = false,
    ) {
        SettingToggleItem(
            icon = rowIcon(JellyPlaySyncRows.SyncEnabled),
            title = rowTitle(JellyPlaySyncRows.SyncEnabled),
            subtitle = rowSubtitle(JellyPlaySyncRows.SyncEnabled),
            checked = syncState.enabled,
            index = 0, count = syncNowCount,
            highlighted = lastClickedSettingId == JellyPlaySyncRows.SyncEnabled.id,
            onCheckedChange = { enabled -> viewModel.setJellyPlaySyncEnabled(enabled) },
        )
        SettingListItem(
            icon = rowIcon(JellyPlaySyncRows.SyncNow),
            title = rowTitle(JellyPlaySyncRows.SyncNow),
            subtitle = rowSubtitle(JellyPlaySyncRows.SyncNow),
            trailingText = syncState.lastSyncAt?.let { formatSyncTime(it) }
                ?: stringResource(Res.string.settings_sync_never),
            index = 1, count = syncNowCount,
            highlighted = lastClickedSettingId == JellyPlaySyncRows.SyncNow.id,
            onClick = { viewModel.syncJellyPlayNow() },
        )
    }
}

/** HH:mm of the last successful sync, locale-formatted; fallback keeps the raw millis honest. */
private fun formatSyncTime(epochMillis: Long): String = runCatching {
    java.time.Instant.ofEpochMilli(epochMillis)
        .atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
}.getOrDefault(epochMillis.toString())
