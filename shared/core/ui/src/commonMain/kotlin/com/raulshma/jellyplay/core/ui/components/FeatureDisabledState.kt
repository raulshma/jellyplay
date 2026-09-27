package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.Database
import com.composables.icons.tabler.outline.Settings
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

/**
 * Full-screen "this feature is switched off" state, mirroring
 * [ScreenEmptyState] / [ScreenErrorState]: the feature's icon, a title +
 * body explaining the disabled feature, and a settings button for enabling
 * it. Strings are caller-resolved so each feature screen keeps its own copy
 * (this component owns no string resources); the icon is the *arr Database
 * mark both launch sites (arr-queue / upcoming calendar) shared.
 */
@Composable
fun FeatureDisabledState(
    title: String,
    body: String,
    ctaLabel: String,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Tabler.Outline.Database,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onOpenSettings, shape = ShapeCache.smooth12) {
                Icon(Tabler.Outline.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(ctaLabel)
            }
        }
    }
}
