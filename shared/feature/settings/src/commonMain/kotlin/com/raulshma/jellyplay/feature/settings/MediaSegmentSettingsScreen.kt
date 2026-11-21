package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_entry_segments
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Skipping & segments leaf (ADR-0017): the media-segments group
 * ([PlaybackMediaSegmentsGroup]) as ONE advanced-only slot behind the hub's
 * Skipping & segments entrance. The group only composes behind the advanced
 * toggle, so the leaf carries the toggle and the derived hidden-settings
 * hint.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MediaSegmentSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val rowFlags = playbackRowAdmissionFlags(isTv = isTv, showAdvanced = showAdvanced, preferences = preferences)
    val scope = rememberCoroutineScope()

    val segmentGroups = listOf(
        SettingsScreenGroups.playbackMediaSegments.asScreenGroup(advancedOnly = true) { activePicker ->
            PlaybackMediaSegmentsGroup(
                preferences = preferences,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_playback_entry_segments),
        onBack = onBack,
        focusTag = "media_segments_init",
        highlightSettingId = highlightSettingId,
        groups = segmentGroups,
        advancedToggle = PreferenceAdvancedToggle(
            showAdvanced = showAdvanced,
            onToggle = { viewModel.setShowAdvancedSettings(!showAdvanced) },
        ),
        hiddenHint = PreferenceHiddenHint(
            rowFlags = rowFlags,
            onShowAdvanced = { viewModel.setShowAdvancedSettings(true) },
        ),
        pickerHost = true,
    )
}
