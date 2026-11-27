package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.model.PlayerType
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_playback_entry_engines
import org.koin.compose.viewmodel.koinViewModel

/**
 * The playback engines leaf's ONE ordered group declaration — parameterized
 * by the preferred player because the engine slot's content-count hook is
 * the one runtime-keyed shape the flags vocabulary cannot express (only the
 * preferred player's rows exist at runtime, so the declared all-engines
 * group contributes its delta for that prefix alone; the screen-local reset
 * row cancels in the delta). The contract test pins the shipped JVM shape
 * (LIBVLC — the desktop seam backs the mpv device rows) plus the MPV
 * honesty pin against the SAME declaration, so the per-engine hook is
 * stated exactly once (ADR-0009's amendment). Per-group row emission stays
 * hand-written: the screen maps the slot to its body inline.
 */
internal fun PlayerEngineSettingsScreenSlots(
    preferredPlayer: PlayerType,
): List<PreferenceScreenGroup> = listOf(
    SettingsScreenGroups.playbackEngine.asScreenGroup(
        advancedOnly = true,
        emittedRows = { flags ->
            when (preferredPlayer) {
                PlayerType.MPV -> rowTotalFor(SettingsScreenGroups.playbackEngine, flags) { it.id.startsWith("mpv_") }
                PlayerType.LIBVLC -> rowTotalFor(SettingsScreenGroups.playbackEngine, flags) { it.id.startsWith("vlc_") }
                PlayerType.EXO_PLAYER -> rowTotalFor(SettingsScreenGroups.playbackEngine, flags) { it.id.startsWith("exo_") }
                PlayerType.EXTERNAL -> rowTotalFor(SettingsScreenGroups.playbackEngine, flags) { it.id == PlaybackRows.ExternalPlayerApp.id }
                else -> 0
            }
        },
    ),
)

/**
 * The Player engines leaf (ADR-0017): all four engine-config branches as ONE
 * advanced-only slot behind the hub's Player engines entrance — the branch
 * admission rides the preferred player exactly as on the former combined
 * screen ([PlayerEngineSettingsScreenSlots]). The group only composes behind
 * the advanced toggle, so the leaf carries the toggle and the derived
 * hidden-settings hint.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerEngineSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val showAdvanced by viewModel.showAdvancedSettings.collectAsStateWithLifecycle()
    val isTv = LocalTvMode.current
    val rowFlags = playbackRowAdmissionFlags(isTv = isTv, showAdvanced = showAdvanced, preferences = preferences)
    val scope = rememberCoroutineScope()

    val engineGroups = PlayerEngineSettingsScreenSlots(preferredPlayer = preferences.preferredPlayer).withContent(
        bodies = listOf(
            { activePicker ->
                PlaybackEngineGroup(
                    preferences = preferences,
                    rowFlags = rowFlags,
                    highlightSettingId = highlightSettingId,
                    viewModel = viewModel,
                    scope = scope,
                    activePicker = activePicker,
                )
            },
        ),
    )

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_playback_entry_engines),
        onBack = onBack,
        focusTag = "player_engines_init",
        highlightSettingId = highlightSettingId,
        groups = engineGroups,
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
