package com.raulshma.jellyplay.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * A self-describing spec for a settings picker sheet, rendered by
 * [SettingsPickerDialog].
 *
 * **Why this exists.** Every settings screen used to declare its own
 * `sealed class XxxSettingsDialog { object None; object Picker1; ... }`
 * hierarchy plus a ladder of `if (activeDialog == XxxSettingsDialog.PickerK)`
 * blocks — one per picker — each structurally identical: build an option
 * list, render `SettingsList/Chip/SliderSheet`, wire `isSelected` /
 * `onSelect` to a ViewModel setter. Across 9 screens that ladder ran ~110
 * times. The recent "convert dialog enums to sealed classes" refactor
 * promised exhaustive `when` dispatch but delivered 0 — the type change
 * happened, the call-site pattern did not.
 *
 * `PickerState` carries the picker's *payload* (title, options, label,
 * selection predicate, select callback) instead of just an identity tag, so a
 * single [SettingsPickerDialog] composable can render any picker via an
 * exhaustive `when` over the [kind]. Call sites build a `PickerState` and
 * assign it to one `activePicker: PickerState<*>?` state field; the ladder
 * collapses to one call. The caller still owns dismissal (clearing the active
 * state) — it passes an [onDismiss] to [SettingsPickerDialog] alongside the
 * state.
 *
 * Variants mirror the existing sheet composables:
 *  - [List]   → `SettingsListPickerSheet`
 *  - [Chip]   → `SettingsChipPickerSheet`
 *  - [Slider] → `SettingsSliderSheet`
 */
sealed interface PickerState<out T> {

    /** Human-readable sheet title (already resolved from string resources). */
    val title: String

    /**
     * Render-time discriminator. Kept as a separate enum so [SettingsPickerDialog]
     * can `when` over it exhaustively without reflective type checks, and so a
     * future picker variant added here forces the dispatcher to handle it.
     */
    val kind: Kind

    enum class Kind { List, Chip, Slider }

    @Immutable
    data class List<T>(
        override val title: String,
        val items: kotlin.collections.List<T>,
        val label: (T) -> String,
        val subtitle: (T) -> String = { "" },
        val isSelected: (T) -> Boolean,
        val onSelect: (T) -> Unit,
    ) : PickerState<T> {
        override val kind get() = Kind.List
    }

    @Immutable
    data class Chip(
        override val title: String,
        val options: kotlin.collections.List<String>,
        val selectedIndex: Int,
        val onSelect: (Int) -> Unit,
    ) : PickerState<Int> {
        override val kind get() = Kind.Chip
    }

    /**
     * Slider sheet — a continuous value with a confirm step. Mirrors
     * [SettingsSliderSheet]. [onConfirm] receives the confirmed float; the
     * dispatcher invokes [onDismiss] after [onConfirm].
     */
    @Immutable
    data class Slider(
        override val title: String,
        val value: Float,
        val valueRange: ClosedFloatingPointRange<Float>,
        val steps: Int,
        val valueLabel: (Float) -> String,
        val rangeStartLabel: String,
        val rangeEndLabel: String,
        val onConfirm: (Float) -> Unit,
    ) : PickerState<Float> {
        override val kind get() = Kind.Slider
    }
}

/**
 * Renders whichever [PickerState] is currently active via an exhaustive
 * `when` over its [PickerState.kind], or nothing for `null`. The single
 * dispatcher that replaces the per-screen `if`-ladders.
 *
 * [onDismiss] is the caller's "clear the active picker" callback — invoked
 * when the user dismisses the sheet (back gesture / scrim tap / swipe).
 */
@Composable
internal fun SettingsPickerDialog(
    state: PickerState<*>?,
    onDismiss: () -> Unit,
) {
    when (state?.kind) {
        null -> Unit
        PickerState.Kind.List -> {
            @Suppress("UNCHECKED_CAST")
            val list = state as PickerState.List<Any?>
            SettingsListPickerSheet(
                title = list.title,
                items = list.items,
                label = list.label,
                subtitle = list.subtitle,
                isSelected = list.isSelected,
                onDismiss = onDismiss,
                onSelect = { selected ->
                    list.onSelect(selected)
                    onDismiss()
                },
            )
        }
        PickerState.Kind.Chip -> {
            val chip = state as PickerState.Chip
            SettingsChipPickerSheet(
                title = chip.title,
                options = chip.options,
                selectedIndex = chip.selectedIndex,
                onDismiss = onDismiss,
                onSelect = { index ->
                    chip.onSelect(index)
                    onDismiss()
                },
            )
        }
        PickerState.Kind.Slider -> {
            val slider = state as PickerState.Slider
            SettingsSliderSheet(
                title = slider.title,
                value = slider.value,
                valueRange = slider.valueRange,
                steps = slider.steps,
                valueLabel = slider.valueLabel,
                rangeStartLabel = slider.rangeStartLabel,
                rangeEndLabel = slider.rangeEndLabel,
                onDismiss = onDismiss,
                onConfirm = { value ->
                    slider.onConfirm(value)
                    onDismiss()
                },
            )
        }
    }
}
