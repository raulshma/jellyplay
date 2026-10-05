package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.model.PlaybackPreferences
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.model.ExternalPlayerApp
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingsItemList
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import kotlinx.coroutines.CoroutineScope
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_engine_config

/** The `playback.engine` group header plus the per-engine branch dispatch. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun PlaybackEngineGroup(
    preferences: PlaybackPreferences,
    rowFlags: RowAdmissionFlags,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                SettingsGroup(
                    icon = Tabler.Outline.Settings,
                    title = stringResource(Res.string.settings_engine_config),
                    summary = { preferences.preferredPlayer.displayName },
                    modifier = Modifier.padding(vertical = 8.dp),
                    initiallyExpanded = highlightSettingId in SettingsScreenGroups.playbackEngine.itemIdSet,
                ) {
                    when (preferences.preferredPlayer) {
                        PlayerType.MPV -> {
                            PlaybackMpvEngineRows(
                                preferences = preferences,
                                rowFlags = rowFlags,
                                highlightSettingId = highlightSettingId,
                                viewModel = viewModel,
                                scope = scope,
                                activePicker = activePicker,
                            )
                        }
                        PlayerType.LIBVLC -> {
                            PlaybackVlcEngineRows(
                                preferences = preferences,
                                highlightSettingId = highlightSettingId,
                                viewModel = viewModel,
                                scope = scope,
                                activePicker = activePicker,
                            )
                        }
                        PlayerType.EXO_PLAYER -> {
                            PlaybackExoEngineRows(
                                preferences = preferences,
                                highlightSettingId = highlightSettingId,
                                viewModel = viewModel,
                                scope = scope,
                                activePicker = activePicker,
                            )
                        }
                        PlayerType.EXTERNAL -> {
                            PlaybackExternalEngineRows(
                                preferences = preferences,
                                highlightSettingId = highlightSettingId,
                                viewModel = viewModel,
                                scope = scope,
                                activePicker = activePicker,
                            )
                        }
                        else -> {}
                    }
                }
}

/** The `playback.engine` group's external hand-off branch rows. */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlaybackExternalEngineRows(
    preferences: PlaybackPreferences,
    highlightSettingId: String?,
    viewModel: PlaybackSettingsViewModel,
    scope: CoroutineScope,
    activePicker: MutableState<PickerState<*>?>
) {
                            // The branch's single declared row — there is no
                            // reset row (no engine-config object to reset) and
                            // nothing platform-gated, so the total is the
                            // declared admission over this one id.
                            SettingsItemList(
                                total = rowTotalFor(
                                    SettingsScreenGroups.playbackEngine,
                                    RowAdmissionFlags(showAdvanced = true),
                                ) { it.id == PlaybackRows.ExternalPlayerApp.id },
                            ) {
                            val externalAppTitle = rowTitle(PlaybackRows.ExternalPlayerApp)
                            SettingListItem(
                                icon = rowIcon(PlaybackRows.ExternalPlayerApp),
                                title = rowTitle(PlaybackRows.ExternalPlayerApp),
                                subtitle = rowSubtitle(PlaybackRows.ExternalPlayerApp),
                                trailingText = preferences.preferredExternalPlayer.displayName,
                                highlighted = highlightSettingId == PlaybackRows.ExternalPlayerApp.id,
                                onClick = {
                                    activePicker.value = PickerState.List(
                                        title = externalAppTitle,
                                        items = ExternalPlayerApp.entries,
                                        label = { it.displayName },
                                        isSelected = { it == preferences.preferredExternalPlayer },
                                        onSelect = { viewModel.edit { app -> app.playback.setPreferredExternalPlayer(it) } },
                                    )
                                },
                            )
                            }
}
