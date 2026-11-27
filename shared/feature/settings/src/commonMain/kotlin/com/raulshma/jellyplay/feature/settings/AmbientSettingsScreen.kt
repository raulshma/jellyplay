package com.raulshma.jellyplay.feature.settings

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_ambient
import org.koin.compose.viewmodel.koinViewModel

/**
 * Whether the Ambient screen can show content on this surface: the TV dream
 * group renders in TV mode, the desktop idle-ambient group only where the
 * platform backs the idle screen — the screen exists when EITHER gate can
 * open (ADR-0017's one Ambient domain under Look & feel; the root's Ambient
 * entrance reads the same fold).
 */
internal fun ambientScreenAdmitted(isTv: Boolean): Boolean =
    isTv || settingsCapabilities.supportsIdleAmbientScreen

/**
 * The Ambient settings screen (ADR-0017): the TV dream/screensaver group and
 * the desktop idle-ambient group as ONE capability-gated screen behind the
 * root's Look & feel Ambient entrance — the root's former inline tuning
 * groups, moved (IA-only: same rows, same stores, same gates) onto one
 * declared surface. The screensaver group composes only in TV mode
 * ([LocalTvMode]); the idle-ambient group only where
 * `settingsCapabilities.supportsIdleAmbientScreen` holds — hidden =
 * structurally absent, never rendered-then-disabled.
 *
 * The screen's ONE ordered group declaration drives the deep-link highlight
 * scroll (the shell derives it) — a search hit on a `screensaver_*` or
 * `idle_ambient_*` row routes here with the id baked in and lands on the
 * owning card, exactly the sub-screen contract. Pickers flow through the
 * scaffold's single [SettingsPickerDialog] host.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AmbientSettingsScreen(
    onBack: () -> Unit,
    highlightSettingId: String? = null,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val isTv = LocalTvMode.current
    val preferences = viewModel.preferences

    val ambientGroups = buildList {
        if (isTv) {
            add(
                SettingsScreenGroups.systemScreensaver.asScreenGroup { activePicker ->
                    SettingsScreensaverSection(
                        preferences = preferences,
                        viewModel = viewModel,
                        highlightSettingId = highlightSettingId,
                        activeDialog = activePicker,
                    )
                },
            )
        }
        // The desktop idle "Ready to play" ambient rows — capability-gated
        // (structurally absent on Android, whose lock-screen/screensaver story
        // owns idle display).
        if (settingsCapabilities.supportsIdleAmbientScreen) {
            add(
                SettingsScreenGroups.systemIdleAmbient.asScreenGroup { activePicker ->
                    SettingsIdleAmbientSection(
                        preferences = preferences,
                        viewModel = viewModel,
                        highlightSettingId = highlightSettingId,
                        activeDialog = activePicker,
                    )
                },
            )
        }
    }

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_ambient),
        onBack = onBack,
        focusTag = "ambient_init",
        highlightSettingId = highlightSettingId,
        groups = ambientGroups,
        pickerHost = true,
    )
}
