package com.raulshma.jellyplay.feature.settings

import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.HandMove
import com.composables.icons.tabler.outline.Keyboard
import com.composables.icons.tabler.outline.PlayerTrackNext
import com.composables.icons.tabler.outline.Refresh
import com.composables.icons.tabler.outline.Settings
import com.raulshma.jellyplay.core.model.InputPattern
import com.raulshma.jellyplay.core.model.PlayerAction
import com.raulshma.jellyplay.core.model.PlayerBinding
import com.raulshma.jellyplay.core.model.PlayerInputDefaults
import com.raulshma.jellyplay.core.model.bindableToDiscreteRow
import com.raulshma.jellyplay.core.ui.components.SettingListItem
import com.raulshma.jellyplay.core.ui.components.SettingToggleItem
import com.raulshma.jellyplay.core.ui.model.localizedDisplayName
import com.raulshma.jellyplay.core.ui.model.verticalSwipeLabel
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_reset
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_section_keyboard
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_section_mouse
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_section_touch
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_section_tv
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_subtitle_off
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_subtitle_on
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_input_bindings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The player input-binding editor (issue #171 generalized): one row per
 * bindable input, each carrying its action (the picker) and its enabled
 * flag (the subtitle; the picker's "Unbound" arms it off without erasing
 * the action — issue #171's disable semantics). Sections group the
 * surfaces: touch, mouse & scroll, keyboard, TV remote. List order is the
 * resolver's priority; exact duplicates are blocked at write time, so no
 * row here can shadow another's pattern.
 */
@Composable
fun InputBindingsScreen(
    onBack: () -> Unit,
    viewModel: InputBindingsViewModel = koinViewModel(),
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val map = preferences.videoInputBindings

    PreferenceScreenScaffold(
        title = stringResource(Res.string.settings_input_bindings_title),
        onBack = onBack,
        focusTag = "input_bindings_init",
        pickerHost = true,
    ) { activePicker ->
        val (touch, nonTouch) = map.bindings.partition { PlayerInputDefaults.isTouchPattern(it.pattern) }
        val (wheel, keyOrDpad) = nonTouch.partition { it.pattern is InputPattern.Wheel }
        val (keyboard, tv) = keyOrDpad.partition { it.pattern is InputPattern.Key }

        sectionHeader(Res.string.settings_input_bindings_section_touch)
        items(touch, key = { it.id }) { binding ->
            BindingRow(binding, Tabler.Outline.HandMove, viewModel, activePicker)
        }
        sectionHeader(Res.string.settings_input_bindings_section_mouse)
        items(wheel, key = { it.id }) { binding ->
            BindingRow(binding, Tabler.Outline.Settings, viewModel, activePicker)
        }
        sectionHeader(Res.string.settings_input_bindings_section_keyboard)
        items(keyboard, key = { it.id }) { binding ->
            BindingRow(binding, Tabler.Outline.Keyboard, viewModel, activePicker)
        }
        sectionHeader(Res.string.settings_input_bindings_section_tv)
        items(tv, key = { it.id }) { binding ->
            BindingRow(binding, Tabler.Outline.PlayerTrackNext, viewModel, activePicker)
        }

        item(key = "reset") {
            SettingListItem(
                icon = Tabler.Outline.Refresh,
                title = stringResource(Res.string.settings_input_bindings_reset),
                subtitle = "",
                onClick = { viewModel.resetToDefaults() },
            )
        }
    }
}

private fun LazyListScope.sectionHeader(titleRes: StringResource) {
    item {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
        )
    }
}

/**
 * One binding row: title = the input, subtitle = on/off + action, row click =
 * the action picker, trailing switch = the enabled flag (issue #171's
 * toggle). "Unbound" in the picker is the explicit erase. Picker labels
 * resolve in composable scope (the sheet's label lambda runs outside
 * composition).
 */
@Composable
private fun BindingRow(
    binding: PlayerBinding,
    sectionIcon: ImageVector,
    viewModel: InputBindingsViewModel,
    activePicker: MutableState<PickerState<*>?>,
) {
    val actionLabel = binding.action.localizedDisplayName()
    // The vertical-swipe halves label side + THIS row's action — the
    // side→action pairing is config, so a swapped pairing must not keep
    // showing the factory word.
    val patternTitle = when (val pattern = binding.pattern) {
        is InputPattern.VerticalSwipe -> verticalSwipeLabel(pattern.side, binding.action)
        else -> binding.pattern.localizedDisplayName()
    }
    val enabledLabel = stringResource(
        if (binding.enabled) Res.string.settings_input_bindings_subtitle_on
        else Res.string.settings_input_bindings_subtitle_off,
    )
    // Pre-resolved picker labels: the sheet's label lambda runs outside
    // composition (the orientation-row pattern).
    val actionLabels = PlayerAction.entries.associateWith { action -> action.localizedDisplayName() }
    SettingToggleItem(
        icon = sectionIcon,
        title = patternTitle,
        subtitle = if (binding.action == PlayerAction.NONE) {
            enabledLabel
        } else {
            "$enabledLabel · $actionLabel"
        },
        checked = binding.enabled,
        onCheckedChange = { enabled -> viewModel.setBindingEnabled(binding.id, enabled) },
        onClick = {
            activePicker.value = PickerState.List(
                title = patternTitle,
                // Only actions with a discrete effect (plus NONE = the
                // explicit erase) — the continuous gesture class would be a
                // dead binding on any rebound (see
                // PlayerAction.bindableToDiscreteRow).
                items = PlayerAction.entries.filter { it == PlayerAction.NONE || it.bindableToDiscreteRow },
                label = { actionLabels.getValue(it) },
                isSelected = { it == binding.action },
                onSelect = { action -> viewModel.setBindingAction(binding.id, action) },
            )
        },
    )
}
