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
 * times. The "convert dialog enums to sealed classes" refactor promised
 * exhaustive `when` dispatch but delivered 0 — the type change happened, the
 * call-site pattern did not.
 *
 * `PickerState` carries the picker's *payload* (title, options, label,
 * selection predicate, select callback) instead of just an identity tag, so a
 * single [SettingsPickerDialog] composable can render any picker via an
 * exhaustive `when` over the sealed subtype itself. Call sites build a
 * `PickerState` and assign it to one `activePicker: PickerState<*>?` state
 * field; the ladder collapses to one call. The caller still owns dismissal
 * (clearing the active state) — it passes an [onDismiss] to
 * [SettingsPickerDialog] alongside the state.
 *
 * Variants mirror the existing sheet composables:
 *  - [List]   → `SettingsListPickerSheet`
 *  - [Chip]   → `SettingsChipPickerSheet`
 *  - [Slider] → `SettingsSliderSheet`
 *
 * Adding a variant forces the dispatcher to handle it: Kotlin's exhaustive
 * `when` over the sealed hierarchy is the discriminator — no parallel `Kind`
 * enum, no unchecked casts.
 */
sealed interface PickerState<out T> {

    /** Human-readable sheet title (already resolved from string resources). */
    val title: String

    @Immutable
    data class List<T>(
        override val title: String,
        val items: kotlin.collections.List<T>,
        val label: (T) -> String,
        val subtitle: (T) -> String = { "" },
        val isSelected: (T) -> Boolean,
        val onSelect: (T) -> Unit,
    ) : PickerState<T>

    @Immutable
    data class Chip(
        override val title: String,
        val options: kotlin.collections.List<String>,
        val selectedIndex: Int,
        val onSelect: (Int) -> Unit,
    ) : PickerState<Int>

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
    ) : PickerState<Float>

    /**
     * Free-form multiline text editor sheet — the mpv.conf escape hatch. The
     * user edits [initialText] in a monospace field and submits via [onSave].
     * Used by the raw mpv options editor; intentionally generic so other
     * free-form config (e.g. a future input.conf) can reuse it.
     */
    @Immutable
    data class Text(
        override val title: String,
        val initialText: String,
        val helperText: String = "",
        val onSave: (String) -> Unit,
    ) : PickerState<String>
}

/**
 * Builds a [PickerState.Chip] from a typed option list, centralizing the
 * `map { label }` + `indexOf(current)` shape that recurs across the settings
 * screens. [onSelect] receives the selected typed value (not the index), so
 * call sites need no captured list + re-index.
 *
 * When [current] is absent from [values], `indexOf` returns -1 and [defaultIndex]
 * is substituted (default 0) — pass the desired fallback position for pickers
 * that want a sensible default rather than "nothing selected".
 */
internal inline fun <reified T> pickerChip(
    title: String,
    values: kotlin.collections.List<T>,
    current: T,
    label: (T) -> String,
    defaultIndex: Int = 0,
    crossinline onSelect: (T) -> Unit,
): PickerState.Chip {
    val index = values.indexOf(current).let { if (it < 0) defaultIndex else it }
    return PickerState.Chip(
        title = title,
        options = values.map(label),
        selectedIndex = index,
        onSelect = { selected -> onSelect(values[selected]) },
    )
}

/**
 * Renders whichever [PickerState] is currently active via an exhaustive
 * `when` over the sealed subtype, or nothing for `null`. The single
 * dispatcher that replaces the per-screen `if`-ladders.
 *
 * [onDismiss] is the caller's "clear the active picker" callback — invoked
 * when the user dismisses the sheet (back gesture / scrim tap / swipe), and
 * again after a successful select/confirm so the caller can clear the state.
 */
@Composable
internal fun SettingsPickerDialog(
    state: PickerState<*>?,
    onDismiss: () -> Unit,
) {
    when (state) {
        null -> Unit
        is PickerState.List<*> -> {
            // Star-projection captures the element type; cast through Any? to satisfy the
            // sheet's invariant `(T) -> …` lambdas. Safe because `items: List<T>` and the
            // lambdas all share the same `T` from the originating `PickerState.List<T>`.
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
        is PickerState.Chip -> SettingsChipPickerSheet(
            title = state.title,
            options = state.options,
            selectedIndex = state.selectedIndex,
            onDismiss = onDismiss,
            onSelect = { index ->
                state.onSelect(index)
                onDismiss()
            },
        )
        is PickerState.Slider -> SettingsSliderSheet(
            title = state.title,
            value = state.value,
            valueRange = state.valueRange,
            steps = state.steps,
            valueLabel = state.valueLabel,
            rangeStartLabel = state.rangeStartLabel,
            rangeEndLabel = state.rangeEndLabel,
            onDismiss = onDismiss,
            onConfirm = { value ->
                state.onConfirm(value)
                onDismiss()
            },
        )
        is PickerState.Text -> SettingsTextPickerSheet(
            title = state.title,
            initialText = state.initialText,
            helperText = state.helperText,
            onDismiss = onDismiss,
            onSave = { text ->
                state.onSave(text)
                onDismiss()
            },
        )
    }
}
