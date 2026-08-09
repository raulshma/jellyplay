package com.raulshma.jellyplay.feature.livetv.channeldetail

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.raulshma.jellyplay.core.designsystem.theme.ShapeCache
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.composables.icons.tabler.Tabler
import com.composables.icons.tabler.outline.PlayerPlay
import com.composables.icons.tabler.outline.Video
import com.composables.icons.tabler.outline.VideoPlus
import com.composables.icons.tabler.outline.X
import com.raulshma.jellyplay.core.model.LiveTvProgram
import com.raulshma.jellyplay.core.ui.adaptive.LocalAdaptiveInfo
import com.raulshma.jellyplay.core.ui.adaptive.contentPadding
import com.raulshma.jellyplay.core.ui.components.focusIndicator
import com.raulshma.jellyplay.core.ui.components.LocalReducedMotion
import com.raulshma.jellyplay.core.ui.tv.LocalTvMode
import com.raulshma.jellyplay.core.ui.tv.tvFocusRestorer
import com.raulshma.jellyplay.feature.livetv.R
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

/**
 * The channel detail content: header + Watch Live, now-playing hero (with live
 * progress bar), and the "On Today" timeline of upcoming programs.
 *
 * Record / Record Series actions live on each program timeline row (per-program
 * semantics, matching the jellyfin official client) — not on the channel
 * header.
 */
@Composable
internal fun ChannelDetailContent(
    state: ChannelDetailUiState,
    onPlayChannel: () -> Unit,
    onRecord: (LiveTvProgram) -> Unit,
    onRecordSeries: (LiveTvProgram) -> Unit,
    onCancelTimer: (LiveTvProgram) -> Unit,
    onCancelSeries: (LiveTvProgram) -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptiveInfo = LocalAdaptiveInfo.current
    val isTv = LocalTvMode.current
    val contentPad = adaptiveInfo.contentPadding(isTv)

    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .focusGroup()
            .tvFocusRestorer(),
        contentPadding = PaddingValues(start = contentPad, end = contentPad, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // ── Header: logo/name/number + Watch Live ──
        item(key = "header") {
            ChannelHeader(state = state, onWatchLive = onPlayChannel)
        }

        // ── Now-playing hero ──
        val current = state.currentProgram
        if (current != null) {
            item(key = "hero_${current.id}") {
                NowPlayingHero(program = current)
            }
        }

        // ── On Today timeline ──
        item(key = "timeline_header") {
            Text(
                text = stringResource(R.string.livetv_channel_on_today),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
        }
        if (state.programs.isEmpty()) {
            item(key = "timeline_empty") {
                Text(
                    text = stringResource(R.string.livetv_channel_no_program_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val airingId = current?.id
            items(items = state.programs, key = { it.id }) { program ->
                ProgramTimelineRow(
                    program = program,
                    isAiring = program.id == airingId,
                    onRecord = onRecord,
                    onRecordSeries = onRecordSeries,
                    onCancelTimer = onCancelTimer,
                    onCancelSeries = onCancelSeries,
                )
            }
        }
    }
}

@Composable
private fun ChannelHeader(state: ChannelDetailUiState, onWatchLive: () -> Unit) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            text = state.channelName,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        state.channelNumber?.let { num ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = "Ch. $num",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.height(16.dp))
        // Watch Live — primary play action.
        Row(
            modifier = Modifier
                .clip(ShapeCache.smooth28)
                .background(MaterialTheme.colorScheme.primary)
                .focusIndicator(ShapeCache.smooth28)
                .clickable(onClick = onWatchLive)
                .padding(horizontal = 32.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Tabler.Outline.PlayerPlay,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.livetv_channel_watch_live),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/**
 * Material 3 Expressive split button pairing the single-episode and series
 * record actions. Leading button: Record / Cancel Recording (toggled by
 * [LiveTvProgram.timerId]). Trailing button: Record Series / Cancel Series
 * (toggled by [LiveTvProgram.seriesTimerId]).
 *
 * The trailing half is a checkable split-button segment by API contract; we
 * drive it as a one-shot action (fire on check-on, then uncheck) so it behaves
 * like a plain click — same pattern used by [PlayShuffleSplitButton].
 *
 * @param size button height in dp; passed to the SplitButton `*For(size)` shape
 *   / padding / icon factories so the leading label and trailing toggle scale
 *   together. Use 40.dp for hero placement, 32.dp inside dense program rows.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecordSplitButton(
    program: LiveTvProgram,
    size: androidx.compose.ui.unit.Dp,
    onRecord: () -> Unit,
    onRecordSeries: () -> Unit,
    onCancelTimer: () -> Unit,
    onCancelSeries: () -> Unit,
) {
    val hasTimer = !program.timerId.isNullOrEmpty()
    val hasSeriesTimer = !program.seriesTimerId.isNullOrEmpty()
    val leadingLabel = if (hasTimer) {
        stringResource(R.string.livetv_cancel_recording)
    } else {
        stringResource(R.string.livetv_record_once)
    }
    val leadingIcon = if (hasTimer) Tabler.Outline.X else Tabler.Outline.Video
    val trailingContentDescription = if (hasSeriesTimer) "Cancel series" else "Record series"
    val trailingIcon = if (hasSeriesTimer) Tabler.Outline.X else Tabler.Outline.VideoPlus

    // Trailing check state driven locally; we only react to check-on so each
    // tap fires exactly one action and the thumb snaps back.
    var trailingChecked by remember(program.id, hasSeriesTimer) { mutableStateOf(false) }

    val leadingColors = if (hasTimer) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.error,
            contentColor = MaterialTheme.colorScheme.onError,
        )
    } else {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
    val trailingColors = ButtonDefaults.buttonColors(
        containerColor = if (hasSeriesTimer) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (hasSeriesTimer) MaterialTheme.colorScheme.onError
        else MaterialTheme.colorScheme.onSecondaryContainer,
    )

    SplitButtonLayout(
        leadingButton = {
            SplitButtonDefaults.LeadingButton(
                onClick = { if (hasTimer) onCancelTimer() else onRecord() },
                shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(size),
                colors = leadingColors,
            ) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = leadingLabel,
                    modifier = Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(size)),
                )
                Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
                Text(
                    text = leadingLabel,
                    style = ButtonDefaults.textStyleFor(size),
                )
            }
        },
        trailingButton = {
            SplitButtonDefaults.TrailingButton(
                checked = trailingChecked,
                onCheckedChange = { checked ->
                    trailingChecked = checked
                    if (checked) {
                        if (hasSeriesTimer) onCancelSeries() else onRecordSeries()
                        // Snap back so the next tap re-fires deterministically.
                        trailingChecked = false
                    }
                },
                shapes = SplitButtonDefaults.trailingButtonShapesFor(size),
                contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(size),
                colors = trailingColors,
            ) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = trailingContentDescription,
                    modifier = Modifier.size(SplitButtonDefaults.trailingButtonIconSizeFor(size)),
                )
            }
        },
    )
}

@Composable
private fun NowPlayingHero(program: LiveTvProgram) {
    val progress = rememberLiveProgress(program)
    val reducedMotion = LocalReducedMotion.current
    val infinite = rememberInfiniteTransition(label = "liveDot")
    val dotAlpha by if (reducedMotion) {
        androidx.compose.runtime.mutableStateOf(1f)
    } else {
        infinite.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(animation = tween(900), repeatMode = RepeatMode.Reverse),
            label = "dotAlpha",
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(ShapeCache.smooth20)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = dotAlpha)),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.livetv_channel_live),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.error,
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = program.name,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        program.episodeTitle?.takeIf { it.isNotBlank() }?.let { ep ->
            Spacer(Modifier.height(2.dp))
            Text(
                text = ep,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
        )
        program.overview?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ProgramTimelineRow(
    program: LiveTvProgram,
    isAiring: Boolean,
    onRecord: (LiveTvProgram) -> Unit,
    onRecordSeries: (LiveTvProgram) -> Unit,
    onCancelTimer: (LiveTvProgram) -> Unit,
    onCancelSeries: (LiveTvProgram) -> Unit,
) {
    val airingProgress = if (isAiring) rememberLiveProgress(program) else 0f
    val time = program.startDate?.let {
        runCatching {
            OffsetDateTime.parse(it, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                .format(DateTimeFormatter.ofPattern("h:mm a"))
        }.getOrNull()
    } ?: "--"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(ShapeCache.smooth14)
            .background(
                if (isAiring) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge,
            color = if (isAiring) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(86.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = program.name,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (isAiring) FontWeight.SemiBold else FontWeight.Normal),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            program.episodeTitle?.takeIf { it.isNotBlank() }?.let { ep ->
                Text(
                    text = ep,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (isAiring) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { airingProgress },
                    modifier = Modifier
                        .width(120.dp)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        // Per-program record / record-series split button. Bound to this row's
        // program (not the channel) — matches the jellyfin official client,
        // where each EPG program carries its own Record affordance.
        RecordSplitButton(
            program = program,
            size = 32.dp,
            onRecord = { onRecord(program) },
            onRecordSeries = { onRecordSeries(program) },
            onCancelTimer = { onCancelTimer(program) },
            onCancelSeries = { onCancelSeries(program) },
        )
    }
}

/**
 * Live progress fraction (0..1) of a program, derived from its start/end ISO
 * strings. Computed once per composition (no periodic tick — refresh on open
 * only per the design decision).
 */
@Composable
private fun rememberLiveProgress(program: LiveTvProgram): Float {
    val now = Instant.now()
    val start = program.startDate?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0f
    val end = program.endDate?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return 0f
    val total = end.epochSecond - start.epochSecond
    return if (total <= 0) 0f
    else (((now.epochSecond - start.epochSecond).toFloat() / total)).coerceIn(0f, 1f)
}
