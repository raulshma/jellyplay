package com.raulshma.jellyplay.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Pins the pure payload logic of [PickerState] — the self-describing picker
 * spec the screens build instead of dialog enums (SettingsPickerDialog itself
 * is Compose and stays screen-verified):
 *
 *  - [pickerChip] centralizes the `map { label }` + `indexOf(current)` shape:
 *    options render the labels, [PickerState.Chip.selectedIndex] points at the
 *    current value, and the select callback routes the chosen INDEX back to
 *    the corresponding TYPED value (so call sites need no captured list).
 *  - A current value absent from the list falls back to [pickerChip]'s
 *    `defaultIndex` (default 0) instead of the raw `indexOf` -1.
 *  - The [PickerState.Slider] / [PickerState.Text] / [PickerState.List]
 *    variants carry their confirm/save/label lambdas as payload.
 */
class PickerStateTest {

    private enum class Fruit { APPLE, BANANA, CHERRY }

    @Test
    fun `pickerChip maps values to labels and selects the current value`() {
        val values = listOf(Fruit.APPLE, Fruit.BANANA, Fruit.CHERRY)

        val chip = pickerChip(
            title = "Pick a fruit",
            values = values,
            current = Fruit.CHERRY,
            label = { it.name.lowercase() },
        ) {}

        assertEquals("Pick a fruit", chip.title)
        assertEquals(listOf("apple", "banana", "cherry"), chip.options)
        assertEquals(2, chip.selectedIndex)
    }

    @Test
    fun `pickerChip falls back to the default index when current is absent`() {
        val values = listOf(Fruit.APPLE, Fruit.BANANA)

        val chip = pickerChip(
            title = "Fruits",
            values = values,
            current = Fruit.CHERRY, // not in values → indexOf would be -1
            label = { it.name.lowercase() },
        ) {}

        assertEquals(0, chip.selectedIndex, "absent current defaults to the first option")
    }

    @Test
    fun `pickerChip honors a custom default index for the absent current`() {
        val values = listOf(Fruit.APPLE, Fruit.BANANA)

        val chip = pickerChip(
            title = "Fruits",
            values = values,
            current = Fruit.CHERRY, // not in values
            label = { it.name.lowercase() },
            defaultIndex = 1,
        ) {}

        assertEquals(1, chip.selectedIndex)
    }

    @Test
    fun `pickerChip select routes the index back to the typed value`() {
        val values = listOf(Fruit.APPLE, Fruit.BANANA, Fruit.CHERRY)
        var picked: Fruit? = null

        val chip = pickerChip(
            title = "Fruits",
            values = values,
            current = Fruit.APPLE,
            label = { it.name.lowercase() },
        ) { picked = it }

        chip.onSelect(1)
        assertEquals(Fruit.BANANA, picked, "the callback must receive the VALUE, not the index")
    }

    @Test
    fun `chip select after a default fallback still maps to a real value`() {
        val values = listOf(Fruit.APPLE, Fruit.BANANA)
        var picked: Fruit? = null

        val chip = pickerChip(
            title = "Fruits",
            values = values,
            current = Fruit.CHERRY,
            label = { it.name.lowercase() },
        ) { picked = it }

        // The fallback index (0) must select a REAL value, never index -1 out.
        chip.onSelect(0)
        assertEquals(Fruit.APPLE, picked)
    }

    @Test
    fun `slider variant carries value, range and the confirm callback`() {
        var confirmed: Float? = null

        val slider = PickerState.Slider(
            title = "Crossfade",
            value = 4f,
            valueRange = 0f..12f,
            steps = 11,
            valueLabel = { "$it s" },
            rangeStartLabel = "off",
            rangeEndLabel = "12 s",
            onConfirm = { confirmed = it },
        )

        assertEquals("Crossfade", slider.title)
        assertEquals(4f, slider.value)
        // The label is caller-supplied raw float interpolation: 4f -> "4.0".
        assertEquals("4.0 s", slider.valueLabel(4f))
        slider.onConfirm(6.5f)
        assertEquals(6.5f, confirmed)
    }

    @Test
    fun `text variant carries the initial text and the save callback`() {
        var saved: String? = null

        val text = PickerState.Text(
            title = "mpv.conf",
            initialText = "hwdec=auto",
            onSave = { saved = it },
        )

        assertEquals("hwdec=auto", text.initialText)
        assertTrue(text.helperText.isEmpty(), "helper text defaults to empty")
        text.onSave("hwdec=auto\nvo=gpu")
        assertEquals("hwdec=auto\nvo=gpu", saved)
    }

    @Test
    fun `list variant carries label, subtitle and selection predicates`() {
        data class Voice(val name: String, val lang: String)

        val voices = listOf(Voice("Ana", "pt-BR"), Voice("Bo", "en-US"))

        val list = PickerState.List(
            title = "TTS voice",
            items = voices,
            label = { it.name },
            subtitle = { it.lang },
            isSelected = { it === voices[1] },
            onSelect = {},
        )

        assertEquals("Ana", list.label(voices[0]))
        assertEquals("en-US", list.subtitle(voices[1]))
        assertTrue(list.isSelected(voices[1]))
    }
}
