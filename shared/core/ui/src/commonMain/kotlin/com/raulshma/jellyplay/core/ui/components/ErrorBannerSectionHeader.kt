package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertTriangle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.ui.tv.rememberTvFocusState
import com.raulshma.jellyplay.core.ui.tv.tvFocusIndicator

/**
 * Inline, dismissible error banner for failed in-place operations (save, install,
 * upload, ...). Companion to [ErrorScreen]: where [ErrorScreen] replaces the whole
 * screen for unrecoverable load failures, this renders inside existing content so
 * partial state around it stays visible and interactive.
 *
 * The dismiss label is a caller parameter rather than a core/ui resource because
 * features localize it differently (e.g. de "Schließen" vs "Ausblenden").
 */
@Composable
fun ErrorBanner(
    message: String,
    onDismiss: () -> Unit,
    dismissLabel: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = ShapeCache.smooth16,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Tabler.Outline.AlertTriangle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onErrorContainer,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f),
            )
            val dismissFocusState = rememberTvFocusState(focusedScale = 1.05f)
            TextButton(
                onClick = onDismiss,
                shape = ShapeCache.smooth12,
                modifier = Modifier
                    .then(dismissFocusState.focusModifier)
                    .tvFocusIndicator(dismissFocusState, ShapeCache.smooth12),
            ) {
                Text(dismissLabel)
            }
        }
    }
}

/**
 * Section label for grouped list content: `titleMedium` SemiBold on `onSurface`,
 * horizontally aligned with sibling rows via [contentPad]. On TV the section is
 * not focusable (it is a label, not a control).
 */
@Composable
fun SectionHeader(
    title: String,
    contentPad: Dp,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.padding(horizontal = contentPad, vertical = 6.dp),
    )
}
