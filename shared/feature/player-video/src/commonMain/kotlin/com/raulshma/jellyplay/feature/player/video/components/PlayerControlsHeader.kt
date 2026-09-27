package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import com.raulshma.jellyplay.core.designsystem.theme.SyncStatusColors
import com.raulshma.jellyplay.feature.player.video.rememberIs24HourFormat
import com.raulshma.jellyplay.feature.player.video.generated.resources.Res
import com.raulshma.jellyplay.feature.player.video.generated.resources.player_video_ends_at
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.compose.resources.stringResource

// ── Section split: the header row's indicator + time labels ───────────────
// The SyncPlay header pill and the "Ends at HH:mm" label (+ its wall-clock
// helper). Moved verbatim from PlayerControls.kt as a composition-only
// section split (the VideoPlayerScreenOverlays.kt precedent): declarations
// are unchanged except `private` → `internal` where the root file calls the
// symbol (rememberEndsAtTime stays private — its only consumer,
// EndsAtLabel, moved with it). See the section-host map on PlayerControls.kt.

@Composable
internal fun SyncPlayHeaderIndicator(
    groupName: String,
    participantCount: Int,
    isSynced: Boolean,
    isSyncing: Boolean = false,
    onClick: () -> Unit,
) {
    val statusColor = when {
        isSynced -> SyncStatusColors.synced
        isSyncing -> SyncStatusColors.syncing
        else -> SyncStatusColors.else_
    }

    Surface(
        shape = ShapeCache.smoothPill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = statusColor,
                modifier = Modifier.size(7.dp),
            ) {}
            Text(
                text = when {
                    isSynced -> "Synced"
                    isSyncing -> "Syncing"
                    else -> "Buffering"
                },
                color = statusColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = groupName,
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = "$participantCount",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/**
 * Renders the "Ends at HH:mm" header label. Collects [currentPositionFlow]
 * here at the leaf so the 4 Hz position tick recomposes only this tiny
 * composable, not the entire PlayerControls body. `duration` and
 * `playbackSpeed` are stable/low-churn scalars, safe to pass by value.
 */
@Composable
internal fun EndsAtLabel(
    currentPositionFlow: StateFlow<Long>,
    duration: Long,
    playbackSpeed: Float,
    controlsVisible: Boolean,
) {
    val currentPosition by currentPositionFlow.collectAsStateWithLifecycle()
    val remainingMs = (duration - currentPosition).coerceAtLeast(0)
    val realRemainingMs = if (playbackSpeed > 0f) (remainingMs / playbackSpeed).toLong() else remainingMs
    val endsAt = rememberEndsAtTime(realRemainingMs, controlsVisible)
    Text(
        text = stringResource(Res.string.player_video_ends_at, endsAt),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
    )
}

@Composable
private fun rememberEndsAtTime(remainingMs: Long, controlsVisible: Boolean): String {
    val is24Hour = rememberIs24HourFormat()
    val pattern = if (is24Hour) "HH:mm" else "h:mm a"
    val formatter = remember(pattern) { SimpleDateFormat(pattern, Locale.getDefault()) }
    var currentSystemTime by remember { mutableStateOf(System.currentTimeMillis()) }
    // Only poll while the controls (and therefore the "ends-at" label) are
    // visible, and align the delay to the wall-clock minute boundary — the
    // label only needs minute precision, so sub-minute updates are invisible.
    // Was an unconditional 5 s poll for the lifetime of PlayerControls.
    LaunchedEffect(controlsVisible) {
        if (!controlsVisible) return@LaunchedEffect
        while (true) {
            currentSystemTime = System.currentTimeMillis()
            val msToNextMinute = 60_000L - (currentSystemTime % 60_000L)
            kotlinx.coroutines.delay(msToNextMinute.coerceAtLeast(1_000L))
        }
    }
    return remember((currentSystemTime + remainingMs) / 60_000L, formatter) {
        val endsAtDate = Date(currentSystemTime + remainingMs)
        formatter.format(endsAtDate)
    }
}
