package com.raulshma.jellyplay.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.AlertCircle
import com.composables.icons.tabler.outline.InfoCircle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache

/**
 * The canonical app-wide snackbar host. Renders every snackbar with the shared
 * "transcode pill" styling first introduced for the video-player force-transcode
 * toast (`Switched to transcoded stream — re-buffering`): a smooth pill with a
 * 1dp border, 6dp elevation, a leading icon and a bold action label.
 *
 * Error vs. info is inferred from the snackbar duration — the convention already
 * used app-wide, where `UserMessage.Error` is shown with [SnackbarDuration.Long]
 * and `UserMessage.Info` with [SnackbarDuration.Short]. Callers therefore don't
 * need to pass a severity; they only set the duration.
 *
 * Each screen keeps its own positioning (alignment / bottom-clearance padding) —
 * pass it via [modifier]. Only the visual styling is unified here.
 *
 * @see JellyPlaySnackbarContent for the content-only variant used by hosts that
 * keep their own `SnackbarHost` for positioning reasons.
 */
@Composable
fun JellyPlaySnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        JellyPlaySnackbarContent(snackbarData = data)
    }
}

/**
 * The shared snackbar content (pill surface + icon + message + optional action).
 * Extracted verbatim from `JellyPlayApp.kt`'s root host so every screen renders
 * an identical pill. Error/info styling is chosen by [snackbarData]'s duration.
 */
@Composable
fun JellyPlaySnackbarContent(
    snackbarData: SnackbarData,
    modifier: Modifier = Modifier,
) {
    val isError = snackbarData.visuals.duration == SnackbarDuration.Long
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.95f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val iconColor = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.primary
    }
    val borderColor = if (isError) {
        MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
    }
    val icon = if (isError) {
        Tabler.Outline.AlertCircle
    } else {
        Tabler.Outline.InfoCircle
    }

    Surface(
        shape = ShapeCache.smoothPill,
        color = containerColor,
        contentColor = contentColor,
        shadowElevation = 6.dp,
        border = BorderStroke(width = 1.dp, color = borderColor),
        modifier = modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .widthIn(max = 480.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = snackbarData.visuals.message,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
            if (snackbarData.visuals.actionLabel != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = snackbarData.visuals.actionLabel!!,
                    style = MaterialTheme.typography.labelMedium,
                    color = iconColor,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { snackbarData.performAction() },
                )
            }
        }
    }
}
