package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_entry_live_tv
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Live TV & DVR leaf (ADR-0017): the DVR group (recording pre/post
 * padding and quality) as ONE screen behind the hub's Live TV & DVR
 * entrance. The group carries no advanced row, so the leaf has no advanced
 * tier (no toggle, no hidden hint).
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LiveTvDvrSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    val dvrGroups = listOf(
        SettingsScreenGroups.playbackDvr.asScreenGroup { activePicker ->
            PlaybackDvrGroup(
                preferences = preferences,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_playback_entry_live_tv),
        onBack = onBack,
        focusTag = "live_tv_dvr_init",
        highlightSettingId = highlightSettingId,
        groups = dvrGroups,
        pickerHost = true,
    )
}
