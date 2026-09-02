package com.raulshma.jellyplay.feature.settings

import com.raulshma.jellyplay.core.ui.components.SheetHeader
import com.raulshma.jellyplay.core.ui.components.TvSafeSheet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.expressiveListShape
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.*
import org.jetbrains.compose.resources.stringResource
import com.raulshma.jellyplay.feature.settings.generated.resources.Res
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_confirm
import com.raulshma.jellyplay.feature.settings.generated.resources.settings_save

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsChipPickerSheet(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    // Horizontal chips stay legible only for short option sets; otherwise the
    // equal-weight row squeezes each chip and the text wraps vertically. Fall
    // back to the vertical list layout used by SettingsListPickerSheet when
    // there are too many options or any label is too long to fit on one line.
    val useVertical = options.size > MAX_HORIZONTAL_CHIPS ||
        options.any { it.length > MAX_HORIZONTAL_CHIP_LABEL_CHARS }

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = title)
            if (useVertical) {
                LazyColumn {
                    itemsIndexed(
                        options,
                        key = { _, label -> label },
                        contentType = { _, _ -> "option" },
                    ) { index, label ->
                        ChipOptionRow(
                            label = label,
                            selected = index == selectedIndex,
                            index = index,
                            count = options.size,
                            onClick = { onSelect(index); onDismiss() },
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    options.forEachIndexed { index, label ->
                        val isSelected = index == selectedIndex
                        FilterChip(
                            selected = isSelected,
                            onClick = { onSelect(index); onDismiss() },
                            label = {
                                Text(
                                    label,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.weight(1f),
                            shape = ShapeCache.smoothPill,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary,
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                borderColor = Color.Transparent,
                                selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                enabled = true,
                                selected = isSelected,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Max options rendered as horizontal chips before falling back to a vertical list.
 * Beyond this the equal-weight row squeezes each chip and labels wrap vertically.
 */
private const val MAX_HORIZONTAL_CHIPS = 4

/**
 * Max characters per chip label for the horizontal layout. Longer labels (e.g.
 * "Track Normalization") overflow a single line and get squished.
 */
private const val MAX_HORIZONTAL_CHIP_LABEL_CHARS = 8

/**
 * A single vertical option row matching [SettingsListPickerSheet]'s styling, so
 * the horizontal chip fallback reads as the same picker rather than a different
 * component.
 */
@Composable
private fun ChipOptionRow(
    label: String,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val shape = when {
        count == 1 -> ShapeCache.smooth16
        index == 0 || index == count - 1 -> expressiveListShape(index, count)
        else -> ShapeCache.smooth8
    }
    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp)
            .clip(shape)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
            )
            .then(tvFocusState.focusModifier)
            .tvFocusIndicator(tvFocusState, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface,
        )
        if (selected) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Tabler.Outline.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SettingsListPickerSheet(
    title: String,
    items: List<T>,
    label: (T) -> String,
    subtitle: (T) -> String = { "" },
    isSelected: (T) -> Boolean,
    onDismiss: () -> Unit,
    onSelect: (T) -> Unit,
) {
    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = title)
            LazyColumn {
                itemsIndexed(items, key = { _, item -> label(item) }, contentType = { _, _ -> "option" }) { index, item ->
                    val selected = isSelected(item)
                    val shape = when {
                        items.size == 1 -> ShapeCache.smooth16
                        index == 0 || index == items.lastIndex -> expressiveListShape(index, items.size)
                        else -> ShapeCache.smooth8
                    }

                    val tvFocusState = rememberTvFocusState(focusedScale = 1.01f)

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .clip(shape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f),
                            )
                            .then(tvFocusState.focusModifier)
                            .tvFocusIndicator(tvFocusState, shape)
                            .clickable {
                                onSelect(item)
                                onDismiss()
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                label(item),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                ),
                                color = if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface,
                            )
                            subtitle(item).takeIf { it.isNotBlank() }?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Tabler.Outline.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSliderSheet(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    rangeStartLabel: String,
    rangeEndLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit,
) {
    var sliderValue by remember(value) { mutableStateOf(value) }

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = title)
            Text(
                valueLabel(sliderValue),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(16.dp))
            Slider(
                value = sliderValue,
                onValueChange = { sliderValue = it },
                valueRange = valueRange,
                steps = steps,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    rangeStartLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    rangeEndLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
            androidx.compose.material3.Button(
                onClick = { onConfirm(sliderValue) },
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCache.smoothPill,
            ) {
                Text(stringResource(Res.string.settings_confirm))
            }
        }
    }
}

// Note: the TV-vs-Mobile sheet branch lives canonically in
// `core/ui/.../components/TvSafeSheet.kt`. The local `AdaptiveSheet` wrapper that used to forward
// to it (and to a divergent Material3 `ModalBottomSheet` on mobile) was collapsed — callers now
// invoke `TvSafeSheet` directly.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsTextPickerSheet(
    title: String,
    initialText: String,
    helperText: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var text by remember(initialText) { mutableStateOf(initialText) }

    TvSafeSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
        ) {
            SheetHeader(title = title)
            if (helperText.isNotEmpty()) {
                Text(
                    helperText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.None,
                ),
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(
                onClick = { onSave(text) },
                modifier = Modifier.fillMaxWidth(),
                shape = ShapeCache.smoothPill,
            ) {
                Text(stringResource(Res.string.settings_save))
            }
        }
    }
}
