package com.raulshma.jellyplay.feature.player.video.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.ClockPlay
import com.raulshma.jellyplay.core.ui.tv.components.TvOrTouchSlider
import com.raulshma.jellyplay.feature.player.video.R
import com.raulshma.jellyplay.feature.player.video.subtitle.SubtitleParserHelper
import com.raulshma.jellyplay.feature.player.video.subtitle.TimedCue
import androidx.compose.ui.res.stringResource
import kotlin.math.roundToLong

/**
 * G10: timestamp + cue-preview subtitle-sync helper.
 *
 * The user enters a media timestamp (mm:ss.mmm, defaulting to the current
 * playback position). The active subtitle line at that timestamp is rendered
 * between its previous (↑, dimmed) and next (↓, dimmed) neighbours. An offset
 * slider live-recomputes the active cue as it drags — positive offset shifts
 * subs later, negative earlier — so the user can visually dial in sync while
 * watching the highlighted line change. `onOffsetChange` is fired on
 * `onValueChangeFinished` to persist the correction.
 *
 * When [cues] is null (embedded/image subs that can't be text-parsed for
 * preview), an "unavailable" message replaces the cue stack.
 *
 * The offset and the timestamp are both in media-time ms, so the preview
 * computation is independent of playback speed.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun SubtitleSyncPreview(
    positionMs: () -> Long,
    currentOffsetMs: Long,
    cues: List<TimedCue>?,
    isTv: Boolean,
    onOffsetChange: (Long) -> Unit,
) {
    // Local slider value, initialised from the persisted offset. Keyed on
    // currentOffsetMs so an external change (DelayRow drag, measurer result,
    // engine hydration) moves the preview slider too; the sheet's own slider is
    // the only writer while it is being dragged (onOffsetChange fires only on
    // release), so a mid-drag reset is not possible.
    var offsetMs by remember(currentOffsetMs) { mutableLongStateOf(currentOffsetMs) }

    // Timestamp field. Initialised to the live position once; the user can edit
    // or refresh it via the "use current position" button.
    var timestampText by remember {
        mutableStateOf(formatTimestamp(positionMs()))
    }

    val positionUs = remember(timestampText) { parseTimestampMs(timestampText) * 1000L }

    val context = remember(cues, positionUs, offsetMs) {
        if (cues.isNullOrEmpty()) null
        else SubtitleParserHelper.findAdjacentCues(cues, positionUs, offsetMs * 1000L)
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.player_video_sync_preview_title),
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.player_video_sync_preview_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        // Timestamp field + "use current position" affordance.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = timestampText,
                onValueChange = { timestampText = sanitizeTimestamp(it) },
                label = { Text(stringResource(R.string.player_video_sync_preview_timestamp)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { timestampText = formatTimestamp(positionMs()) }) {
                Icon(
                    Tabler.Outline.ClockPlay,
                    contentDescription = stringResource(R.string.player_video_sync_preview_use_current),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (context == null) {
            // No cues parsed for this track (embedded/image subs).
            Text(
                stringResource(R.string.player_video_sync_preview_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CueStack(
                previous = context.previous,
                active = context.active,
                next = context.next,
            )
        }

        Spacer(Modifier.height(12.dp))

        // Offset slider — live-recomputes the active cue above as it drags.
        Text(
            stringResource(R.string.player_video_subtitle_offset, formatDelayLabel(offsetMs)),
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.height(4.dp))
        TvOrTouchSlider(
            value = offsetMs.toFloat(),
            onValueChange = { offsetMs = (it / 50f).roundToLong() * 50 },
            onValueChangeFinished = { onOffsetChange(offsetMs) },
            valueRange = -30000f..30000f,
            modifier = Modifier.fillMaxWidth().testTag("preview_offset_slider"),
            isTv = isTv,
            steps = 1199,
            dpadStep = 100f,
        )
    }
}

/**
 * Renders the previous (↑, dimmed) / active (highlighted) / next (↓, dimmed)
 * cue stack. `active` may be null when the timestamp falls in a gap; the
 * previous/next cues still provide context.
 */
@Composable
private fun CueStack(
    previous: TimedCue?,
    active: TimedCue?,
    next: TimedCue?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CueRow(
            cue = previous,
            arrow = "↑",
            emphasize = false,
            singleLine = true,
            testTag = "prev_cue",
        )
        CueRow(
            cue = active,
            arrow = "▶",
            emphasize = true,
            singleLine = false,
            testTag = "active_cue",
        )
        CueRow(
            cue = next,
            arrow = "↓",
            emphasize = false,
            singleLine = true,
            testTag = "next_cue",
        )
    }
}

@Composable
private fun CueRow(
    cue: TimedCue?,
    arrow: String,
    emphasize: Boolean,
    singleLine: Boolean,
    testTag: String,
) {
    val color = if (emphasize) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val weight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal
    val text = cue?.text?.toString() ?: "—"
    Row(verticalAlignment = Alignment.Top) {
        Text(
            arrow,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = weight),
            color = color,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            overflow = if (singleLine) TextOverflow.Ellipsis else TextOverflow.Visible,
            modifier = Modifier.testTag(testTag),
        )
    }
}

/** Formats milliseconds as mm:ss.mmm. */
private fun formatTimestamp(ms: Long): String {
    val clamped = ms.coerceAtLeast(0L)
    val totalSec = clamped / 1000
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val millis = clamped % 1000
    return "%02d:%02d.%03d".format(minutes, seconds, millis)
}

/** Parses an mm:ss.mmm string to milliseconds; returns 0 on parse failure. */
private fun parseTimestampMs(text: String): Long {
    val match = Regex("(\\d+):(\\d{1,2})(?:\\.(\\d{1,3}))?").find(text.trim()) ?: return 0L
    val minutes = match.groupValues[1].toLongOrNull() ?: return 0L
    val seconds = match.groupValues[2].toLongOrNull() ?: return 0L
    val millis = match.groupValues[3].takeIf { it.isNotBlank() }?.padEnd(3, '0').orEmpty()
    val ms = millis.toLongOrNull() ?: 0L
    return minutes * 60_000L + seconds * 1_000L + ms
}

/**
 * Restricts input to the mm:ss.mmm shape as the user types, preserving cursor
 * position reasonably. Allows partial intermediate forms (e.g. "1", "1:2").
 */
private fun sanitizeTimestamp(input: String): String {
    // Keep digits, ':' and '.' only.
    val cleaned = input.filter { it.isDigit() || it == ':' || it == '.' }
    // At most one ':' and one '.'.
    val firstColon = cleaned.indexOf(':')
    val firstDot = cleaned.indexOf('.')
    return buildString {
        var colonSeen = false
        var dotSeen = false
        for (ch in cleaned) {
            when {
                ch == ':' && !colonSeen && firstDot == -1 -> {
                    append(ch); colonSeen = true
                }
                ch == '.' && !dotSeen && colonSeen -> {
                    append(ch); dotSeen = true
                }
                ch.isDigit() -> append(ch)
            }
        }
    }.take(12) // mm:ss.mmm max length
}
