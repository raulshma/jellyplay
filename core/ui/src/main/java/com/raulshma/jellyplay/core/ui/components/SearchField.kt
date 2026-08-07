package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Search
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.R

/**
 * The shared single-line search field used by the Requests, Devices and Logs
 * screens (and any future screen needing the same affordance). A leading
 * magnifier, a trailing clear-X that only appears when the field is non-empty,
 * the smooth12 shape, and the focused-primary / unfocused-outlineVariant border
 * pair — extracted so the three prior verbatim copies stay in sync.
 *
 * @param value current query text.
 * @param onValueChange called on every keystroke and when the clear X is tapped
 *   (with `""`).
 * @param placeholder localized hint shown when empty.
 * @param modifier outer modifier; defaults to fill-max-width.
 */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = {
            Text(
                placeholder,
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        leadingIcon = {
            Icon(
                Tabler.Outline.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = if (value.isNotEmpty()) {
            {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(
                        Tabler.Outline.X,
                        contentDescription = stringResource(R.string.core_clear),
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else null,
        singleLine = true,
        shape = ShapeCache.smooth12,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colorScheme.primary,
            unfocusedBorderColor = colorScheme.outlineVariant.copy(alpha = 0.5f),
        ),
        textStyle = MaterialTheme.typography.bodyMedium,
    )
}
