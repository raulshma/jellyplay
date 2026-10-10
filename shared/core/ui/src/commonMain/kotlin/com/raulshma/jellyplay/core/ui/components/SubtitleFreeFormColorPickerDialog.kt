package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.generated.resources.Res
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_apply
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_cancel
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_hex
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_hue
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_invalid_hex
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_picker_title
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_saturation
import com.raulshma.jellyplay.core.ui.generated.resources.core_ui_color_value
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode

/**
 * Free-form subtitle color picker: HSV sliders plus a hex text field
 * (paste-friendly for values copied straight out of an mpv.conf line).
 * Accepts `#RRGGBB`, `#AARRGGBB`, and 3-digit shorthand; the hex field and
 * the sliders stay in two-way sync. Returns an ARGB int via
 * [onColorSelected] and closes itself on confirm or dismiss.
 */
@Composable
fun SubtitleFreeFormColorPickerDialog(
    initialColor: Color,
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
) {
    val initialHsv = remember { subtitleColorToHsv(initialColor.toArgb()) }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    var alpha by remember { mutableIntStateOf(initialColor.toArgb() ushr 24) }
    var hexValue by remember {
        mutableStateOf(TextFieldValue(formatHexColor(initialColor.toArgb())))
    }
    var hexError by remember { mutableStateOf(false) }
    val isTv = LocalTvMode.current

    /** The live picker color: alpha from the hex field, RGB from the sliders. */
    fun currentArgb(): Int =
        (alpha shl 24) or (subtitleHsvToColor(floatArrayOf(hue, saturation, value)) and 0x00FFFFFF)

    fun setFromHex(text: String) {
        val parsed = parseHexColorOrNull(text)
        if (parsed == null) {
            hexError = true
            return
        }
        hexError = false
        alpha = parsed ushr 24
        val parsedHsv = subtitleColorToHsv(parsed)
        hue = parsedHsv[0]
        saturation = parsedHsv[1]
        value = parsedHsv[2]
    }

    val previewColor = Color(currentArgb())

    /** Label + slider row for one HSV channel; any drag clears the hex error
     *  and re-syncs the hex field to the sliders. */
    @Composable
    fun HsvSliderRow(label: String, value: Float, valueRange: ClosedFloatingPointRange<Float>, onValueChange: (Float) -> Unit) {
        Text(label, style = MaterialTheme.typography.labelSmall)
        TvOrTouchSlider(
            value = value,
            onValueChange = {
                onValueChange(it)
                hexError = false
                hexValue = TextFieldValue(formatHexColor(currentArgb()))
            },
            valueRange = valueRange,
            isTv = isTv,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.core_ui_color_picker_title)) },
        text = {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(ShapeCache.smooth8)
                        .background(previewColor),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = hexValue,
                    onValueChange = {
                        hexValue = it
                        setFromHex(it.text)
                    },
                    label = { Text(stringResource(Res.string.core_ui_color_hex)) },
                    isError = hexError,
                    supportingText = if (hexError) {
                        { Text(stringResource(Res.string.core_ui_color_invalid_hex)) }
                    } else {
                        null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                HsvSliderRow(
                    label = stringResource(Res.string.core_ui_color_hue),
                    value = hue,
                    valueRange = 0f..360f,
                    onValueChange = { hue = it },
                )
                HsvSliderRow(
                    label = stringResource(Res.string.core_ui_color_saturation),
                    value = saturation,
                    valueRange = 0f..1f,
                    onValueChange = { saturation = it },
                )
                HsvSliderRow(
                    label = stringResource(Res.string.core_ui_color_value),
                    value = value,
                    valueRange = 0f..1f,
                    onValueChange = { value = it },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !hexError,
                onClick = {
                    onColorSelected(previewColor.toArgb())
                    onDismiss()
                },
            ) { Text(stringResource(Res.string.core_ui_color_apply)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.core_ui_color_cancel)) }
        },
    )
}
