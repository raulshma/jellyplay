package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * A long [text] capped at [collapsedMaxLines] lines with a "Read more"/"Show less"
 * toggle. Extracted so detail screens (MediaDetailBody, PersonDetailScreen) share
 * one implementation instead of repeating the expand toggle inline.
 *
 * @param text              the body content.
 * @param collapsedMaxLines lines shown before the user expands; expands to all lines.
 * @param style             text style for the body (e.g. bodyLarge/bodyMedium).
 * @param color             body text color; defaults to [MaterialTheme.colorScheme].onSurfaceVariant.
 * @param toggleColor       toggle text color; defaults to [MaterialTheme.colorScheme].primary.
 * @param modifier          applied to the wrapping [Column].
 */
@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 4,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    toggleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier.padding(contentPadding)) {
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = if (expanded) "Show less" else "Read more",
            style = MaterialTheme.typography.labelMedium,
            color = toggleColor,
            modifier = Modifier
                .focusIndicator()
                .clickable { expanded = !expanded }
                .padding(top = 4.dp),
        )
    }
}
