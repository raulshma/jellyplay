package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.ui.message.LocalUserMessageBus
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_redetect_done
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_redetect_failed
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_entry_video_quality
import org.koin.compose.viewmodel.koinViewModel

/**
 * The Video & transcoding leaf (ADR-0017): the whole Advanced Video group
 * ([PlaybackAdvancedVideoGroup]) as ONE advanced-only slot behind the hub's
 * Video & transcoding entrance. The group only composes behind the advanced
 * toggle, so the leaf carries the toggle and the derived hidden-settings
 * hint. The Re-detect action row lives here, so the one-shot Re-detect
 * confirmations (issue #186 §5.10, the PrivacyData screen-forward pattern)
 * resolve their resource text here and post through the app-wide
 * UserMessageBus — the effect the former combined screen hosted.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaybackVideoQualityScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val rowFlags = playbackRowAdmissionFlags(isTv = isTv, showAdvanced = showAdvanced, preferences = preferences)
    val scope = rememberCoroutineScope()

    val bus = LocalUserMessageBus.current
    LaunchedEffect(bus) {
        viewModel.redetectMessages.collect { message ->
            when (message) {
                is PerformanceRedetectMessage.Detected ->
                    bus.info(getString(Res.string.settings_redetect_done, message.tier.displayName))
                PerformanceRedetectMessage.Failed ->
                    bus.info(getString(Res.string.settings_redetect_failed))
            }
        }
    }

    val videoQualityGroups = listOf(
        SettingsScreenGroups.playbackAdvancedVideo.asScreenGroup(advancedOnly = true) { activePicker ->
            PlaybackAdvancedVideoGroup(
                preferences = preferences,
                rowFlags = rowFlags,
                highlightSettingId = highlightSettingId,
                viewModel = viewModel,
                scope = scope,
                activePicker = activePicker,
            )
        },
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_playback_entry_video_quality),
        onBack = onBack,
        focusTag = "playback_video_quality_init",
        highlightSettingId = highlightSettingId,
        groups = videoQualityGroups,
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
