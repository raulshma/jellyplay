package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_entry_syncplay_casting
import org.koin.compose.viewmodel.koinViewModel

/**
 * The SyncPlay & casting leaf (ADR-0017): the SyncPlay trio and the casting
 * trio — the two always-visible groups the former combined screen trailed —
 * as ONE screen behind the hub's SyncPlay & casting entrance. Neither group
 * carries an advanced row, so the leaf has no advanced tier (no toggle, no
 * hidden hint), exactly the Ambient screen's shape.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SyncPlayCastingSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val syncPlayCastingGroups = listOf(
        SettingsScreenGroups.playbackSyncPlay.asScreenGroup { activePicker ->
            PlaybackSyncPlayGroup(
                preferences = preferences,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
        SettingsScreenGroups.playbackCasting.asScreenGroup { activePicker ->
            PlaybackCastingGroup(
                preferences = preferences,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_playback_entry_syncplay_casting),
        onBack = onBack,
        focusTag = "syncplay_casting_init",
        highlightSettingId = highlightSettingId,
        groups = syncPlayCastingGroups,
        pickerHost = true,
    )
}
